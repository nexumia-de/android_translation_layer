#include <libportal/portal.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_location_LocationManager.h"

static XdpPortal *portal = NULL;

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
    gint64 timestamp_ms,
    JavaVM *jvm)
{
	JNIEnv *env;
	(*jvm)->GetEnv(jvm, (void **)&env, JNI_VERSION_1_6);
	/* the portal sends seconds and microseconds of the fix time */
	jlong timestamp = timestamp_s * 1000 + timestamp_ms / 1000;
	J__LocationManager__locationUpdated(env, latitude, longitude, altitude, accuracy, speed, heading, timestamp);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static void monitor_started(GObject *source, GAsyncResult *result, gpointer user_data)
{
	g_autoptr(GError) error = NULL;
	if (!xdp_portal_location_monitor_start_finish(XDP_PORTAL(source), result, &error))
		g_warning("LocationManager: cannot start the location portal: %s", error->message);
}

JNIEXPORT void JNICALL Java_android_location_LocationManager_nativeStartMonitor(JNIEnv *env, jclass)
{
	if (!getenv("ATL_UGLY_ENABLE_LOCATION")) {
		// Location access is prohibited by default until sanboxing is implemented.
		// Set ATL_UGLY_ENABLE_LOCATION environment variable to enable it.
		return;
	}

	if (!portal) {
		portal = xdp_portal_new();
		JavaVM *jvm;
		(*env)->GetJavaVM(env, &jvm);
		g_signal_connect(portal, "location-updated", G_CALLBACK(location_updated), jvm);
	}

	xdp_portal_location_monitor_start(portal, NULL, 0, 0, XDP_LOCATION_ACCURACY_EXACT, XDP_LOCATION_MONITOR_FLAG_NONE, NULL, monitor_started, NULL);
}

JNIEXPORT void JNICALL Java_android_location_LocationManager_nativeStopMonitor(JNIEnv *env, jclass)
{
	if (portal)
		xdp_portal_location_monitor_stop(portal);
}
