/* Runtime permissions without a portal (docs/hardening-roadmap.md, phase 4): every app Flatpak may
 * call de.nexumia.atl.Permissions, a process of its own: the D-Bus proxy lets an app reach every name
 * of a connection through its unique name, so sharing one with the installer service would open
 * that service to all apps. The service asks the user with the desktop's own access dialog
 * (org.freedesktop.impl.portal.Access, on Phosh the shell's system dialog) and keeps the answers in
 * the xdg permission store, table "de.nexumia.atl", id = permission, app = Flatpak app id, value
 * ["yes"] or ["no", <times denied>]. The caller is identified by its sandbox, never by a
 * parameter, and gets only permissions its APK requests (plus ATL's own, atl.google-services). */

#include <gio/gdesktopappinfo.h>
#include <gio/gio.h>

#include <stdlib.h>
#include <string.h>

#include "caller.h"

#define BUS_NAME         "de.nexumia.atl.Permissions"
#define PERMISSIONS_PATH "/de/nexumia/atl/Permissions"
#define IDLE_TIMEOUT     120
#define APP_PREFIX       "de.nexumia.atl."
#define STORE_NAME       "org.freedesktop.impl.portal.PermissionStore"
#define STORE_PATH       "/org/freedesktop/impl/portal/PermissionStore"
#define STORE_TABLE      "de.nexumia.atl"
#define GOOGLE_SERVICES  "atl.google-services"
/* like Android 11+: after the second "no" the app is not allowed to ask again */
#define MAX_DENIALS 2

#define PERMISSIONS_VERSION 3

struct question {
	const char *permission;
	const char *title; // %s: the app's name
	const char *body;
	const char *grant;
};

/* the permissions this service decides; the rest are portals, Flatpak permissions or normal ones */
static const struct question questions[] = {
	{"android.permission.READ_CONTACTS", "Allow %s to read your contacts?", "ATL has no address book yet, so the app sees none either way.", "Allow"},
	{"android.permission.WRITE_CONTACTS", "Allow %s to change your contacts?", "ATL has no address book yet, so the app sees none either way.", "Allow"},
	{"android.permission.READ_CALENDAR", "Allow %s to read your calendar?", "ATL has no calendar yet, so the app sees none either way.", "Allow"},
	{"android.permission.WRITE_CALENDAR", "Allow %s to change your calendar?", "ATL has no calendar yet, so the app sees none either way.", "Allow"},
	{"android.permission.READ_SMS", "Allow %s to read your text messages?", "ATL has no messages yet, so the app sees none either way.", "Allow"},
	{"android.permission.SEND_SMS", "Allow %s to send text messages?", "ATL cannot send messages yet.", "Allow"},
	{"android.permission.READ_CALL_LOG", "Allow %s to read your call log?", "ATL has no call log yet, so the app sees none either way.", "Allow"},
	{"android.permission.READ_PHONE_STATE", "Allow %s to see your phone's state?", "Phone number, network and calls.", "Allow"},
	{"android.permission.ACCESS_BACKGROUND_LOCATION", "Allow %s to use your location in the background?", "Also while none of its windows is shown.", "Allow"},
	{"android.permission.RECORD_AUDIO", "Allow %s to record audio?", "The app can use the microphone.", "Allow"},
	{"android.permission.BLUETOOTH_CONNECT", "Allow %s to connect to Bluetooth devices?", "Paired devices like watches and headphones.", "Allow"},
	{"android.permission.BLUETOOTH_SCAN", "Allow %s to find Bluetooth devices nearby?", "Scanning can also reveal where you are.", "Allow"},
	{"android.permission.BODY_SENSORS", "Allow %s to use body sensors?", "Heart rate and similar sensors.", "Allow"},
	{"android.permission.ACTIVITY_RECOGNITION", "Allow %s to recognize your physical activity?", "Steps, walking, cycling, driving.", "Allow"},
	{GOOGLE_SERVICES, "%s wants to use Google services", "This connects to Google servers. Google can see that this app is used on this phone and when.", "Accept"},
};

static GMainLoop *loop;
static guint idle_source;
static int running;
/* open questions by "<app id>\n<permission>": the invocations waiting for that one answer */
static GHashTable *pending;

static gboolean idle_quit(gpointer data)
{
	g_main_loop_quit(loop);
	return G_SOURCE_REMOVE;
}

static void hold(void)
{
	running++;
	g_clear_handle_id(&idle_source, g_source_remove);
}

static void release(void)
{
	if (--running == 0)
		idle_source = g_timeout_add_seconds(IDLE_TIMEOUT, idle_quit, NULL);
}

static const struct question *find_question(const char *permission)
{
	for (gsize i = 0; i < G_N_ELEMENTS(questions); i++)
		if (!strcmp(questions[i].permission, permission))
			return &questions[i];
	return NULL;
}

/* the caller's Flatpak app id, or NULL outside an ATL app's sandbox */
static char *caller_app_id(GDBusMethodInvocation *invocation)
{
	GKeyFile *info = NULL;
	if (!caller_identify(invocation, &info, NULL) || !info)
		return NULL;
	char *app_id = g_key_file_get_string(info, "Application", "name", NULL);
	g_key_file_free(info);
	if (app_id && !g_str_has_prefix(app_id, APP_PREFIX))
		g_clear_pointer(&app_id, g_free);
	return app_id;
}

static char *flatpak_user_dir(void)
{
	const char *dir = g_getenv("FLATPAK_USER_DIR");
	return dir ? g_strdup(dir) : g_build_filename(g_get_user_data_dir(), "flatpak", NULL);
}

/* the APK's <uses-permission> list, written by make-app-flatpak into the app's Flatpak */
static gboolean app_requests(const char *app_id, const char *permission)
{
	if (!strcmp(permission, GOOGLE_SERVICES))
		return TRUE;
	char *flatpak = flatpak_user_dir();
	char *path = g_build_filename(flatpak, "app", app_id, "current", "active", "files", "share", "atl", "permissions", NULL);
	char *contents = NULL;
	gboolean found = FALSE;
	if (g_file_get_contents(path, &contents, NULL, NULL)) {
		char **lines = g_strsplit(contents, "\n", -1);
		found = g_strv_contains((const char *const *)lines, permission);
		g_strfreev(lines);
	}
	g_free(contents);
	g_free(path);
	g_free(flatpak);
	return found;
}

static char *app_name(const char *app_id)
{
	char *desktop_id = g_strconcat(app_id, ".desktop", NULL);
	GDesktopAppInfo *info = g_desktop_app_info_new(desktop_id);
	char *name = g_strdup(info ? g_app_info_get_name(G_APP_INFO(info)) : app_id + strlen(APP_PREFIX));
	g_clear_object(&info);
	g_free(desktop_id);
	return name;
}

/* the stored answer: 1 yes, 0 no, -1 none; *denials how often the user said no */
static int stored_answer(GDBusConnection *bus, const char *app_id, const char *permission, int *denials)
{
	*denials = 0;
	GVariant *reply = g_dbus_connection_call_sync(bus, STORE_NAME, STORE_PATH, STORE_NAME, "GetPermission",
	                                              g_variant_new("(sss)", STORE_TABLE, permission, app_id),
	                                              G_VARIANT_TYPE("(as)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	if (!reply) // no entry for this permission yet
		return -1;
	const char **value;
	g_variant_get(reply, "(^a&s)", &value);
	int answer = -1;
	if (value[0] && !strcmp(value[0], "yes")) {
		answer = 1;
	} else if (value[0] && !strcmp(value[0], "no")) {
		answer = 0;
		*denials = value[1] ? MAX(1, atoi(value[1])) : 1;
	}
	g_free(value);
	g_variant_unref(reply);
	return answer;
}

static void store_answer(GDBusConnection *bus, const char *app_id, const char *permission, gboolean yes, int denials)
{
	char *count = g_strdup_printf("%d", denials);
	const char *value[] = {yes ? "yes" : "no", yes ? NULL : count, NULL};
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_sync(bus, STORE_NAME, STORE_PATH, STORE_NAME, "SetPermission",
	                                              g_variant_new("(sbss^as)", STORE_TABLE, TRUE, permission, app_id, value),
	                                              NULL, G_DBUS_CALL_FLAGS_NONE, -1, NULL, &err);
	if (!reply) {
		g_warning("storing %s for %s: %s", permission, app_id, err->message);
		g_error_free(err);
	} else {
		g_variant_unref(reply);
	}
	g_free(count);
}

/* RECORD_AUDIO is also enforced by the app's sockets (flatpak/microphone/ and make-app-flatpak): by
 * default it has only a pulse server that refuses recording; "yes" gives it the normal pulse socket as
 * a Flatpak user override. Applies from the app's next start; ATL's own check covers the time until
 * then. */
static void apply_microphone_answer(const char *app_id, gboolean yes)
{
	char *norecord = g_strdup_printf("--env=PULSE_SERVER=unix:%s/atl-pulse-norecord/native", g_get_user_runtime_dir());
	const char *allow[] = {"flatpak", "override", "--user", "--socket=pulseaudio", "--unset-env=PULSE_SERVER",
	                       "--nofilesystem=xdg-run/atl-pulse-norecord", app_id, NULL};
	const char *deny[] = {"flatpak", "override", "--user", "--nosocket=pulseaudio", norecord,
	                      "--filesystem=xdg-run/atl-pulse-norecord:ro", app_id, NULL};
	int wait_status;
	GError *err = NULL;
	if (!g_spawn_sync(NULL, (char **)(yes ? allow : deny), NULL, G_SPAWN_SEARCH_PATH | G_SPAWN_STDOUT_TO_DEV_NULL, NULL, NULL,
	                  NULL, NULL, &wait_status, &err) ||
	    !g_spawn_check_wait_status(wait_status, &err)) {
		g_warning("microphone override for %s: %s", app_id, err->message);
		g_error_free(err);
	}
	g_free(norecord);
}

/* Background location (Android 11's rule, agreed with the location portal work, #76): only for an app
 * that already has location in the foreground, i.e. a location portal grant (xdg-desktop-portal's
 * table "location", value [accuracy, last use]); otherwise "no" without a dialog. */
static gboolean foreground_location_granted(GDBusConnection *bus, const char *app_id)
{
	GVariant *reply = g_dbus_connection_call_sync(bus, STORE_NAME, STORE_PATH, STORE_NAME, "GetPermission",
	                                              g_variant_new("(sss)", "location", "location", app_id),
	                                              G_VARIANT_TYPE("(as)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	if (!reply)
		return FALSE;
	const char **value;
	g_variant_get(reply, "(^a&s)", &value);
	gboolean granted = value[0] && strcmp(value[0], "NONE");
	g_free(value);
	g_variant_unref(reply);
	return granted;
}

static const char *answer_text(int answer)
{
	return answer == 1 ? "yes" : answer == 0 ? "no" : "ask";
}

struct request {
	char *key; // in pending
	GDBusConnection *bus;
	char *app_id;
	char *permission;
	GVariant *dialog_args;
	int backend; // index into access_backends
};

/* the desktop's access dialog: Phosh's shell, else GTK's (headless tests) */
static const char *const access_backends[] = {"mobi.phosh.Shell.Portal", "org.freedesktop.impl.portal.desktop.gtk"};

/* answers everyone waiting for this question */
static void request_answer(struct request *r, const char *answer)
{
	GPtrArray *waiting = g_hash_table_lookup(pending, r->key);
	for (guint i = 0; waiting && i < waiting->len; i++)
		g_dbus_method_invocation_return_value(g_ptr_array_index(waiting, i), g_variant_new("(s)", answer));
	g_hash_table_remove(pending, r->key);
}

static void request_free(struct request *r)
{
	g_free(r->key);
	g_object_unref(r->bus);
	g_free(r->app_id);
	g_free(r->permission);
	g_clear_pointer(&r->dialog_args, g_variant_unref);
	g_free(r->key);
	g_free(r);
	release();
}

static void ask_backend(struct request *r);

static void dialog_answered(GObject *source, GAsyncResult *res, gpointer user_data)
{
	struct request *r = user_data;
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_finish(G_DBUS_CONNECTION(source), res, &err);
	if (!reply) {
		gboolean missing = g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_SERVICE_UNKNOWN) ||
		                   g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_NAME_HAS_NO_OWNER) ||
		                   g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_UNKNOWN_METHOD);
		if (missing && ++r->backend < (int)G_N_ELEMENTS(access_backends)) {
			g_error_free(err);
			ask_backend(r);
			return;
		}
		/* no dialog: answer "no" without storing it, the app may ask again */
		g_warning("asking for %s: %s", r->permission, err->message);
		g_error_free(err);
		request_answer(r, "no");
		request_free(r);
		return;
	}
	guint32 response;
	g_variant_get(reply, "(u@a{sv})", &response, NULL);
	g_variant_unref(reply);
	gboolean yes = response == 0;
	/* 1 is "Deny"; anything else (2: dismissed) is no answer: "no" for now, nothing stored, so it
	 * does not count toward the two denials after which the app may not ask again */
	if (response <= 1) {
		/* the count as stored now, not as it was when the dialog opened */
		int denials;
		stored_answer(r->bus, r->app_id, r->permission, &denials);
		store_answer(r->bus, r->app_id, r->permission, yes, yes ? 0 : denials + 1);
		if (!strcmp(r->permission, "android.permission.RECORD_AUDIO"))
			apply_microphone_answer(r->app_id, yes);
	}
	g_message("%s %s: %s (response %u)", r->app_id, r->permission, yes ? "yes" : "no", response);
	request_answer(r, yes ? "yes" : "no");
	request_free(r);
}

static void ask_backend(struct request *r)
{
	g_dbus_connection_call(r->bus, access_backends[r->backend], "/org/freedesktop/portal/desktop",
	                       "org.freedesktop.impl.portal.Access", "AccessDialog", g_variant_ref(r->dialog_args),
	                       G_VARIANT_TYPE("(ua{sv})"), G_DBUS_CALL_FLAGS_NONE, G_MAXINT, NULL, dialog_answered, r);
}

static void handle_review(GDBusMethodInvocation *invocation, GVariant *params);

static void handle(GDBusMethodInvocation *invocation, const char *method, GVariant *params)
{
	if (!strcmp(method, "Review")) {
		handle_review(invocation, params);
		return;
	}
	const char *permission;
	g_variant_get(params, "(&s)", &permission);
	char *app_id = caller_app_id(invocation);
	if (!app_id) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.AccessDenied",
		                                           "only apps in their own ATL Flatpak");
		return;
	}
	const struct question *q = find_question(permission);
	if (!q) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.InvalidArgs",
		                                           "not a permission this service decides");
		g_free(app_id);
		return;
	}
	GDBusConnection *bus = g_dbus_method_invocation_get_connection(invocation);
	/* like Android: what the manifest does not request is denied without asking */
	/* background location only while the location portal grants foreground location: for Check and
	 * Request, also after an earlier "yes" */
	if (!app_requests(app_id, permission) ||
	    (!strcmp(permission, "android.permission.ACCESS_BACKGROUND_LOCATION") && !foreground_location_granted(bus, app_id))) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", "no"));
		g_free(app_id);
		return;
	}
	int denials;
	int answer = stored_answer(bus, app_id, permission, &denials);
	if (!strcmp(method, "Check") || answer == 1 || (answer == 0 && denials >= MAX_DENIALS)) {
		g_dbus_method_invocation_return_value(invocation, g_variant_new("(s)", answer_text(answer)));
		g_free(app_id);
		return;
	}

	/* one dialog per app and permission: a second Request waits for the first one's answer */
	char *key = g_strdup_printf("%s\n%s", app_id, permission);
	GPtrArray *waiting = g_hash_table_lookup(pending, key);
	if (waiting) {
		g_ptr_array_add(waiting, invocation);
		g_free(key);
		g_free(app_id);
		return;
	}
	waiting = g_ptr_array_new();
	g_ptr_array_add(waiting, invocation);
	g_hash_table_insert(pending, g_strdup(key), waiting);

	static guint serial;
	char *handle_path = g_strdup_printf(PERMISSIONS_PATH "/request/%u", ++serial);
	char *name = app_name(app_id);
	char *title = g_strdup_printf(q->title, name);
	GVariantBuilder options;
	g_variant_builder_init(&options, G_VARIANT_TYPE_VARDICT);
	g_variant_builder_add(&options, "{sv}", "deny_label", g_variant_new_string("Deny"));
	g_variant_builder_add(&options, "{sv}", "grant_label", g_variant_new_string(q->grant));
	g_variant_builder_add(&options, "{sv}", "icon", g_variant_new_string(strcmp(permission, GOOGLE_SERVICES) ? app_id : "dialog-warning-symbolic"));

	struct request *r = g_new0(struct request, 1);
	r->key = key;
	r->bus = g_object_ref(bus);
	r->app_id = app_id;
	r->permission = g_strdup(permission);
	r->dialog_args = g_variant_ref_sink(g_variant_new("(osssssa{sv})", handle_path, app_id, "", title, "", q->body, &options));
	hold();
	ask_backend(r);
	g_free(title);
	g_free(name);
	g_free(handle_path);
}

static const char *atl_binary(void)
{
	const char *path = g_getenv("ATL_BINARY");
	return path ? path : INSTALL_BINDIR "/android-translation-layer";
}

/* App info windows this service started, by app id; a sandboxed app must not be able to open more
 * than one, or open them as fast as it can call */
static GHashTable *app_info_open;
static GHashTable *app_info_last_call;
#define APP_INFO_MIN_INTERVAL (2 * G_USEC_PER_SEC)

static void app_info_closed(GPid pid, gint status, gpointer user_data)
{
	g_hash_table_remove(app_info_open, user_data);
	g_spawn_close_pid(pid);
	release();
}

/* the launcher's "App info" action: the caller's own App info, on the host (--app-info) */
static void handle_open_app_info(GDBusMethodInvocation *invocation)
{
	char *app_id = caller_app_id(invocation);
	if (!app_id) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.AccessDenied",
		                                           "only apps in their own ATL Flatpak");
		return;
	}
	if (!app_info_open) {
		app_info_open = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, NULL);
		app_info_last_call = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, NULL);
	}
	gint64 now = g_get_monotonic_time();
	gint64 last = GPOINTER_TO_SIZE(g_hash_table_lookup(app_info_last_call, app_id));
	gboolean too_soon = last && now - last < APP_INFO_MIN_INTERVAL;
	g_hash_table_insert(app_info_last_call, g_strdup(app_id), GSIZE_TO_POINTER((gsize)now));
	if (too_soon || g_hash_table_contains(app_info_open, app_id)) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.LimitsExceeded",
		                                           "App info is already open or was just opened");
		g_free(app_id);
		return;
	}
	const char *argv[] = {atl_binary(), "--app-info", app_id + strlen(APP_PREFIX), NULL};
	GError *err = NULL;
	GPid pid;
	if (!g_spawn_async(NULL, (char **)argv, NULL, G_SPAWN_DO_NOT_REAP_CHILD, NULL, NULL, &pid, &err)) {
		g_dbus_method_invocation_return_gerror(invocation, err);
		g_error_free(err);
		g_free(app_id);
		return;
	}
	char *key = g_strdup(app_id);
	g_hash_table_add(app_info_open, key);
	hold(); // until the window closes
	g_child_watch_add(pid, app_info_closed, key);
	g_dbus_method_invocation_return_value(invocation, NULL);
	g_free(app_id);
}

static void method_call(GDBusConnection *connection, const char *sender, const char *object_path, const char *interface_name,
                        const char *method_name, GVariant *params, GDBusMethodInvocation *invocation, gpointer user_data)
{
	hold();
	if (!strcmp(method_name, "OpenAppInfo"))
		handle_open_app_info(invocation);
	else
		handle(invocation, method_name, params);
	release();
}

static GVariant *get_property(GDBusConnection *connection, const char *sender, const char *object_path,
                              const char *interface_name, const char *property_name, GError **error, gpointer user_data)
{
	return g_variant_new_uint32(PERMISSIONS_VERSION);
}

static const GDBusInterfaceVTable vtable = {method_call, get_property, NULL, {0}};

static void bus_acquired(GDBusConnection *connection, const char *name, gpointer user_data)
{
	GBytes *xml = g_resources_lookup_data("/de/nexumia/atl/Installer/de.nexumia.atl.Permissions.xml", 0, NULL);
	GDBusNodeInfo *node = g_dbus_node_info_new_for_xml(g_bytes_get_data(xml, NULL), NULL);
	g_dbus_connection_register_object(connection, PERMISSIONS_PATH, node->interfaces[0], &vtable, NULL, NULL, NULL);
	g_dbus_node_info_unref(node);
	g_bytes_unref(xml);
}

static void name_lost(GDBusConnection *connection, const char *name, gpointer user_data)
{
	g_main_loop_quit(loop);
}

int main(int argc, char **argv)
{
	loop = g_main_loop_new(NULL, FALSE);
	pending = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, (GDestroyNotify)g_ptr_array_unref);
	g_bus_own_name(G_BUS_TYPE_SESSION, BUS_NAME, G_BUS_NAME_OWNER_FLAGS_NONE, bus_acquired, NULL, name_lost, NULL, NULL);
	idle_source = g_timeout_add_seconds(IDLE_TIMEOUT, idle_quit, NULL);
	g_main_loop_run(loop);
	return 0;
}

/* -------- legacy permission review (owner, 2026-11-03) --------
 * Apps made for Android before 6 (target SDK < 23, or run with SDK_INT < 23) never ask at runtime:
 * androidx answers from checkSelfPermission. As on Android 10+ and GrapheneOS, their first start
 * shows one review of the dangerous permissions this service decides, each off by default (an
 * earlier "yes" ticked, an earlier final "no" left out), stored like the other answers, once (id
 * atl.legacy-review). Location and camera are not part of it: their portals ask at first use. Only
 * for apps whose Flatpak says they are made for Android before 6. */

#define REVIEW_DONE "atl.legacy-review"

static const struct {
	const char *permission;
	const char *label;
} reviewed[] = {
	{"android.permission.RECORD_AUDIO", "Microphone"},
	{"android.permission.READ_CONTACTS", "Contacts"},
	{"android.permission.WRITE_CONTACTS", "Change contacts"},
	{"android.permission.READ_CALENDAR", "Calendar"},
	{"android.permission.WRITE_CALENDAR", "Change calendar"},
	{"android.permission.READ_SMS", "Text messages"},
	{"android.permission.SEND_SMS", "Send text messages"},
	{"android.permission.READ_CALL_LOG", "Call log"},
	{"android.permission.READ_PHONE_STATE", "Phone state"},
	{"android.permission.BODY_SENSORS", "Body sensors"},
};

/* target SDK (make-app-flatpak's target-sdk) or the --sdk-int the app runs with below 23 */
static gboolean app_is_legacy(const char *app_id)
{
	char *flatpak = flatpak_user_dir();
	char *files = g_build_filename(flatpak, "app", app_id, "current", "active", "files", NULL);
	char *target_path = g_build_filename(files, "share", "atl", "target-sdk", NULL);
	char *launcher_path = g_build_filename(files, "bin", "atl-app", NULL);
	char *contents = NULL;
	gboolean legacy = FALSE;
	if (g_file_get_contents(target_path, &contents, NULL, NULL) && *g_strstrip(contents))
		legacy = atoi(contents) > 0 && atoi(contents) < 23;
	g_clear_pointer(&contents, g_free);
	if (!legacy && g_file_get_contents(launcher_path, &contents, NULL, NULL)) {
		char *sdk = strstr(contents, "--sdk-int ");
		legacy = sdk && atoi(sdk + strlen("--sdk-int ")) > 0 && atoi(sdk + strlen("--sdk-int ")) < 23;
	}
	g_free(contents);
	g_free(launcher_path);
	g_free(target_path);
	g_free(files);
	g_free(flatpak);
	return legacy;
}

struct review {
	GDBusConnection *bus;
	char *app_id;
	GPtrArray *permissions; // the reviewed ones this app requests
	GVariant *dialog_args;
	int backend; // index into access_backends
	char *key; // in pending: the invocations waiting for this review
};

static void review_answered(GObject *source, GAsyncResult *res, gpointer user_data);

static void review_ask(struct review *r)
{
	g_dbus_connection_call(r->bus, access_backends[r->backend], "/org/freedesktop/portal/desktop",
	                       "org.freedesktop.impl.portal.Access", "AccessDialog", g_variant_ref(r->dialog_args),
	                       G_VARIANT_TYPE("(ua{sv})"), G_DBUS_CALL_FLAGS_NONE, G_MAXINT, NULL, review_answered, r);
}

static void review_reply(struct review *r, GHashTable *granted)
{
	GVariantBuilder answers;
	g_variant_builder_init(&answers, G_VARIANT_TYPE("a{ss}"));
	for (guint i = 0; i < r->permissions->len; i++) {
		const char *permission = g_ptr_array_index(r->permissions, i);
		int denials;
		int answer = granted ? (g_hash_table_contains(granted, permission) ? 1 : 0)
		                     : stored_answer(r->bus, r->app_id, permission, &denials);
		g_variant_builder_add(&answers, "{ss}", permission, answer == 1 ? "yes" : "no");
	}
	GVariant *value = g_variant_ref_sink(g_variant_new("(a{ss})", &answers));
	GPtrArray *waiting = g_hash_table_lookup(pending, r->key);
	for (guint i = 0; waiting && i < waiting->len; i++)
		g_dbus_method_invocation_return_value(g_ptr_array_index(waiting, i), g_variant_ref(value));
	g_hash_table_remove(pending, r->key);
	g_variant_unref(value);
}

static void review_free(struct review *r)
{
	g_object_unref(r->bus);
	g_free(r->app_id);
	g_ptr_array_unref(r->permissions);
	g_clear_pointer(&r->dialog_args, g_variant_unref);
	g_free(r);
	release();
}

static void review_answered(GObject *source, GAsyncResult *res, gpointer user_data)
{
	struct review *r = user_data;
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_finish(G_DBUS_CONNECTION(source), res, &err);
	guint32 response = 2;
	GVariant *results = NULL;
	if (reply) {
		g_variant_get(reply, "(u@a{sv})", &response, &results);
		g_variant_unref(reply);
	} else {
		gboolean missing = g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_SERVICE_UNKNOWN) ||
		                   g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_NAME_HAS_NO_OWNER) ||
		                   g_error_matches(err, G_DBUS_ERROR, G_DBUS_ERROR_UNKNOWN_METHOD);
		if (missing && ++r->backend < (int)G_N_ELEMENTS(access_backends)) {
			g_error_free(err);
			review_ask(r);
			return;
		}
		g_warning("review for %s: %s", r->app_id, err->message);
		g_error_free(err);
	}
	GHashTable *granted = g_hash_table_new(g_str_hash, g_str_equal);
	GVariant *choices = results && response == 0 ? g_variant_lookup_value(results, "choices", G_VARIANT_TYPE("a(ss)")) : NULL;
	if (choices) {
		GVariantIter iter;
		const char *id, *value;
		g_variant_iter_init(&iter, choices);
		while (g_variant_iter_next(&iter, "(&s&s)", &id, &value))
			if (!strcmp(value, "true"))
				for (guint i = 0; i < r->permissions->len; i++)
					if (!strcmp(id, g_ptr_array_index(r->permissions, i)))
						g_hash_table_add(granted, g_ptr_array_index(r->permissions, i));
	}
	/* an answered review is final (until App info changes it); a dismissed one comes again */
	if (response <= 1) {
		for (guint i = 0; i < r->permissions->len; i++) {
			const char *permission = g_ptr_array_index(r->permissions, i);
			gboolean yes = g_hash_table_contains(granted, permission);
			store_answer(r->bus, r->app_id, permission, yes, yes ? 0 : MAX_DENIALS);
			if (!strcmp(permission, "android.permission.RECORD_AUDIO"))
				apply_microphone_answer(r->app_id, yes);
		}
		store_answer(r->bus, r->app_id, REVIEW_DONE, TRUE, 0);
	}
	g_message("review for %s: response %u, %u of %u allowed", r->app_id, response, g_hash_table_size(granted), r->permissions->len);
	review_reply(r, granted);
	g_hash_table_unref(granted);
	g_clear_pointer(&choices, g_variant_unref);
	g_clear_pointer(&results, g_variant_unref);
	review_free(r);
}

static void handle_review(GDBusMethodInvocation *invocation, GVariant *params)
{
	char *app_id = caller_app_id(invocation);
	if (!app_id) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.AccessDenied",
		                                           "only apps in their own ATL Flatpak");
		return;
	}
	if (!app_is_legacy(app_id)) {
		g_dbus_method_invocation_return_dbus_error(invocation, "org.freedesktop.DBus.Error.AccessDenied",
		                                           "only for apps made for Android before 6");
		g_free(app_id);
		return;
	}
	/* one review at a time per app: later calls wait for its answers */
	char *key = g_strdup_printf("%s\n" REVIEW_DONE, app_id);
	GPtrArray *waiting = g_hash_table_lookup(pending, key);
	if (waiting) {
		g_ptr_array_add(waiting, invocation);
		g_free(key);
		g_free(app_id);
		return;
	}
	waiting = g_ptr_array_new();
	g_ptr_array_add(waiting, invocation);
	g_hash_table_insert(pending, g_strdup(key), waiting);
	struct review *r = g_new0(struct review, 1);
	r->key = key;
	r->bus = g_object_ref(g_dbus_method_invocation_get_connection(invocation));
	r->app_id = app_id;
	r->permissions = g_ptr_array_new();
	GVariantBuilder choices;
	g_variant_builder_init(&choices, G_VARIANT_TYPE("a(ssa(ss)s)"));
	for (gsize i = 0; i < G_N_ELEMENTS(reviewed); i++) {
		if (app_requests(app_id, reviewed[i].permission) != TRUE)
			continue;
		/* a final "no" (asked twice, or an earlier review) stays; App info can change it */
		int earlier_denials;
		int answer = stored_answer(r->bus, app_id, reviewed[i].permission, &earlier_denials);
		if (answer == 0 && earlier_denials >= MAX_DENIALS)
			continue;
		g_ptr_array_add(r->permissions, (gpointer)reviewed[i].permission);
		/* a check box, off by default, ticked after an earlier "yes" */
		g_variant_builder_add(&choices, "(ss@a(ss)s)", reviewed[i].permission, reviewed[i].label,
		                      g_variant_new_array(G_VARIANT_TYPE("(ss)"), NULL, 0), answer == 1 ? "true" : "false");
	}
	hold();
	int denials;
	if (r->permissions->len == 0 || stored_answer(r->bus, app_id, REVIEW_DONE, &denials) == 1) {
		g_variant_builder_clear(&choices);
		review_reply(r, NULL);
		review_free(r);
		return;
	}
	char *name = app_name(app_id);
	char *title = g_strdup_printf("Review Permissions of %s", name);
	GVariantBuilder options;
	g_variant_builder_init(&options, G_VARIANT_TYPE_VARDICT);
	g_variant_builder_add(&options, "{sv}", "deny_label", g_variant_new_string("Deny All"));
	g_variant_builder_add(&options, "{sv}", "grant_label", g_variant_new_string("Continue"));
	g_variant_builder_add(&options, "{sv}", "icon", g_variant_new_string(app_id));
	g_variant_builder_add(&options, "{sv}", "choices", g_variant_builder_end(&choices));
	static guint serial;
	char *handle_path = g_strdup_printf(PERMISSIONS_PATH "/review/%u", ++serial);
	r->dialog_args = g_variant_ref_sink(g_variant_new("(osssssa{sv})", handle_path, app_id, "", title,
	                               "This app was made for an older Android and does not ask when it needs something.",
	                               "Choose what it may use. You can change it later in App info.", &options));
	/* the shell's dialog first, then GTK's, as for single questions */
	review_ask(r);
	g_free(handle_path);
	g_free(title);
	g_free(name);
}
