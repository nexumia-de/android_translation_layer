#define _GNU_SOURCE

#include <gio/gdesktopappinfo.h>
#include <gtk/gtk.h>
#include <libportal/portal.h>
#include <string.h>
#ifdef XDP_TYPE_INPUT_CAPTURE_SESSION // libportal >= 0.8
	#include <libportal/settings.h>
#endif

#include "portal-email.h"
#include "portal-openuri.h"
#include "unifiedpush-connector.h"
#include "unifiedpush-distributor.h"

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_content_Context.h"

#include "android_content_Context.h"

static void monitor_changed_cb(GdkSurface *surface, GdkMonitor *monitor, jobject configuration)
{
	JNIEnv *env = get_jni_env();
	GdkRectangle geometry;
	gdk_monitor_get_geometry(monitor, &geometry);
	if (!configuration)
		configuration = _GET_STATIC_OBJ_FIELD(_CACHED_CLASS(Context), "sys_config", "Landroid/content/res/Configuration;");

	_SET_INT_FIELD(configuration, "screenWidthDp", geometry.width);
	_SET_INT_FIELD(configuration, "screenHeightDp", geometry.height);
	_SET_INT_FIELD(configuration, "smallestScreenWidthDp", MIN(geometry.width, geometry.height));
	if (geometry.width >= 800 && geometry.height >= 800)
		_SET_INT_FIELD(configuration, "screenLayout", /*SCREENLAYOUT_SIZE_LARGE*/ 0x03);
	else
		_SET_INT_FIELD(configuration, "screenLayout", /*SCREENLAYOUT_SIZE_NORMAL*/ 0x02);
}

#ifdef XDP_TYPE_INPUT_CAPTURE_SESSION // libportal >= 0.8
static void settings_changed_cb(XdpSettings *xdp_settings, gchar *namestpace, gchar *key, GVariant *value, jobject configuration)
{
	JNIEnv *env;
	if (!strcmp(namestpace, "org.freedesktop.appearance") && !strcmp(key, "color-scheme")) {
		int color_sheme = g_variant_get_uint32(value);
		g_object_set(gtk_settings_get_default(), "gtk-application-prefer-dark-theme", color_sheme == 1, NULL);
		env = get_jni_env();
		if (!configuration) {
			configuration = _GET_STATIC_OBJ_FIELD(_CACHED_CLASS(Context), "sys_config", "Landroid/content/res/Configuration;");
		}
		if (color_sheme == 1) // Prefer dark appearance
			_SET_INT_FIELD(configuration, "uiMode", /*UI_MODE_NIGHT_YES*/ 0x20);
		else if (color_sheme == 2) // Prefer light appearance
			_SET_INT_FIELD(configuration, "uiMode", /*UI_MODE_NIGHT_NO*/ 0x10);
		else // No preference
			_SET_INT_FIELD(configuration, "uiMode", /*UI_MODE_NIGHT_UNDEFINED*/ 0x00);
	}
}

static XdpSettings *xdp_settings = NULL;
#endif

JNIEXPORT void JNICALL Java_android_content_Context_native_1updateConfig(JNIEnv *env, jclass this, jobject config)
{
	GdkDisplay *display = gdk_display_get_default();
	GdkMonitor *monitor = g_list_model_get_item(gdk_display_get_monitors(display), 0);
	monitor_changed_cb(NULL, monitor, config);

#ifdef XDP_TYPE_INPUT_CAPTURE_SESSION // libportal >= 0.8
	if (!xdp_settings) {
		GError *error = NULL;
		XdpPortal *portal = xdp_portal_initable_new(&error);
		if (!portal) {
			printf("xdp_portal_initable_new failed: %s\n", error->message);
			g_error_free(error);
			return;
		}
		xdp_settings = xdp_portal_get_settings(portal);
		g_object_unref(portal);
		g_signal_connect(xdp_settings, "changed", G_CALLBACK(settings_changed_cb), NULL);
	}
	GVariant *color_sheme = xdp_settings_read_value(xdp_settings, "org.freedesktop.appearance", "color-scheme", NULL, NULL);
	if (color_sheme) {
		settings_changed_cb(xdp_settings, "org.freedesktop.appearance", "color-scheme", color_sheme, config);
		g_variant_unref(color_sheme);
	}
#endif
}

void update_config_for_window(GtkWindow *window)
{
	GdkSurface *surface = gtk_native_get_surface(GTK_NATIVE(window));
	monitor_changed_cb(surface, gdk_display_get_monitor_at_surface(gdk_display_get_default(), surface), NULL);
	g_signal_connect(surface, "enter-monitor", G_CALLBACK(monitor_changed_cb), NULL);
}

JNIEXPORT void JNICALL Java_android_content_Context_nativeOpenFile(JNIEnv *env, jclass class, jint fd)
{
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	OpenURI *openuri = open_uri_proxy_new_sync(connection, 0, "org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", NULL, NULL);
	GVariantBuilder opt_builder;
	g_variant_builder_init(&opt_builder, G_VARIANT_TYPE_VARDICT);
	GUnixFDList *fd_list = g_unix_fd_list_new_from_array(&fd, 1);
	open_uri_call_open_file_sync(openuri, "", g_variant_new("h", 0), g_variant_builder_end(&opt_builder), fd_list, NULL, NULL, NULL, NULL);
	g_object_unref(fd_list);
	g_object_unref(openuri);
	g_object_unref(connection);
}

struct desktop_launch {
	char *desktop_id;
	char *action; // a [Desktop Action], or NULL for the app itself
};

static gboolean launch_desktop_app(gpointer user_data)
{
	struct desktop_launch *launch = user_data;
	GDesktopAppInfo *app_info = g_desktop_app_info_new(launch->desktop_id);
	if (!app_info) {
		fprintf(stderr, "launch: no desktop entry %s\n", launch->desktop_id);
	} else {
		/* GTK's launch context hands the app an activation token for our window, so the compositor
		 * also raises an app that is already running; a separate gtk-launch process has no window
		 * for a token and needs its own display */
		GdkAppLaunchContext *context = gdk_display_get_app_launch_context(gdk_display_get_default());
		GError *err = NULL;
		if (launch->action) {
			g_desktop_app_info_launch_action(app_info, launch->action, G_APP_LAUNCH_CONTEXT(context));
		} else if (!g_app_info_launch(G_APP_INFO(app_info), NULL, G_APP_LAUNCH_CONTEXT(context), &err)) {
			fprintf(stderr, "launch: could not launch %s: %s\n", launch->desktop_id, err->message);
			g_error_free(err);
		}
		g_object_unref(context);
		g_object_unref(app_info);
	}
	g_free(launch->desktop_id);
	g_free(launch->action);
	g_free(launch);
	return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_content_Context_nativeLaunchDesktopApp(JNIEnv *env, jclass class, jstring desktop_id_jstr, jstring action_jstr)
{
	struct desktop_launch *launch = g_new0(struct desktop_launch, 1);
	const char *desktop_id = (*env)->GetStringUTFChars(env, desktop_id_jstr, NULL);
	launch->desktop_id = g_strdup(desktop_id);
	(*env)->ReleaseStringUTFChars(env, desktop_id_jstr, desktop_id);
	if (action_jstr) {
		const char *action = (*env)->GetStringUTFChars(env, action_jstr, NULL);
		launch->action = g_strdup(action);
		(*env)->ReleaseStringUTFChars(env, action_jstr, action);
	}
	g_main_context_invoke(NULL, launch_desktop_app, launch);
}

char *fd_get_path(int fd)
{
	char *fdlink = g_strdup_printf("/proc/self/fd/%d", fd);
	char *buf = g_malloc(PATH_MAX);

	ssize_t len = readlink(fdlink, buf, PATH_MAX - 1);
	g_free(fdlink);
	if (len < 0)
		return NULL;

	buf[len] = '\0';
	return buf;
}

extern GtkWindow *window;

/* The XDG specification does not provide anything comparable to the Android share API yet. Therefore, ContextImpl
 * shows a dialog allowing the user to copy to clipboard or send per mail using the composeEmail portal.
 */
JNIEXPORT void JNICALL Java_android_content_Context_nativeShareCopy(JNIEnv *env, jclass class, jstring text_jstr, jint fd)
{
	GdkClipboard *clipboard = gdk_display_get_clipboard(gtk_root_get_display(GTK_ROOT(window)));
	if (fd != -1) {
		char *path = fd_get_path(fd);
		GFile *file = g_file_new_for_path(path);
		gdk_clipboard_set(clipboard, G_TYPE_FILE, file);
		g_object_unref(file);
		g_free(path);
	}
	if (text_jstr) {
		const char *text = (*env)->GetStringUTFChars(env, text_jstr, NULL);
		gdk_clipboard_set_text(clipboard, text);
		(*env)->ReleaseStringUTFChars(env, text_jstr, text);
	}
}

JNIEXPORT void JNICALL Java_android_content_Context_nativeShareEmail(JNIEnv *env, jclass class, jstring text_jstr, jint fd)
{
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	Email *email = email_proxy_new_sync(connection, 0, "org.freedesktop.portal.Desktop", "/org/freedesktop/portal/desktop", NULL, NULL);
	GUnixFDList *fd_list = g_unix_fd_list_new();
	GVariantBuilder opt_builder;
	g_variant_builder_init(&opt_builder, G_VARIANT_TYPE_VARDICT);
	if (text_jstr) {
		const char *text = (*env)->GetStringUTFChars(env, text_jstr, NULL);
		g_variant_builder_add(&opt_builder, "{sv}", "body", g_variant_new_string(text));
		(*env)->ReleaseStringUTFChars(env, text_jstr, text);
	}
	if (fd != -1) {
		int fd_handle = g_unix_fd_list_append(fd_list, fd, NULL);
		GVariantBuilder fd_array_builder;
		g_variant_builder_init(&fd_array_builder, G_VARIANT_TYPE("ah"));
		g_variant_builder_add(&fd_array_builder, "h", fd_handle);
		g_variant_builder_add(&opt_builder, "{sv}", "attachment_fds", g_variant_builder_end(&fd_array_builder));
		/* The `attachment_fds` option is ignored by most email applications, so we also set the file path as subject */
		char *path = fd_get_path(fd);
		g_variant_builder_add(&opt_builder, "{sv}", "subject", g_variant_new_string(path));
		g_free(path);
	}
	email_call_compose_email_sync(email, "", g_variant_builder_end(&opt_builder), fd_list, NULL, NULL, NULL, NULL);
	g_object_unref(fd_list);
	g_object_unref(email);
	g_object_unref(connection);
}

static void on_bus_acquired(GDBusConnection *connection, const char *name, gpointer user_data)
{
	Connector1 *connector1 = user_data;
	g_dbus_interface_skeleton_export(G_DBUS_INTERFACE_SKELETON(connector1),
	                                 connection, "/org/unifiedpush/Connector", NULL);
}

static gboolean on_new_endpoint(Connector1 *connector, GDBusMethodInvocation *invocation, gpointer user_data)
{
	GVariant *parameters = g_dbus_method_invocation_get_parameters(invocation);
	const char *token;
	g_variant_get_child(parameters, 0, "s", &token);
	const char *endpoint;
	g_variant_get_child(parameters, 1, "s", &endpoint);
	connector1_complete_new_endpoint(connector, invocation);

	JNIEnv *env = get_jni_env();
	jobject intent = J_new__Intent(env);
	_SET_OBJ_FIELD(intent, "action", "Ljava/lang/String;", _JSTRING("org.unifiedpush.android.connector.NEW_ENDPOINT"));
	J__Intent__putExtraCharSequence(env, intent, _JSTRING("token"), _JSTRING(token));
	J__Intent__putExtraCharSequence(env, intent, _JSTRING("endpoint"), _JSTRING(endpoint));

	jobject context = _GET_STATIC_OBJ_FIELD(_CACHED_CLASS(Context), "this_application", "Landroid/app/Application;");
	J__Context__sendBroadcast(env, context, intent);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
	}
	return TRUE;
}

static gboolean on_message(Connector1 *connector, GDBusMethodInvocation *invocation, gpointer user_data)
{
	GVariant *parameters = g_dbus_method_invocation_get_parameters(invocation);
	const char *token;
	g_variant_get_child(parameters, 0, "s", &token);
	gsize size;
	const int8_t *message = g_variant_get_fixed_array(g_variant_get_child_value(parameters, 1), &size, 1);
	const char *message_id;
	g_variant_get_child(parameters, 2, "&s", &message_id);

	JNIEnv *env = get_jni_env();
	jobject intent = J_new__Intent(env);
	_SET_OBJ_FIELD(intent, "action", "Ljava/lang/String;", _JSTRING("org.unifiedpush.android.connector.MESSAGE"));
	J__Intent__putExtraCharSequence(env, intent, _JSTRING("token"), _JSTRING(token));
	jbyteArray bytesMessage = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, bytesMessage, 0, size, message);
	J__Intent__putExtraByteArray(env, intent, _JSTRING("bytesMessage"), bytesMessage);
	if (*message_id)
		J__Intent__putExtraCharSequence(env, intent, _JSTRING("id"), _JSTRING(message_id));

	jobject context = _GET_STATIC_OBJ_FIELD(_CACHED_CLASS(Context), "this_application", "Landroid/app/Application;");
	J__Context__sendBroadcast(env, context, intent);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
	}
	connector1_complete_message(connector, invocation);
	return TRUE;
}

static gboolean on_unregistered(Connector1 *connector, GDBusMethodInvocation *invocation, gpointer user_data)
{
	const char *token;
	g_variant_get_child(g_dbus_method_invocation_get_parameters(invocation), 0, "&s", &token);

	JNIEnv *env = get_jni_env();
	jobject intent = J_new__Intent(env);
	_SET_OBJ_FIELD(intent, "action", "Ljava/lang/String;", _JSTRING("org.unifiedpush.android.connector.UNREGISTERED"));
	J__Intent__putExtraCharSequence(env, intent, _JSTRING("token"), _JSTRING(token));
	connector1_complete_unregistered(connector, invocation);

	jobject context = _GET_STATIC_OBJ_FIELD(_CACHED_CLASS(Context), "this_application", "Landroid/app/Application;");
	J__Context__sendBroadcast(env, context, intent);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
	}
	return TRUE;
}

JNIEXPORT void JNICALL Java_android_content_Context_nativeExportUnifiedPush(JNIEnv *env, jclass this, jstring application_jstr)
{
	const char *application = (*env)->GetStringUTFChars(env, application_jstr, NULL);

	Connector1 *connector1 = connector1_skeleton_new();
	g_signal_connect(connector1, "handle-new-endpoint", G_CALLBACK(on_new_endpoint), NULL);
	g_signal_connect(connector1, "handle-message", G_CALLBACK(on_message), NULL);
	g_signal_connect(connector1, "handle-unregistered", G_CALLBACK(on_unregistered), NULL);
	g_bus_own_name(G_BUS_TYPE_SESSION, application, G_BUS_NAME_OWNER_FLAGS_NONE,
	               on_bus_acquired, NULL, NULL, connector1, NULL);
	(*env)->ReleaseStringUTFChars(env, application_jstr, application);
}

#define UNIFIEDPUSH_DISTRIBUTOR_PREFIX "org.unifiedpush.Distributor."

/* The UnifiedPush D-Bus spec: distributors own a name below org.unifiedpush.Distributor.
 * Prefer one that runs, then one that D-Bus can start. ATL_UNIFIEDPUSH_DISTRIBUTOR picks one. */
static char *unifiedpush_find_distributor(GDBusConnection *connection)
{
	const char *chosen = getenv("ATL_UNIFIEDPUSH_DISTRIBUTOR");
	if (chosen && *chosen)
		return g_strdup(chosen);
	const char *methods[] = {"ListNames", "ListActivatableNames"};
	for (int i = 0; i < 2; i++) {
		GVariant *names = g_dbus_connection_call_sync(connection, "org.freedesktop.DBus", "/org/freedesktop/DBus",
		                                              "org.freedesktop.DBus", methods[i], NULL, G_VARIANT_TYPE("(as)"),
		                                              G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
		if (!names)
			continue;
		GVariantIter *iter;
		const char *name;
		char *found = NULL;
		g_variant_get(names, "(as)", &iter);
		while (!found && g_variant_iter_next(iter, "&s", &name)) {
			if (g_str_has_prefix(name, UNIFIEDPUSH_DISTRIBUTOR_PREFIX))
				found = g_strdup(name);
		}
		g_variant_iter_free(iter);
		g_variant_unref(names);
		if (found)
			return found;
	}
	return NULL;
}

JNIEXPORT jstring JNICALL Java_android_content_Context_nativeUnifiedPushDistributor(JNIEnv *env, jclass this)
{
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!connection)
		return NULL;
	char *name = unifiedpush_find_distributor(connection);
	g_object_unref(connection);
	jstring ret = name ? _JSTRING(name) : NULL;
	g_free(name);
	return ret;
}

/* Returns NULL on success, else the reason for REGISTRATION_FAILED. */
JNIEXPORT jstring JNICALL Java_android_content_Context_nativeRegisterUnifiedPush(JNIEnv *env, jclass this, jstring token_jstr, jstring application_jstr, jstring description_jstr)
{
	if (!token_jstr || !application_jstr)
		return _JSTRING("INTERNAL_ERROR");
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	char *name = connection ? unifiedpush_find_distributor(connection) : NULL;
	if (!name) {
		if (connection)
			g_object_unref(connection);
		printf("nativeRegisterUnifiedPush: no UnifiedPush distributor on the session bus\n");
		return _JSTRING("INTERNAL_ERROR");
	}
	const char *token = (*env)->GetStringUTFChars(env, token_jstr, NULL);
	const char *application = (*env)->GetStringUTFChars(env, application_jstr, NULL);
	const char *description = description_jstr ? (*env)->GetStringUTFChars(env, description_jstr, NULL) : "";

	jstring ret = NULL;
	GError *error = NULL;
	char *result = NULL;
	char *reason = NULL;
	Distributor1 *distributor1 = distributor1_proxy_new_sync(connection, 0, name, "/org/unifiedpush/Distributor", NULL, &error);
	if (distributor1)
		distributor1_call_register_sync(distributor1, application, token, description, &result, &reason, NULL, &error);
	if (error) {
		printf("nativeRegisterUnifiedPush: %s: error=%s\n", name, error->message);
		ret = _JSTRING("NETWORK");
		g_error_free(error);
	} else if (g_strcmp0(result, "REGISTRATION_SUCCEEDED")) {
		printf("nativeRegisterUnifiedPush: %s: %s %s\n", name, result, reason ? reason : "");
		ret = _JSTRING("INTERNAL_ERROR");
	}
	g_free(result);
	g_free(reason);
	g_free(name);
	if (distributor1)
		g_object_unref(distributor1);
	g_object_unref(connection);

	(*env)->ReleaseStringUTFChars(env, token_jstr, token);
	(*env)->ReleaseStringUTFChars(env, application_jstr, application);
	if (description_jstr)
		(*env)->ReleaseStringUTFChars(env, description_jstr, description);
	return ret;
}

JNIEXPORT void JNICALL Java_android_content_Context_nativeUnregisterUnifiedPush(JNIEnv *env, jclass this, jstring token_jstr)
{
	if (!token_jstr)
		return;
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	char *name = connection ? unifiedpush_find_distributor(connection) : NULL;
	if (name) {
		const char *token = (*env)->GetStringUTFChars(env, token_jstr, NULL);
		Distributor1 *distributor1 = distributor1_proxy_new_sync(connection, 0, name, "/org/unifiedpush/Distributor", NULL, NULL);
		if (distributor1) {
			distributor1_call_unregister(distributor1, token, NULL, NULL, NULL);
			g_object_unref(distributor1);
		}
		(*env)->ReleaseStringUTFChars(env, token_jstr, token);
	}
	g_free(name);
	if (connection)
		g_object_unref(connection);
}

JNIEXPORT void JNICALL Java_android_content_Context_nativeStartExternalService(JNIEnv *env, jclass this, jobject intent)
{
	GVariant *variant = intent_serialize(env, intent);
	jstring package_jstr = _GET_OBJ_FIELD(intent, "packageName", "Ljava/lang/String;");
	const char *package = (*env)->GetStringUTFChars(env, package_jstr, NULL);
	char *object_path = g_strdup_printf("/%s", package);
	g_strdelimit(object_path, ".", '/');
	GDBusConnection *connection = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	GActionGroup *action_group = G_ACTION_GROUP(g_dbus_action_group_get(connection, package, object_path));
	g_action_group_activate_action(action_group, "startService", variant);
	g_object_unref(action_group);
	g_object_unref(connection);
	g_free(object_path);
	(*env)->ReleaseStringUTFChars(env, package_jstr, package);
}
