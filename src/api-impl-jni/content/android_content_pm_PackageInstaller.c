/* Inside an app's own Flatpak, installing, uninstalling, listing and starting other apps goes through
 * the installer service on the host (src/installer-service), which only app stores may reach. */

#include <gio/gio.h>
#include <gio/gunixfdlist.h>
#include <glib/gstdio.h>

#include <fcntl.h>
#include <unistd.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_content_pm_PackageInstaller.h"

#define SERVICE_NAME      "de.nexumia.atl.Installer"
#define SERVICE_PATH      "/de/nexumia/atl/Installer"
#define SERVICE_INTERFACE "de.nexumia.atl.Installer"

/* the interface version this code needs, see src/installer-service/de.nexumia.atl.Installer.xml;
 * the service comes with the host package and is updated separately from this engine */
#define SERVICE_MIN_VERSION 1

static gboolean check_service_version(GDBusConnection *bus, GError **error)
{
	static guint32 version = 0;
	G_LOCK_DEFINE_STATIC(version);
	G_LOCK(version);
	if (version) {
		G_UNLOCK(version);
		return version >= SERVICE_MIN_VERSION ? TRUE : (g_set_error(error, G_IO_ERROR, G_IO_ERROR_NOT_SUPPORTED,
		                                                        "the installer service on the host is too old (interface %u, "
		                                                        "need %u): update android-translation-layer",
		                                                        version, SERVICE_MIN_VERSION), FALSE);
	}
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_sync(bus, SERVICE_NAME, SERVICE_PATH, "org.freedesktop.DBus.Properties", "Get",
	                                              g_variant_new("(ss)", SERVICE_INTERFACE, "Version"), G_VARIANT_TYPE("(v)"),
	                                              G_DBUS_CALL_FLAGS_NONE, -1, NULL, &err);
	if (reply) {
		GVariant *value;
		g_variant_get(reply, "(v)", &value);
		version = g_variant_is_of_type(value, G_VARIANT_TYPE_UINT32) ? g_variant_get_uint32(value) : 1;
		g_variant_unref(value);
		g_variant_unref(reply);
	} else if (g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_SERVICE_UNKNOWN) ||
	           g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_NAME_HAS_NO_OWNER)) {
		/* inside a Flatpak the D-Bus proxy answers the same when the app may not talk to it */
		g_set_error(error, G_IO_ERROR, G_IO_ERROR_NOT_FOUND,
		            "the installer service is not reachable: this app does not request REQUEST_INSTALL_PACKAGES, "
		            "or the host's android-translation-layer is older than this engine");
		g_error_free(err);
		G_UNLOCK(version);
		return FALSE;
	} else {
		version = 1; // the first service had no Version property
		g_error_free(err);
	}
	G_UNLOCK(version);
	return check_service_version(bus, error);
}

/* waits as long as the user looks at the install page */
static GVariant *call_service(const char *method, GVariant *args, const char *reply_type, GUnixFDList *fds_in,
                              GUnixFDList **fds_out, GError **error)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, error);
	if (!bus)
		return NULL;
	if (!check_service_version(bus, error)) {
		g_object_unref(bus);
		return NULL;
	}
	GVariant *reply = g_dbus_connection_call_with_unix_fd_list_sync(bus, SERVICE_NAME, SERVICE_PATH, SERVICE_INTERFACE, method, args,
	                                                                G_VARIANT_TYPE(reply_type), G_DBUS_CALL_FLAGS_NONE, G_MAXINT,
	                                                                fds_in, fds_out, NULL, error);
	g_object_unref(bus);
	return reply;
}

JNIEXPORT jstring JNICALL Java_android_content_pm_PackageInstaller_nativeInstallThroughService(JNIEnv *env, jclass class, jobjectArray apks,
                                                                                              jstring sdk_int_jstr, jint width, jint height)
{
	GUnixFDList *fds = g_unix_fd_list_new();
	GVariantBuilder handles;
	g_variant_builder_init(&handles, G_VARIANT_TYPE("ah"));
	int n = (*env)->GetArrayLength(env, apks);
	for (int i = 0; i < n; i++) {
		jstring path_jstr = (*env)->GetObjectArrayElement(env, apks, i);
		const char *path = (*env)->GetStringUTFChars(env, path_jstr, NULL);
		int fd = open(path, O_RDONLY | O_CLOEXEC);
		(*env)->ReleaseStringUTFChars(env, path_jstr, path);
		if (fd < 0) {
			g_variant_builder_clear(&handles);
			g_object_unref(fds);
			return _JSTRING("could not open an APK");
		}
		g_variant_builder_add(&handles, "h", g_unix_fd_list_append(fds, fd, NULL));
		close(fd);
	}
	GVariantBuilder options;
	g_variant_builder_init(&options, G_VARIANT_TYPE("a{sv}"));
	const char *sdk_int = (*env)->GetStringUTFChars(env, sdk_int_jstr, NULL);
	g_variant_builder_add(&options, "{sv}", "sdk-int", g_variant_new_string(sdk_int));
	(*env)->ReleaseStringUTFChars(env, sdk_int_jstr, sdk_int);
	g_variant_builder_add(&options, "{sv}", "width", g_variant_new_int32(width));
	g_variant_builder_add(&options, "{sv}", "height", g_variant_new_int32(height));

	GError *err = NULL;
	GVariant *reply = call_service("Install", g_variant_new("(aha{sv})", &handles, &options), "(s)", fds, NULL, &err);
	g_object_unref(fds);
	if (!reply) {
		jstring message = _JSTRING(err->message);
		g_error_free(err);
		return message;
	}
	const char *error;
	g_variant_get(reply, "(&s)", &error);
	jstring result = *error ? _JSTRING(error) : NULL;
	g_variant_unref(reply);
	return result;
}

JNIEXPORT jboolean JNICALL Java_android_content_pm_PackageInstaller_nativeUninstallThroughService(JNIEnv *env, jclass class, jstring package_jstr)
{
	const char *package = (*env)->GetStringUTFChars(env, package_jstr, NULL);
	GError *err = NULL;
	GVariant *reply = call_service("Uninstall", g_variant_new("(s)", package), "(b)", NULL, NULL, &err);
	(*env)->ReleaseStringUTFChars(env, package_jstr, package);
	if (!reply) {
		fprintf(stderr, "uninstall through the installer service: %s\n", err->message);
		g_error_free(err);
		return JNI_FALSE;
	}
	gboolean removed;
	g_variant_get(reply, "(b)", &removed);
	g_variant_unref(reply);
	return removed;
}

JNIEXPORT void JNICALL Java_android_content_pm_PackageInstaller_nativeLaunchThroughService(JNIEnv *env, jclass class, jstring package_jstr, jstring uri_jstr)
{
	const char *package = (*env)->GetStringUTFChars(env, package_jstr, NULL);
	const char *uri = uri_jstr ? (*env)->GetStringUTFChars(env, uri_jstr, NULL) : NULL;
	GError *err = NULL;
	GVariant *reply = call_service("Launch", g_variant_new("(ss)", package, uri ? uri : ""), "()", NULL, NULL, &err);
	if (reply) {
		g_variant_unref(reply);
	} else {
		fprintf(stderr, "launch %s through the installer service: %s\n", package, err->message);
		g_error_free(err);
	}
	(*env)->ReleaseStringUTFChars(env, package_jstr, package);
	if (uri)
		(*env)->ReleaseStringUTFChars(env, uri_jstr, uri);
}

/* package name -> fd of its apk, kept open: dir/<package>.apk links to /proc/self/fd/<fd>, so the
 * rest of ATL reads installed apps from a directory as outside a sandbox */
static GHashTable *installed_fds = NULL;
G_LOCK_DEFINE_STATIC(installed_fds);

JNIEXPORT void JNICALL Java_android_content_pm_PackageInstaller_nativeRefreshInstalledThroughService(JNIEnv *env, jclass class, jstring dir_jstr)
{
	GUnixFDList *fds = NULL;
	GError *err = NULL;
	GVariant *reply = call_service("List", NULL, "(a(sh))", NULL, &fds, &err);
	if (!reply) {
		fprintf(stderr, "list through the installer service: %s\n", err->message);
		g_error_free(err);
		return;
	}
	const char *dir_c = (*env)->GetStringUTFChars(env, dir_jstr, NULL);
	char *dir = g_strdup(dir_c);
	(*env)->ReleaseStringUTFChars(env, dir_jstr, dir_c);
	g_mkdir_with_parents(dir, 0700);

	G_LOCK(installed_fds);
	if (!installed_fds)
		installed_fds = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, NULL);
	GHashTable *seen = g_hash_table_new(g_str_hash, g_str_equal);
	GVariantIter *iter;
	const char *package;
	gint32 handle;
	g_variant_get(reply, "(a(sh))", &iter);
	while (g_variant_iter_next(iter, "(&sh)", &package, &handle)) {
		if (strchr(package, '/') || package[0] == '.')
			continue;
		int fd = fds ? g_unix_fd_list_get(fds, handle, NULL) : -1;
		if (fd < 0)
			continue;
		gpointer old;
		if (g_hash_table_lookup_extended(installed_fds, package, NULL, &old))
			close(GPOINTER_TO_INT(old));
		g_hash_table_insert(installed_fds, g_strdup(package), GINT_TO_POINTER(fd));
		g_hash_table_add(seen, (gpointer)package);
		char *link = g_strdup_printf("%s/%s.apk", dir, package);
		char *target = g_strdup_printf("/proc/self/fd/%d", fd);
		g_unlink(link);
		if (symlink(target, link))
			fprintf(stderr, "can't link %s: %m\n", link);
		g_free(target);
		g_free(link);
	}
	g_variant_iter_free(iter);
	/* apps uninstalled since */
	GHashTableIter table_iter;
	gpointer key, value;
	g_hash_table_iter_init(&table_iter, installed_fds);
	while (g_hash_table_iter_next(&table_iter, &key, &value)) {
		if (g_hash_table_contains(seen, key))
			continue;
		char *link = g_strdup_printf("%s/%s.apk", dir, (char *)key);
		g_unlink(link);
		g_free(link);
		close(GPOINTER_TO_INT(value));
		g_hash_table_iter_remove(&table_iter);
	}
	G_UNLOCK(installed_fds);
	g_hash_table_unref(seen);
	g_variant_unref(reply);
	g_clear_object(&fds);
	g_free(dir);
}
