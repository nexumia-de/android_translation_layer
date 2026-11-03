/* de.nexumia.atl.Permissions of the host's installer service, see ATLPermissions.java */

#include <gio/gio.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_atl_ATLPermissions.h"
#include "atl_permissions.h"

/* "yes", "no" or "ask" from the service, NULL if it cannot be reached; Request waits for the user */
static char *call_service(const char *method, const char *permission)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return NULL;
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_sync(bus, "de.nexumia.atl.Permissions", "/de/nexumia/atl/Permissions",
	                                              "de.nexumia.atl.Permissions", method, g_variant_new("(s)", permission),
	                                              G_VARIANT_TYPE("(s)"), G_DBUS_CALL_FLAGS_NONE,
	                                              strcmp(method, "Request") ? -1 : G_MAXINT, NULL, &err);
	char *answer = NULL;
	if (reply) {
		g_variant_get(reply, "(s)", &answer);
		g_variant_unref(reply);
	} else {
		fprintf(stderr, "ATLPermissions: %s %s: %s\n", method, permission, err->message);
		g_error_free(err);
	}
	g_object_unref(bus);
	return answer;
}

gboolean atl_permission_granted(const char *permission)
{
	if (!g_file_test("/.flatpak-info", G_FILE_TEST_EXISTS))
		return FALSE;
	char *answer = call_service("Check", permission);
	gboolean yes = answer && !strcmp(answer, "yes");
	g_free(answer);
	return yes;
}

JNIEXPORT jstring JNICALL Java_android_atl_ATLPermissions_nativeCall(JNIEnv *env, jclass clazz, jstring method_jstr, jstring permission_jstr)
{
	const char *method = (*env)->GetStringUTFChars(env, method_jstr, NULL);
	const char *permission = (*env)->GetStringUTFChars(env, permission_jstr, NULL);
	char *answer = call_service(method, permission);
	(*env)->ReleaseStringUTFChars(env, method_jstr, method);
	(*env)->ReleaseStringUTFChars(env, permission_jstr, permission);
	jstring result = answer ? (*env)->NewStringUTF(env, answer) : NULL;
	g_free(answer);
	return result;
}

JNIEXPORT jstring JNICALL Java_android_atl_ATLPermissions_nativeReview(JNIEnv *env, jclass clazz)
{
	GDBusConnection *bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (!bus)
		return NULL;
	GError *err = NULL;
	/* waits for the user */
	GVariant *reply = g_dbus_connection_call_sync(bus, "de.nexumia.atl.Permissions", "/de/nexumia/atl/Permissions",
	                                              "de.nexumia.atl.Permissions", "Review", NULL, G_VARIANT_TYPE("(a{ss})"),
	                                              G_DBUS_CALL_FLAGS_NONE, G_MAXINT, NULL, &err);
	g_object_unref(bus);
	if (!reply) {
		fprintf(stderr, "ATLPermissions: Review: %s\n", err->message);
		g_error_free(err);
		return NULL;
	}
	GString *answers = g_string_new("");
	GVariantIter *iter;
	const char *permission, *answer;
	g_variant_get(reply, "(a{&s&s})", &iter);
	while (g_variant_iter_next(iter, "{&s&s}", &permission, &answer))
		g_string_append_printf(answers, "%s%s=%s", answers->len ? ";" : "", permission, answer);
	g_variant_iter_free(iter);
	g_variant_unref(reply);
	jstring result = (*env)->NewStringUTF(env, answers->str);
	g_string_free(answers, TRUE);
	return result;
}
