#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_os_PowerManager.h"

/*
 * Whether the screen is on: Phosh's and GNOME's display configuration
 * reports PowerSaveMode 0 while the screen is on; the screen saver
 * (locked or blanked) is the fallback. Without either, say on.
 */
JNIEXPORT jboolean JNICALL Java_android_os_PowerManager_nativeIsScreenOn(JNIEnv *env, jclass class)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return JNI_TRUE;
	jboolean result = JNI_TRUE;
	GVariant *reply = g_dbus_connection_call_sync(bus, "org.gnome.Mutter.DisplayConfig", "/org/gnome/Mutter/DisplayConfig",
	                                              "org.freedesktop.DBus.Properties", "Get",
	                                              g_variant_new("(ss)", "org.gnome.Mutter.DisplayConfig", "PowerSaveMode"),
	                                              G_VARIANT_TYPE("(v)"), G_DBUS_CALL_FLAGS_NO_AUTO_START, 500, NULL, NULL);
	if (reply) {
		GVariant *value;
		g_variant_get(reply, "(v)", &value);
		if (g_variant_is_of_type(value, G_VARIANT_TYPE_INT32))
			result = g_variant_get_int32(value) == 0;
		g_variant_unref(value);
		g_variant_unref(reply);
	} else {
		reply = g_dbus_connection_call_sync(bus, "org.gnome.ScreenSaver", "/org/gnome/ScreenSaver", "org.gnome.ScreenSaver", "GetActive",
		                                    NULL, G_VARIANT_TYPE("(b)"), G_DBUS_CALL_FLAGS_NO_AUTO_START, 500, NULL, NULL);
		if (reply) {
			gboolean active;
			g_variant_get(reply, "(b)", &active);
			result = !active;
			g_variant_unref(reply);
		}
	}
	g_object_unref(bus);
	return result;
}

/* one inhibit cookie per flags value (suspend, or suspend + idle) */
static guint inhibit_cookies[16];

struct inhibit_request {
	int flags;
	gboolean inhibit;
	char *reason;
};

static gboolean inhibit_on_main(gpointer data)
{
	struct inhibit_request *req = data;
	GtkApplication *application = GTK_APPLICATION(g_application_get_default());
	guint *cookie = &inhibit_cookies[req->flags & 15];
	if (req->inhibit && !*cookie) {
		*cookie = gtk_application_inhibit(application, NULL, req->flags, req->reason);
		if (!*cookie)
			g_warning("PowerManager: could not inhibit (flags %d): no session manager or Inhibit portal", req->flags);
	} else if (!req->inhibit && *cookie) {
		gtk_application_uninhibit(application, *cookie);
		*cookie = 0;
	}
	g_free(req->reason);
	g_free(req);
	return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_os_PowerManager_nativeInhibit(JNIEnv *env, jclass class, jint flags, jboolean inhibit, jstring reason)
{
	struct inhibit_request *req = g_new(struct inhibit_request, 1);
	const char *reason_utf = (*env)->GetStringUTFChars(env, reason, NULL);
	req->flags = flags;
	req->inhibit = inhibit;
	req->reason = g_strdup(reason_utf);
	(*env)->ReleaseStringUTFChars(env, reason, reason_utf);
	g_main_context_invoke(NULL, inhibit_on_main, req);
}
