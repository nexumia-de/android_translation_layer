#ifndef PROMPT_PAGE_H
#define PROMPT_PAGE_H

#include <gtk/gtk.h>

/* a page in its own window, for asking before installing or uninstalling an app: the app's icon,
 * name and details, a message, and the buttons at the bottom, in reach of the thumb on a phone */
struct prompt_page {
	GtkWindow *window;
	GtkWidget *status;
	GtkWidget *message;
	GtkWidget *spinner;
	GtkWidget *buttons;
	GtkWidget *box;
	GtkWidget *action; // the action button of the last prompt_page_set_buttons, or NULL
	gboolean busy;
};

typedef void (*prompt_page_callback)(struct prompt_page *page, gpointer user_data);

/* icon may be NULL for a generic one; adwaita_style loads libadwaita's style sheet, which also
 * restyles the GTK widgets of an app running in this process */
struct prompt_page *prompt_page_new(GtkApplication *app, const char *window_title, GdkPaintable *icon, const char *name,
                                    const char *details, gboolean adwaita_style);
void prompt_page_set_icon(struct prompt_page *page, GdkPaintable *icon);
void prompt_page_set_message(struct prompt_page *page, const char *message);
/* the cancel button, Escape and closing the window call on_cancel; action_label may be NULL for a
 * page with one button */
void prompt_page_set_buttons(struct prompt_page *page, const char *cancel_label, const char *action_label, gboolean destructive,
                             prompt_page_callback on_cancel, prompt_page_callback on_action, gpointer user_data);
/* a spinner instead of the buttons while something runs; the window can't be closed meanwhile */
/* a widget below the message, e.g. a question that must be answered before the action */
void prompt_page_add_child(struct prompt_page *page, GtkWidget *child);
/* a question with two answers, none preselected, below the message; on_answer gets 1 for yes and
 * 0 for no. Returns the question's widget */
GtkWidget *prompt_page_add_question(struct prompt_page *page, const char *title, const char *yes_title, const char *yes_subtitle,
                                    const char *no_title, const char *no_subtitle, void (*on_answer)(int answer, gpointer user_data),
                                    gpointer user_data);
void prompt_page_set_busy(struct prompt_page *page, const char *message);
/* the icon of an image file, e.g. a launcher's Icon= */
GdkPaintable *prompt_page_icon_from_file(GtkWidget *widget, const char *path);

#endif
