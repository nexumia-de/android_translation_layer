#include <gtk/gtk.h>

#include "android_app_Activity.h"

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_app_Service.h"

extern GtkWidget *window;

/* foreground services holding the GApplication; main thread only */
static int foreground_holds = 0;

gboolean foreground_service_running(void)
{
	return foreground_holds > 0;
}

/* The process ends when the GApplication has no windows and no holds left. While a foreground
 * service runs, closing the window only hides it (main.c, app_exit), so a later launch can show
 * it again; when the last foreground service ends, a hidden window goes too. */
static gboolean hold_on_main(gpointer data)
{
	GApplication *app = g_application_get_default();
	if (!app)
		return G_SOURCE_REMOVE;
	if (GPOINTER_TO_INT(data)) {
		foreground_holds++;
		g_application_hold(app);
	} else {
		foreground_holds--;
		if (foreground_holds == 0 && window && !gtk_widget_get_visible(window)) {
			GtkWidget *hidden = window;
			window = NULL;
			gtk_window_destroy(GTK_WINDOW(hidden));
		}
		g_application_release(app);
	}
	return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_app_Service_nativeHold(JNIEnv *env, jclass, jboolean hold)
{
	g_main_context_invoke(NULL, hold_on_main, GINT_TO_POINTER(hold));
}
