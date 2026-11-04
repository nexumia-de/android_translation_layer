/* --app-info PACKAGE: Android's "App info" for an app in its own Flatpak (docs/hardening-roadmap.md,
 * phase 4, place 3). Runs on the host, never in the app's sandbox, so the app cannot draw over it or
 * change it. Shows what GNOME Settings does not: internet (a Flatpak user override), the answers of
 * the permissions without a portal (permission store table de.nexumia.atl), clear data and
 * uninstall; "More permissions" opens the app's page in GNOME Settings. App stores get "Update apps
 * without asking" (docs/installing-apps.md, "Updates without asking"). */

#include <adwaita.h>
#include <string.h>

#include "app_info.h"

#define APP_PREFIX  "de.nexumia.atl."
#define STORE_NAME  "org.freedesktop.impl.portal.PermissionStore"
#define STORE_PATH  "/org/freedesktop/impl/portal/PermissionStore"
#define STORE_TABLE "de.nexumia.atl"
/* read by --install-ask (may_update_unattended in main.c) */
#define UPDATE_WITHOUT_ASKING "atl.update-without-asking"

struct permission_row {
	const char *permission;
	const char *title;
};

/* the permissions the installer service asks for (src/installer-service/permissions.c, #75) */
static const struct permission_row permission_rows[] = {
	{"android.permission.RECORD_AUDIO", "Microphone"},
	{"android.permission.ACCESS_BACKGROUND_LOCATION", "Location in the background"},
	{"android.permission.READ_CONTACTS", "Contacts"},
	{"android.permission.READ_CALENDAR", "Calendar"},
	{"android.permission.READ_SMS", "Text messages"},
	{"android.permission.SEND_SMS", "Send text messages"},
	{"android.permission.READ_CALL_LOG", "Call log"},
	{"android.permission.READ_PHONE_STATE", "Phone state"},
	{"android.permission.BLUETOOTH_CONNECT", "Bluetooth devices"},
	{"android.permission.BLUETOOTH_SCAN", "Find Bluetooth devices"},
	{"android.permission.BODY_SENSORS", "Body sensors"},
	{"android.permission.ACTIVITY_RECOGNITION", "Physical activity"},
	{"atl.google-services", "Google services"},
};

struct app_info {
	GtkApplication *app;
	GtkWindow *window;
	AdwToastOverlay *toasts;
	AdwBanner *restart_banner;
	int restart_tries;
	char *package;
	char *id;
	char *prgname;
	GDBusConnection *bus;
};

static char *flatpak_user_dir(void)
{
	const char *dir = g_getenv("FLATPAK_USER_DIR");
	return dir ? g_strdup(dir) : g_build_filename(g_get_user_data_dir(), "flatpak", NULL);
}

static char *app_file(struct app_info *info, const char *name)
{
	char *dir = flatpak_user_dir();
	char *path = g_build_filename(dir, "app", info->id, "current", "active", "files", "share", "atl", name, NULL);
	g_free(dir);
	return path;
}

/* the APK's <uses-permission> list, written by make-app-flatpak (#75): 1 requested, 0 not, -1 unknown
 * (an app built before the list existed) */
static int app_requests(struct app_info *info, const char *permission)
{
	if (!strcmp(permission, "atl.google-services"))
		return TRUE; // ATL's own: asked by ATL's Google clients, not in a manifest
	char *path = app_file(info, "permissions");
	char *contents = NULL;
	int found = -1;
	if (g_file_get_contents(path, &contents, NULL, NULL)) {
		char **lines = g_strsplit(contents, "\n", -1);
		found = g_strv_contains((const char *const *)lines, permission);
		g_strfreev(lines);
	}
	g_free(contents);
	g_free(path);
	return found;
}

static gboolean run(const char *const *argv)
{
	int wait_status;
	GError *err = NULL;
	gboolean ok = g_spawn_sync(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH | G_SPAWN_STDOUT_TO_DEV_NULL, NULL, NULL, NULL, NULL,
	                           &wait_status, &err) &&
	              g_spawn_check_wait_status(wait_status, &err);
	if (!ok) {
		fprintf(stderr, "app-info: %s: %s\n", argv[0], err->message);
		g_error_free(err);
	}
	return ok;
}

static gboolean app_running(struct app_info *info)
{
	char *out = NULL;
	const char *argv[] = {"flatpak", "ps", "--columns=application", NULL};
	gboolean running = FALSE;
	if (g_spawn_sync(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH | G_SPAWN_STDERR_TO_DEV_NULL, NULL, NULL, &out, NULL, NULL, NULL)) {
		char **lines = g_strsplit(out, "\n", -1);
		running = g_strv_contains((const char *const *)lines, info->id);
		g_strfreev(lines);
	}
	g_free(out);
	return running;
}

/* Flatpak applies static permissions when the app starts */
static void changed_static_permission(struct app_info *info)
{
	if (app_running(info))
		adw_banner_set_revealed(info->restart_banner, TRUE);
	else
		adw_toast_overlay_add_toast(info->toasts, adw_toast_new("Applies when the app starts"));
}

#define RESTART_POLL_MS 200
#define RESTART_TRIES   50 // 10 s

/* starts the app again once Flatpak no longer lists the old instance */
static gboolean restart_when_stopped(gpointer user_data)
{
	struct app_info *info = user_data;
	if (app_running(info) && ++info->restart_tries < RESTART_TRIES)
		return G_SOURCE_CONTINUE;
	if (info->restart_tries >= RESTART_TRIES) {
		adw_toast_overlay_add_toast(info->toasts, adw_toast_new("The app did not stop"));
	} else {
		const char *run_argv[] = {"flatpak", "run", "--user", info->id, NULL};
		g_spawn_async(NULL, (char **)run_argv, NULL, G_SPAWN_SEARCH_PATH, NULL, NULL, NULL, NULL);
	}
	gtk_widget_set_sensitive(GTK_WIDGET(info->restart_banner), TRUE);
	g_application_release(G_APPLICATION(info->app));
	return G_SOURCE_REMOVE;
}

static void restart_clicked(AdwBanner *banner, struct app_info *info)
{
	const char *kill_argv[] = {"flatpak", "kill", info->id, NULL};
	run(kill_argv);
	adw_banner_set_revealed(banner, FALSE);
	gtk_widget_set_sensitive(GTK_WIDGET(banner), FALSE);
	info->restart_tries = 0;
	g_application_hold(G_APPLICATION(info->app)); // until the app runs again, even if the window closes
	g_timeout_add(RESTART_POLL_MS, restart_when_stopped, info);
}

/* -------- internet -------- */

static int network_answer(struct app_info *info)
{
	char *dir = flatpak_user_dir();
	char *path = g_build_filename(dir, "overrides", info->id, NULL);
	int answer = -1;
	GKeyFile *key_file = g_key_file_new();
	if (g_key_file_load_from_file(key_file, path, G_KEY_FILE_NONE, NULL)) {
		char **shared = g_key_file_get_string_list(key_file, "Context", "shared", NULL, NULL);
		for (int i = 0; shared && shared[i]; i++) {
			if (!strcmp(shared[i], "network"))
				answer = 1;
			else if (!strcmp(shared[i], "!network"))
				answer = 0;
		}
		g_strfreev(shared);
	}
	g_key_file_free(key_file);
	g_free(path);
	g_free(dir);
	return answer;
}

static void internet_toggled(AdwSwitchRow *row, GParamSpec *pspec, struct app_info *info)
{
	gboolean yes = adw_switch_row_get_active(row);
	const char *argv[] = {"flatpak", "override", "--user", yes ? "--share=network" : "--unshare=network", info->id, NULL};
	if (run(argv))
		changed_static_permission(info);
	else
		adw_toast_overlay_add_toast(info->toasts, adw_toast_new("Could not change internet access"));
}

/* -------- answers in the permission store -------- */

static int stored_answer(struct app_info *info, const char *permission)
{
	GVariant *reply = g_dbus_connection_call_sync(info->bus, STORE_NAME, STORE_PATH, STORE_NAME, "GetPermission",
	                                              g_variant_new("(sss)", STORE_TABLE, permission, info->id),
	                                              G_VARIANT_TYPE("(as)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	if (!reply)
		return -1;
	const char **value;
	g_variant_get(reply, "(^a&s)", &value);
	int answer = value[0] ? (!strcmp(value[0], "yes") ? 1 : !strcmp(value[0], "no") ? 0 : -1) : -1;
	g_free(value);
	g_variant_unref(reply);
	return answer;
}

/* The microphone is also enforced by the app's pulse socket (flatpak/microphone/, #68): the same
 * override the permission service writes for an answer in its dialog (#75). */
static gboolean apply_microphone(struct app_info *info, gboolean yes)
{
	char *norecord = g_strdup_printf("--env=PULSE_SERVER=unix:%s/atl-pulse-norecord/native", g_get_user_runtime_dir());
	const char *allow[] = {"flatpak", "override", "--user", "--socket=pulseaudio", "--unset-env=PULSE_SERVER",
	                       "--nofilesystem=xdg-run/atl-pulse-norecord", info->id, NULL};
	const char *deny[] = {"flatpak", "override", "--user", "--nosocket=pulseaudio", norecord,
	                      "--filesystem=xdg-run/atl-pulse-norecord:ro", info->id, NULL};
	gboolean ok = run(yes ? allow : deny);
	g_free(norecord);
	return ok;
}

static gboolean store_answer(struct app_info *info, const char *permission, gboolean yes)
{
	/* "no" from here may be asked again by the app, like revoking on Android */
	const char *value[] = {yes ? "yes" : "no", yes ? NULL : "0", NULL};
	GError *err = NULL;
	GVariant *reply = g_dbus_connection_call_sync(info->bus, STORE_NAME, STORE_PATH, STORE_NAME, "SetPermission",
	                                              g_variant_new("(sbss^as)", STORE_TABLE, TRUE, permission, info->id, value),
	                                              NULL, G_DBUS_CALL_FLAGS_NONE, -1, NULL, &err);
	if (!reply) {
		adw_toast_overlay_add_toast(info->toasts, adw_toast_new("Could not save the answer"));
		fprintf(stderr, "app-info: %s\n", err->message);
		g_error_free(err);
		return FALSE;
	}
	g_variant_unref(reply);
	return TRUE;
}

static void permission_toggled(AdwSwitchRow *row, GParamSpec *pspec, struct app_info *info)
{
	const char *permission = g_object_get_data(G_OBJECT(row), "permission");
	gboolean yes = adw_switch_row_get_active(row);
	gboolean microphone = !strcmp(permission, "android.permission.RECORD_AUDIO");
	/* the socket first: the stored answer must never claim less than the app can do */
	if (microphone && !apply_microphone(info, yes)) {
		adw_toast_overlay_add_toast(info->toasts, adw_toast_new("Could not change the microphone"));
		g_signal_handlers_block_by_func(row, permission_toggled, info);
		adw_switch_row_set_active(row, !yes);
		g_signal_handlers_unblock_by_func(row, permission_toggled, info);
		return;
	}
	if (!store_answer(info, permission, yes))
		return;
	adw_action_row_set_subtitle(ADW_ACTION_ROW(row), NULL); // answered now
	if (microphone)
		changed_static_permission(info);
}

/* -------- app stores -------- */

static void update_without_asking_toggled(AdwSwitchRow *row, GParamSpec *pspec, struct app_info *info)
{
	gboolean yes = adw_switch_row_get_active(row);
	if (!store_answer(info, UPDATE_WITHOUT_ASKING, yes)) {
		g_signal_handlers_block_by_func(row, update_without_asking_toggled, info);
		adw_switch_row_set_active(row, !yes);
		g_signal_handlers_unblock_by_func(row, update_without_asking_toggled, info);
	}
}

/* -------- other rows -------- */

static void more_permissions_activated(AdwActionRow *row, struct app_info *info)
{
	const char *argv[] = {"gnome-control-center", "applications", info->id, NULL};
	if (!g_spawn_async(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH, NULL, NULL, NULL, NULL))
		adw_toast_overlay_add_toast(info->toasts, adw_toast_new("GNOME Settings is not installed"));
}

static void clear_data_response(AdwAlertDialog *dialog, const char *response, struct app_info *info)
{
	if (strcmp(response, "clear"))
		return;
	const char *kill_argv[] = {"flatpak", "kill", info->id, NULL};
	if (app_running(info))
		run(kill_argv);
	/* the app's whole data directory, as Android's "Clear storage" */
	char *dir = g_build_filename(g_get_home_dir(), ".var", "app", info->id, NULL);
	const char *rm_argv[] = {"rm", "-rf", "--", dir, NULL};
	gboolean ok = g_str_has_prefix(info->id, APP_PREFIX) && run(rm_argv);
	adw_toast_overlay_add_toast(info->toasts, adw_toast_new(ok ? "Data cleared" : "Could not clear the data"));
	g_free(dir);
}

static void clear_data_activated(AdwButtonRow *row, struct app_info *info)
{
	AdwDialog *dialog = adw_alert_dialog_new("Clear Data?", "All of the app's data, accounts and settings will be removed.");
	adw_alert_dialog_add_responses(ADW_ALERT_DIALOG(dialog), "cancel", "_Cancel", "clear", "C_lear", NULL);
	adw_alert_dialog_set_response_appearance(ADW_ALERT_DIALOG(dialog), "clear", ADW_RESPONSE_DESTRUCTIVE);
	g_signal_connect(dialog, "response", G_CALLBACK(clear_data_response), info);
	adw_dialog_present(dialog, GTK_WIDGET(info->window));
}

static void uninstall_activated(AdwButtonRow *row, struct app_info *info)
{
	/* the existing uninstall page asks and removes the app with its data */
	const char *argv[] = {info->prgname, "--uninstall-flatpak", info->package, NULL};
	if (g_spawn_async(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH, NULL, NULL, NULL, NULL))
		gtk_window_close(info->window);
}

static void all_apps_activated(AdwActionRow *row, struct app_info *info)
{
	const char *argv[] = {info->prgname, "--android-apps", NULL};
	g_spawn_async(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH, NULL, NULL, NULL, NULL);
}

static void close_window(GtkWindow *window, struct app_info *info)
{
	g_application_release(G_APPLICATION(info->app));
}

void app_info_show(GtkApplication *app, const char *package, const char *prgname)
{
	struct app_info *info = g_new0(struct app_info, 1);
	info->app = app;
	info->package = g_strdup(package);
	info->id = g_strconcat(APP_PREFIX, package, NULL);
	info->prgname = g_strdup(prgname);

	char *flatpak = flatpak_user_dir();
	char *desktop_file = g_build_filename(flatpak, "exports", "share", "applications", info->id, NULL);
	char *desktop_path = g_strconcat(desktop_file, ".desktop", NULL);
	GKeyFile *key_file = g_key_file_new();
	if (strchr(package, '/') || !g_key_file_load_from_file(key_file, desktop_path, G_KEY_FILE_NONE, NULL)) {
		fprintf(stderr, "app-info: %s is not installed as a Flatpak\n", package);
		exit(1);
	}
	char *label = g_key_file_get_locale_string(key_file, G_KEY_FILE_DESKTOP_GROUP, G_KEY_FILE_DESKTOP_KEY_NAME, NULL, NULL);
	g_key_file_free(key_file);
	g_free(desktop_path);
	g_free(desktop_file);
	info->bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);

	adw_init();
	g_application_hold(G_APPLICATION(app));
	info->window = GTK_WINDOW(adw_application_window_new(app));
	gtk_window_set_title(info->window, "App Info");
	gtk_window_set_default_size(info->window, 400, 640);
	g_signal_connect(info->window, "destroy", G_CALLBACK(close_window), info);

	GtkWidget *toolbar_view = adw_toolbar_view_new();
	adw_toolbar_view_add_top_bar(ADW_TOOLBAR_VIEW(toolbar_view), adw_header_bar_new());
	info->restart_banner = ADW_BANNER(adw_banner_new("Restart the app to apply the change"));
	adw_banner_set_button_label(info->restart_banner, "_Restart");
	g_signal_connect(info->restart_banner, "button-clicked", G_CALLBACK(restart_clicked), info);
	adw_toolbar_view_add_top_bar(ADW_TOOLBAR_VIEW(toolbar_view), GTK_WIDGET(info->restart_banner));

	GtkWidget *page = adw_preferences_page_new();

	/* who: icon, name, package and version */
	GtkWidget *header = adw_preferences_group_new();
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_VERTICAL, 6);
	GtkIconTheme *theme = gtk_icon_theme_get_for_display(gtk_widget_get_display(GTK_WIDGET(info->window)));
	char *icons = g_build_filename(flatpak, "exports", "share", "icons", NULL);
	gtk_icon_theme_add_search_path(theme, icons);
	g_free(icons);
	GtkWidget *icon = gtk_image_new_from_icon_name(gtk_icon_theme_has_icon(theme, info->id) ? info->id : "application-x-executable");
	gtk_image_set_pixel_size(GTK_IMAGE(icon), 96);
	gtk_box_append(GTK_BOX(box), icon);
	GtkWidget *name = gtk_label_new(label ? label : package);
	gtk_widget_add_css_class(name, "title-2");
	gtk_label_set_wrap(GTK_LABEL(name), TRUE);
	gtk_box_append(GTK_BOX(box), name);
	char *version_path = app_file(info, "version");
	char *version = NULL;
	g_file_get_contents(version_path, &version, NULL, NULL);
	g_free(version_path);
	char *details = version && *version ? g_strdup_printf("%s\nVersion %s", package, g_strstrip(version)) : g_strdup(package);
	GtkWidget *details_label = gtk_label_new(details);
	gtk_label_set_justify(GTK_LABEL(details_label), GTK_JUSTIFY_CENTER);
	gtk_widget_add_css_class(details_label, "dimmed");
	gtk_box_append(GTK_BOX(box), details_label);
	g_free(details);
	g_free(version);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(header), box);
	adw_preferences_page_add(ADW_PREFERENCES_PAGE(page), ADW_PREFERENCES_GROUP(header));

	/* permissions */
	GtkWidget *permissions = adw_preferences_group_new();
	adw_preferences_group_set_title(ADW_PREFERENCES_GROUP(permissions), "Permissions");
	int network = network_answer(info);
	if (app_requests(info, "android.permission.INTERNET") != 0 || network >= 0) {
		GtkWidget *row = adw_switch_row_new();
		adw_preferences_row_set_title(ADW_PREFERENCES_ROW(row), "Internet");
		adw_action_row_set_subtitle(ADW_ACTION_ROW(row), "Connect to any server");
		adw_switch_row_set_active(ADW_SWITCH_ROW(row), network == 1);
		g_signal_connect(row, "notify::active", G_CALLBACK(internet_toggled), info);
		adw_preferences_group_add(ADW_PREFERENCES_GROUP(permissions), row);
	}
	for (gsize i = 0; i < G_N_ELEMENTS(permission_rows); i++) {
		int answer = info->bus ? stored_answer(info, permission_rows[i].permission) : -1;
		/* the ones the app requests, and Google services once the app asked for them */
		int requested = app_requests(info, permission_rows[i].permission);
		if (requested == 0 || (requested < 0 && answer < 0) ||
		    (!strcmp(permission_rows[i].permission, "atl.google-services") && answer < 0))
			continue;
		GtkWidget *row = adw_switch_row_new();
		adw_preferences_row_set_title(ADW_PREFERENCES_ROW(row), permission_rows[i].title);
		adw_action_row_set_subtitle(ADW_ACTION_ROW(row), answer < 0 ? "Asked when the app needs it" : NULL);
		adw_switch_row_set_active(ADW_SWITCH_ROW(row), answer == 1);
		g_object_set_data(G_OBJECT(row), "permission", (gpointer)permission_rows[i].permission);
		g_signal_connect(row, "notify::active", G_CALLBACK(permission_toggled), info);
		adw_preferences_group_add(ADW_PREFERENCES_GROUP(permissions), row);
	}
	GtkWidget *more = adw_action_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(more), "More Permissions");
	adw_action_row_set_subtitle(ADW_ACTION_ROW(more), "Location, camera, notifications in Settings");
	adw_action_row_add_suffix(ADW_ACTION_ROW(more), gtk_image_new_from_icon_name("go-next-symbolic"));
	gtk_list_box_row_set_activatable(GTK_LIST_BOX_ROW(more), TRUE);
	g_signal_connect(more, "activated", G_CALLBACK(more_permissions_activated), info);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(permissions), more);
	adw_preferences_page_add(ADW_PREFERENCES_PAGE(page), ADW_PREFERENCES_GROUP(permissions));

	/* app stores: updates without the install page, like Android 12+ allows the installer of record */
	if (app_requests(info, "android.permission.REQUEST_INSTALL_PACKAGES") == 1 && info->bus) {
		GtkWidget *store = adw_preferences_group_new();
		adw_preferences_group_set_title(ADW_PREFERENCES_GROUP(store), "App Store");
		GtkWidget *row = adw_switch_row_new();
		adw_preferences_row_set_title(ADW_PREFERENCES_ROW(row), "Update Apps Without Asking");
		adw_action_row_set_subtitle(ADW_ACTION_ROW(row), "Only apps it installed, and only updates that ask for no new permissions");
		adw_switch_row_set_active(ADW_SWITCH_ROW(row), stored_answer(info, UPDATE_WITHOUT_ASKING) == 1);
		g_signal_connect(row, "notify::active", G_CALLBACK(update_without_asking_toggled), info);
		adw_preferences_group_add(ADW_PREFERENCES_GROUP(store), row);
		adw_preferences_page_add(ADW_PREFERENCES_PAGE(page), ADW_PREFERENCES_GROUP(store));
	}

	/* the settings for all Android apps */
	GtkWidget *all_group = adw_preferences_group_new();
	GtkWidget *all = adw_action_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(all), "All Android Apps");
	adw_action_row_set_subtitle(ADW_ACTION_ROW(all), "Navigation, push server, installed apps");
	adw_action_row_add_suffix(ADW_ACTION_ROW(all), gtk_image_new_from_icon_name("go-next-symbolic"));
	gtk_list_box_row_set_activatable(GTK_LIST_BOX_ROW(all), TRUE);
	g_signal_connect(all, "activated", G_CALLBACK(all_apps_activated), info);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(all_group), all);
	adw_preferences_page_add(ADW_PREFERENCES_PAGE(page), ADW_PREFERENCES_GROUP(all_group));

	/* storage and removal */
	GtkWidget *actions = adw_preferences_group_new();
	GtkWidget *clear = adw_button_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(clear), "_Clear Data");
	adw_preferences_row_set_use_underline(ADW_PREFERENCES_ROW(clear), TRUE);
	g_signal_connect(clear, "activated", G_CALLBACK(clear_data_activated), info);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(actions), clear);
	GtkWidget *uninstall = adw_button_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(uninstall), "_Uninstall");
	adw_preferences_row_set_use_underline(ADW_PREFERENCES_ROW(uninstall), TRUE);
	gtk_widget_add_css_class(uninstall, "destructive-action");
	g_signal_connect(uninstall, "activated", G_CALLBACK(uninstall_activated), info);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(actions), uninstall);
	adw_preferences_page_add(ADW_PREFERENCES_PAGE(page), ADW_PREFERENCES_GROUP(actions));

	info->toasts = ADW_TOAST_OVERLAY(adw_toast_overlay_new());
	adw_toast_overlay_set_child(info->toasts, page);
	adw_toolbar_view_set_content(ADW_TOOLBAR_VIEW(toolbar_view), GTK_WIDGET(info->toasts));
	adw_application_window_set_content(ADW_APPLICATION_WINDOW(info->window), toolbar_view);
	g_free(label);
	g_free(flatpak);
	gtk_window_present(info->window);
}
