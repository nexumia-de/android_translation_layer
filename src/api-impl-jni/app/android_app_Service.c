#include <gio/gio.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_app_Service.h"

/* the process ends when the GApplication has no windows and no holds left */
static gboolean hold_on_main(gpointer data)
{
	GApplication *app = g_application_get_default();
	if (!app)
		return G_SOURCE_REMOVE;
	if (GPOINTER_TO_INT(data))
		g_application_hold(app);
	else
		g_application_release(app);
	return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_app_Service_nativeHold(JNIEnv *env, jclass, jboolean hold)
{
	g_main_context_invoke(NULL, hold_on_main, GINT_TO_POINTER(hold));
}
