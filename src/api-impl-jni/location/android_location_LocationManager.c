#include <libportal/portal.h>
#include <unistd.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_location_LocationManager.h"

static XdpPortal *portal = NULL;
/* main thread only. libportal's monitor_stop does nothing until the
 * CreateSession reply arrived, so a stop that comes while starting is kept */
static gboolean starting = FALSE;
static gboolean stop_pending = FALSE;

static void location_updated(
    XdpPortal *self,
    gdouble latitude,
    gdouble longitude,
    gdouble altitude,
    gdouble accuracy,
    gdouble speed,
    gdouble heading,
    gchar *description,
    gint64 timestamp_s,
    gint64 timestamp_us, /* libportal calls it timestamp_ms, but the portal sends microseconds */
    gpointer user_data)
{
	JNIEnv *env;
	(*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6);
	jlong timestamp = timestamp_s * 1000 + timestamp_us / 1000;
	J__LocationManager__locationUpdated(env, latitude, longitude, altitude, accuracy, speed, heading, timestamp);
}

static void monitor_started(GObject *source, GAsyncResult *result, gpointer user_data)
{
	g_autoptr(GError) error = NULL;
	gboolean ok = xdp_portal_location_monitor_start_finish(XDP_PORTAL(source), result, &error);
	starting = FALSE;
	if (!ok) {
		g_warning("LocationManager: location portal refused or failed: %s", error ? error->message : "unknown");
		JNIEnv *env;
		(*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6);
		J__LocationManager__locationDenied(env);
	}
	if (stop_pending) {
		stop_pending = FALSE;
		xdp_portal_location_monitor_stop(XDP_PORTAL(source));
	}
}

/* libportal delivers signals in the main context it was called from, so always
 * call it on the main thread, which runs the GLib main loop */
static gboolean start_on_main(gpointer data)
{
	XdpLocationAccuracy accuracy = GPOINTER_TO_INT(data);
	if (!portal) {
		portal = xdp_portal_new();
		g_signal_connect(portal, "location-updated", G_CALLBACK(location_updated), NULL);
	}
	if (starting) { /* start, stop, start again before the portal answered */
		stop_pending = FALSE;
		return G_SOURCE_REMOVE;
	}
	starting = TRUE;
	xdp_portal_location_monitor_start(portal, NULL, 0, 0, accuracy, XDP_LOCATION_MONITOR_FLAG_NONE, NULL, monitor_started, NULL);
	return G_SOURCE_REMOVE;
}

static gboolean stop_on_main(gpointer data)
{
	if (starting)
		stop_pending = TRUE;
	else if (portal)
		xdp_portal_location_monitor_stop(portal);
	return G_SOURCE_REMOVE;
}

JNIEXPORT jboolean JNICALL Java_android_location_LocationManager_nativeStart(JNIEnv *env, jclass, jint accuracy)
{
	if (!getenv("ATL_UGLY_ENABLE_LOCATION") && access("/.flatpak-info", F_OK)) {
		// Outside a sandbox the portal cannot tell apps apart, so location is
		// prohibited there unless ATL_UGLY_ENABLE_LOCATION is set. In a Flatpak
		// the location portal asks the user and remembers the answer per app.
		return JNI_FALSE;
	}
	g_main_context_invoke(NULL, start_on_main, GINT_TO_POINTER(accuracy));
	return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_android_location_LocationManager_nativeStop(JNIEnv *env, jclass)
{
	g_main_context_invoke(NULL, stop_on_main, NULL);
}
