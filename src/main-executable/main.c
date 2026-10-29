// for dladdr
#define _GNU_SOURCE

#include <cairo-svg.h>
#include <gdk/wayland/gdkwayland.h>
#include <gio/gdesktopappinfo.h>
#include <gtk/gtk.h>
#include <libportal/portal.h>

#include "../api-impl-jni/defines.h"
#include "../api-impl-jni/util.h"
#include "../api-impl-jni/app/android_app_Activity.h"
#include "../api-impl-jni/content/android_content_Context.h"

#include "actions.h"
#include "back_button.h"
#include "libc_bio_path_overrides.h"
#include "prompt_page.h"

#include <dlfcn.h>
#include <glib/gstdio.h>
#include <errno.h>
#include <ftw.h>
#include <libgen.h>
#include <locale.h>
#include <stdio.h>
#include <sys/resource.h>
#include <sys/stat.h>

#ifndef DEFFILEMODE
	#define DEFFILEMODE (S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH) /* 0666*/
#endif

#ifdef __x86_64__
	#define NATIVE_ARCH "x86_64"
#elifdef __i386__
	#define NATIVE_ARCH "x86"
#elifdef __aarch64__
	#define NATIVE_ARCH "arm64-v8a"
#elifdef __arm__
	#define NATIVE_ARCH "armeabi-v7a"
#else
	#error unknown native architecture
#endif

GtkWidget *window;
char *apk_path;

// standard Gtk Application stuff, more or less

gboolean app_exit(GtkWindow *self, JNIEnv *env) // TODO: do more cleanup?
{
	activity_close_all();
	return false;
}

// this is the equivalent of /data/data/com.example.app/
char *app_data_dir = NULL;
char *get_app_data_dir()
{
	return app_data_dir;
}

char *construct_classpath(char *prefix, char **cp_array, size_t len)
{
	size_t result_len = strlen(prefix);
	for (int i = 0; i < len; i++) {
		if (cp_array[i])
			result_len += strlen(cp_array[i]) + 1; // the 1 is for either : or the final \0
	}

	char *result = malloc(result_len);
	strcpy(result, prefix);
	for (int i = 0; i < len; i++) {
		if (cp_array[i]) {
			if (i > 0)
				strcat(result, ":");
			strcat(result, cp_array[i]);
		}
	}

	return result;
}

#define JDWP_ARG    "-XjdwpOptions:transport=dt_socket,server=y,suspend=y,address="
#define SDK_INT_ARG "-DBuild.VERSION.SDK_INT="

JNIEnv *create_vm(char *api_impl_jar, char *apk_classpath, char *framework_res_apk, char *test_runner_jar, char *api_impl_natives_dir, char *app_lib_dir, char *sdk_int, char **extra_jvm_options)
{
	JavaVM *jvm;
	JNIEnv *env;
	JavaVMInitArgs args = {
		.version = JNI_VERSION_1_6,
		.nOptions = 5,
	};
	JavaVMOption *options;

	int option_counter = args.nOptions;

	char jdwp_option_string[sizeof(JDWP_ARG) + 5] = JDWP_ARG;          // 5 chars for port number, NULL byte is counted by sizeof
	char sdk_int_option_string[sizeof(SDK_INT_ARG) + 2] = SDK_INT_ARG; // 2 chars for SDK_INT, NULL byte is counted by sizeof

	const char *jdwp_port = getenv("JDWP_LISTEN");

	if (jdwp_port)
		args.nOptions += 2;
	if (sdk_int)
		args.nOptions += 1;
	if (extra_jvm_options)
		args.nOptions += g_strv_length(extra_jvm_options);
	options = malloc(sizeof(JavaVMOption) * args.nOptions);

	if (getenv("RUN_FROM_BUILDDIR")) {
		options[0].optionString = construct_classpath("-Djava.library.path=", (char *[]){"./", app_lib_dir}, 2);
	} else {
		options[0].optionString = construct_classpath("-Djava.library.path=", (char *[]){api_impl_natives_dir, app_lib_dir}, 2);
	}

	// ATLPaths relies on the first element in java.class.path being api-impl.jar
	options[1].optionString = construct_classpath("-Djava.class.path=", (char *[]){api_impl_jar, framework_res_apk, test_runner_jar}, 3);
	// ATLLoadedApp relies on the first element in atl.app.class.path being the main apk
	options[2].optionString = construct_classpath("-Datl.app.class.path=", (char *[]){apk_classpath}, 1);
	options[3].optionString = construct_classpath("-Datl.app.library.path=", (char *[]){api_impl_natives_dir, app_lib_dir}, 2);
	options[4].optionString = "-Xcheck:jni";
	if (jdwp_port) {
		strncat(jdwp_option_string, jdwp_port, 5); // 5 chars is enough for a port number, and won't overflow our array
		options[option_counter++].optionString = "-XjdwpProvider:internal";
		options[option_counter++].optionString = jdwp_option_string;
	}

	if (sdk_int) {
		strncat(sdk_int_option_string, sdk_int, 2); // 2 chars should be enough for the foreseeable future, and won't overflow our array
		options[option_counter++].optionString = sdk_int_option_string;
	}

	while (extra_jvm_options && *extra_jvm_options) {
		options[option_counter++].optionString = *(extra_jvm_options++);
	}

	args.options = options;
	args.ignoreUnrecognized = JNI_FALSE;

	int ret = JNI_CreateJavaVM(&jvm, (void **)&env, &args);
	if (ret < 0) {
		fprintf(stderr, "Unable to Launch JVM\n");
	} else {
		fprintf(stderr, "JVM launched successfully\n");
	}

	free(options);
	return env;
}

void icon_override(GtkWidget *window, GList *icon_list)
{
	GdkSurface *window_surface = gtk_native_get_surface(GTK_NATIVE(window));
	// set app icon as window icon; this is a noop on Wayland because there is currently no way to set a window icon on Wayland
	gdk_toplevel_set_icon_list(GDK_TOPLEVEL(window_surface), icon_list);
}

/*
 * There is no way to get a nice clean callback for when the window is ready to be used for stuff
 * that requires non-zero dimensions, so we just check periodically
 */
gboolean hacky_on_window_focus_changed_callback(JNIEnv *env)
{
	if (gtk_widget_get_width(window) != 0) {
		activity_window_ready();
		return G_SOURCE_REMOVE;
	}

	return G_SOURCE_CONTINUE;
}

struct dynamic_launcher_callback_data {
	char *desktop_file_id;
	char *desktop_entry;
};
static void remove_launcher_icons(const char *app_data_dir_base, const char *package_name);

/* dbus-broker (unlike dbus-daemon) only reads service files again when asked to */
static void reload_dbus_config(void)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return;
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_sync(bus, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
	                                              "ReloadConfig", NULL, NULL, 0, -1, NULL, &err);
	if (reply)
		g_variant_unref(reply);
	else {
		fprintf(stderr, "could not reload the D-Bus configuration: %s\n", err->message);
		g_clear_error(&err);
	}
	g_object_unref(bus);
}

/* outside a sandbox, ATL writes the launcher itself: no dialog (the user already chose to install,
 * in the app store or by running --install), and with actions, which the portal's Install() rejects.
 * Exits. */
static void install_launcher_file(XdpPortal *portal, const char *package_name, const char *app_label, GVariant *icon_serialized,
                                  const char *app_data_dir_base, GString *desktop_entry)
{
	char *desktop_file_id = g_strdup_printf("%s.desktop", package_name);
	/* a launcher made through the portal by an earlier install */
	xdp_portal_dynamic_launcher_uninstall(portal, desktop_file_id, NULL);

	remove_launcher_icons(app_data_dir_base, package_name);
	GString *keys = g_string_new("");
	g_string_append_printf(keys, "Name=%s\n", app_label);
	GIcon *icon = icon_serialized ? g_icon_deserialize(icon_serialized) : NULL;
	if (icon && G_IS_BYTES_ICON(icon)) {
		gsize size;
		const char *data = g_bytes_get_data(g_bytes_icon_get_bytes(G_BYTES_ICON(icon)), &size);
		const char *extension = "png";
		if (size >= 4 && !memcmp(data, "RIFF", 4))
			extension = "webp";
		else if (size >= 1 && data[0] == '<')
			extension = "svg";
		char *icons_dir = g_build_filename(app_data_dir_base, "_launcher_icons_", NULL);
		g_mkdir_with_parents(icons_dir, 0755);
		char *icon_name = g_strdup_printf("%s.%s", package_name, extension);
		char *icon_path = g_build_filename(icons_dir, icon_name, NULL);
		GError *err = NULL;
		if (g_file_set_contents(icon_path, data, size, &err)) {
			g_string_append_printf(keys, "Icon=%s\n", icon_path);
		} else {
			fprintf(stderr, "could not write the launcher icon %s: %s\n", icon_path, err->message);
			g_clear_error(&err);
		}
		g_free(icon_path);
		g_free(icon_name);
		g_free(icons_dir);
	}
	if (icon)
		g_object_unref(icon);
	/* into [Desktop Entry], which ends where the action's group starts */
	char *action_group = strstr(desktop_entry->str, "\n[Desktop Action ");
	g_string_insert(desktop_entry, action_group ? action_group - desktop_entry->str : desktop_entry->len, keys->str);
	g_string_free(keys, TRUE);

	char *applications_dir = g_build_filename(g_get_user_data_dir(), "applications", NULL);
	g_mkdir_with_parents(applications_dir, 0755);
	char *desktop_file_path = g_build_filename(applications_dir, desktop_file_id, NULL);
	printf("installing %s\n\n%s\n", desktop_file_path, desktop_entry->str);
	GError *err = NULL;
	if (!g_file_set_contents(desktop_file_path, desktop_entry->str, -1, &err)) {
		fprintf(stderr, "could not write the launcher %s: %s\n", desktop_file_path, err->message);
		exit(1);
	}
	char *update_desktop_database = g_strdup_printf("update-desktop-database %s", applications_dir);
	printf("running: `%s`\n", update_desktop_database);
	system(update_desktop_database);
	/* not exit(): its cleanup crashed the runtime's still running JIT thread (SIGSEGV),
	 * and the installing app store would take the status as a failed install */
	fflush(NULL);
	_exit(0);
}

static void dynamic_launcher_ready_callback(GObject *portal, GAsyncResult *res, gpointer user_data)
{
	struct dynamic_launcher_callback_data *data = user_data;
	GError *err = NULL;
	GVariant *result = xdp_portal_dynamic_launcher_prepare_install_finish(XDP_PORTAL(portal), res, &err);
	if (!result) {
		/* exit status 2 tells PackageInstaller that the user said no */
		if (g_error_matches(err, G_IO_ERROR, G_IO_ERROR_CANCELLED)) {
			fprintf(stderr, "cancelled\n");
			exit(2);
		}
		fprintf(stderr, "failed to prepare dynamic launcher: %s\n", err ? err->message : "unknown error");
		exit(1);
	}
	const char *token;
	g_variant_lookup(result, "token", "s", &token);
	xdp_portal_dynamic_launcher_install(XDP_PORTAL(portal), token, data->desktop_file_id, data->desktop_entry, &err);
	g_free(data->desktop_file_id);
	g_free(data->desktop_entry);
	g_free(data);
	if (err) {
		fprintf(stderr, "failed to install dynamic launcher: %s\n", err->message);
		exit(1);
	}
	// run update-desktop-database to add the new x-scheme-handler entries to ~/.local/share/applications/mimeinfo.cache
	char *update_desktop_database = g_strdup_printf("update-desktop-database %s/applications", g_get_user_data_dir());
	printf("running: `%s`\n", update_desktop_database);
	system(update_desktop_database);
	g_free(update_desktop_database);

	exit(0);
}

static cairo_status_t cairo_write_func_gstring(void *closure, const unsigned char *data, unsigned int length)
{
	GString *str = closure;
	g_string_append_len(str, (gchar *)data, length);
	return CAIRO_STATUS_SUCCESS;
}

// this is exported by the shim bionic linker
void dl_parse_library_path(const char *path, char *delim);

#define REL_DEX_INSTALL_PATH              "../java/dex"

#define REL_API_IMPL_JAR_INSTALL_PATH     "android_translation_layer/api-impl.jar"
#define REL_API_IMPL_NATIVES_INSTALL_PATH "android_translation_layer/natives"
#define REL_FRAMEWORK_RES_INSTALL_PATH    "android_translation_layer/framework-res.apk"
#define REL_TEST_RUNNER_JAR_INSTALL_PATH  "android_translation_layer/test_runner.jar"

#define API_IMPL_JAR_PATH_LOCAL           "./api-impl.jar"
#define FRAMEWORK_RES_PATH_LOCAL          "./res/framework-res/framework-res.apk"
#define TEST_RUNNER_JAR_PATH_LOCAL        "./test_runner.jar"

struct jni_callback_data {
	char *apk_main_activity_class;
	char *apk_instrumentation_class;
	uint32_t window_width;
	uint32_t window_height;
	gboolean install;
	gboolean install_internal;
	gboolean install_ask;
	gboolean install_flatpak;
	char *prgname;
	char **extra_jvm_options;
	char **extra_string_keys;
	char *sdk_int;
	gboolean uninstall;
	gboolean dbus_activated;
};

static char *uri_option = NULL;

/* the installed apk and its data directory, for the launcher's "uninstall" action */
static char *uninstall_apk_path = NULL;
static char *uninstall_data_dir = NULL;
static gboolean uninstall_allowed = FALSE;
static char *uninstall_data_dir_base = NULL;

static void remove_launcher_icons(const char *app_data_dir_base, const char *package_name)
{
	const char *extensions[] = {"png", "webp", "svg"};
	for (int i = 0; i < ARRAY_SIZE(extensions); i++) {
		char *icon_name = g_strdup_printf("%s.%s", package_name, extensions[i]);
		char *icon_path = g_build_filename(app_data_dir_base, "_launcher_icons_", icon_name, NULL);
		remove(icon_path);
		g_free(icon_path);
		g_free(icon_name);
	}
}

static int remove_entry(const char *path, const struct stat *sb, int typeflag, struct FTW *ftwbuf)
{
	if (remove(path))
		fprintf(stderr, "uninstall: can't remove %s (%m)\n", path);
	return 0;
}

static void remove_recursively(const char *path)
{
	struct stat st;
	if (!lstat(path, &st))
		nftw(path, remove_entry, 16, FTW_DEPTH | FTW_PHYS);
}

/* removes what --install created: the apk with its splits, the app's data and cache, the D-Bus
 * service file and the launcher; the same as PackageInstaller.uninstallPackage */
static void uninstall_package(GApplication *app)
{
	const char *app_id = g_application_get_application_id(app);
	printf("uninstalling %s (%s)\n", app_id, uninstall_apk_path);

	remove_recursively(uninstall_apk_path);
	char *splits = g_strdup_printf("%s.splits", uninstall_apk_path);
	remove_recursively(splits);
	g_free(splits);
	remove_recursively(uninstall_data_dir);
	char *cache_dir = g_build_filename(g_get_user_cache_dir(), "android_translation_layer", app_id, NULL);
	remove_recursively(cache_dir);
	g_free(cache_dir);
	char *service_name = g_strdup_printf("%s.service", app_id);
	char *service_path = g_build_filename(g_get_user_data_dir(), "dbus-1", "services", service_name, NULL);
	remove_recursively(service_path);
	g_free(service_path);
	g_free(service_name);
	reload_dbus_config();

	remove_launcher_icons(uninstall_data_dir_base, app_id);
	char *desktop_file_id = g_strdup_printf("%s.desktop", app_id);
	char *desktop_file_path = g_build_filename(g_get_user_data_dir(), "applications", desktop_file_id, NULL);
	remove(desktop_file_path);
	g_free(desktop_file_path);
	/* a launcher made through the portal (in a sandbox, or by an older ATL) */
	XdpPortal *portal = xdp_portal_new();
	xdp_portal_dynamic_launcher_uninstall(portal, desktop_file_id, NULL);
	g_free(desktop_file_id);
	g_object_unref(portal);
	char *update_desktop_database = g_strdup_printf("update-desktop-database %s/applications", g_get_user_data_dir());
	system(update_desktop_database);
	g_free(update_desktop_database);
}

static void uninstall_cancelled(struct prompt_page *page, gpointer user_data)
{
	gtk_window_destroy(page->window);
	g_free(page);
	g_application_release(G_APPLICATION(user_data));
}

static void uninstall_confirmed(struct prompt_page *page, gpointer user_data)
{
	uninstall_package(G_APPLICATION(user_data));
	fflush(NULL);
	_exit(0); // the app's runtime may be running, see install_launcher_file
}

/* asks first, like Android; the label and icon come from the launcher */
static void ask_uninstall(GApplication *app)
{
	if (!uninstall_allowed) {
		fprintf(stderr, "uninstall: %s is not an installed apk\n", uninstall_apk_path);
		return;
	}
	const char *app_id = g_application_get_application_id(app);
	char *desktop_file = g_strdup_printf("applications/%s.desktop", app_id);
	GKeyFile *key_file = g_key_file_new();
	char *label = NULL;
	char *icon_path = NULL;
	if (g_key_file_load_from_data_dirs(key_file, desktop_file, NULL, G_KEY_FILE_NONE, NULL)) {
		label = g_key_file_get_locale_string(key_file, G_KEY_FILE_DESKTOP_GROUP, G_KEY_FILE_DESKTOP_KEY_NAME, NULL, NULL);
		icon_path = g_key_file_get_string(key_file, G_KEY_FILE_DESKTOP_GROUP, G_KEY_FILE_DESKTOP_KEY_ICON, NULL);
	}
	g_key_file_free(key_file);
	g_free(desktop_file);

	g_application_hold(app);
	/* libadwaita's style would also restyle the running app */
	struct prompt_page *page = prompt_page_new(GTK_APPLICATION(app), "Uninstall App", NULL, label ? label : app_id, app_id, !window);
	if (icon_path && g_path_is_absolute(icon_path)) {
		GdkPaintable *icon = prompt_page_icon_from_file(GTK_WIDGET(page->window), icon_path);
		prompt_page_set_icon(page, icon);
		g_object_unref(icon);
	}
	g_free(icon_path);
	g_free(label);
	prompt_page_set_message(page, "Do you want to uninstall this app? Its data will be removed too.");
	prompt_page_set_buttons(page, "_Cancel", "_Uninstall", TRUE, uninstall_cancelled, uninstall_confirmed, app);
	gtk_window_present(page->window);
}

/* the launcher icon: the apk's own file, or the adaptive or vector Drawable rendered into an SVG */
static GBytes *app_icon_bytes(JNIEnv *env, jobject application_object, const char *app_icon_path)
{
	if (app_icon_path) {
		/* we can import the icon as-is */
		extract_from_apk(app_icon_path, app_icon_path);
		char *app_icon_path_full = g_strdup_printf("%s/%s", app_data_dir, app_icon_path);
		GMappedFile *icon_file = g_mapped_file_new(app_icon_path_full, FALSE, NULL);
		g_free(app_icon_path_full);
		if (icon_file) {
			GBytes *icon_bytes = g_mapped_file_get_bytes(icon_file);
			g_mapped_file_unref(icon_file);
			return icon_bytes;
		}
	}
	/* the icon is a generalized Drawable, let's render it into an SVG */
	_SET_STATIC_BOOL_FIELD(_CACHED_CLASS(VectorDrawable), "direct_draw_override", true);
	GdkPaintable *icon_paintable = _PTR(J__Application__get_app_icon_paintable(env, application_object));
	GString *svg_string = g_string_new("");
	cairo_surface_t *svg_surface = cairo_svg_surface_create_for_stream(cairo_write_func_gstring, svg_string, 108, 108);
	cairo_t *cr = cairo_create(svg_surface);
	GdkSnapshot *snapshot = gtk_snapshot_new();
	gdk_paintable_snapshot(icon_paintable, snapshot, 108, 108);
	GskRenderNode *node = gtk_snapshot_to_node(snapshot);
	if (node) {
		gsk_render_node_draw(node, cr);
		gsk_render_node_unref(node);
	}
	g_object_unref(snapshot);
	cairo_destroy(cr);
	cairo_surface_destroy(svg_surface);
	return g_string_free_to_bytes(svg_string);
}

static const char *icon_extension(GBytes *icon_bytes)
{
	gsize size;
	const char *data = g_bytes_get_data(icon_bytes, &size);
	if (size >= 4 && !memcmp(data, "RIFF", 4))
		return "webp";
	if (size >= 1 && data[0] == '<')
		return "svg";
	return "png";
}

static char *jstring_dup(JNIEnv *env, jstring str)
{
	char *copy = str ? g_strdup(_CSTRING(str)) : NULL;
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
	return copy;
}

/* --install-flatpak (docs/hardening-roadmap.md, phase 2): the apk becomes its own Flatpak
 * de.nexumia.atl.<package> on the ATL engine. Name, icon, URL schemes and permissions come from the
 * apk, read without running any of its code, since this runs outside the sandbox the app gets;
 * make-app-flatpak builds and installs the Flatpak. Exits with its status. */
static void install_flatpak(struct jni_callback_data *d, JNIEnv *env, jobject application_object, GFile **files, int nfiles,
                            gboolean data_dir_existed)
{
	char *package_name = jstring_dup(env, J__Context__getPackageName(env, application_object));
	char *label = jstring_dup(env, J__Application__get_app_label(env, application_object));
	char *icon_path = jstring_dup(env, J__Application__get_app_icon_path(env, application_object));
	char *mime_types = jstring_dup(env, J__Application__get_supported_mime_types(env, application_object));
	char *permissions = jstring_dup(env, J__Application__get_requested_permissions(env, application_object));
	if (!package_name) {
		fprintf(stderr, "install-flatpak: the apk has no package name\n");
		exit(1);
	}

	GError *err = NULL;
	char *tmp_dir = g_dir_make_tmp("atl-install-XXXXXX", &err);
	if (!tmp_dir) {
		fprintf(stderr, "install-flatpak: %s\n", err->message);
		exit(1);
	}
	GBytes *icon_bytes = app_icon_bytes(env, application_object, icon_path);
	char *icon_name = g_strdup_printf("icon.%s", icon_extension(icon_bytes));
	char *icon_file = g_build_filename(tmp_dir, icon_name, NULL);
	g_free(icon_name);
	gsize icon_size;
	const char *icon_data = g_bytes_get_data(icon_bytes, &icon_size);
	gboolean have_icon = icon_size > 0 && g_file_set_contents(icon_file, icon_data, icon_size, NULL);
	g_bytes_unref(icon_bytes);

	/* the arguments ATL gets when the app starts, like those --install writes into the launcher */
	GString *atl_args = g_string_new("");
	if (d->apk_main_activity_class)
		g_string_append_printf(atl_args, "-l %s ", d->apk_main_activity_class);
	if (d->window_width)
		g_string_append_printf(atl_args, "-w %d ", d->window_width);
	if (d->window_height)
		g_string_append_printf(atl_args, "-h %d ", d->window_height);
	if (d->sdk_int)
		g_string_append_printf(atl_args, "--sdk-int %s ", d->sdk_int);

	const char *helper = getenv("ATL_MAKE_FLATPAK");
	if (!helper)
		helper = INSTALL_LIBEXECDIR "/android-translation-layer/make-app-flatpak";
	GPtrArray *argv = g_ptr_array_new_with_free_func(g_free);
	g_ptr_array_add(argv, g_strdup(helper));
	g_ptr_array_add(argv, g_strdup("-p"));
	g_ptr_array_add(argv, g_strdup(package_name));
	g_ptr_array_add(argv, g_strdup("-n"));
	g_ptr_array_add(argv, g_strdup(label && *label ? label : package_name));
	if (have_icon) {
		g_ptr_array_add(argv, g_strdup("-i"));
		g_ptr_array_add(argv, g_strdup(icon_file));
	}
	if (atl_args->len) {
		g_ptr_array_add(argv, g_strdup("-a"));
		g_ptr_array_add(argv, g_strdup(atl_args->str));
	}
	if (mime_types) {
		g_ptr_array_add(argv, g_strdup("-m"));
		g_ptr_array_add(argv, g_strdup(mime_types));
	}
	if (permissions && *permissions) {
		g_ptr_array_add(argv, g_strdup("-P"));
		g_ptr_array_add(argv, g_strdup(permissions));
	}
	for (int i = 0; i < nfiles; i++)
		g_ptr_array_add(argv, g_file_get_path(files[i]));
	g_ptr_array_add(argv, NULL);
	g_string_free(atl_args, TRUE);

	printf("install-flatpak: running %s for %s\n", helper, package_name);
	int status = 1;
	int wait_status;
	if (!g_spawn_sync(NULL, (char **)argv->pdata, NULL, G_SPAWN_CHILD_INHERITS_STDIN, NULL, NULL, NULL, NULL, &wait_status, &err)) {
		fprintf(stderr, "install-flatpak: %s\n", err->message);
		g_clear_error(&err);
	} else if (g_spawn_check_wait_status(wait_status, &err)) {
		status = 0;
	} else {
		fprintf(stderr, "install-flatpak: %s\n", err->message);
		g_clear_error(&err);
	}
	g_ptr_array_free(argv, TRUE);

	remove_recursively(tmp_dir);
	/* the host data directory ATL made for reading the apk; the app keeps its data in its sandbox */
	if (!data_dir_existed)
		remove_recursively(app_data_dir);
	fflush(NULL);
	_exit(status); // the runtime is loaded, see install_launcher_file
}

/* --install-ask, for opening an apk file: asks first, like Android's package installer, then
 * installs it like PackageInstaller does: as <package>.apk, through --install */
#define INSTALL_ASK_DESKTOP_ID "com.gitlab.android_translation_layer.install"

/* Application.get_install_state */
enum install_state {
	INSTALL_NEW,
	INSTALL_UPDATE,
	INSTALL_REINSTALL,
	INSTALL_DOWNGRADE,
	INSTALL_CONFLICT,
};

struct install_request {
	struct prompt_page *page;
	struct jni_callback_data *d;
	char *package_name;
	GFile *apk;
	char *app_data_dir_base;
	char *staging_dir;
	char *staged_apk;
	gboolean updating;
	/* the data directory made for the opened file, which is not the installed app's */
	gboolean remove_data_dir;
};

static void install_ask_exit(struct install_request *r, int status)
{
	if (r->staging_dir)
		remove_recursively(r->staging_dir);
	if (r->remove_data_dir)
		remove_recursively(app_data_dir);
	fflush(NULL);
	_exit(status); // the app's runtime is loaded, see install_launcher_file
}

/* exit status 2 like --install when the user said no */
static void install_ask_cancelled(struct prompt_page *page, gpointer user_data)
{
	install_ask_exit(user_data, 2);
}

static void install_ask_done(struct prompt_page *page, gpointer user_data)
{
	install_ask_exit(user_data, 0);
}

static void install_ask_failed(struct prompt_page *page, gpointer user_data)
{
	install_ask_exit(user_data, 1);
}

static void install_ask_open(struct prompt_page *page, gpointer user_data)
{
	struct install_request *r = user_data;
	char *desktop_id = g_strdup_printf("%s.desktop", r->package_name);
	GDesktopAppInfo *app_info = g_desktop_app_info_new(desktop_id);
	if (!app_info) { // GIO's list of launchers may not know it yet
		char *path = g_build_filename(g_get_user_data_dir(), "applications", desktop_id, NULL);
		app_info = g_desktop_app_info_new_from_filename(path);
		g_free(path);
	}
	g_free(desktop_id);
	if (app_info) {
		GdkAppLaunchContext *context = gdk_display_get_app_launch_context(gtk_widget_get_display(GTK_WIDGET(page->window)));
		GError *err = NULL;
		if (!g_app_info_launch(G_APP_INFO(app_info), NULL, G_APP_LAUNCH_CONTEXT(context), &err)) {
			fprintf(stderr, "install: could not open %s: %s\n", r->package_name, err->message);
			g_error_free(err);
		}
		g_object_unref(context);
		g_object_unref(app_info);
		/* the launcher is D-Bus activatable, send the request before we exit */
		GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
		if (bus) {
			g_dbus_connection_flush_sync(bus, NULL, NULL);
			g_object_unref(bus);
		}
	}
	install_ask_exit(r, 0);
}

static void install_ask_show_failure(struct install_request *r, const char *reason)
{
	fprintf(stderr, "install: %s\n", reason);
	prompt_page_set_message(r->page, "The app could not be installed.");
	prompt_page_set_buttons(r->page, "_Close", NULL, FALSE, install_ask_failed, NULL, r);
}

static void install_ask_installed(GObject *source, GAsyncResult *res, gpointer user_data)
{
	struct install_request *r = user_data;
	GError *err = NULL;
	if (!g_subprocess_wait_check_finish(G_SUBPROCESS(source), res, &err)) {
		install_ask_show_failure(r, err->message);
		g_error_free(err);
		return;
	}
	prompt_page_set_message(r->page, r->updating ? "App updated." : "App installed.");
	prompt_page_set_buttons(r->page, "_Done", "_Open", FALSE, install_ask_done, install_ask_open, r);
}

/* the --sdk-int in the launcher of the installed app, e.g. from the app store that installed it */
static char *installed_launcher_sdk_int(const char *package_name)
{
	char *desktop_file = g_strdup_printf("%s/applications/%s.desktop", g_get_user_data_dir(), package_name);
	GKeyFile *key_file = g_key_file_new();
	char *exec = NULL;
	if (g_key_file_load_from_file(key_file, desktop_file, G_KEY_FILE_NONE, NULL))
		exec = g_key_file_get_string(key_file, G_KEY_FILE_DESKTOP_GROUP, G_KEY_FILE_DESKTOP_KEY_EXEC, NULL);
	g_key_file_free(key_file);
	g_free(desktop_file);
	char **argv = NULL;
	char *sdk_int = NULL;
	if (exec && g_shell_parse_argv(exec, NULL, &argv, NULL)) {
		for (int i = 0; argv[i] && argv[i + 1]; i++) {
			if (!strcmp(argv[i], "--sdk-int")) {
				sdk_int = g_strdup(argv[i + 1]);
				break;
			}
		}
		g_strfreev(argv);
	}
	g_free(exec);
	return sdk_int;
}

static void install_ask_copied(GObject *source, GAsyncResult *res, gpointer user_data)
{
	struct install_request *r = user_data;
	GError *err = NULL;
	if (!g_file_copy_finish(G_FILE(source), res, &err)) {
		install_ask_show_failure(r, err->message);
		g_error_free(err);
		return;
	}
	/* the installed app starts with this window size, like one an app store installs */
	char *width = g_strdup_printf("%d", r->d->window_width);
	char *height = g_strdup_printf("%d", r->d->window_height);
	/* an update keeps the SDK level the app ran at, e.g. the one its app store gave it */
	char *sdk_int = r->d->sdk_int ? g_strdup(r->d->sdk_int) : r->updating ? installed_launcher_sdk_int(r->package_name) : NULL;
	const char *argv[] = {r->d->prgname, "--install", "-w", width, "-h", height, r->staged_apk, NULL, NULL, NULL};
	if (sdk_int) {
		argv[6] = "--sdk-int";
		argv[7] = sdk_int;
		argv[8] = r->staged_apk;
	}
	printf("install: running %s --install%s%s for %s\n", r->d->prgname, sdk_int ? " --sdk-int " : "", sdk_int ? sdk_int : "", r->staged_apk);
	GSubprocess *install = g_subprocess_newv(argv, G_SUBPROCESS_FLAGS_NONE, &err);
	g_free(width);
	g_free(height);
	g_free(sdk_int);
	if (!install) {
		install_ask_show_failure(r, err->message);
		g_error_free(err);
		return;
	}
	g_subprocess_wait_check_async(install, NULL, install_ask_installed, r);
	g_object_unref(install);
}

static void install_ask_confirmed(struct prompt_page *page, gpointer user_data)
{
	struct install_request *r = user_data;
	prompt_page_set_busy(page, r->updating ? "Updating…" : "Installing…");
	char *sessions_dir = g_build_filename(r->app_data_dir_base, "_installer_sessions_", NULL);
	g_mkdir_with_parents(sessions_dir, 0755);
	r->staging_dir = g_build_filename(sessions_dir, "ask-XXXXXX", NULL);
	g_free(sessions_dir);
	if (!g_mkdtemp(r->staging_dir)) {
		g_clear_pointer(&r->staging_dir, g_free);
		install_ask_show_failure(r, "could not create a staging directory");
		return;
	}
	/* other apps find a package as <package>.apk, and its data directory is named after it */
	char *apk_name = g_strdup_printf("%s.apk", r->package_name);
	r->staged_apk = g_build_filename(r->staging_dir, apk_name, NULL);
	g_free(apk_name);
	GFile *dest = g_file_new_for_path(r->staged_apk);
	g_file_copy_async(r->apk, dest, G_FILE_COPY_NONE, G_PRIORITY_DEFAULT, NULL, NULL, NULL, install_ask_copied, r);
	g_object_unref(dest);
}

static void ask_install(GtkApplication *app, struct jni_callback_data *d, JNIEnv *env, jobject application_object, GFile *apk,
                        const char *package_name, const char *app_icon_path, const char *app_data_dir_base, gboolean data_dir_existed)
{
	struct install_request *r = g_new0(struct install_request, 1);
	r->d = d;
	r->package_name = g_strdup(package_name);
	r->apk = g_object_ref(apk);
	r->app_data_dir_base = g_strdup(app_data_dir_base);
	char *installed_data_dir = g_strdup_printf("%s/%s.apk_/", app_data_dir_base, package_name);
	r->remove_data_dir = !data_dir_existed && strcmp(app_data_dir, installed_data_dir);
	g_free(installed_data_dir);

	jstring label_jstr = J__Application__get_app_label(env, application_object);
	jstring version_jstr = J__Application__get_version_name(env, application_object);
	enum install_state state = J__Application__get_install_state(env, application_object);
	jstring installed_version_jstr = J__Application__get_installed_version_name(env, application_object);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		state = INSTALL_NEW;
	}
	const char *label = label_jstr ? _CSTRING(label_jstr) : NULL;
	const char *version = version_jstr ? _CSTRING(version_jstr) : NULL;
	const char *installed_version = installed_version_jstr ? _CSTRING(installed_version_jstr) : NULL;

	char *details;
	if (state == INSTALL_UPDATE && version && installed_version)
		details = g_strdup_printf("%s\nVersion %s → %s", package_name, installed_version, version);
	else if (version)
		details = g_strdup_printf("%s\nVersion %s", package_name, version);
	else
		details = g_strdup(package_name);
	r->page = prompt_page_new(app, "Install App", NULL, label && *label ? label : package_name, details, TRUE);
	g_free(details);

	GdkPaintable *icon = NULL;
	if (app_icon_path) {
		extract_from_apk(app_icon_path, app_icon_path);
		char *icon_file = g_build_filename(app_data_dir, app_icon_path, NULL);
		icon = prompt_page_icon_from_file(GTK_WIDGET(r->page->window), icon_file);
		g_free(icon_file);
	} else { // a Drawable, as for the launcher
		_SET_STATIC_BOOL_FIELD(_CACHED_CLASS(VectorDrawable), "direct_draw_override", true);
		icon = _PTR(J__Application__get_app_icon_paintable(env, application_object));
		if (icon)
			g_object_ref(icon);
	}
	if (icon) {
		prompt_page_set_icon(r->page, icon);
		g_object_unref(icon);
	}

	r->updating = state == INSTALL_UPDATE || state == INSTALL_REINSTALL;
	switch (state) {
		case INSTALL_NEW:
			prompt_page_set_message(r->page, "Do you want to install this app?");
			prompt_page_set_buttons(r->page, "_Cancel", "_Install", FALSE, install_ask_cancelled, install_ask_confirmed, r);
			break;
		case INSTALL_UPDATE:
			prompt_page_set_message(r->page, "Do you want to update this app? Its data stays.");
			prompt_page_set_buttons(r->page, "_Cancel", "_Update", FALSE, install_ask_cancelled, install_ask_confirmed, r);
			break;
		case INSTALL_REINSTALL:
			prompt_page_set_message(r->page, "This version is already installed. Do you want to install it again? Its data stays.");
			prompt_page_set_buttons(r->page, "_Cancel", "_Reinstall", FALSE, install_ask_cancelled, install_ask_confirmed, r);
			break;
		case INSTALL_DOWNGRADE: {
			char *message = g_strdup_printf("A newer version (%s) is already installed. Uninstall it first to install this one.",
			                                installed_version ? installed_version : "?");
			prompt_page_set_message(r->page, message);
			g_free(message);
			prompt_page_set_buttons(r->page, "_Close", NULL, FALSE, install_ask_cancelled, NULL, r);
			break;
		}
		case INSTALL_CONFLICT:
			prompt_page_set_message(r->page, "An app with this package name is already installed, signed by someone else. "
			                                 "Uninstall it first to install this one.");
			prompt_page_set_buttons(r->page, "_Close", NULL, FALSE, install_ask_cancelled, NULL, r);
			break;
	}
	gtk_window_present(r->page->window);
	/* Phosh shows the installer's name and icon for the window */
	GdkSurface *surface = gtk_native_get_surface(GTK_NATIVE(r->page->window));
	if (GDK_IS_WAYLAND_TOPLEVEL(surface))
		gdk_wayland_toplevel_set_application_id(GDK_WAYLAND_TOPLEVEL(surface), INSTALL_ASK_DESKTOP_ID);
}

static gboolean uninstall_pending = FALSE;
static gboolean app_starting = FALSE;

static void action_uninstall(GSimpleAction *action, GVariant *parameter, gpointer user_data)
{
	GApplication *app = G_APPLICATION(user_data);
	ask_uninstall(app);
	if (uninstall_pending) { // the hold from open(), which did not start the app
		uninstall_pending = FALSE;
		g_application_release(app);
	}
}

static GMutex dbus_request_mutex;
static GCond dbus_request_cond;
enum dbus_request {
	DBUS_REQUEST_NONE,
	DBUS_REQUEST_OTHER,
	DBUS_REQUEST_UNINSTALL,
};
static enum dbus_request dbus_request = DBUS_REQUEST_NONE;

/* runs in GDBus' worker thread; only looks at the request, which is still dispatched as usual */
static GDBusMessage *dbus_request_filter(GDBusConnection *connection, GDBusMessage *message, gboolean incoming, gpointer user_data)
{
	if (!incoming || g_dbus_message_get_message_type(message) != G_DBUS_MESSAGE_TYPE_METHOD_CALL ||
	    g_strcmp0(g_dbus_message_get_interface(message), "org.freedesktop.Application"))
		return message;
	enum dbus_request request = DBUS_REQUEST_OTHER;
	GVariant *body = g_dbus_message_get_body(message);
	if (!g_strcmp0(g_dbus_message_get_member(message), "ActivateAction") && body &&
	    g_variant_is_of_type(body, G_VARIANT_TYPE("(sava{sv})"))) {
		const char *action_name;
		g_variant_get_child(body, 0, "&s", &action_name);
		if (!strcmp(action_name, "uninstall"))
			request = DBUS_REQUEST_UNINSTALL;
	}
	g_mutex_lock(&dbus_request_mutex);
	if (dbus_request == DBUS_REQUEST_NONE)
		dbus_request = request;
	g_cond_signal(&dbus_request_cond);
	g_mutex_unlock(&dbus_request_mutex);
	return message;
}

/* D-Bus activation starts us for whatever was asked of the app (activate it, open a URI, run a
 * launcher action), but the request only arrives once we own the bus name. Own it before the app
 * starts and wait briefly for the request, so the launcher's "uninstall" action does not start the
 * app first (which it might not survive). */
static gboolean dbus_activation_asks_uninstall(GApplication *app)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return FALSE;
	guint filter = g_dbus_connection_add_filter(bus, dbus_request_filter, NULL, NULL);
	GVariant *reply = g_dbus_connection_call_sync(bus, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
	                                              "RequestName", g_variant_new("(su)", g_application_get_application_id(app), G_BUS_NAME_OWNER_FLAGS_NONE),
	                                              G_VARIANT_TYPE("(u)"), 0, -1, NULL, NULL);
	if (reply)
		g_variant_unref(reply);
	gint64 end_time = g_get_monotonic_time() + 500 * G_TIME_SPAN_MILLISECOND;
	g_mutex_lock(&dbus_request_mutex);
	while (dbus_request == DBUS_REQUEST_NONE)
		if (!g_cond_wait_until(&dbus_request_cond, &dbus_request_mutex, end_time))
			break;
	gboolean uninstall = dbus_request == DBUS_REQUEST_UNINSTALL;
	g_mutex_unlock(&dbus_request_mutex);
	g_dbus_connection_remove_filter(bus, filter);
	g_object_unref(bus);
	return uninstall;
}

static void parse_string_extras(JNIEnv *env, char **extra_string_keys, jobject intent)
{
	GError *error = NULL;
	GRegex *regex = g_regex_new("(?<!\\\\)=", 0, 0, &error);
	if (!regex) {
		fprintf(stderr, "g_regex_new error: '%s'\n", error->message);
		exit(1);
	}

	for (char **arg = extra_string_keys; *arg; arg++) {
		gchar **keyval = g_regex_split_full(regex, *arg, -1, 0, 0, 2, NULL);
		if (!keyval || !keyval[0] || !keyval[1]) {
			fprintf(stderr, "extra string arg not in 'key=value' format: '%s'\n", *arg);
			exit(1);
		}
		J__Intent__putExtraCharSequence(env, intent, _JSTRING(keyval[0]), _JSTRING(keyval[1]));
		g_strfreev(keyval);
	}
	g_regex_unref(regex);
}

/* Drag and drop callback to simulate ACTION_SEND intents */
static gboolean on_drop(GtkDropTarget *target, const GValue *value, double x, double y, gpointer user_data)
{
	const char *data = g_value_get_string(value);
	if (!data || !*data) {
		return FALSE;
	}

	JNIEnv *env = get_jni_env();
	jobject intent = J_new__Intent(env);
	_SET_OBJ_FIELD(intent, "action", "Ljava/lang/String;", _JSTRING("android.intent.action.SEND"));
	_SET_OBJ_FIELD(intent, "data", "Landroid/net/Uri;", J__Uri__parse(env, _JSTRING(data)));

	jobject activity = J__Context__resolveActivityInternal(env, intent);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
	}
	if (!activity) {
		fprintf(stderr, "failed to resolve activity to handle URI: %s\n", data);
		return FALSE;
	}
	activity_start(env, activity);
	return TRUE;
}

char *find_jar_or_die(char *builddir_path, char *installed_path, char *install_prefix)
{
	char *path;

	if (getenv("RUN_FROM_BUILDDIR")) {
		path = strdup(builddir_path); // for running out of builddir; using strdup so we can always safely call free on this
	} else {
		path = g_strdup_printf("%s/%s", install_prefix, installed_path);
	}

	if (access(path, F_OK) < 0) {
		fprintf(stderr, "error: can't stat %s (%s)\n",
		        path, strerror(errno));
		exit(1);
	}

	return path;
}

static gboolean install_dbus_service_file(const char *package_name, const char *exec_cmd)
{
	if (g_getenv("FLATPAK_ID"))
		return FALSE;
	const char *user_data_dir = g_get_user_data_dir();
	char *service_dir = g_build_filename(user_data_dir, "dbus-1", "services", NULL);
	if (g_mkdir_with_parents(service_dir, 0755)) {
		fprintf(stderr, "could not create D-Bus service directory %s\n", service_dir);
		g_free(service_dir);
		return FALSE;
	}
	char *service_name = g_strdup_printf("%s.service", package_name);
	char *service_path = g_build_filename(service_dir, service_name, NULL);
	g_free(service_dir);
	g_free(service_name);

	GString *service_file = g_string_new("[D-BUS Service]\n");
	g_string_append_printf(service_file, "Name=%s\n", package_name);
	g_string_append_printf(service_file, "Exec=%s --dbus-activated\n", exec_cmd);

	GError *error = NULL;
	if (!g_file_set_contents(service_path, service_file->str, -1, &error)) {
		fprintf(stderr, "could not write D-Bus service file %s: %s\n", service_path, error->message);
		g_error_free(error);
		g_string_free(service_file, TRUE);
		g_free(service_path);
		return FALSE;
	}
	g_string_free(service_file, TRUE);
	g_free(service_path);
	reload_dbus_config();

	return TRUE;
}

/* split APKs (files after the first) go to <installed apk>.splits/, replacing those of an
 * earlier install; returns their paths, each after a space, for the launcher's command line */
static char *install_split_apks(GFile *dest, GFile **files, gint nfiles)
{
	char *dest_path = g_file_get_path(dest);
	char *dir = g_strdup_printf("%s.splits", dest_path);
	g_free(dest_path);

	GDir *old = g_dir_open(dir, 0, NULL);
	if (old) {
		const char *name;
		while ((name = g_dir_read_name(old))) {
			char *path = g_build_filename(dir, name, NULL);
			g_unlink(path);
			g_free(path);
		}
		g_dir_close(old);
		g_rmdir(dir);
	}

	GString *paths = g_string_new("");
	if (nfiles > 1)
		g_mkdir_with_parents(dir, 0755);
	for (int i = 1; i < nfiles; i++) {
		char *name = g_file_get_basename(files[i]);
		char *path = g_build_filename(dir, name, NULL);
		GFile *split = g_file_new_for_path(path);
		GError *err = NULL;
		printf("installing split %s to %s\n", name, path);
		g_file_copy(files[i], split, G_FILE_COPY_OVERWRITE, NULL, NULL, NULL, &err);
		if (err) {
			fprintf(stderr, "error copying split apk: %s\n", err->message);
			g_error_free(err);
		}
		g_string_append_printf(paths, " %s", path);
		g_object_unref(split);
		g_free(path);
		g_free(name);
	}
	g_free(dir);
	return g_string_free(paths, FALSE);
}

static void open(GtkApplication *app, GFile **files, gint nfiles, const gchar *hint, struct jni_callback_data *d)
{
	if (app_starting && !window) { // the bus name is owned early when D-Bus activated
		fprintf(stderr, "ignoring a D-Bus open request while the app starts\n");
		return;
	}
	if (window) { // this is not the first launch, but a DBus request to open an URI in the running app
		fprintf(stderr, "opening uri over DBus %p\n", files[0]);
		char *uri = g_file_get_uri(files[0]);
		JNIEnv *env = get_jni_env();
		fprintf(stderr, "opening uri over DBus: %s\n", uri);
		jobject activity = J__Activity__createMainActivity(env,
		                                                   _JSTRING(d->apk_main_activity_class),
		                                                   _INTPTR(window), _JSTRING(uri));
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
		if (activity) // NULL: an open singleTop/singleTask activity got the URI through onNewIntent
			activity_start(env, activity);
		gtk_window_present(GTK_WINDOW(window));
		return;
	}
	char *dex_install_dir;
	char *api_impl_jar;
	char *test_runner_jar = NULL;
	char *framework_res_apk = NULL;
	const char *package_name;
	int ret;
	jobject activity_object;
	jobject application_object;

	char **apk_classpaths = g_new0(gchar *, nfiles + 1); // +1 for NULL terminator for g_strjoinv
	char *apk_name = g_file_get_basename(files[0]);
	for (int i = 0; i < nfiles; i++) {
		apk_classpaths[i] = g_file_get_path(files[i]);
		if (apk_classpaths[i] == NULL) {
			fprintf(stderr, "error: the specified file path (%s) doesn't seem to be valid\n", g_file_get_uri(files[i]));
			exit(1);
		}
		if (access(apk_classpaths[i], F_OK) < 0) {
			fprintf(stderr, "error: the specified file path (%s) doesn't seem to exist (%m)\n", apk_classpaths[i]);
			exit(1);
		}
	}
	char *apk_classpath = g_strjoinv(":", apk_classpaths);
	g_free(apk_classpaths);

	Dl_info libart_so_dl_info;
	// JNI_CreateJavaVM chosen arbitrarily, what matters is that it's a symbol exported by by libart.so
	// TODO: we shouldn't necessarily count on art being installed in the same prefix as we are
	void *_JNI_CreateJavaVM = dlsym(RTLD_NEXT, "JNI_CreateJavaVM");
	dladdr(_JNI_CreateJavaVM, &libart_so_dl_info);
	// make sure we didn't get NULL
	if (libart_so_dl_info.dli_fname) {
		// it's simpler if we can modify the string, so strdup it
		char *libart_so_full_path = strdup(libart_so_dl_info.dli_fname);
		*strrchr(libart_so_full_path, '/') = '\0'; // now we should have something like /usr/lib64/art
		dex_install_dir = g_strdup_printf("%s/%s", libart_so_full_path, REL_DEX_INSTALL_PATH);
		free(libart_so_full_path);
	} else {
		fprintf(stderr, "error: couldn't find art install path\n");
		exit(1);
	}

	char *app_data_dir_base = getenv("ANDROID_APP_DATA_DIR");
	if (!app_data_dir_base) {
		const char *user_data_dir = g_get_user_data_dir();
		if (user_data_dir) {
			app_data_dir_base = g_strdup_printf("%s/android_translation_layer", user_data_dir);
			ret = mkdir(app_data_dir_base, DEFFILEMODE | S_IXUSR | S_IXGRP | S_IXOTH);
			if (ret) {
				if (errno != EEXIST) {
					fprintf(stderr, "error: ANDROID_APP_DATA_DIR not set, and the default directory (%s) couldn't be created (error: %s)\n", app_data_dir_base, strerror(errno));
					exit(1);
				}
			}
		} else {
			fprintf(stderr, "error: ANDROID_APP_DATA_DIR not set, and HOME is not set either so we can't construct the default path\n");
			exit(1);
		}
	}

	// TODO: we should possibly use the app id instead, but we don't currently have a way to get that soon enough
	// arguably both the app id and the apk name might have an issue with duplicates, but if two apks use the same app id, chances are it's less of an issue than when two apks have the same name
	// !IMPORTANT! Unity can't comprehend that a directory name could end in .apk, so we have to avoid that here by adding `_`
	app_data_dir = g_strdup_printf("%s/%s_/", app_data_dir_base, apk_name);
	gboolean app_data_dir_existed = g_file_test(app_data_dir, G_FILE_TEST_EXISTS);

	uninstall_apk_path = g_file_get_path(files[0]);
	uninstall_data_dir = g_strdup(app_data_dir);
	uninstall_data_dir_base = g_strdup(app_data_dir_base);
	char *apk_dir = g_path_get_dirname(uninstall_apk_path);
	char *installed_apks_dir = g_build_filename(app_data_dir_base, "_installed_apks_", NULL);
	/* only what --install put there, never an apk the user started from elsewhere */
	uninstall_allowed = !strcmp(apk_dir, installed_apks_dir) &&
	                    strcmp(g_application_get_application_id(G_APPLICATION(app)), "com.example.demo_application");
	g_free(apk_dir);
	g_free(installed_apks_dir);
	if (d->uninstall) {
		if (!uninstall_allowed) {
			fprintf(stderr, "error: --uninstall needs an apk installed with --install and --gapplication-app-id\n");
			exit(1);
		}
		ask_uninstall(G_APPLICATION(app));
		return;
	}
	if (d->dbus_activated && !d->install && uninstall_allowed && dbus_activation_asks_uninstall(G_APPLICATION(app))) {
		uninstall_pending = TRUE;
		g_application_hold(G_APPLICATION(app));
		return;
	}
	app_starting = TRUE;

	ret = mkdir(app_data_dir, DEFFILEMODE | S_IXUSR | S_IXGRP | S_IXOTH);
	if (ret && errno != EEXIST) {
		fprintf(stderr, "can't create app data dir %s (%s)\n", app_data_dir, strerror(errno));
		exit(1);
	}

	// check for jars and apks  in './' (if running from builddir), or in system install path
	api_impl_jar = find_jar_or_die(API_IMPL_JAR_PATH_LOCAL, REL_API_IMPL_JAR_INSTALL_PATH, dex_install_dir);
	framework_res_apk = find_jar_or_die(FRAMEWORK_RES_PATH_LOCAL, REL_FRAMEWORK_RES_INSTALL_PATH, dex_install_dir);
	if (d->apk_instrumentation_class)
		test_runner_jar = find_jar_or_die(TEST_RUNNER_JAR_PATH_LOCAL, REL_TEST_RUNNER_JAR_INSTALL_PATH, dex_install_dir);

	char *api_impl_natives_dir = g_strdup_printf("%s/%s", dex_install_dir, REL_API_IMPL_NATIVES_INSTALL_PATH);

	char *app_lib_dir = malloc(strlen(app_data_dir) + strlen("/lib") + 1); // +1 for NULL
	strcpy(app_lib_dir, app_data_dir);
	strcat(app_lib_dir, "/lib");
	// create lib dir
	mkdir(app_lib_dir, DEFFILEMODE | S_IXUSR | S_IXGRP | S_IXOTH);

	// Apps which extract libraries on their own can place them anywhere in app_data_dir. Therefore, we add app_data_dir/** to the
	// BIONIC_LD_LIBRARY_PATH. While app_data_dir/lib is already matched by the wildcard, it needs to be specified again to allow loading
	// libraries by libname from app_data_dir/lib
	char *ld_path = getenv("BIONIC_LD_LIBRARY_PATH");
	ld_path = (ld_path ? ld_path : "");
	ld_path = g_strdup_printf("%s:%s**:%s", app_lib_dir, app_data_dir, ld_path);
	// calling directly into the shim bionic linker to whitelist the app's lib dir as containing bionic-linked libraries
	dl_parse_library_path(ld_path, ":");
	g_free(ld_path);

	JNIEnv *env = create_vm(api_impl_jar, apk_classpath, framework_res_apk, test_runner_jar, api_impl_natives_dir, app_lib_dir, d->sdk_int, d->extra_jvm_options);

	free(app_lib_dir);

	jclass display_class = _CACHED_CLASS(Display);
	_SET_STATIC_INT_FIELD(display_class, "window_width", d->window_width);
	_SET_STATIC_INT_FIELD(display_class, "window_height", d->window_height);

	/* -- register our JNI library under the appropriate classloader -- */

	/* 'android/view/View' is part of the "hax.dex" package, any other class from that package would serve just as well */
	jobject class_loader = J__Class__getClassLoader(env, _CACHED_CLASS(View));

	jobject java_runtime = J__Runtime__getRuntime(env);

	/* this method is private, but it seems we get away with calling it from C */
	J__Runtime__loadLibrary(env, java_runtime, _JSTRING("translation_layer_main"), class_loader);

	// some apps need the apk path since they directly read their apk
	apk_path = strdup(apk_classpath);

	(*env)->GetJavaVM(env, &jvm);

	/* -- misc -- */

	window = gtk_application_window_new(app);
	GtkWidget *stack = gtk_stack_new();
	gtk_stack_set_transition_type(GTK_STACK(stack), GTK_STACK_TRANSITION_TYPE_SLIDE_LEFT_RIGHT);
	/* dialogs are layers over the activities, see android_app_Dialog.c */
	GtkWidget *overlay = gtk_overlay_new();
	gtk_overlay_set_child(GTK_OVERLAY(overlay), stack);
	gtk_window_set_child(GTK_WINDOW(window), overlay);
	g_object_set_data(G_OBJECT(window), "activity-stack", stack);
	g_object_set_data(G_OBJECT(window), "dialog-overlay", overlay);

	const char *disable_decoration_env = getenv("ATL_DISABLE_WINDOW_DECORATIONS");
	gboolean decorated;
	if (disable_decoration_env)
		decorated = !strcmp(disable_decoration_env, "0") || !strcmp(disable_decoration_env, "false");
	else { // by default only enable decorations if there are any action buttons to show in the title bar
		char *decoration_layout;
		g_object_get(G_OBJECT(gtk_settings_get_default()), "gtk-decoration-layout", &decoration_layout, NULL);
		GString *gstring = g_string_new_take(decoration_layout);
		g_string_replace(gstring, "appmenu", "", 0); // ignore appmenu button
		g_string_replace(gstring, "menu", "", 0);    // ignore menu button
		g_string_replace(gstring, ":", "", 0);       // ignore separation colon
		decorated = gstring->len > 0;
		g_string_free(gstring, TRUE);
	}
	gtk_window_set_decorated(GTK_WINDOW(window), decorated);

	if (getenv("ATL_FORCE_FULLSCREEN"))
		gtk_window_fullscreen(GTK_WINDOW(window));

	// Load default css stylesheet
	GtkCssProvider *cssProvider = gtk_css_provider_new();
	gtk_css_provider_load_from_resource(cssProvider, "/com/gitlab/android-translation-layer/android-translation-layer/default-stylesheet.css");
	gtk_style_context_add_provider_for_display(gdk_display_get_default(), GTK_STYLE_PROVIDER(cssProvider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);

	prepare_main_looper(env);

	/* extract native libraries from apk*/
	if (!getenv("ATL_SKIP_NATIVES_EXTRACTION") && !d->install_ask && !d->install_flatpak) // these only read the apk
		extract_from_apk("lib/" NATIVE_ARCH "/", "lib/");

	if (d->install_flatpak) {
		application_object = J__Application__create_for_installer(env);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
		install_flatpak(d, env, application_object, files, nfiles, app_data_dir_existed);
	}

	// construct Application
	application_object = J__Context__createApplication(env, _INTPTR(window));
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	J__ContentProvider__createContentProviders(env);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	J__Application__onCreate(env, application_object);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	if (d->apk_instrumentation_class) {
		if (d->apk_main_activity_class) {
			fprintf(stderr, "error: both --instrument and --launch-activity supplied, exiting\n");
			exit(1);
		}

		jobject intent = NULL;

		if (d->extra_string_keys) {
			intent = J_new__Intent(env);
			parse_string_extras(env, d->extra_string_keys, intent);
		}

		J__Instrumentation__create(env, _JSTRING(d->apk_instrumentation_class), intent);

		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}

	// construct main Activity
	if (!d->apk_instrumentation_class && !d->install_internal && !d->install_ask) {
		activity_object = J__Activity__createMainActivity(env, _JSTRING(d->apk_main_activity_class),
		                                                  _INTPTR(window), (uri_option && *uri_option) ? _JSTRING(uri_option) : NULL);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
		if (uri_option)
			g_free(uri_option);

		if (d->extra_string_keys) {
			jobject intent = _GET_OBJ_FIELD(activity_object, "intent", "Landroid/content/Intent;");
			parse_string_extras(env, d->extra_string_keys, intent);
			g_strfreev(d->extra_string_keys);
		}
	}
	/* -- set the window title and app icon -- */

	if (!d->apk_instrumentation_class) {
		jstring package_name_jstr = J__Context__getPackageName(env, application_object);
		package_name = package_name_jstr ? _CSTRING(package_name_jstr) : NULL;
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}

	jstring app_icon_path_jstr = J__Application__get_app_icon_path(env, application_object);
	const char *app_icon_path = app_icon_path_jstr ? _CSTRING(app_icon_path_jstr) : NULL;
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);

	if (d->install_ask && !d->apk_instrumentation_class) {
		ask_install(app, d, env, application_object, files[0], package_name, app_icon_path, app_data_dir_base, app_data_dir_existed);
		return;
	}

	if (d->install || d->install_internal) {
		if (d->apk_instrumentation_class) {
			fprintf(stderr, "error: --instrument supplied together with --install, exiting\n");
			exit(1);
		}

		XdpPortal *portal = xdp_portal_new();

		jstring app_label_jstr = J__Application__get_app_label(env, application_object);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
		const char *app_label = app_label_jstr ? _CSTRING(app_label_jstr) : NULL;
		/* the portal rejects an empty name and never calls back */
		if (!app_label || !*app_label)
			app_label = package_name;

		GVariant *icon_serialized = NULL;
		if (!d->install_internal) {
			GBytes *icon_bytes = app_icon_bytes(env, application_object, app_icon_path);
			GIcon *icon = g_bytes_icon_new(icon_bytes);
			icon_serialized = g_icon_serialize(icon);
			g_object_unref(icon);
			g_bytes_unref(icon_bytes);
		}

		gchar *dest_name = g_strdup_printf("%s.apk", package_name);
		GFile *dest = g_file_new_build_filename(app_data_dir_base, "_installed_apks_", d->install_internal ? dest_name : apk_name, NULL);
		free(dest_name);
		printf("installing %s to %s\n", apk_name, g_file_get_path(dest));
		g_file_make_directory(g_file_get_parent(dest), NULL, NULL);
		GError *err = NULL;
		g_file_copy(files[0], dest, G_FILE_COPY_OVERWRITE, NULL, NULL, NULL, &err);
		if (err)
			fprintf(stderr, "error copying apk: %s\n", err->message);
		char *split_paths = install_split_apks(dest, files, nfiles);

		if (d->install_internal)
			exit(0);

		jstring supported_mime_types_jstr = J__Application__get_supported_mime_types(env, application_object);
		const char *supported_mime_types = supported_mime_types_jstr ? _CSTRING(supported_mime_types_jstr) : NULL;
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);

		/* the D-Bus service file needs an absolute path, dbus-daemon does not search PATH */
		GString *exec_cmd = g_string_new("/usr/bin/env ");
		if (getenv("RUN_FROM_BUILDDIR")) {
			printf("WARNING: RUN_FROM_BUILDDIR set and --install given: using current directory in desktop entry\n");
			g_string_append_printf(exec_cmd, "-C %s ", g_get_current_dir());
		}
		char *envs[] = {"RUN_FROM_BUILDDIR", "LD_LIBRARY_PATH", "ANDROID_APP_DATA_DIR", "ATL_UGLY_ENABLE_LOCATION", "ATL_UGLY_ENABLE_MICROPHONE", "ATL_UGLY_ENABLE_WEBVIEW", "ATL_DISABLE_WINDOW_DECORATIONS", "ATL_FORCE_FULLSCREEN", "ATL_IS_AUTOMOTIVE", "ATL_IS_TELEVISION", "ATL_IS_WATCH"};
		for (int i = 0; i < ARRAY_SIZE(envs); i++) {
			if (getenv(envs[i])) {
				g_string_append_printf(exec_cmd, "%s=%s ", envs[i], getenv(envs[i]));
			}
		}
		g_string_append_printf(exec_cmd, "%s ", d->prgname);
		g_string_append_printf(exec_cmd, "--gapplication-app-id %s ", package_name);
		if (d->apk_main_activity_class)
			g_string_append_printf(exec_cmd, "-l %s ", d->apk_main_activity_class);
		if (d->window_width)
			g_string_append_printf(exec_cmd, "-w %d ", d->window_width);
		if (d->window_height)
			g_string_append_printf(exec_cmd, "-h %d ", d->window_height);
		if (d->sdk_int)
			g_string_append_printf(exec_cmd, "--sdk-int %s ", d->sdk_int);
		g_string_append_printf(exec_cmd, "%s%s", g_file_get_path(dest), split_paths);
		g_free(split_paths);

		gboolean dbus_activatable = install_dbus_service_file(package_name, exec_cmd->str);

		GString *desktop_entry = g_string_new("[Desktop Entry]\n"
		                                      "Type=Application\n"
		                                      "StartupNotify=true\n"
		                                      "X-Purism-FormFactor=Workstation;Mobile;\n");
		if (dbus_activatable)
			g_string_append(desktop_entry, "DBusActivatable=true\n");
		g_string_append_printf(desktop_entry, "Exec=%s --uri %%u\n", exec_cmd->str);
		if (supported_mime_types)
			g_string_append_printf(desktop_entry, "MimeType=%s\n", supported_mime_types);
		if (dbus_activatable) { // not in a Flatpak sandbox
			/* shown in the launcher's context menu, e.g. a long press in Phosh's app grid */
			g_string_append_printf(desktop_entry, "Actions=uninstall;\n\n[Desktop Action uninstall]\nName=Uninstall\nExec=%s --uninstall\n", exec_cmd->str);
			install_launcher_file(portal, package_name, app_label, icon_serialized, app_data_dir_base, desktop_entry);
		}
		g_string_free(exec_cmd, TRUE);
		struct dynamic_launcher_callback_data *cb_data = g_new(struct dynamic_launcher_callback_data, 1);
		cb_data->desktop_file_id = g_strdup_printf("%s.desktop", package_name);
		cb_data->desktop_entry = g_string_free(desktop_entry, FALSE);
		printf("installing %s\n\n%s\n", cb_data->desktop_file_id, cb_data->desktop_entry);
		xdp_portal_dynamic_launcher_prepare_install(portal, NULL, app_label, icon_serialized, XDP_LAUNCHER_APPLICATION, NULL, TRUE, TRUE, NULL, dynamic_launcher_ready_callback, cb_data);
		return;
	}

	if (!d->apk_instrumentation_class)
		gtk_window_set_title(GTK_WINDOW(window), package_name);
	gtk_window_set_default_size(GTK_WINDOW(window), d->window_width, d->window_height);
	g_signal_connect(window, "close-request", G_CALLBACK(app_exit), env);

	GtkWidget *header_bar = gtk_header_bar_new();
	GtkWidget *back_button = back_button_new();

	gtk_header_bar_pack_start(GTK_HEADER_BAR(header_bar), back_button);
	gtk_window_set_titlebar(GTK_WINDOW(window), header_bar);
	GtkDropTarget *drop_target = gtk_drop_target_new(G_TYPE_STRING, GDK_ACTION_COPY);
	g_signal_connect(drop_target, "drop", G_CALLBACK(on_drop), NULL);
	gtk_widget_add_controller(window, GTK_EVENT_CONTROLLER(drop_target));
	gtk_window_present(GTK_WINDOW(window));

	// set package name as application id for window icon on Wayland. Needs a {package_name}.desktop file defining the icon
	const char *app_id = g_application_get_application_id(G_APPLICATION(app));
	GdkToplevel *toplevel = GDK_TOPLEVEL(gtk_native_get_surface(GTK_NATIVE(window)));
	if (GDK_IS_WAYLAND_TOPLEVEL(toplevel) && !d->apk_instrumentation_class && !strcmp(app_id, "com.example.demo_application")) {
		gdk_wayland_toplevel_set_application_id(GDK_WAYLAND_TOPLEVEL(toplevel), package_name);
	}

	update_config_for_window(GTK_WINDOW(window));

	if (!d->apk_instrumentation_class && app_icon_path) {
		char *app_icon_path_full = malloc(strlen(app_data_dir) + 1 + strlen(app_icon_path) + 1); // +1 for /, +1 for NULL
		sprintf(app_icon_path_full, "%s/%s", app_data_dir, app_icon_path);

		extract_from_apk(app_icon_path, app_icon_path);

		GError *error = NULL;
		GList *icon_list = g_list_append(NULL, gdk_texture_new_from_filename(app_icon_path_full, &error));
		if (error) {
			fprintf(stderr, "gdk_texture_new_from_filename: %s\n", error->message);
			g_clear_error(&error);
		}
		icon_override(window, icon_list);
		/* if Gtk sets the icon list to NULL, override it again */
		g_signal_connect_after(window, "realize", G_CALLBACK(icon_override), icon_list);
	}

	if (!d->apk_instrumentation_class) {
		activity_start(env, activity_object);

		g_timeout_add(10, G_SOURCE_FUNC(hacky_on_window_focus_changed_callback), env);
	}

	jobject input_queue_callback = g_object_get_data(G_OBJECT(window), "input_queue_callback");
	if (input_queue_callback) {
		jobject input_queue = g_object_get_data(G_OBJECT(window), "input_queue");

		J__InputQueue__Callback__onInputQueueCreated(env, input_queue_callback, input_queue);
		if ((*env)->ExceptionCheck(env))
			(*env)->ExceptionDescribe(env);
	}

	if (strcmp(app_id, "com.example.demo_application")) {
		// This would normally happen automatically, if the GApplication is not contructed with G_APPLICATION_NON_UNIQUE
		g_dbus_connection_call(g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL),
		                       "org.freedesktop.DBus",
		                       "/org/freedesktop/DBus",
		                       "org.freedesktop.DBus",
		                       "RequestName",
		                       g_variant_new("(su)", app_id, G_BUS_NAME_OWNER_FLAGS_NONE),
		                       G_VARIANT_TYPE("(u)"),
		                       0, -1, NULL, NULL, NULL);
	}
}

static void activate(GtkApplication *app, struct jni_callback_data *d)
{
	if (app_starting && !window) // D-Bus activation's own request, which started us
		return;
	if (window) { // this is not the first launch, but a DBus activate request to the running app
		gtk_window_present(GTK_WINDOW(window));
		return;
	}
	fprintf(stderr, "error: usage: ./android-translation-layer [app.apk] [-l path/to/activity]\n"
	                "you can specify --help to see the list of options\n");
	exit(1);
}

static gboolean option_uri_cb(const gchar *option_name, const gchar *value, gpointer data, GError **error)
{
	fprintf(stderr, "option_uri_cb: %s %s, %p, %p\n", option_name, value, data, error);
	uri_option = g_strdup(value);
	return TRUE;
}

void init_cmd_parameters(GApplication *app, struct jni_callback_data *d)
{
	const GOptionEntry cmd_params[] = {
		/* clang-format off */
		/* long_name | short_name | flags | arg                 | arg_data                     | description                                                                                   | arg_desc */
		{ "launch-activity",  'l', 0, G_OPTION_ARG_STRING,       &d->apk_main_activity_class,   "the fully qualifed name of the activity you wish to launch (usually the apk's main activity)", "ACTIVITY_NAME" },
		{ "instrument",        0,  0, G_OPTION_ARG_STRING,       &d->apk_instrumentation_class, "the fully qualifed name of the instrumentation you wish to launch",                            "CLASS_NAME"    },
		{ "window-width",     'w', 0, G_OPTION_ARG_INT,          &d->window_width,              "window width to launch with (some apps react poorly to runtime window size adjustments)",      "WIDTH"         },
		{ "window-height",    'h', 0, G_OPTION_ARG_INT,          &d->window_height,             "window height to launch with (some apps react poorly to runtime window size adjustments)",     "HEIGHT"        },
		{ "install",          'i', 0, G_OPTION_ARG_NONE,         &d->install,                   "install .desktop file for the given apk",                                                      NULL            },
		{ "install-internal",  0 , 0, G_OPTION_ARG_NONE,         &d->install_internal,          "copy an apk to _installed_apks_ but don't create a desktop entry",                             NULL            },
		{ "install-ask",       0 , 0, G_OPTION_ARG_NONE,         &d->install_ask,               "ask before installing the given apk, like Android's package installer (for opening apk files)", NULL            },
		{ "install-flatpak",   0 , 0, G_OPTION_ARG_NONE,         &d->install_flatpak,           "install the given apk as its own Flatpak, without running its code",                           NULL            },
		{ "uninstall",         0 , 0, G_OPTION_ARG_NONE,         &d->uninstall,                 "remove an apk installed with --install, its data and its launcher (asks first)",                NULL            },
		{ "dbus-activated",    0 , G_OPTION_FLAG_HIDDEN, G_OPTION_ARG_NONE, &d->dbus_activated, "started through the launcher's D-Bus service file",                                          NULL            },
		{ "extra-jvm-option", 'X', 0, G_OPTION_ARG_STRING_ARRAY, &d->extra_jvm_options,         "pass an additional option directly to art (e.g -X \"-verbose:jni\")",                          "\"OPTION\""    },
		{ "extra-string-key", 'e', 0, G_OPTION_ARG_STRING_ARRAY, &d->extra_string_keys,         "pass a string extra (-e key=value)",                                                           "\"KEY=VALUE\"" },
		{ "sdk-int",           0 , 0, G_OPTION_ARG_STRING,       &d->sdk_int,                   "shorthand for -X \"-DBuild.VERSION.SDK_INT=<version>\"",                                                           "SDK_INT" },
		/* long_name | short_name | flags                     | arg                  | arg_data     | description                                                                              | arg_desc */
		{ "uri",              'u', G_OPTION_FLAG_OPTIONAL_ARG, G_OPTION_ARG_CALLBACK, option_uri_cb, "open the given URI inside the application",                                               "URI"           },
		{NULL}
		/* clang-format on */
	};

	g_application_add_main_option_entries(G_APPLICATION(app), cmd_params);
}

void init__r_debug();
void remove_ongoing_notifications();

typedef bool(apply_path_overrides_func_type)(char **);
void libc_bio_set_apply_path_overrides_func(apply_path_overrides_func_type *func);

/*
 * The main thread's stack is initialized to 128KiB by Linux.
 * When the stack grows below the allocated mapping, the kernel
 * automagically maps in more memory (as long as there is no existing
 * mapping in the way).
 * However, ART puts in a guard page of it's own in order to catch stack
 * overflows (and if they happen in Java code, it handles them gracefully).
 * When ART uses pthread_getattr_np to get the main thread's stack size,
 * glibc reports RLIMIT_STACK (minus TLS and stuff), while musl reports
 * the current stack size. Therefore on musl we have to pre-grow the stack
 * to make the kernel actually grow the mapping before ART puts in it's guard
 * page and makes further growth impossible.
 */
static void pregrow_stack()
{
	/* set RLIMIT_STACK to 8 MiB, which should fit both our 6 MiB stack and whatever
	 * the libc stores above the top of stack */
	setrlimit(RLIMIT_STACK, &(struct rlimit){8 * MiB, 8 * MiB});
	/* accessing the first element of this array will grow the stack down 6MiB */
	volatile uint8_t dummy[6 * MiB];
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wuninitialized"
	/* noop, but should generate an access because volatile */
	dummy[0] = dummy[0];
#pragma GCC diagnostic pop
}

int main(int argc, char **argv)
{
	GtkApplication *app;
	int status;

	pregrow_stack();

	/* this has to be done in the main executable, so might as well do it here */
	init__r_debug();

	// locale on android is always either C or C.UTF-8, and some apps might unbeknownst to them depend on that
	// for correct functionality
	setenv("LC_ALL", "C.UTF-8", 1);

	libc_bio_set_apply_path_overrides_func(apply_path_overrides);

	struct jni_callback_data *callback_data = malloc(sizeof(struct jni_callback_data));
	callback_data->apk_main_activity_class = NULL;
	callback_data->apk_instrumentation_class = NULL;
	callback_data->window_width = 960;
	callback_data->window_height = 540;
	callback_data->install = FALSE;
	callback_data->install_internal = FALSE;
	callback_data->install_ask = FALSE;
	callback_data->prgname = argv[0];
	callback_data->extra_jvm_options = NULL;
	callback_data->extra_string_keys = NULL;
	callback_data->sdk_int = NULL;
	callback_data->uninstall = FALSE;
	callback_data->dbus_activated = FALSE;

	app = gtk_application_new("com.example.demo_application", G_APPLICATION_NON_UNIQUE | G_APPLICATION_HANDLES_OPEN | G_APPLICATION_CAN_OVERRIDE_APP_ID);

	// cmdline related setup
	init_cmd_parameters(G_APPLICATION(app), callback_data);
	g_application_set_option_context_summary(G_APPLICATION(app), "a translation layer for running android applications natively on Linux");

	g_signal_connect(app, "activate", G_CALLBACK(activate), callback_data);
	g_signal_connect(app, "open", G_CALLBACK(open), callback_data);
	g_action_map_add_action_entries(G_ACTION_MAP(app), action_entries, action_entries_count, NULL);
	const GActionEntry uninstall_action_entry = {"uninstall", action_uninstall};
	g_action_map_add_action_entries(G_ACTION_MAP(app), &uninstall_action_entry, 1, app);
	status = g_application_run(G_APPLICATION(app), argc, argv);
	remove_ongoing_notifications();
	g_object_unref(app);

	if (jvm) {
		JNIEnv *env = get_jni_env();
		J__System__exit(env, status);
	}

	return status;
}

/* TODO: recall what this is doing here */
const char dl_loader[] __attribute__((section(".interp"))) = "/lib/ld-linux.so.2";
