#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "../views/AndroidLayout.h"
#include "../widgets/WrapperWidget.h"

#include "../generated_headers/android_view_View.h"
#include "../generated_headers/android_view_ViewGroup.h"

JNIEXPORT void JNICALL Java_android_view_ViewGroup_native_1addView(JNIEnv *env, jobject this, jlong widget, jlong child, jint index, jobject layout_params)
{
	if (layout_params) {
		/*
		GtkWidget *_child = gtk_widget_get_parent(GTK_WIDGET(_PTR(child)));
		jint child_width = -1;
		jint child_height = -1;

		jint child_width = _GET_INT_FIELD(layout_params, "width");
		jint child_height = _GET_INT_FIELD(layout_params, "height");

		jint child_gravity = _GET_INT_FIELD(layout_params, "gravity");

		if(child_width > 0)
			g_object_set(G_OBJECT(_child), "width-request", child_width, NULL);
		if(child_height > 0)
			g_object_set(G_OBJECT(_child), "height-request", child_height, NULL);

		if(child_gravity != -1) {
			printf(":::-: setting child gravity: %d", child_gravity);
			Java_android_view_View_setGravity(env, child, child_gravity);
		}*/
	}
	GtkWidget *parent = _PTR(widget);
	GtkWidget *iter = gtk_widget_get_first_child(parent);
	for (int i = 0; i < index; i++) {
		iter = gtk_widget_get_next_sibling(iter);
		if (iter == NULL)
			break;
	}

	gtk_widget_insert_before(gtk_widget_get_parent(GTK_WIDGET(_PTR(child))), parent, iter);
}

JNIEXPORT void JNICALL Java_android_view_ViewGroup_native_1removeView(JNIEnv *env, jobject this, jlong widget, jlong child)
{
	gtk_widget_unparent(gtk_widget_get_parent(GTK_WIDGET(_PTR(child))));
}

JNIEXPORT void JNICALL Java_android_view_ViewGroup_native_1drawChildren(JNIEnv *env, jobject this, jlong widget_ptr, jlong snapshot_ptr)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(_PTR(widget_ptr))));
	GdkSnapshot *snapshot = GDK_SNAPSHOT(_PTR(snapshot_ptr));
	gtk_widget_snapshot_child(&wrapper->parent_instance, wrapper->child, snapshot);
}

JNIEXPORT void JNICALL Java_android_view_ViewGroup_native_1drawChild(JNIEnv *env, jobject this, jlong widget_ptr, jlong child_ptr, jlong snapshot_ptr)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(widget_ptr));
	GtkWidget *child = gtk_widget_get_parent(GTK_WIDGET(_PTR(child_ptr)));
	GdkSnapshot *snapshot = GDK_SNAPSHOT(_PTR(snapshot_ptr));
	gtk_widget_queue_draw(child); // FIXME: why didn't compose UI invalidate the child?
	gtk_widget_snapshot_child(widget, child, snapshot);
}

JNIEXPORT void JNICALL Java_android_view_ViewGroup_native_1offsetRect(JNIEnv *env, jobject this, jlong from_widget_ptr, jlong to_widget_ptr, jobject rect)
{
	GtkWidget *from_widget = gtk_widget_get_parent(GTK_WIDGET(_PTR(from_widget_ptr)));
	GtkWidget *to_widget = gtk_widget_get_parent(GTK_WIDGET(_PTR(to_widget_ptr)));

	graphene_point_t point_in = {_GET_INT_FIELD(rect, "left"), _GET_INT_FIELD(rect, "top")};
	graphene_point_t point_out;
	if (gtk_widget_compute_point(from_widget, to_widget, &point_in, &point_out)) {
		_SET_INT_FIELD(rect, "left", point_out.x);
		_SET_INT_FIELD(rect, "top", point_out.y);
		_SET_INT_FIELD(rect, "right", _GET_INT_FIELD(rect, "right") + point_out.x - point_in.x);
		_SET_INT_FIELD(rect, "bottom", _GET_INT_FIELD(rect, "bottom") + point_out.y - point_in.y);
	}
}
