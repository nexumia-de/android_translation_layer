/* de.nexumia.atl.Permissions of the host's installer service, see ATLPermissions.java */

#include <gio/gio.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_atl_ATLPermissions.h"

JNIEXPORT jstring JNICALL Java_android_atl_ATLPermissions_nativeCall(JNIEnv *env, jclass clazz, jstring method_jstr, jstring permission_jstr)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return NULL;
	const char *method = (*env)->GetStringUTFChars(env, method_jstr, NULL);
	const char *permission = (*env)->GetStringUTFChars(env, permission_jstr, NULL);
	GError *err = NULL;
	/* Request waits for the user */
	GVariant *reply = g_dbus_connection_call_sync(bus, "de.nexumia.atl.Permissions", "/de/nexumia/atl/Permissions",
	                                              "de.nexumia.atl.Permissions", method, g_variant_new("(s)", permission),
	                                              G_VARIANT_TYPE("(s)"), G_DBUS_CALL_FLAGS_NONE,
	                                              strcmp(method, "Request") ? -1 : G_MAXINT, NULL, &err);
	jstring answer = NULL;
	if (reply) {
		const char *text;
		g_variant_get(reply, "(&s)", &text);
		answer = (*env)->NewStringUTF(env, text);
		g_variant_unref(reply);
	} else {
		fprintf(stderr, "ATLPermissions: %s %s: %s\n", method, permission, err->message);
		g_error_free(err);
	}
	(*env)->ReleaseStringUTFChars(env, method_jstr, method);
	(*env)->ReleaseStringUTFChars(env, permission_jstr, permission);
	g_object_unref(bus);
	return answer;
}
