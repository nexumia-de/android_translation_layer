/* --android-apps: the "Android apps" page (docs/hardening-roadmap.md, phase 4, place 2). Phosh Mobile
 * Settings has no public plugin API, so this is a window of ATL on the host, opened from every app's
 * App info. Settings for all Android apps: navigation, push server, wake interval, which apps may
 * install others; and the installed apps, each with a link to its App info. */

#include <adwaita.h>
#include <string.h>

#include "android_apps.h"
#include "navigation.h"

#define APP_PREFIX       "de.nexumia.atl."
#define INSTALLER_NAME   "de.nexumia.atl.Installer"
#define DEFAULT_PUSH_URL "https://unifiedpush.kde.org/"

struct android_apps {
	GtkApplication *app;
	GtkWindow *window;
	AdwToastOverlay *toasts;
	AdwEntryRow *push_row;
	char *prgname;
};

static char *flatpak_user_dir(void)
{
	const char *dir = g_getenv("FLATPAK_USER_DIR");
	return dir ? g_strdup(dir) : g_build_filename(g_get_user_data_dir(), "flatpak", NULL);
}

static void toast(struct android_apps *page, const char *text)
{
	adw_toast_overlay_add_toast(page->toasts, adw_toast_new(text));
}

static gboolean run(const char *const *argv, char **out, char **err_out)
{
	int wait_status;
	GError *err = NULL;
	gboolean ok = g_spawn_sync(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH, NULL, NULL, out, err_out, &wait_status, &err) &&
	              g_spawn_check_wait_status(wait_status, &err);
	if (err) {
		fprintf(stderr, "android-apps: %s: %s\n", argv[0], err->message);
		g_error_free(err);
	}
	return ok;
}

static int compare_strings(gconstpointer a, gconstpointer b)
{
	return g_strcmp0(*(char *const *)a, *(char *const *)b);
}

/* the installed Android apps: Flatpaks de.nexumia.atl.<package> with a launcher */
static GPtrArray *installed_apps(void)
{
	GPtrArray *ids = g_ptr_array_new_with_free_func(g_free);
	char *flatpak = flatpak_user_dir();
	char *dir_path = g_build_filename(flatpak, "exports", "share", "applications", NULL);
	GDir *dir = g_dir_open(dir_path, 0, NULL);
	const char *name;
	while (dir && (name = g_dir_read_name(dir)))
		if (g_str_has_prefix(name, APP_PREFIX) && g_str_has_suffix(name, ".desktop") && strcmp(name, "de.nexumia.atl.BaseApp.desktop"))
			g_ptr_array_add(ids, g_strndup(name, strlen(name) - strlen(".desktop")));
	if (dir)
		g_dir_close(dir);
	g_ptr_array_sort(ids, compare_strings);
	g_free(dir_path);
	g_free(flatpak);
	return ids;
}

static char *app_label(const char *id)
{
	char *flatpak = flatpak_user_dir();
	char *path = g_strdup_printf("%s/exports/share/applications/%s.desktop", flatpak, id);
	GKeyFile *key_file = g_key_file_new();
	char *label = NULL;
	if (g_key_file_load_from_file(key_file, path, G_KEY_FILE_NONE, NULL))
		label = g_key_file_get_locale_string(key_file, G_KEY_FILE_DESKTOP_GROUP, G_KEY_FILE_DESKTOP_KEY_NAME, NULL, NULL);
	g_key_file_free(key_file);
	g_free(path);
	g_free(flatpak);
	return label ? label : g_strdup(id + strlen(APP_PREFIX));
}

/* an app's Session Bus Policy as Flatpak applies it: metadata, then the user's override */
static char *installer_policy(const char *id)
{
	char *flatpak = flatpak_user_dir();
	char *paths[] = {g_build_filename(flatpak, "app", id, "current", "active", "metadata", NULL),
	                 g_build_filename(flatpak, "overrides", id, NULL)};
	char *policy = NULL;
	for (int i = 0; i < 2; i++) {
		GKeyFile *key_file = g_key_file_new();
		if (g_key_file_load_from_file(key_file, paths[i], G_KEY_FILE_NONE, NULL)) {
			char *value = g_key_file_get_string(key_file, "Session Bus Policy", INSTALLER_NAME, NULL);
			if (value) {
				g_free(policy);
				policy = value;
			}
		}
		g_key_file_free(key_file);
		g_free(paths[i]);
	}
	g_free(flatpak);
	return policy;
}

/* -------- navigation -------- */

static const char *const navigation_values[] = {"both", "buttons", "gesture"};

static void navigation_selected(AdwComboRow *row, GParamSpec *pspec, struct android_apps *page)
{
	guint selected = adw_combo_row_get_selected(row);
	if (selected >= G_N_ELEMENTS(navigation_values))
		return;
	const char *mode = navigation_values[selected];
	GError *err = NULL;
	if (!atl_navigation_set_mode(mode, &err)) {
		toast(page, "Could not save the setting");
		fprintf(stderr, "android-apps: %s\n", err->message);
		g_error_free(err);
		return;
	}
	/* apps in their own Flatpak read it from their environment, set per app */
	GPtrArray *ids = installed_apps();
	char *env = g_strdup_printf("--env=ATL_NAVIGATION=%s", mode);
	gboolean ok = TRUE;
	for (guint i = 0; i < ids->len; i++) {
		const char *argv[] = {"flatpak", "override", "--user", env, g_ptr_array_index(ids, i), NULL};
		ok &= run(argv, NULL, NULL);
	}
	g_free(env);
	g_ptr_array_unref(ids);
	toast(page, ok ? "Applies when an app starts" : "Could not change every app");
}

/* -------- push server (atl-push-server, docs/push.md) -------- */

static char *push_server_get(void)
{
	char *out = NULL;
	const char *argv[] = {"atl-push-server", "get", NULL};
	if (!run(argv, &out, NULL)) {
		g_free(out);
		return NULL;
	}
	return g_strstrip(out);
}

static void push_server_set(struct android_apps *page, const char *url)
{
	char *err_out = NULL;
	const char *set_argv[] = {"atl-push-server", "set", url, NULL};
	const char *reset_argv[] = {"atl-push-server", "reset", NULL};
	gboolean reset = !*url || !strcmp(url, DEFAULT_PUSH_URL);
	if (run(reset ? reset_argv : set_argv, NULL, &err_out)) {
		toast(page, "Push server changed");
	} else {
		char *message = err_out && *g_strstrip(err_out) ? g_strdup(err_out) : g_strdup("Could not change the push server");
		toast(page, message);
		g_free(message);
	}
	g_free(err_out);
	char *current = push_server_get();
	gtk_editable_set_text(GTK_EDITABLE(page->push_row), current ? current : "");
	g_free(current);
}

static void push_server_apply(AdwEntryRow *row, struct android_apps *page)
{
	push_server_set(page, g_strstrip(g_strdup(gtk_editable_get_text(GTK_EDITABLE(row)))));
}

static void push_server_reset(GtkButton *button, struct android_apps *page)
{
	push_server_set(page, "");
}

/* -------- wake interval (docs/decisions/wake-windows.md) -------- */

/* the system timer's schedule; it is changed by the owner (a polkit helper is planned) */
static char *wake_interval(void)
{
	char *out = NULL;
	const char *argv[] = {"systemctl", "show", "--property=TimersCalendar", "--value", "atl-wake.timer", NULL};
	char *text = NULL;
	if (run(argv, &out, NULL) && out && strstr(out, "OnCalendar=")) {
		char *calendar = strstr(out, "OnCalendar=") + strlen("OnCalendar=");
		char *end = strpbrk(calendar, " ;\n");
		if (end)
			*end = '\0';
		const char *slash = strstr(calendar, "0/");
		text = slash ? g_strdup_printf("Every %s minutes", slash + 2) : g_strdup(calendar);
	}
	g_free(out);
	return text ? text : g_strdup("Off (the wake timer is not installed)");
}

/* -------- apps -------- */

static void installer_toggled(AdwSwitchRow *row, GParamSpec *pspec, struct android_apps *page)
{
	const char *id = g_object_get_data(G_OBJECT(row), "app-id");
	gboolean yes = adw_switch_row_get_active(row);
	char *arg = g_strdup_printf(yes ? "--talk-name=%s" : "--no-talk-name=%s", INSTALLER_NAME);
	const char *argv[] = {"flatpak", "override", "--user", arg, id, NULL};
	toast(page, run(argv, NULL, NULL) ? "Applies when the app starts" : "Could not change it");
	g_free(arg);
}

static void app_activated(AdwActionRow *row, struct android_apps *page)
{
	const char *id = g_object_get_data(G_OBJECT(row), "app-id");
	const char *argv[] = {page->prgname, "--app-info", id + strlen(APP_PREFIX), NULL};
	g_spawn_async(NULL, (char **)argv, NULL, G_SPAWN_SEARCH_PATH, NULL, NULL, NULL, NULL);
}

static void close_window(GtkWindow *window, struct android_apps *page)
{
	g_application_release(G_APPLICATION(page->app));
}

void android_apps_show(GtkApplication *app, const char *prgname)
{
	struct android_apps *page = g_new0(struct android_apps, 1);
	page->app = app;
	page->prgname = g_strdup(prgname);

	adw_init();
	g_application_hold(G_APPLICATION(app));
	page->window = GTK_WINDOW(adw_application_window_new(app));
	gtk_window_set_title(page->window, "Android Apps");
	gtk_window_set_default_size(page->window, 400, 640);
	g_signal_connect(page->window, "destroy", G_CALLBACK(close_window), page);

	GtkWidget *toolbar_view = adw_toolbar_view_new();
	adw_toolbar_view_add_top_bar(ADW_TOOLBAR_VIEW(toolbar_view), adw_header_bar_new());
	GtkWidget *prefs = adw_preferences_page_new();

	/* all apps */
	GtkWidget *general = adw_preferences_group_new();
	GtkWidget *navigation = adw_combo_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(navigation), "Back Navigation");
	const char *navigation_labels[] = {"Button and Swipe", "Back Button", "Swipe from the Edge", NULL};
	adw_combo_row_set_model(ADW_COMBO_ROW(navigation), G_LIST_MODEL(gtk_string_list_new(navigation_labels)));
	enum atl_navigation mode = atl_navigation_mode();
	adw_combo_row_set_selected(ADW_COMBO_ROW(navigation), mode == ATL_NAVIGATION_BUTTONS ? 1 : mode == ATL_NAVIGATION_GESTURE ? 2 : 0);
	g_signal_connect(navigation, "notify::selected", G_CALLBACK(navigation_selected), page);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(general), navigation);

	page->push_row = ADW_ENTRY_ROW(adw_entry_row_new());
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(page->push_row), "Push Server");
	adw_entry_row_set_show_apply_button(page->push_row, TRUE);
	gtk_editable_set_text(GTK_EDITABLE(page->push_row), "");
	char *push_url = push_server_get();
	gtk_editable_set_text(GTK_EDITABLE(page->push_row), push_url ? push_url : DEFAULT_PUSH_URL);
	g_free(push_url);
	GtkWidget *reset = gtk_button_new_from_icon_name("edit-undo-symbolic");
	gtk_widget_set_valign(reset, GTK_ALIGN_CENTER);
	gtk_widget_add_css_class(reset, "flat");
	gtk_widget_set_tooltip_text(reset, "Use the default server");
	gtk_accessible_update_property(GTK_ACCESSIBLE(reset), GTK_ACCESSIBLE_PROPERTY_LABEL, "Use the default server", -1);
	g_signal_connect(reset, "clicked", G_CALLBACK(push_server_reset), page);
	adw_entry_row_add_suffix(page->push_row, reset);
	g_signal_connect(page->push_row, "apply", G_CALLBACK(push_server_apply), page);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(general), GTK_WIDGET(page->push_row));

	GtkWidget *wake = adw_action_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(wake), "Wake for Background Work");
	char *interval = wake_interval();
	adw_action_row_set_subtitle(ADW_ACTION_ROW(wake), interval);
	g_free(interval);
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(general), wake);

	GtkWidget *signatures = adw_action_row_new();
	adw_preferences_row_set_title(ADW_PREFERENCES_ROW(signatures), "Signature Checks");
	adw_action_row_set_subtitle(ADW_ACTION_ROW(signatures), "Always on: only signed apps, updates only from the same signer");
	adw_preferences_group_add(ADW_PREFERENCES_GROUP(general), signatures);
	adw_preferences_page_add(ADW_PREFERENCES_PAGE(prefs), ADW_PREFERENCES_GROUP(general));

	/* per app */
	GPtrArray *ids = installed_apps();
	GtkWidget *stores = adw_preferences_group_new();
	adw_preferences_group_set_title(ADW_PREFERENCES_GROUP(stores), "May Install Apps");
	GtkWidget *apps = adw_preferences_group_new();
	adw_preferences_group_set_title(ADW_PREFERENCES_GROUP(apps), "Installed Apps");
	gboolean any_store = FALSE;
	for (guint i = 0; i < ids->len; i++) {
		const char *id = g_ptr_array_index(ids, i);
		char *label = app_label(id);
		char *policy = installer_policy(id);
		/* app stores: the APK requested REQUEST_INSTALL_PACKAGES, so make-app-flatpak granted it */
		if (policy) {
			GtkWidget *row = adw_switch_row_new();
			adw_preferences_row_set_title(ADW_PREFERENCES_ROW(row), label);
			adw_switch_row_set_active(ADW_SWITCH_ROW(row), !strcmp(policy, "talk") || !strcmp(policy, "own"));
			g_object_set_data_full(G_OBJECT(row), "app-id", g_strdup(id), g_free);
			g_signal_connect(row, "notify::active", G_CALLBACK(installer_toggled), page);
			adw_preferences_group_add(ADW_PREFERENCES_GROUP(stores), row);
			any_store = TRUE;
		}
		GtkWidget *row = adw_action_row_new();
		adw_preferences_row_set_title(ADW_PREFERENCES_ROW(row), label);
		adw_action_row_set_subtitle(ADW_ACTION_ROW(row), id + strlen(APP_PREFIX));
		adw_action_row_add_suffix(ADW_ACTION_ROW(row), gtk_image_new_from_icon_name("go-next-symbolic"));
		gtk_list_box_row_set_activatable(GTK_LIST_BOX_ROW(row), TRUE);
		g_object_set_data_full(G_OBJECT(row), "app-id", g_strdup(id), g_free);
		g_signal_connect(row, "activated", G_CALLBACK(app_activated), page);
		adw_preferences_group_add(ADW_PREFERENCES_GROUP(apps), row);
		g_free(policy);
		g_free(label);
	}
	if (any_store)
		adw_preferences_page_add(ADW_PREFERENCES_PAGE(prefs), ADW_PREFERENCES_GROUP(stores));
	if (ids->len == 0)
		adw_preferences_group_set_description(ADW_PREFERENCES_GROUP(apps), "No Android apps installed");
	adw_preferences_page_add(ADW_PREFERENCES_PAGE(prefs), ADW_PREFERENCES_GROUP(apps));
	g_ptr_array_unref(ids);

	page->toasts = ADW_TOAST_OVERLAY(adw_toast_overlay_new());
	adw_toast_overlay_set_child(page->toasts, prefs);
	adw_toolbar_view_set_content(ADW_TOOLBAR_VIEW(toolbar_view), GTK_WIDGET(page->toasts));
	adw_application_window_set_content(ADW_APPLICATION_WINDOW(page->window), toolbar_view);
	gtk_window_present(page->window);
}
