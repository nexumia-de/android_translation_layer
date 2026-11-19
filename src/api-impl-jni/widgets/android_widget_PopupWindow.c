#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "WrapperWidget.h"

#include "../generated_headers/android_widget_PopupWindow.h"

JNIEXPORT jlong JNICALL Java_android_widget_PopupWindow_native_1constructor(JNIEnv *env, jobject this)
{
	GtkWidget *popover = g_object_ref(gtk_popover_new());
	gtk_widget_set_name(popover, "PopupWindow");
	/* autohiding works by the widget grabbing events, which is not something apps expect */
	gtk_popover_set_autohide(GTK_POPOVER(popover), false);
	return _INTPTR(popover);
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1setContentView(JNIEnv *env, jobject this, jlong popover_ptr, jlong content_ptr)
{
	WrapperWidget *content = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(_PTR(content_ptr))));
	gtk_popover_set_child(GTK_POPOVER(_PTR(popover_ptr)), GTK_WIDGET(content));
}

static inline void set_offset(GtkPopover *popover, GtkWidget *anchor, int x, int y)
{
	/* FIXME: assumes GTK_POS_BOTTOM */
	gtk_popover_set_offset(popover, x - gtk_widget_get_width(anchor) / 2, y - gtk_widget_get_height(anchor));
	gtk_popover_set_pointing_to(popover, &(GdkRectangle){.x = 0, .y = 0, .width = gtk_widget_get_width(anchor), .height = gtk_widget_get_height(anchor)});
}

static void on_closed_cb(GtkWidget *popover, jobject this)
{
	g_signal_handlers_disconnect_matched(popover, G_SIGNAL_MATCH_FUNC | G_SIGNAL_MATCH_DATA, 0, 0, NULL, on_closed_cb, this);
	JNIEnv *env = get_jni_env();
	J__PopupWindow__performDismiss(env, this);
	_UNREF(this);
	gtk_widget_unparent(popover);
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1showAsDropDown(JNIEnv *env, jobject this, jlong popover_ptr, jlong anchor_ptr, jint x, jint y, jint gravity)
{
	GtkPopover *popover = GTK_POPOVER(_PTR(popover_ptr));
	WrapperWidget *anchor = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(_PTR(anchor_ptr))));

	gtk_widget_insert_before(GTK_WIDGET(popover), GTK_WIDGET(anchor), NULL);
	set_offset(popover, GTK_WIDGET(anchor), x, y);
	if (!gtk_widget_is_visible(GTK_WIDGET(popover)))
		g_signal_connect(popover, "closed", G_CALLBACK(on_closed_cb), _REF(this));
	gtk_popover_present(GTK_POPOVER(popover));
	gtk_popover_popup(popover);
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1setWidth(JNIEnv *env, jobject this, jlong popover_ptr, jint width)
{
	int height;
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	gtk_widget_get_size_request(popover, NULL, &height);
	gtk_widget_set_size_request(popover, width, height);
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1setHeight(JNIEnv *env, jobject this, jlong popover_ptr, jint height)
{
	int width;
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	gtk_widget_get_size_request(popover, &width, NULL);
	gtk_widget_set_size_request(popover, width, height);
}

JNIEXPORT jint JNICALL Java_android_widget_PopupWindow_native_1getWidth(JNIEnv *env, jobject this, jlong popover_ptr)
{
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	GtkRequisition natural_size;
	gtk_widget_get_preferred_size(popover, &natural_size, NULL);
	return natural_size.width;
}

JNIEXPORT jint JNICALL Java_android_widget_PopupWindow_native_1getHeight(JNIEnv *env, jobject this, jlong popover_ptr)
{
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	GtkRequisition natural_size;
	gtk_widget_get_preferred_size(popover, &natural_size, NULL);
	return natural_size.height;
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1setTouchable(JNIEnv *env, jobject this, jlong popover_ptr, jboolean touchable)
{
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	gtk_widget_set_sensitive(popover, touchable);
}

JNIEXPORT jboolean JNICALL Java_android_widget_PopupWindow_native_1isTouchable(JNIEnv *env, jobject this, jlong popover_ptr)
{
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	return gtk_widget_is_sensitive(popover);
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1setTouchModal(JNIEnv *env, jobject this, jlong popover_ptr, jboolean touch_modal)
{
	GtkPopover *popover = GTK_POPOVER(_PTR(popover_ptr));
	/* FIXME: we should only add grab (not autohide), however we need to remove it again in umap;
	 * GtkPopover is not final, so we should subclass it and check whether it's modal in map/unmap
	 * to add/remove grab, which is the desired part of what GtkPopover does with autohide enabled */
	gtk_popover_set_autohide(popover, touch_modal);
}

extern GtkWidget *window;

static void outside_controller_destroy(gpointer controller)
{
	gtk_widget_remove_controller(window, GTK_EVENT_CONTROLLER(controller));
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1setOutsideTouchable(JNIEnv *env, jobject this, jlong popover_ptr, jboolean outside_touchable)
{
	GtkWidget *popover = GTK_WIDGET(_PTR(popover_ptr));
	GtkEventController *controller = g_object_get_data(G_OBJECT(popover), "atl-outside-controller");

	if (outside_touchable && !controller) {
		controller = GTK_EVENT_CONTROLLER(gtk_gesture_click_new());
		gtk_event_controller_set_propagation_phase(controller, GTK_PHASE_CAPTURE);
		g_signal_connect_swapped(controller, "pressed", G_CALLBACK(gtk_popover_popdown), popover);
		g_object_set_data_full(G_OBJECT(popover), "atl-outside-controller", controller, outside_controller_destroy);
		gtk_widget_add_controller(window, controller);
	} else if (!outside_touchable && controller) {
		g_object_set_data(G_OBJECT(popover), "atl-outside-controller", NULL);
	}
}

JNIEXPORT jboolean JNICALL Java_android_widget_PopupWindow_native_1isShowing(JNIEnv *env, jobject this, jlong popover_ptr)
{
	return gtk_widget_get_visible(GTK_WIDGET(_PTR(popover_ptr)));
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1dismiss(JNIEnv *env, jobject this, jlong popover_ptr)
{
	gtk_popover_popdown(GTK_POPOVER(_PTR(popover_ptr)));
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1update(JNIEnv *env, jobject this, jlong popover_ptr, jlong anchor_ptr, jint x, jint y, jint width, jint height)
{
	GtkPopover *popover = GTK_POPOVER(_PTR(popover_ptr));
	WrapperWidget *anchor = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(_PTR(anchor_ptr))));
	gtk_widget_set_size_request(GTK_WIDGET(popover), width, height);
	set_offset(popover, GTK_WIDGET(anchor), x, y);
	gtk_widget_insert_before(GTK_WIDGET(popover), GTK_WIDGET(anchor), NULL);
	if (!gtk_widget_is_visible(GTK_WIDGET(popover)))
		g_signal_connect(popover, "closed", G_CALLBACK(on_closed_cb), _REF(this));
	gtk_popover_present(GTK_POPOVER(popover));
	gtk_popover_popup(popover);
}

JNIEXPORT void JNICALL Java_android_widget_PopupWindow_native_1destructor(JNIEnv *env, jobject this, jlong popover_ptr)
{
	g_object_unref(_PTR(popover_ptr));
}
