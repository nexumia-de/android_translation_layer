#include <gtk/gtk.h>

#include "../defines.h"
#include "../generated_headers/android_view_WindowManagerImpl.h"
#include "gdk/gdk.h"
#include "glib-object.h"

#define FIRST_SUB_WINDOW 1000
#define LAST_SUB_WINDOW  1999

#define MATCH_PARENT -1

/*
 * A view added with WindowManager.addView (Compose's Popup) is a layer in the main window's
 * overlay, like a dialog (see android_app_Dialog.c), at x and y of the window: a GtkPopover
 * drew an arrow and placed the view below x and y instead of at them, so Compose's docked
 * search bar showed as a floating box under the search field it should cover.
 */

extern GtkWindow *window;

static GtkOverlay *window_overlay(void)
{
	return GTK_OVERLAY(g_object_get_data(G_OBJECT(window), "dialog-overlay"));
}

static void place_view(GtkWidget *wrapper, int x, int y, int width, int height)
{
	/* x and y are in the coordinates of the window's surface (see View.getGlobalVisibleRect) */
	graphene_point_t origin;
	double off_x = 0, off_y = 0;
	gtk_native_get_surface_transform(GTK_NATIVE(window), &off_x, &off_y);
	if (gtk_widget_compute_point(GTK_WIDGET(window_overlay()), GTK_WIDGET(window), &GRAPHENE_POINT_INIT(0, 0), &origin)) {
		x -= origin.x + off_x;
		y -= origin.y + off_y;
	}
	gtk_widget_set_halign(wrapper, width == MATCH_PARENT ? GTK_ALIGN_FILL : GTK_ALIGN_START);
	gtk_widget_set_valign(wrapper, height == MATCH_PARENT ? GTK_ALIGN_FILL : GTK_ALIGN_START);
	gtk_widget_set_margin_start(wrapper, MAX(x, 0));
	gtk_widget_set_margin_top(wrapper, MAX(y, 0));
}

JNIEXPORT void JNICALL Java_android_view_WindowManagerImpl_native_1addView(JNIEnv *env, jclass clazz, jlong widget_ptr, jint type, jint x, jint y, jint width, jint height)
{
	GtkWidget *wrapper = gtk_widget_get_parent(GTK_WIDGET(_PTR(widget_ptr)));
	if (type < FIRST_SUB_WINDOW || type > LAST_SUB_WINDOW) {
		// TODO: handle toplevel windows properly
		printf("WARNING: non subwindow types not implemented properly in WindowManagerImpl\n");
	}
	place_view(wrapper, x, y, width, height);
	gtk_overlay_add_overlay(window_overlay(), wrapper);
}

JNIEXPORT void JNICALL Java_android_view_WindowManagerImpl_native_1updateViewLayout(JNIEnv *env, jclass clazz, jlong widget_ptr, jint x, jint y, jint width, jint height)
{
	place_view(gtk_widget_get_parent(GTK_WIDGET(_PTR(widget_ptr))), x, y, width, height);
}

JNIEXPORT void JNICALL Java_android_view_WindowManagerImpl_native_1removeView(JNIEnv *env, jclass clazz, jlong widget_ptr)
{
	GtkWidget *wrapper = gtk_widget_get_parent(GTK_WIDGET(_PTR(widget_ptr)));
	if (gtk_widget_get_parent(wrapper) == GTK_WIDGET(window_overlay()))
		gtk_overlay_remove_overlay(window_overlay(), wrapper);
}
