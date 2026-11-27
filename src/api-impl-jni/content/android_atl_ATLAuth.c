/* the confirmation methods of de.nexumia.atl.Permissions, see ATLAuth.java and
 * src/installer-service/auth.c */

#include <gio/gio.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_atl_ATLAuth.h"

JNIEXPORT jstring JNICALL Java_android_atl_ATLAuth_nativeCall(JNIEnv *env, jclass clazz, jstring method_jstr)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return NULL;
	const char *method = (*env)->GetStringUTFChars(env, method_jstr, NULL);
	GError *err = NULL;
	/* VerifyFingerprint ends within a minute on its own; ConfirmCredential waits for the user */
	GVariant *reply = g_dbus_connection_call_sync(bus, "de.nexumia.atl.Permissions", "/de/nexumia/atl/Permissions",
	                                              "de.nexumia.atl.Permissions", method, NULL, NULL, G_DBUS_CALL_FLAGS_NONE,
	                                              G_MAXINT, NULL, &err);
	char *text = NULL;
	if (reply) {
		if (g_variant_is_of_type(reply, G_VARIANT_TYPE("(s)"))) {
			g_variant_get(reply, "(s)", &text);
		} else if (g_variant_is_of_type(reply, G_VARIANT_TYPE("(b)"))) {
			gboolean b;
			g_variant_get(reply, "(b)", &b);
			text = g_strdup(b ? "true" : "false");
		} else if (g_variant_is_of_type(reply, G_VARIANT_TYPE("(us)"))) {
			guint32 bits;
			const char *s;
			g_variant_get(reply, "(u&s)", &bits, &s);
			text = g_strdup_printf("%u %s", bits, s);
		} else {
			text = g_strdup("");
		}
		g_variant_unref(reply);
	} else {
		fprintf(stderr, "ATLAuth: %s: %s\n", method, err->message);
		g_error_free(err);
	}
	(*env)->ReleaseStringUTFChars(env, method_jstr, method);
	g_object_unref(bus);
	jstring result = text ? (*env)->NewStringUTF(env, text) : NULL;
	g_free(text);
	return result;
}
