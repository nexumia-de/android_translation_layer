#ifndef ATL_NAVIGATION_H
#define ATL_NAVIGATION_H

#include <gtk/gtk.h>

/* on-screen means of going back, see docs/navigation.md; keys always work */
enum atl_navigation {
	ATL_NAVIGATION_BUTTONS = 1 << 0, /* the header bar's back button */
	ATL_NAVIGATION_GESTURE = 1 << 1, /* a swipe from the left or right edge */
	ATL_NAVIGATION_BOTH = ATL_NAVIGATION_BUTTONS | ATL_NAVIGATION_GESTURE,
};

/* the user's choice: ATL_NAVIGATION=buttons|gesture|both, else the "Android apps" page's setting,
 * default both */
enum atl_navigation atl_navigation_mode(void);
/* saves the page's setting (buttons, gesture or both) */
gboolean atl_navigation_set_mode(const char *mode, GError **error);

/* back keys on the window, the edge swipe on its content (the dialog
 * overlay), and the back button's visibility */
void navigation_setup(GtkWindow *window, GtkWidget *back_button);

#endif
