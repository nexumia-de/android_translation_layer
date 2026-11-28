#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mman.h>

#include <fontconfig/fontconfig.h>
#include <glib.h>

/* Owner-configurable environment disguise, part 3: ATL_DECOY_LIB=<path>[@<hexbase>]
 * maps a real Android library (e.g. a bionic libc.so pulled from a device image)
 * into this process as an inert, read+exec decoy at a fixed base, so an
 * anti-tamper/RASP library that (a) reads the on-disk libc to resolve a symbol
 * and (b) calls <libc_base_from_maps> + <symbol_offset> directly (bypassing its
 * own PLT) lands in genuine library code instead of ATL's smaller shim. Pair
 * with ATL_FAKE_MAPS, whose libc.so region must name this same base. The decoy
 * is never relocated or initialised and is not the libc the app actually uses.
 * OFF unless the variable is set. See docs/environment-disguise.md. */
#ifndef ATL_DECOY_DEFAULT_BASE
#define ATL_DECOY_DEFAULT_BASE 0x5b0000000UL
#endif
__attribute__((constructor))
static void atl_map_decoy_lib(void)
{
	const char *spec = getenv("ATL_DECOY_LIB");
	if (!spec || !*spec)
		return;
	char path[4096];
	unsigned long base = ATL_DECOY_DEFAULT_BASE;
	const char *at = strchr(spec, '@');
	if (at) {
		size_t n = (size_t)(at - spec);
		if (n >= sizeof path) n = sizeof path - 1;
		memcpy(path, spec, n); path[n] = 0;
		base = strtoul(at + 1, NULL, 16);
	} else {
		snprintf(path, sizeof path, "%s", spec);
	}
	int fd = open(path, O_RDONLY);
	if (fd < 0) { fprintf(stderr, "ATL_DECOY: open %s failed\n", path); return; }
	off_t sz = lseek(fd, 0, SEEK_END);
	void *m = mmap((void *)base, (size_t)sz, PROT_READ | PROT_EXEC,
	               MAP_PRIVATE | MAP_FIXED_NOREPLACE, fd, 0);
	if (m == MAP_FAILED || (unsigned long)m != base)
		fprintf(stderr, "ATL_DECOY: mmap %s at %#lx failed (got %p)\n", path, base, m);
	else
		fprintf(stderr, "ATL_DECOY: mapped %s (%ld bytes) R-X at %#lx\n", path, (long)sz, base);
	close(fd);
}

/* helpers */
static bool starts_with(const char *string, const char *substring)
{
	return !strncmp(string, substring, strlen(substring));
}

static bool ends_with(const char *str, const char *suffix)
{
	if (!str || !suffix)
		return 0;

	size_t str_len = strlen(str);
	size_t suffix_len = strlen(suffix);
	if (suffix_len > str_len)
		return 0;
	return (strcmp(str + (str_len - suffix_len), suffix) == 0);
}

static bool file_exists(char *path)
{
	return (!access(path, F_OK));
}

/* use fontconfig to find a font */
static char *get_font_path(char *filename)
{
	int ret;

	/* just load the list once on first call to this function */
	static FcFontSet *font_set = NULL;
	if (!font_set) {
		ret = FcInit();
		if (!ret)
			return NULL;

		// match all fonts
		FcPattern *pattern = FcPatternCreate();
		if (!pattern)
			return NULL;

		FcObjectSet *object_set = FcObjectSetBuild(FC_FILE, NULL);
		if (!object_set) {
			FcPatternDestroy(pattern);
			return NULL;
		}

		// Retrieve the list of fonts
		font_set = FcFontList(NULL, pattern, object_set);
		if (!font_set) {
			FcObjectSetDestroy(object_set);
			FcPatternDestroy(pattern);
			return NULL;
		}
	}

	for (int i = 0; i < font_set->nfont; i++) {
		FcPattern *font = font_set->fonts[i];
		FcChar8 *file = NULL;
		if (FcPatternGetString(font, FC_FILE, 0, &file) == FcResultMatch && file) {
			if (ends_with((char *)file, filename)) {
				return (char *)file;
			}
		}
	}

	return NULL;
}

static char *fonts_overrides[] = {
	"/etc/fonts.xml",
	/* INSTALL_DATADIR will be something like /usr/local/share */
	INSTALL_DATADIR "/atl/system/etc/fonts.xml",
	/* Relative path when running from ./android_translation_layer/builddir */
	"../res/fonts.xml",
	NULL,
};

extern char *get_app_data_dir(void);

/* Android keeps the trusted CA certificates as <subject hash>.0 files in
 * /system/etc/security/cacerts (Dart's dart:io and other TLS code read them
 * there); distributions keep the same hashed links in their certificate
 * directory. */
static const char *cacerts_dirs[] = {
	"/etc/ssl/certs",
	"/etc/pki/ca-trust/extracted/pem/directory-hash",
	NULL,
};

static const char *get_cacerts_dir(void)
{
	static const char *dir = NULL;

	for (int i = 0; !dir && cacerts_dirs[i]; i++)
		if (g_file_test(cacerts_dirs[i], G_FILE_TEST_IS_DIR))
			dir = cacerts_dirs[i];
	return dir;
}

/* the Android process name (the package name), set once the app is parsed */
char *atl_process_name = NULL;

/* Android sets a process's argv[0] to its process name, and libraries (Firebase sessions, WorkManager
 * on old SDKs) read it from /proc/<pid>/cmdline. Here that file holds ATL's whole command line, which
 * Firebase base64-encodes into a file name longer than NAME_MAX, so its datastore can never be
 * written. Serve a file that holds only the process name instead. */
static bool is_own_cmdline(const char *path)
{
	if (!strcmp(path, "/proc/self/cmdline") || !strcmp(path, "/proc/thread-self/cmdline"))
		return true;
	char own[64];
	snprintf(own, sizeof(own), "/proc/%d/cmdline", getpid());
	return !strcmp(path, own);
}

static const char *get_cmdline_file(void)
{
	static char *file = NULL;
	static bool tried = false;

	if (tried)
		return file;
	tried = true;
	char *name = g_strdup_printf("atl-cmdline-%s", atl_process_name);
	char *candidate = g_build_filename(g_get_user_cache_dir(), name, NULL);
	g_free(name);
	g_mkdir_with_parents(g_get_user_cache_dir(), 0700);
	/* "name\0", like Android's cmdline */
	if (g_file_set_contents(candidate, atl_process_name, strlen(atl_process_name) + 1, NULL))
		file = candidate;
	else
		g_free(candidate);
	return file;
}

bool apply_path_overrides(char **path)
{
	bool free_path = false;

	if (atl_process_name && is_own_cmdline(*path)) {
		const char *cmdline_file = get_cmdline_file();
		if (cmdline_file) {
			*path = (char *)cmdline_file;
			return false;
		}
	}

	/* TODO: read the overrides from a config file */
	/* TODO: compare with canonicalized path */
	if (!strcmp(*path, "/system/etc/fonts.xml")) {
		for (char **override = fonts_overrides; *override; override++) {
			if (file_exists(*override)) {
				*path = *override;
				break;
			}
		}
	}

	/* AOSP seems to put all the fonts in `/system/fonts`. On a standard Linux distro, the font
	 * could be anywhere, so we need to get all the fonts fontconfig knows about and check
	 * if the filename matches. */
	if (starts_with(*path, "/system/fonts/")) {
		char *font_filename = strchr(*path + 8, '/'); // after /fonts
		char *new_path = get_font_path(font_filename);
		if (new_path)
			*path = new_path;
		else
			fprintf(stderr, "%s: !!! app trying to access a font at >%s<, but there is no >%s< in the fontconfig cache\n", __func__, *path, font_filename);
	}

	/* Some apps literally hardcode the external storage path (even with assumed userid=0). */
	if (starts_with(*path, "/storage/emulated/0/")) {
		char *suffix = *path + sizeof("/storage/emulated/0");
		char *new_path = g_strdup_printf("%s/%s", get_app_data_dir(), suffix);

		fprintf(stderr, "%s: !!! app trying to access >%s<, rewriting to >%s<\n", __func__, *path, new_path);

		*path = new_path;
		free_path = true;
	}

#define ANDROID_CACERTS "/system/etc/security/cacerts"
	if (starts_with(*path, ANDROID_CACERTS) && ((*path)[sizeof(ANDROID_CACERTS) - 1] == '\0' || (*path)[sizeof(ANDROID_CACERTS) - 1] == '/') && get_cacerts_dir()) {
		char *new_path = g_strconcat(get_cacerts_dir(), *path + sizeof(ANDROID_CACERTS) - 1, NULL);

		if (free_path)
			free(*path);
		*path = new_path;
		free_path = true;
	}

	/* Owner-configurable environment disguise: ATL_FAKE_SYSROOT=<dir> serves an
	 * Android-like /system and /apex tree from <dir> so an anti-tamper/RASP
	 * library that re-opens the modules it found in /proc/self/maps can read
	 * them. OFF unless the variable is set, so the engine behaves exactly as
	 * before for every app. Pairs with ATL_FAKE_MAPS (bionic). See the fork's
	 * docs/environment-disguise.md and decisions/gms-local-only.md. */
	{
		static const char *sysroot;
		static bool sysroot_inited;
		if (!sysroot_inited) { sysroot = getenv("ATL_FAKE_SYSROOT"); sysroot_inited = true; }
		if (sysroot && *sysroot && (starts_with(*path, "/system/") || starts_with(*path, "/apex/"))) {
			char *new_path = g_strconcat(sysroot, *path, NULL);
			if (file_exists(new_path)) {
				if (free_path)
					free(*path);
				*path = new_path;
				return true;
			}
			g_free(new_path);
		}
	}

	if (starts_with(*path, "/system/") || starts_with(*path, "/data/")) {
		fprintf(stderr, "%s: !!! app trying to access >%s<, which will certainly fail\n", __func__, *path);
		fflush(stderr);
	}

	return free_path;
}
