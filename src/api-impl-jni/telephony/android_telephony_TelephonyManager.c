#include <gio/gio.h>

#include "../defines.h"
#include "../generated_headers/android_telephony_TelephonyManager.h"

/* ModemManager lists one object per modem it manages; phones like the PinePhone or the
 * Librem 5 have one, laptops and desktops usually none */
JNIEXPORT jboolean JNICALL Java_android_telephony_TelephonyManager_nativeHasModem(JNIEnv *env, jclass clazz)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, NULL);
	if (!bus)
		return JNI_FALSE;
	GVariant *reply = g_dbus_connection_call_sync(bus, "org.freedesktop.ModemManager1", "/org/freedesktop/ModemManager1",
	                                              "org.freedesktop.DBus.ObjectManager", "GetManagedObjects", NULL,
	                                              G_VARIANT_TYPE("(a{oa{sa{sv}}})"), G_DBUS_CALL_FLAGS_NO_AUTO_START, 2000, NULL, NULL);
	g_object_unref(bus);
	if (!reply)
		return JNI_FALSE;
	GVariant *objects = g_variant_get_child_value(reply, 0);
	jboolean found = g_variant_n_children(objects) > 0;
	g_variant_unref(objects);
	g_variant_unref(reply);
	return found;
}
