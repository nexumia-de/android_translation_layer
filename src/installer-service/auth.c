/* Confirming that the phone's owner is there, for BiometricPrompt and keystore keys bound to user
 * authentication (docs/keystore-auth.md). Apps in their own Flatpak cannot reach fingerprintd or
 * polkit on the system bus; this service does it for them:
 *
 * - fingerprint: one verify attempt on fingerprintd's net.reactivated.Fprint device per call; the
 *   app shows its own "touch the sensor" dialog meanwhile. Five failed attempts in a row lock the
 *   fingerprint for 30 seconds for every app, as on Android.
 * - device credential: the polkit action de.nexumia.atl.confirm-credential (auth_self, never
 *   kept), so Phosh's own system prompt asks for the owner's password and checks it through PAM.
 *
 * The answer goes back to the calling app's process, which enforces it (ATL's keystore); this
 * protects against someone using the unlocked phone, not against the app itself. */

#include <gio/gio.h>

#include <string.h>
#include <unistd.h>

#include "auth.h"

#define FPRINT_NAME       "net.reactivated.Fprint"
#define FPRINT_MANAGER    "/net/reactivated/Fprint/Manager"
#define FPRINT_DEVICE     "net.reactivated.Fprint.Device"
#define POLKIT_NAME       "org.freedesktop.PolicyKit1"
#define POLKIT_PATH       "/org/freedesktop/PolicyKit1/Authority"
#define POLKIT_IFACE      "org.freedesktop.PolicyKit1.Authority"
#define CREDENTIAL_ACTION "de.nexumia.atl.confirm-credential"

/* android.hardware.biometrics.BiometricManager.Authenticators */
#define BIOMETRIC_STRONG  0x000F
#define DEVICE_CREDENTIAL 0x8000

#define MAX_FAILED       5
#define LOCKOUT_USEC     (30 * G_USEC_PER_SEC)
#define VERIFY_TIMEOUT_S 60

static int failed_in_a_row;
static gint64 locked_out_until;

/* one running confirmation per app id */
static GHashTable *running_auth;

struct auth {
	int refs;
	char *app_id;
	GDBusMethodInvocation *invocation; // NULL once answered
	GDBusConnection *system;
	/* fingerprint */
	char *device;
	guint status_sub;
	guint timeout;
	/* polkit */
	char *cancel_id;
};

static struct auth *auth_ref(struct auth *a)
{
	a->refs++;
	return a;
}

static void auth_unref(struct auth *a)
{
	if (--a->refs)
		return;
	g_free(a->app_id);
	g_clear_object(&a->system);
	g_free(a->device);
	g_free(a->cancel_id);
	g_free(a);
	auth_release();
}

static GDBusConnection *system_bus(void)
{
	return g_bus_get_sync(G_BUS_TYPE_SYSTEM, NULL, NULL);
}

/* the fingerprint device, or NULL without fingerprintd */
static char *fprint_device(GDBusConnection *system)
{
	GVariant *reply = g_dbus_connection_call_sync(system, FPRINT_NAME, FPRINT_MANAGER, "net.reactivated.Fprint.Manager",
	                                              "GetDefaultDevice", NULL, G_VARIANT_TYPE("(o)"),
	                                              G_DBUS_CALL_FLAGS_NO_AUTO_START, 5000, NULL, NULL);
	if (!reply)
		return NULL;
	char *path;
	g_variant_get(reply, "(o)", &path);
	g_variant_unref(reply);
	return path;
}

/* the owner's enrolled fingers, sorted, or NULL if none */
static char **enrolled_fingers(GDBusConnection *system, const char *device)
{
	GVariant *reply = g_dbus_connection_call_sync(system, FPRINT_NAME, device, FPRINT_DEVICE, "ListEnrolledFingers",
	                                              g_variant_new("(s)", ""), G_VARIANT_TYPE("(as)"),
	                                              G_DBUS_CALL_FLAGS_NO_AUTO_START, 5000, NULL, NULL);
	if (!reply)
		return NULL;
	char **fingers;
	g_variant_get(reply, "(^as)", &fingers);
	g_variant_unref(reply);
	if (!fingers[0]) {
		g_strfreev(fingers);
		return NULL;
	}
	gsize n = g_strv_length(fingers);
	for (gsize i = 1; i < n; i++) // insertion sort, a handful of names
		for (gsize j = i; j > 0 && strcmp(fingers[j - 1], fingers[j]) > 0; j--) {
			char *t = fingers[j];
			fingers[j] = fingers[j - 1];
			fingers[j - 1] = t;
		}
	return fingers;
}

/* whether polkit knows the action, i.e. the policy file is installed */
static gboolean credential_action_known(GDBusConnection *system)
{
	static int known = -1;
	if (known != -1)
		return known;
	GVariant *reply = g_dbus_connection_call_sync(system, POLKIT_NAME, POLKIT_PATH, POLKIT_IFACE, "EnumerateActions",
	                                              g_variant_new("(s)", ""), NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, NULL);
	if (!reply)
		return FALSE;
	GVariantIter *iter;
	const char *id;
	gboolean found = FALSE;
	g_variant_get(reply, "(a(ssssssuuua{ss}))", &iter);
	while (!found && g_variant_iter_next(iter, "(&s&s&s&s&s&suuu@a{ss})", &id, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL))
		found = !strcmp(id, CREDENTIAL_ACTION);
	g_variant_iter_free(iter);
	g_variant_unref(reply);
	/* only a positive answer is kept: the package may be installed while the service runs */
	if (found)
		known = 1;
	return found;
}

void auth_handle_authenticators(GDBusMethodInvocation *invocation)
{
	GDBusConnection *system = system_bus();
	guint32 available = 0;
	char *enrollment = g_strdup("");
	if (system) {
		char *device = fprint_device(system);
		char **fingers = device ? enrolled_fingers(system, device) : NULL;
		if (fingers) {
			available |= BIOMETRIC_STRONG;
			/* changes when a finger is added or removed; keys with
			 * setInvalidatedByBiometricEnrollment compare it */
			char *joined = g_strjoinv("\n", fingers);
			g_free(enrollment);
			enrollment = g_compute_checksum_for_string(G_CHECKSUM_SHA256, joined, -1);
			g_free(joined);
		}
		if (credential_action_known(system))
			available |= DEVICE_CREDENTIAL;
		g_strfreev(fingers);
		g_free(device);
		g_object_unref(system);
	}
	g_dbus_method_invocation_return_value(invocation, g_variant_new("(us)", available, enrollment));
	g_free(enrollment);
}

/* -------- fingerprint -------- */

static void call_ignoring_reply(GDBusConnection *system, const char *path, const char *method)
{
	g_dbus_connection_call(system, FPRINT_NAME, path, FPRINT_DEVICE, method, NULL, NULL, G_DBUS_CALL_FLAGS_NONE, 5000,
	                       NULL, NULL, NULL);
}

/* ends the attempt: answers the app (unless already answered), stops and releases the device */
static void verify_finish(struct auth *a, const char *result)
{
	if (a->invocation) {
		g_dbus_method_invocation_return_value(a->invocation, g_variant_new("(s)", result));
		a->invocation = NULL;
		g_message("%s fingerprint: %s", a->app_id, result);
	}
	if (a->status_sub) {
		g_dbus_connection_signal_unsubscribe(a->system, a->status_sub);
		a->status_sub = 0;
	}
	g_clear_handle_id(&a->timeout, g_source_remove);
	if (a->device) {
		call_ignoring_reply(a->system, a->device, "VerifyStop");
		call_ignoring_reply(a->system, a->device, "Release");
		g_clear_pointer(&a->device, g_free);
	}
	if (g_hash_table_lookup(running_auth, a->app_id) == a)
		g_hash_table_remove(running_auth, a->app_id); // drops the table's reference
}

static void verify_status(GDBusConnection *connection, const char *sender, const char *path, const char *iface,
                          const char *signal, GVariant *params, gpointer user_data)
{
	struct auth *a = user_data;
	const char *result;
	gboolean done;
	g_variant_get(params, "(&sb)", &result, &done);
	if (!strcmp(result, "verify-match")) {
		failed_in_a_row = 0;
		verify_finish(a, "match");
	} else if (!strcmp(result, "verify-no-match")) {
		if (++failed_in_a_row >= MAX_FAILED) {
			failed_in_a_row = 0;
			locked_out_until = g_get_monotonic_time() + LOCKOUT_USEC;
			verify_finish(a, "lockout");
		} else {
			verify_finish(a, "no-match");
		}
	} else if (done) {
		/* verify-disconnected, verify-unknown-error, or a retry that ended the scan */
		verify_finish(a, g_str_has_prefix(result, "verify-retry") || !strcmp(result, "verify-swipe-too-short") ||
		                         !strcmp(result, "verify-finger-not-centered") || !strcmp(result, "verify-remove-and-retry")
		                     ? "retry"
		                     : "error");
	}
	/* not done: fingerprintd waits for the next press */
}

static gboolean verify_timeout(gpointer user_data)
{
	struct auth *a = user_data;
	a->timeout = 0;
	verify_finish(a, "canceled");
	return G_SOURCE_REMOVE;
}

void auth_handle_verify_fingerprint(GDBusMethodInvocation *invocation, const char *app_id)
{
	if (!running_auth)
		running_auth = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, (GDestroyNotify)auth_unref);
	if (g_hash_table_contains(running_auth, app_id)) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.LimitsExceeded",
		                                           "a confirmation is already running");
		return;
	}
	if (g_get_monotonic_time() < locked_out_until) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "lockout"));
		return;
	}
	GDBusConnection *system = system_bus();
	char *device = system ? fprint_device(system) : NULL;
	char **fingers = device ? enrolled_fingers(system, device) : NULL;
	if (!fingers) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "unavailable"));
		g_free(device);
		g_clear_object(&system);
		return;
	}
	g_strfreev(fingers);
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_sync(system, FPRINT_NAME, device, FPRINT_DEVICE, "Claim", g_variant_new("(s)", ""),
	                                              NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &err);
	if (!reply) {
		/* busy: another program (the lock screen) has it */
		g_message("%s fingerprint: claim failed: %s", app_id, err->message);
		g_error_free(err);
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "unavailable"));
		g_free(device);
		g_object_unref(system);
		return;
	}
	g_variant_unref(reply);

	auth_hold();
	struct auth *a = g_new0(struct auth, 1);
	a->refs = 1; // the table's
	a->app_id = g_strdup(app_id);
	a->invocation = invocation;
	a->system = system;
	a->device = device;
	g_hash_table_insert(running_auth, g_strdup(app_id), a);
	a->status_sub = g_dbus_connection_signal_subscribe(system, FPRINT_NAME, FPRINT_DEVICE, "VerifyStatus", device, NULL,
	                                                   G_DBUS_SIGNAL_FLAGS_NONE, verify_status, auth_ref(a),
	                                                   (GDestroyNotify)auth_unref);
	a->timeout = g_timeout_add_seconds(VERIFY_TIMEOUT_S, verify_timeout, a);
	reply = g_dbus_connection_call_sync(system, FPRINT_NAME, device, FPRINT_DEVICE, "VerifyStart", g_variant_new("(s)", "any"),
	                                    NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, &err);
	if (!reply) {
		g_message("%s fingerprint: VerifyStart failed: %s", app_id, err->message);
		g_error_free(err);
		verify_finish(a, "error");
		return;
	}
	g_variant_unref(reply);
}

/* -------- device credential (polkit) -------- */

static void credential_checked(GObject *source, GAsyncResult *res, gpointer user_data)
{
	struct auth *a = user_data;
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_finish(G_DBUS_CONNECTION(source), res, &err);
	const char *result = "no";
	if (reply) {
		gboolean authorized, challenge;
		g_variant_get(reply, "((bb@a{ss}))", &authorized, &challenge, NULL);
		result = authorized ? "yes" : "no";
		g_variant_unref(reply);
	} else {
		/* cancelled (polkit's Cancelled error), or the action is unknown */
		g_message("%s credential: %s", a->app_id, err->message);
		result = g_dbus_error_is_remote_error(err) && strstr(err->message, "not registered") ? "unavailable" : "no";
		g_error_free(err);
	}
	if (a->invocation) {
		g_dbus_method_invocation_return_value(a->invocation, g_variant_new("(s)", result));
		a->invocation = NULL;
		g_message("%s credential: %s", a->app_id, result);
	}
	g_clear_pointer(&a->cancel_id, g_free);
	if (g_hash_table_lookup(running_auth, a->app_id) == a)
		g_hash_table_remove(running_auth, a->app_id);
	auth_unref(a);
}

void auth_handle_confirm_credential(GDBusMethodInvocation *invocation, const char *app_id)
{
	if (!running_auth)
		running_auth = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, (GDestroyNotify)auth_unref);
	if (g_hash_table_contains(running_auth, app_id)) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.LimitsExceeded",
		                                           "a confirmation is already running");
		return;
	}
	GDBusConnection *system = system_bus();
	if (!system || !credential_action_known(system)) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "unavailable"));
		g_clear_object(&system);
		return;
	}
	auth_hold();
	struct auth *a = g_new0(struct auth, 1);
	a->refs = 1;
	a->app_id = g_strdup(app_id);
	a->invocation = invocation;
	a->system = system;
	static guint serial;
	a->cancel_id = g_strdup_printf("atl-%d-%u", getpid(), ++serial);
	g_hash_table_insert(running_auth, g_strdup(app_id), auth_ref(a));

	/* the subject is this service; polkit finds the owner's graphical session for it, so the
	 * session's agent (Phosh) shows the prompt. AllowUserInteraction = 1. */
	GVariantBuilder subject;
	g_variant_builder_init(&subject, G_VARIANT_TYPE_VARDICT);
	g_variant_builder_add(&subject, "{sv}", "name", g_variant_new_string(g_dbus_connection_get_unique_name(system)));
	GVariantBuilder details;
	g_variant_builder_init(&details, G_VARIANT_TYPE("a{ss}"));
	g_dbus_connection_call(system, POLKIT_NAME, POLKIT_PATH, POLKIT_IFACE, "CheckAuthorization",
	                       g_variant_new("((sa{sv})sa{ss}us)", "system-bus-name", &subject, CREDENTIAL_ACTION, &details, 1,
	                                     a->cancel_id),
	                       G_VARIANT_TYPE("((bba{ss}))"), G_DBUS_CALL_FLAGS_NONE, G_MAXINT, NULL, credential_checked, a);
}

/* -------- both -------- */

void auth_handle_cancel(GDBusMethodInvocation *invocation, const char *app_id)
{
	struct auth *a = running_auth ? g_hash_table_lookup(running_auth, app_id) : NULL;
	if (a && a->cancel_id)
		g_dbus_connection_call(a->system, POLKIT_NAME, POLKIT_PATH, POLKIT_IFACE, "CancelCheckAuthorization",
		                       g_variant_new("(s)", a->cancel_id), NULL, G_DBUS_CALL_FLAGS_NONE, 5000, NULL, NULL, NULL);
	else if (a)
		verify_finish(a, "canceled");
	g_dbus_method_invocation_return_value(invocation, NULL);
}

/* whether Phosh's lock screen is up (org.gnome.ScreenSaver, which Phosh serves) */
void auth_handle_device_locked(GDBusMethodInvocation *invocation)
{
	GDBusConnection *session = g_dbus_method_invocation_get_connection(invocation);
	GVariant *reply = g_dbus_connection_call_sync(session, "org.gnome.ScreenSaver", "/org/gnome/ScreenSaver",
	                                              "org.gnome.ScreenSaver", "GetActive", NULL, G_VARIANT_TYPE("(b)"),
	                                              G_DBUS_CALL_FLAGS_NO_AUTO_START, 2000, NULL, NULL);
	gboolean locked = FALSE;
	if (reply) {
		g_variant_get(reply, "(b)", &locked);
		g_variant_unref(reply);
	}
	g_dbus_method_invocation_return_value(invocation, g_variant_new("(b)", locked));
}
