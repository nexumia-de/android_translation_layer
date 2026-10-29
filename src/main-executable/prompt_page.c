#include <adwaita.h>

#include "prompt_page.h"

#define ICON_SIZE 128

struct buttons_data {
	struct prompt_page *page;
	prompt_page_callback on_cancel;
	prompt_page_callback on_action;
	gpointer user_data;
};

struct prompt_page *prompt_page_new(GtkApplication *app, const char *window_title, GdkPaintable *icon, const char *name,
                                    const char *details, gboolean adwaita_style)
{
	if (adwaita_style)
		adw_init();
	struct prompt_page *page = g_new0(struct prompt_page, 1);
	/* its own window: a dialog needs a parent, and without one it was put in a corner; an
	 * AdwApplicationWindow because GtkWindow would add a title bar of its own */
	page->window = GTK_WINDOW(adw_application_window_new(app));
	gtk_window_set_title(page->window, window_title);
	gtk_window_set_default_size(page->window, 400, 560);

	GtkWidget *header_bar = adw_header_bar_new();
	GtkWidget *toolbar_view = adw_toolbar_view_new();
	adw_toolbar_view_add_top_bar(ADW_TOOLBAR_VIEW(toolbar_view), header_bar);

	page->status = adw_status_page_new();
	if (icon)
		adw_status_page_set_paintable(ADW_STATUS_PAGE(page->status), icon);
	else
		adw_status_page_set_icon_name(ADW_STATUS_PAGE(page->status), "application-x-executable");
	adw_status_page_set_title(ADW_STATUS_PAGE(page->status), name);
	adw_status_page_set_description(ADW_STATUS_PAGE(page->status), details);

	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_VERTICAL, 18);
	page->message = gtk_label_new(NULL);
	gtk_label_set_wrap(GTK_LABEL(page->message), TRUE);
	gtk_label_set_justify(GTK_LABEL(page->message), GTK_JUSTIFY_CENTER);
	gtk_box_append(GTK_BOX(box), page->message);
	page->spinner = adw_spinner_new();
	gtk_widget_set_size_request(page->spinner, 32, 32);
	gtk_widget_set_halign(page->spinner, GTK_ALIGN_CENTER);
	gtk_widget_set_visible(page->spinner, FALSE);
	gtk_box_append(GTK_BOX(box), page->spinner);
	adw_status_page_set_child(ADW_STATUS_PAGE(page->status), box);
	adw_toolbar_view_set_content(ADW_TOOLBAR_VIEW(toolbar_view), page->status);

	page->buttons = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 12);
	gtk_box_set_homogeneous(GTK_BOX(page->buttons), TRUE);
	gtk_widget_set_margin_start(page->buttons, 18);
	gtk_widget_set_margin_end(page->buttons, 18);
	gtk_widget_set_margin_top(page->buttons, 12);
	gtk_widget_set_margin_bottom(page->buttons, 18);
	GtkWidget *clamp = adw_clamp_new();
	adw_clamp_set_maximum_size(ADW_CLAMP(clamp), 400);
	adw_clamp_set_child(ADW_CLAMP(clamp), page->buttons);
	adw_toolbar_view_add_bottom_bar(ADW_TOOLBAR_VIEW(toolbar_view), clamp);

	adw_application_window_set_content(ADW_APPLICATION_WINDOW(page->window), toolbar_view);
	return page;
}

void prompt_page_set_icon(struct prompt_page *page, GdkPaintable *icon)
{
	adw_status_page_set_paintable(ADW_STATUS_PAGE(page->status), icon);
}

void prompt_page_set_message(struct prompt_page *page, const char *message)
{
	gtk_label_set_text(GTK_LABEL(page->message), message);
	gtk_widget_set_visible(page->message, message && *message);
}

static void cancel_clicked(GtkButton *button, struct buttons_data *data)
{
	data->on_cancel(data->page, data->user_data);
}

static void action_clicked(GtkButton *button, struct buttons_data *data)
{
	data->on_action(data->page, data->user_data);
}

static gboolean close_request(GtkWindow *window, struct buttons_data *data)
{
	if (!data->page->busy)
		data->on_cancel(data->page, data->user_data);
	return TRUE; // on_cancel decides
}

static gboolean escape_pressed(GtkWidget *widget, GVariant *args, struct buttons_data *data)
{
	if (!data->page->busy)
		data->on_cancel(data->page, data->user_data);
	return TRUE;
}

static GtkWidget *pill_button(const char *label)
{
	GtkWidget *button = gtk_button_new_with_mnemonic(label);
	gtk_widget_add_css_class(button, "pill");
	gtk_widget_set_hexpand(button, TRUE);
	return button;
}

void prompt_page_set_buttons(struct prompt_page *page, const char *cancel_label, const char *action_label, gboolean destructive,
                             prompt_page_callback on_cancel, prompt_page_callback on_action, gpointer user_data)
{
	page->busy = FALSE;
	gtk_widget_set_visible(page->spinner, FALSE);
	GtkWidget *child;
	while ((child = gtk_widget_get_first_child(page->buttons)))
		gtk_box_remove(GTK_BOX(page->buttons), child);
	gtk_widget_set_visible(page->buttons, TRUE);

	/* the first page's handlers stay connected, they look up the current data */
	struct buttons_data *data = g_object_get_data(G_OBJECT(page->window), "prompt-buttons");
	if (!data) {
		data = g_new0(struct buttons_data, 1);
		data->page = page;
		g_object_set_data_full(G_OBJECT(page->window), "prompt-buttons", data, g_free);
		g_signal_connect(page->window, "close-request", G_CALLBACK(close_request), data);
		GtkEventController *shortcuts = gtk_shortcut_controller_new();
		gtk_shortcut_controller_add_shortcut(GTK_SHORTCUT_CONTROLLER(shortcuts),
		                                     gtk_shortcut_new(gtk_keyval_trigger_new(GDK_KEY_Escape, 0),
		                                                      gtk_callback_action_new((GtkShortcutFunc)escape_pressed, data, NULL)));
		gtk_widget_add_controller(GTK_WIDGET(page->window), shortcuts);
	}
	data->on_cancel = on_cancel;
	data->on_action = on_action;
	data->user_data = user_data;

	GtkWidget *cancel = pill_button(cancel_label);
	g_signal_connect(cancel, "clicked", G_CALLBACK(cancel_clicked), data);
	gtk_box_append(GTK_BOX(page->buttons), cancel);
	if (action_label) {
		GtkWidget *action = pill_button(action_label);
		gtk_widget_add_css_class(action, destructive ? "destructive-action" : "suggested-action");
		g_signal_connect(action, "clicked", G_CALLBACK(action_clicked), data);
		gtk_box_append(GTK_BOX(page->buttons), action);
		/* Enter does what the user came for, unless it destroys something */
		if (!destructive)
			gtk_window_set_default_widget(page->window, action);
	}
}

void prompt_page_set_busy(struct prompt_page *page, const char *message)
{
	page->busy = TRUE;
	prompt_page_set_message(page, message);
	gtk_widget_set_visible(page->buttons, FALSE);
	gtk_widget_set_visible(page->spinner, TRUE);
}

GdkPaintable *prompt_page_icon_from_file(GtkWidget *widget, const char *path)
{
	GtkIconTheme *theme = gtk_icon_theme_get_for_display(gtk_widget_get_display(widget));
	GFile *file = g_file_new_for_path(path);
	GIcon *icon = g_file_icon_new(file);
	GtkIconPaintable *paintable = gtk_icon_theme_lookup_by_gicon(theme, icon, ICON_SIZE, gtk_widget_get_scale_factor(widget),
	                                                             gtk_widget_get_direction(widget), 0);
	g_object_unref(icon);
	g_object_unref(file);
	return GDK_PAINTABLE(paintable);
}
