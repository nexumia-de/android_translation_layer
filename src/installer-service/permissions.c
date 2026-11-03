/* Runtime permissions without a portal (docs/hardening-roadmap.md, phase 4): every app Flatpak may
 * call de.nexumia.atl.Permissions. The service asks the user with the desktop's own access dialog
 * (org.freedesktop.impl.portal.Access, on Phosh the shell's system dialog) and keeps the answers in
 * the xdg permission store, table "de.nexumia.atl", id = permission, app = Flatpak app id, value
 * ["yes"] or ["no", <times denied>]. The caller is identified by its sandbox, never by a
 * parameter, and gets only permissions its APK requests (plus ATL's own, atl.google-services). */

#include <gio/gdesktopappinfo.h>
#include <gio/gio.h>

#include <stdlib.h>
#include <string.h>

#include "permissions.h"

#define PERMISSIONS_PATH "/de/nexumia/atl/Permissions"
#define APP_PREFIX       "de.nexumia.atl."
#define STORE_NAME       "org.freedesktop.impl.portal.PermissionStore"
#define STORE_PATH       "/org/freedesktop/impl/portal/PermissionStore"
#define STORE_TABLE      "de.nexumia.atl"
#define GOOGLE_SERVICES  "atl.google-services"
/* like Android 11+: after the second "no" the app is not allowed to ask again */
#define MAX_DENIALS 2

#define PERMISSIONS_VERSION 1

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

static void (*hold_cb)(void);
static void (*release_cb)(void);

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
	GDBusConnection *bus = g_dbus_method_invocation_get_connection(invocation);
	GVariant *reply = g_dbus_connection_call_sync(bus, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
	                                              "GetConnectionUnixProcessID",
	                                              g_variant_new("(s)", g_dbus_method_invocation_get_sender(invocation)),
	                                              G_VARIANT_TYPE("(u)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	if (!reply)
		return NULL;
	guint32 pid;
	g_variant_get(reply, "(u)", &pid);
	g_variant_unref(reply);
	char *info_path = g_strdup_printf("/proc/%u/root/.flatpak-info", pid);
	GKeyFile *info = g_key_file_new();
	char *app_id = NULL;
	if (g_key_file_load_from_file(info, info_path, G_KEY_FILE_NONE, NULL))
		app_id = g_key_file_get_string(info, "Application", "name", NULL);
	g_key_file_free(info);
	g_free(info_path);
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

/* RECORD_AUDIO is also enforced in PipeWire (flatpak/microphone/): the user's WirePlumber and
 * pipewire-pulse configuration deny recording to every ATL app except those answered "yes". Both
 * read it when they start, so a change applies from the next start; ATL's own check covers the
 * time until then. */
static void write_microphone_rules(GDBusConnection *bus)
{
	GVariant *reply = g_dbus_connection_call_sync(bus, STORE_NAME, STORE_PATH, STORE_NAME, "Lookup",
	                                              g_variant_new("(ss)", STORE_TABLE, "android.permission.RECORD_AUDIO"),
	                                              G_VARIANT_TYPE("(a{sas}v)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	GString *wp_allowed = g_string_new("");
	GString *pulse_allowed = g_string_new("");
	if (reply) {
		GVariantIter *apps;
		const char *app;
		const char **value;
		g_variant_get(reply, "(a{sas}v)", &apps, NULL);
		while (g_variant_iter_next(apps, "{&s^a&s}", &app, &value)) {
			if (value[0] && !strcmp(value[0], "yes") && g_str_has_prefix(app, APP_PREFIX) && !strpbrk(app, "\"\\\n")) {
				g_string_append_printf(wp_allowed, "  {\n    matches = [ { pipewire.access.portal.app_id = \"%s\" } ]\n"
				                                   "    actions = { update-props = { permission_manager_name = \"atl-microphone\" } }\n  }\n", app);
				g_string_append_printf(pulse_allowed, "  {\n    matches = [ { pipewire.access.portal.app_id = \"%s\" } ]\n"
				                                      "    actions = { quirks = [ ] }\n  }\n", app);
			}
			g_free(value);
		}
		g_variant_iter_free(apps);
		g_variant_unref(reply);
	}
	char *wp = g_strdup_printf(
	    "# written by ATL's installer service from the RECORD_AUDIO answers; see flatpak/microphone/ in ATL\n"
	    "access.permission-managers = [\n"
	    "  {\n    name = \"atl-no-microphone\"\n    default_permissions = \"rx\"\n"
	    "    rules = [ { matches = [ { media.class = \"~Audio/Source.*\" } ], actions = { set-permissions = \"-\" } } ]\n  }\n"
	    "  { name = \"atl-microphone\", default_permissions = \"rx\" }\n]\n"
	    "access.rules = [\n"
	    "  {\n    matches = [ { pipewire.access.portal.app_id = \"~" APP_PREFIX "*\" } ]\n"
	    "    actions = { update-props = { permission_manager_name = \"atl-no-microphone\" } }\n  }\n%s]\n",
	    wp_allowed->str);
	char *pulse = g_strdup_printf(
	    "# written by ATL's installer service from the RECORD_AUDIO answers; see flatpak/microphone/ in ATL\n"
	    "pulse.rules = [\n"
	    "  {\n    matches = [ { pipewire.access.portal.app_id = \"~" APP_PREFIX "*\" } ]\n"
	    "    actions = { quirks = [ block-record-stream ] }\n  }\n%s]\n",
	    pulse_allowed->str);
	char *wp_dir = g_build_filename(g_get_user_config_dir(), "wireplumber", "wireplumber.conf.d", NULL);
	char *pulse_dir = g_build_filename(g_get_user_config_dir(), "pipewire", "pipewire-pulse.conf.d", NULL);
	char *wp_file = g_build_filename(wp_dir, "90-atl-microphone.conf", NULL);
	char *pulse_file = g_build_filename(pulse_dir, "90-atl-microphone.conf", NULL);
	GError *err = NULL;
	if (g_mkdir_with_parents(wp_dir, 0755) || g_mkdir_with_parents(pulse_dir, 0755) ||
	    !g_file_set_contents(wp_file, wp, -1, &err) || !g_file_set_contents(pulse_file, pulse, -1, &err)) {
		g_warning("writing the microphone rules: %s", err ? err->message : "cannot create the directories");
		g_clear_error(&err);
	}
	g_free(wp_file);
	g_free(pulse_file);
	g_free(wp_dir);
	g_free(pulse_dir);
	g_free(wp);
	g_free(pulse);
	g_string_free(wp_allowed, TRUE);
	g_string_free(pulse_allowed, TRUE);
}

static const char *answer_text(int answer)
{
	return answer == 1 ? "yes" : answer == 0 ? "no" : "ask";
}

struct request {
	GDBusMethodInvocation *invocation;
	GDBusConnection *bus;
	char *app_id;
	char *permission;
	int denials;
	GVariant *dialog_args;
	int backend; // index into access_backends
};

/* the desktop's access dialog: Phosh's shell, else GTK's (headless tests) */
static const char *const access_backends[] = {"mobi.phosh.Shell.Portal", "org.freedesktop.impl.portal.desktop.gtk"};

static void request_free(struct request *r)
{
	g_object_unref(r->bus);
	g_free(r->app_id);
	g_free(r->permission);
	g_clear_pointer(&r->dialog_args, g_variant_unref);
	g_free(r);
	release_cb();
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
		g_dbus_method_invocation_return_value(r->invocation, g_variant_new("(s)", "no"));
		request_free(r);
		return;
	}
	guint32 response;
	g_variant_get(reply, "(u@a{sv})", &response, NULL);
	g_variant_unref(reply);
	gboolean yes = response == 0;
	store_answer(r->bus, r->app_id, r->permission, yes, yes ? 0 : r->denials + 1);
	if (!strcmp(r->permission, "android.permission.RECORD_AUDIO"))
		write_microphone_rules(r->bus);
	g_message("%s %s: %s", r->app_id, r->permission, yes ? "yes" : "no");
	g_dbus_method_invocation_return_value(r->invocation, g_variant_new("(s)", yes ? "yes" : "no"));
	request_free(r);
}

static void ask_backend(struct request *r)
{
	g_dbus_connection_call(r->bus, access_backends[r->backend], "/org/freedesktop/portal/desktop",
	                       "org.freedesktop.impl.portal.Access", "AccessDialog", g_variant_ref(r->dialog_args),
	                       G_VARIANT_TYPE("(ua{sv})"), G_DBUS_CALL_FLAGS_NONE, G_MAXINT, NULL, dialog_answered, r);
}

static void handle(GDBusMethodInvocation *invocation, const char *method, GVariant *params)
{
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
	if (!app_requests(app_id, permission)) {
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
	r->invocation = invocation;
	r->bus = g_object_ref(bus);
	r->app_id = app_id;
	r->permission = g_strdup(permission);
	r->denials = denials;
	r->dialog_args = g_variant_ref_sink(g_variant_new("(osssssa{sv})", handle_path, app_id, "", title, "", q->body, &options));
	hold_cb();
	ask_backend(r);
	g_free(title);
	g_free(name);
	g_free(handle_path);
}

static void method_call(GDBusConnection *connection, const char *sender, const char *object_path, const char *interface_name,
                        const char *method_name, GVariant *params, GDBusMethodInvocation *invocation, gpointer user_data)
{
	hold_cb();
	handle(invocation, method_name, params);
	release_cb();
}

static GVariant *get_property(GDBusConnection *connection, const char *sender, const char *object_path,
                              const char *interface_name, const char *property_name, GError **error, gpointer user_data)
{
	return g_variant_new_uint32(PERMISSIONS_VERSION);
}

static const GDBusInterfaceVTable vtable = {method_call, get_property, NULL, {0}};

void permissions_register(GDBusConnection *connection, void (*hold)(void), void (*release)(void))
{
	hold_cb = hold;
	release_cb = release;
	GBytes *xml = g_resources_lookup_data("/de/nexumia/atl/Installer/de.nexumia.atl.Permissions.xml", 0, NULL);
	GDBusNodeInfo *node = g_dbus_node_info_new_for_xml(g_bytes_get_data(xml, NULL), NULL);
	g_dbus_connection_register_object(connection, PERMISSIONS_PATH, node->interfaces[0], &vtable, NULL, NULL, NULL);
	g_dbus_node_info_unref(node);
	g_bytes_unref(xml);
}
