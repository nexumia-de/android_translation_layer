#ifndef ATL_DIALOG_H
#define ATL_DIALOG_H

#include <gtk/gtk.h>

/* puts the dialog's content (replacing earlier content) into the dialog's layer */
void atl_dialog_set_child(GtkWidget *dialog, GtkWidget *child);
/* a label that shows the dialog's title, e.g. in an AlertDialog */
void atl_dialog_set_title_label(GtkWidget *dialog, GtkLabel *label);
/* the Android gravity of the content, and the dim amount of the scrim (0 for none) */
void atl_dialog_set_gravity(GtkWidget *dialog, int gravity);
void atl_dialog_set_dim(GtkWidget *dialog, float dim_amount);
gboolean atl_is_dialog(GtkWidget *widget);
/* the topmost dialog showing in the main window, or NULL */
GtkWidget *atl_dialog_get_top(void);

#endif
