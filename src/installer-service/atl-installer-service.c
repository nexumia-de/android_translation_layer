/* The installer service on the host (docs/hardening-roadmap.md, phase 2): app stores running in their
 * own Flatpak hand it APK files; it asks the user, checks the signer and builds the app's own
 * Flatpak with `android-translation-layer --install-ask --install-flatpak`. Only Flatpaks of APKs
 * that request REQUEST_INSTALL_PACKAGES may talk to it (make-app-flatpak). The caller's identity
 * comes from its sandbox, never from a parameter. Started by D-Bus, exits when idle. */

#include <gio/gio.h>
#include <gio/gdesktopappinfo.h>
#include <gio/gunixfdlist.h>
#include <gio/gunixinputstream.h>
#include <glib/gstdio.h>

#include <fcntl.h>
#include <string.h>
#include <unistd.h>

#define BUS_NAME     "de.nexumia.atl.Installer"
#define OBJECT_PATH  "/de/nexumia/atl/Installer"
#define APP_PREFIX   "de.nexumia.atl."
#define IDLE_TIMEOUT 120

/* the interface, with its rules for changes: de.nexumia.atl.Installer.xml */
#define INTERFACE_VERSION 1

static GMainLoop *loop;
static guint idle_source;
static int running;

static gboolean idle_quit(gpointer data)
{
	g_main_loop_quit(loop);
	return G_SOURCE_REMOVE;
}

static void hold(void)
{
	running++;
	g_clear_handle_id(&idle_source, g_source_remove);
}

static void release(void)
{
	if (--running == 0)
		idle_source = g_timeout_add_seconds(IDLE_TIMEOUT, idle_quit, NULL);
}

static const char *atl_binary(void)
{
	const char *path = g_getenv("ATL_BINARY");
	return path ? path : INSTALL_BINDIR "/android-translation-layer";
}

/* Android's rule for package names; they end up in paths and app ids */
static gboolean valid_package_name(const char *name)
{
	static GRegex *regex = NULL;
	if (!regex)
		regex = g_regex_new("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$", 0, 0, NULL);
	return name && strlen(name) < 200 && g_regex_match(regex, name, 0, NULL);
}

static char *flatpak_user_dir(void)
{
	const char *dir = g_getenv("FLATPAK_USER_DIR");
	return dir ? g_strdup(dir) : g_build_filename(g_get_user_data_dir(), "flatpak", NULL);
}

/* the base APK inside an installed app Flatpak */
static char *installed_apk(const char *package)
{
	char *flatpak = flatpak_user_dir();
	char *id = g_strconcat(APP_PREFIX, package, NULL);
	char *path = g_build_filename(flatpak, "app", id, "current", "active", "files", "share", "atl", "apk0.apk", NULL);
	g_free(id);
	g_free(flatpak);
	return path;
}

/* the caller's Flatpak app id and the name the user knows it by; a caller outside a sandbox
 * could do all of this itself and gets a generic name */
static char *caller_name(GDBusMethodInvocation *invocation)
{
	GDBusConnection *bus = g_dbus_method_invocation_get_connection(invocation);
	GVariant *reply = g_dbus_connection_call_sync(bus, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
	                                              "GetConnectionUnixProcessID",
	                                              g_variant_new("(s)", g_dbus_method_invocation_get_sender(invocation)),
	                                              G_VARIANT_TYPE("(u)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	if (!reply)
		return g_strdup("An app");
	guint32 pid;
	g_variant_get(reply, "(u)", &pid);
	g_variant_unref(reply);

	char *info_path = g_strdup_printf("/proc/%u/root/.flatpak-info", pid);
	GKeyFile *info = g_key_file_new();
	char *app_id = NULL;
	if (g_key_file_load_from_file(info, info_path, G_KEY_FILE_NONE, NULL))
		app_id = g_key_file_get_string(info, "Application", "name", NULL);
	g_key_file_free(info);
	g_free(info_path);
	if (!app_id)
		return g_strdup("An app");

	char *desktop_id = g_strconcat(app_id, ".desktop", NULL);
	GDesktopAppInfo *app_info = g_desktop_app_info_new(desktop_id);
	char *name = app_info ? g_strdup(g_app_info_get_name(G_APP_INFO(app_info))) : g_strdup(app_id);
	g_clear_object(&app_info);
	g_free(desktop_id);
	g_free(app_id);
	return name;
}

static gboolean copy_fd_to_file(int fd, const char *path, GError **error)
{
	GInputStream *in = g_unix_input_stream_new(fd, FALSE);
	GFile *file = g_file_new_for_path(path);
	GFileOutputStream *out = g_file_replace(file, NULL, FALSE, G_FILE_CREATE_PRIVATE, NULL, error);
	gboolean ok = out && g_output_stream_splice(G_OUTPUT_STREAM(out), in,
	                                            G_OUTPUT_STREAM_SPLICE_CLOSE_SOURCE | G_OUTPUT_STREAM_SPLICE_CLOSE_TARGET,
	                                            NULL, error) >= 0;
	g_clear_object(&out);
	g_object_unref(file);
	g_object_unref(in);
	return ok;
}

static int remove_entry(const char *path)
{
	GDir *dir = g_dir_open(path, 0, NULL);
	if (dir) {
		const char *name;
		while ((name = g_dir_read_name(dir))) {
			char *child = g_build_filename(path, name, NULL);
			g_unlink(child);
			g_free(child);
		}
		g_dir_close(dir);
	}
	return g_rmdir(path);
}

struct install {
	GDBusMethodInvocation *invocation;
	char *staging_dir;
};

/* exit status of --install-ask: 0 installed, 2 the user said no, 3 refused (signer, version or not
 * signed), anything else failed */
static void install_finished(GObject *source, GAsyncResult *res, gpointer user_data)
{
	struct install *install = user_data;
	GSubprocess *proc = G_SUBPROCESS(source);
	GError *err = NULL;
	const char *error;
	char *message = NULL;
	if (!g_subprocess_wait_finish(proc, res, &err)) {
		message = g_strdup(err->message);
		error = message;
		g_error_free(err);
	} else if (!g_subprocess_get_if_exited(proc)) {
		error = "the installer crashed";
	} else {
		switch (g_subprocess_get_exit_status(proc)) {
			case 0: error = ""; break;
			case 2: error = "cancelled"; break;
			case 3: error = "refused: not signed, signed by someone else, or older than the installed version"; break;
			default: error = "the installer failed"; break;
		}
	}
	g_message("install from %s: %s", install->staging_dir, *error ? error : "installed");
	g_dbus_method_invocation_return_value(install->invocation, g_variant_new("(s)", error));
	g_free(message);
	remove_entry(install->staging_dir);
	g_free(install->staging_dir);
	g_free(install);
	release();
}

static void handle_install(GDBusMethodInvocation *invocation, GVariant *params)
{
	GVariant *handles, *options;
	g_variant_get(params, "(@ah@a{sv})", &handles, &options);
	GDBusMessage *message = g_dbus_method_invocation_get_message(invocation);
	GUnixFDList *fds = g_dbus_message_get_unix_fd_list(message);
	gsize n = g_variant_n_children(handles);
	if (!fds || n == 0 || n > 64) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "no APK given"));
		goto out;
	}

	char *base = g_build_filename(g_get_user_cache_dir(), "android_translation_layer", "installer", NULL);
	g_mkdir_with_parents(base, 0700);
	char *staging_dir = g_build_filename(base, "XXXXXX", NULL);
	g_free(base);
	if (!g_mkdtemp_full(staging_dir, 0700)) {
		g_free(staging_dir);
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "no staging directory"));
		goto out;
	}

	GPtrArray *argv = g_ptr_array_new_with_free_func(g_free);
	g_ptr_array_add(argv, g_strdup(atl_binary()));
	g_ptr_array_add(argv, g_strdup("--install-ask"));
	g_ptr_array_add(argv, g_strdup("--install-flatpak"));
	g_ptr_array_add(argv, g_strdup("--installer-name"));
	g_ptr_array_add(argv, caller_name(invocation));
	const char *sdk_int;
	if (g_variant_lookup(options, "sdk-int", "&s", &sdk_int) && *sdk_int && strspn(sdk_int, "0123456789") == strlen(sdk_int)) {
		g_ptr_array_add(argv, g_strdup("--sdk-int"));
		g_ptr_array_add(argv, g_strdup(sdk_int));
	}
	gint32 size;
	if (g_variant_lookup(options, "width", "i", &size) && size > 0 && size < 10000) {
		g_ptr_array_add(argv, g_strdup("-w"));
		g_ptr_array_add(argv, g_strdup_printf("%d", size));
	}
	if (g_variant_lookup(options, "height", "i", &size) && size > 0 && size < 10000) {
		g_ptr_array_add(argv, g_strdup("-h"));
		g_ptr_array_add(argv, g_strdup_printf("%d", size));
	}

	GError *err = NULL;
	for (gsize i = 0; i < n; i++) {
		gint32 handle;
		g_variant_get_child(handles, i, "h", &handle);
		int fd = g_unix_fd_list_get(fds, handle, &err);
		char *path = g_strdup_printf("%s/apk%zu.apk", staging_dir, i);
		gboolean ok = fd >= 0 && copy_fd_to_file(fd, path, &err);
		if (fd >= 0)
			close(fd);
		if (!ok) {
			g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", err ? err->message : "unreadable APK"));
			g_clear_error(&err);
			g_free(path);
			g_ptr_array_free(argv, TRUE);
			remove_entry(staging_dir);
			g_free(staging_dir);
			goto out;
		}
		g_ptr_array_add(argv, path);
	}
	g_ptr_array_add(argv, NULL);

	GSubprocess *proc = g_subprocess_newv((const char *const *)argv->pdata, G_SUBPROCESS_FLAGS_NONE, &err);
	g_ptr_array_free(argv, TRUE);
	if (!proc) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", err->message));
		g_error_free(err);
		remove_entry(staging_dir);
		g_free(staging_dir);
		goto out;
	}
	struct install *install = g_new0(struct install, 1);
	install->invocation = invocation;
	install->staging_dir = staging_dir;
	hold();
	g_subprocess_wait_async(proc, NULL, install_finished, install);
	g_object_unref(proc);
out:
	g_variant_unref(handles);
	g_variant_unref(options);
}

static void uninstall_finished(GObject *source, GAsyncResult *res, gpointer user_data)
{
	GDBusMethodInvocation *invocation = user_data;
	GSubprocess *proc = G_SUBPROCESS(source);
	gboolean removed = g_subprocess_wait_finish(proc, res, NULL) && g_subprocess_get_if_exited(proc) &&
	                   g_subprocess_get_exit_status(proc) == 0;
	g_dbus_method_invocation_return_value(invocation, g_variant_new("(b)", removed));
	release();
}

static void handle_uninstall(GDBusMethodInvocation *invocation, GVariant *params)
{
	const char *package;
	g_variant_get(params, "(&s)", &package);
	char *apk = installed_apk(package);
	gboolean installed = valid_package_name(package) && g_file_test(apk, G_FILE_TEST_IS_REGULAR);
	g_free(apk);
	if (!installed) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(b)", FALSE));
		return;
	}
	char *name = caller_name(invocation);
	GError *err = NULL;
	GSubprocess *proc = g_subprocess_new(G_SUBPROCESS_FLAGS_NONE, &err, atl_binary(), "--uninstall-flatpak", package,
	                                     "--installer-name", name, NULL);
	g_free(name);
	if (!proc) {
		g_warning("uninstall: %s", err->message);
		g_error_free(err);
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(b)", FALSE));
		return;
	}
	hold();
	g_subprocess_wait_async(proc, NULL, uninstall_finished, invocation);
	g_object_unref(proc);
}

static void handle_list(GDBusMethodInvocation *invocation)
{
	char *flatpak = flatpak_user_dir();
	char *apps = g_build_filename(flatpak, "app", NULL);
	g_free(flatpak);
	GVariantBuilder builder;
	g_variant_builder_init(&builder, G_VARIANT_TYPE("a(sh)"));
	GUnixFDList *fds = g_unix_fd_list_new();
	GDir *dir = g_dir_open(apps, 0, NULL);
	if (dir) {
		const char *id;
		while ((id = g_dir_read_name(dir))) {
			if (!g_str_has_prefix(id, APP_PREFIX))
				continue;
			const char *package = id + strlen(APP_PREFIX);
			if (!valid_package_name(package))
				continue;
			char *apk = installed_apk(package);
			int fd = open(apk, O_RDONLY | O_CLOEXEC);
			g_free(apk);
			if (fd < 0) // the engine and other Flatpaks without an APK
				continue;
			int handle = g_unix_fd_list_append(fds, fd, NULL);
			close(fd);
			if (handle >= 0)
				g_variant_builder_add(&builder, "(sh)", package, handle);
		}
		g_dir_close(dir);
	}
	g_free(apps);
	g_dbus_method_invocation_return_value_with_unix_fd_list(invocation, g_variant_new("(a(sh))", &builder), fds);
	g_object_unref(fds);
}

static void handle_launch(GDBusMethodInvocation *invocation, GVariant *params)
{
	const char *package, *uri;
	g_variant_get(params, "(&s&s)", &package, &uri);
	char *apk = installed_apk(package);
	gboolean installed = valid_package_name(package) && g_file_test(apk, G_FILE_TEST_IS_REGULAR);
	g_free(apk);
	if (!installed) {
		g_dbus_method_invocation_return_error(invocation, G_DBUS_ERROR, G_DBUS_ERROR_INVALID_ARGS, "%s is not installed", package);
		return;
	}
	char *id = g_strconcat(APP_PREFIX, package, NULL);
	char *uri_arg = *uri ? g_strconcat("--uri=", uri, NULL) : NULL;
	GError *err = NULL;
	GSubprocess *proc = g_subprocess_new(G_SUBPROCESS_FLAGS_NONE, &err, "flatpak", "run", "--user", id, uri_arg, NULL);
	g_free(uri_arg);
	g_free(id);
	if (!proc) {
		g_dbus_method_invocation_return_gerror(invocation, err);
		g_error_free(err);
		return;
	}
	g_object_unref(proc);
	g_dbus_method_invocation_return_value(invocation, NULL);
}

static void method_call(GDBusConnection *connection, const char *sender, const char *object_path, const char *interface_name,
                        const char *method_name, GVariant *params, GDBusMethodInvocation *invocation, gpointer user_data)
{
	hold();
	if (!strcmp(method_name, "Install"))
		handle_install(invocation, params);
	else if (!strcmp(method_name, "Uninstall"))
		handle_uninstall(invocation, params);
	else if (!strcmp(method_name, "List"))
		handle_list(invocation);
	else if (!strcmp(method_name, "Launch"))
		handle_launch(invocation, params);
	release();
}

static GVariant *get_property(GDBusConnection *connection, const char *sender, const char *object_path,
                              const char *interface_name, const char *property_name, GError **error, gpointer user_data)
{
	return g_variant_new_uint32(INTERFACE_VERSION);
}

static const GDBusInterfaceVTable vtable = {method_call, get_property, NULL, {0}};

static void bus_acquired(GDBusConnection *connection, const char *name, gpointer user_data)
{
	GBytes *xml = g_resources_lookup_data("/de/nexumia/atl/Installer/de.nexumia.atl.Installer.xml", 0, NULL);
	GDBusNodeInfo *node = g_dbus_node_info_new_for_xml(g_bytes_get_data(xml, NULL), NULL);
	g_dbus_connection_register_object(connection, OBJECT_PATH, node->interfaces[0], &vtable, NULL, NULL, NULL);
	g_dbus_node_info_unref(node);
	g_bytes_unref(xml);
}

static void name_lost(GDBusConnection *connection, const char *name, gpointer user_data)
{
	g_main_loop_quit(loop);
}

int main(int argc, char **argv)
{
	loop = g_main_loop_new(NULL, FALSE);
	g_bus_own_name(G_BUS_TYPE_SESSION, BUS_NAME, G_BUS_NAME_OWNER_FLAGS_NONE, bus_acquired, NULL, name_lost, NULL, NULL);
	idle_source = g_timeout_add_seconds(IDLE_TIMEOUT, idle_quit, NULL);
	g_main_loop_run(loop);
	return 0;
}
