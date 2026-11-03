#ifndef ATL_NAVIGATION_H
#define ATL_NAVIGATION_H

#include <gtk/gtk.h>

/* on-screen means of going back, see docs/navigation.md; keys always work */
enum atl_navigation {
	ATL_NAVIGATION_BUTTONS = 1 << 0, /* the header bar's back button */
	ATL_NAVIGATION_GESTURE = 1 << 1, /* a swipe from the left or right edge */
	ATL_NAVIGATION_BOTH = ATL_NAVIGATION_BUTTONS | ATL_NAVIGATION_GESTURE,
};

/* the user's choice; today ATL_NAVIGATION=buttons|gesture|both, default both */
enum atl_navigation atl_navigation_mode(void);

/* back keys on the window, the edge swipe on its content (the dialog
 * overlay), and the back button's visibility */
void navigation_setup(GtkWindow *window, GtkWidget *back_button);

#endif
