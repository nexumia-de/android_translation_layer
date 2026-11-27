#ifndef ATL_ROTATE_BOX_H
#define ATL_ROTATE_BOX_H

#include <gtk/gtk.h>

/* holds the window's content and, when an activity asks for landscape in a portrait window
 * (a phone that does not rotate the screen), shows it turned by 90 degrees; GTK maps input
 * through the same transform */
#define ATL_TYPE_ROTATE_BOX (atl_rotate_box_get_type())
G_DECLARE_FINAL_TYPE(AtlRotateBox, atl_rotate_box, ATL, ROTATE_BOX, GtkWidget)

GtkWidget *atl_rotate_box_new(GtkWidget *child);
void atl_rotate_box_set_rotated(AtlRotateBox *self, gboolean rotated);
gboolean atl_rotate_box_get_rotated(AtlRotateBox *self);

/* the box of the app window, set by main */
extern AtlRotateBox *atl_window_rotate_box;

/* turns the app window's content and tells the app about its new size (android_content_Context.c) */
void atl_window_set_rotated(gboolean rotated);

#endif
