#include <gio/gio.h>

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
