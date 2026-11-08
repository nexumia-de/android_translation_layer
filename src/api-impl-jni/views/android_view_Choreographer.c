#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_view_Choreographer.h"

extern GtkWidget *window;

/* Frames of the main thread's Choreographer follow GTK's frame clock, which
 * ticks at the display rate while the window is shown and not at all while
 * the compositor sends no frame callbacks (window covered, screen off), as
 * Android's Choreographer follows vsync. One tick callback at a time; it
 * stays while the Choreographer asks for the next frame. */
static guint tick_id = 0;

static gboolean on_frame_clock_tick(GtkWidget *widget, GdkFrameClock *clock, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	/* GDK's frame time is CLOCK_MONOTONIC in µs, like System.nanoTime() in ns */
	jboolean again = J__Choreographer__internalOnFrameClock(env, gdk_frame_clock_get_frame_time(clock) * 1000);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
	return again ? G_SOURCE_CONTINUE : G_SOURCE_REMOVE;
}

static void on_tick_removed(gpointer user_data)
{
	tick_id = 0;
}

/* returns false when the frame clock cannot be used (no window shown yet, or
 * ATL_FRAME_TIMER set); the caller then falls back to a timer */
JNIEXPORT jboolean JNICALL Java_android_view_Choreographer_native_1requestFrame(JNIEnv *env, jclass klass)
{
	if (tick_id)
		return true;
	if (!window || !gtk_widget_get_mapped(window) || getenv("ATL_FRAME_TIMER"))
		return false;
	tick_id = gtk_widget_add_tick_callback(window, on_frame_clock_tick, NULL, on_tick_removed);
	return true;
}
