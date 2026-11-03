/*
 * Watches the desktop's notifications for NotificationListenerService: a
 * monitor connection on the session bus sees every
 * org.freedesktop.Notifications.Notify call, the id the notification daemon
 * answers with, and NotificationClosed.
 */

#include <gio/gio.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_service_notification_NotificationListenerService.h"

static JavaVM *vm;
static guint32 last_notify_serial;

static JNIEnv *attached_env(void)
{
	JNIEnv *env;
	if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK)
		return env;
	JavaVMAttachArgs args = {.version = JNI_VERSION_1_6, .name = "notification monitor", .group = NULL};
	(*vm)->AttachCurrentThreadAsDaemon(vm, (void **)&env, &args);
	return env;
}

static jstring hint_string(JNIEnv *env, GVariant *hints, const char *name)
{
	const char *value = NULL;
	if (!g_variant_lookup(hints, name, "&s", &value) || !value)
		return NULL;
	return _JSTRING(value);
}

static void handle_notify(GDBusMessage *message)
{
	GVariant *body = g_dbus_message_get_body(message);
	if (!body || !g_variant_is_of_type(body, G_VARIANT_TYPE("(susssasa{sv}i)")))
		return;
	const char *app_name, *icon, *summary, *text;
	guint32 replaces_id;
	GVariant *actions, *hints;
	gint32 timeout;
	g_variant_get(body, "(&su&s&s&s@as@a{sv}i)", &app_name, &replaces_id, &icon, &summary, &text, &actions, &hints, &timeout);
	guint8 urgency = 1;
	g_variant_lookup(hints, "urgency", "y", &urgency);
	gboolean resident = FALSE;
	g_variant_lookup(hints, "resident", "b", &resident);
	last_notify_serial = g_dbus_message_get_serial(message);

	JNIEnv *env = attached_env();
	jstring japp_name = _JSTRING(app_name);
	jstring jdesktop_entry = hint_string(env, hints, "desktop-entry");
	jstring jsummary = _JSTRING(summary);
	jstring jtext = _JSTRING(text);
	jstring jcategory = hint_string(env, hints, "category");
	/* this process's own notifications (GLib's fallback backend sends them on our session bus connection) */
	GDBusConnection *own_bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	gboolean own = own_bus && !g_strcmp0(g_dbus_message_get_sender(message), g_dbus_connection_get_unique_name(own_bus));
	if (own_bus)
		g_object_unref(own_bus);
	J__NotificationListenerService__onNotify(env, japp_name, jdesktop_entry, replaces_id, jsummary, jtext, jcategory, urgency, resident, own);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	(*env)->DeleteLocalRef(env, japp_name);
	(*env)->DeleteLocalRef(env, jsummary);
	(*env)->DeleteLocalRef(env, jtext);
	if (jdesktop_entry)
		(*env)->DeleteLocalRef(env, jdesktop_entry);
	if (jcategory)
		(*env)->DeleteLocalRef(env, jcategory);
	g_variant_unref(actions);
	g_variant_unref(hints);
}

static void handle_id(GDBusMessage *message, gboolean closed)
{
	GVariant *body = g_dbus_message_get_body(message);
	/* NotificationClosed is (uu), CloseNotification and Notify's reply are (u) */
	if (!body || !(g_variant_is_of_type(body, G_VARIANT_TYPE("(u)")) || (closed && g_variant_is_of_type(body, G_VARIANT_TYPE("(uu)")))))
		return;
	guint32 id;
	g_variant_get_child(body, 0, "u", &id);
	JNIEnv *env = attached_env();
	if (closed)
		J__NotificationListenerService__onClosed(env, id);
	else
		J__NotificationListenerService__onNotifyReply(env, id);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
}

/* runs on GDBus's worker thread; a monitor only listens, so every message is dropped afterwards */
static GDBusMessage *filter(GDBusConnection *connection, GDBusMessage *message, gboolean incoming, gpointer user_data)
{
	if (!incoming)
		return message;
	const char *interface = g_dbus_message_get_interface(message);
	const char *member = g_dbus_message_get_member(message);
	switch (g_dbus_message_get_message_type(message)) {
		case G_DBUS_MESSAGE_TYPE_METHOD_CALL:
			if (!g_strcmp0(interface, "org.freedesktop.Notifications") && !g_strcmp0(member, "Notify"))
				handle_notify(message);
			else if (!g_strcmp0(interface, "org.freedesktop.Notifications") && !g_strcmp0(member, "CloseNotification"))
				handle_id(message, TRUE);
			break;
		case G_DBUS_MESSAGE_TYPE_METHOD_RETURN:
			if (last_notify_serial && g_dbus_message_get_reply_serial(message) == last_notify_serial) {
				last_notify_serial = 0;
				handle_id(message, FALSE);
			}
			break;
		case G_DBUS_MESSAGE_TYPE_SIGNAL:
			if (!g_strcmp0(interface, "org.freedesktop.Notifications") && !g_strcmp0(member, "NotificationClosed"))
				handle_id(message, TRUE);
			break;
		default:
			break;
	}
	g_object_unref(message);
	return NULL;
}

JNIEXPORT jboolean JNICALL Java_android_service_notification_NotificationListenerService_nativeStartMonitor(JNIEnv *env, jclass class)
{
	GError *error = NULL;
	(*env)->GetJavaVM(env, &vm);
	char *address = g_dbus_address_get_for_bus_sync(G_BUS_TYPE_SESSION, NULL, &error);
	if (!address) {
		fprintf(stderr, "notification monitor: no session bus: %s\n", error->message);
		g_error_free(error);
		return JNI_FALSE;
	}
	GDBusConnection *connection = g_dbus_connection_new_for_address_sync(
	    address, G_DBUS_CONNECTION_FLAGS_AUTHENTICATION_CLIENT | G_DBUS_CONNECTION_FLAGS_MESSAGE_BUS_CONNECTION, NULL, NULL, &error);
	g_free(address);
	if (!connection) {
		fprintf(stderr, "notification monitor: %s\n", error->message);
		g_error_free(error);
		return JNI_FALSE;
	}
	const char *rules[] = {
		"type='method_call',interface='org.freedesktop.Notifications',member='Notify'",
		"type='method_call',interface='org.freedesktop.Notifications',member='CloseNotification'",
		"type='signal',interface='org.freedesktop.Notifications',member='NotificationClosed'",
		"type='method_return',sender='org.freedesktop.Notifications'",
		NULL,
	};
	GVariant *reply = g_dbus_connection_call_sync(connection, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus.Monitoring",
	                                              "BecomeMonitor", g_variant_new("(^asu)", rules, 0), NULL, G_DBUS_CALL_FLAGS_NONE, -1, NULL, &error);
	if (!reply) {
		fprintf(stderr, "notification monitor: BecomeMonitor failed: %s\n", error->message);
		g_error_free(error);
		g_object_unref(connection);
		return JNI_FALSE;
	}
	g_variant_unref(reply);
	g_dbus_connection_add_filter(connection, filter, NULL, NULL);
	return JNI_TRUE;
}
