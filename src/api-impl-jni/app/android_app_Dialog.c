#include <gtk/gtk.h>
#include <jni.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_app_Dialog.h"
#include "dialog.h"

/*
 * A dialog is a layer over the activities in the main window (a GtkOverlay, see main.c), not a
 * window of its own: a separate window got a title bar, and a phone compositor (phoc) maximizes
 * it without drawing the app below, so transparent dialogs showed its accent colour instead. The
 * layer is a GtkOverlay too: a black scrim (its opacity is the Android dim amount) under the
 * content, which is centred unless Window.setGravity() says otherwise.
 */

/* main app window */
extern GtkWindow *window;

static GtkOverlay *dialog_overlay(void)
{
	return GTK_OVERLAY(g_object_get_data(G_OBJECT(window), "dialog-overlay"));
}

static void dialog_dismiss(GtkWidget *dialog)
{
	jobject jobj = g_object_get_data(G_OBJECT(dialog), "dialog-jobject");
	JNIEnv *env = get_jni_env();
	jmethodID dismiss = _METHOD(_CLASS(jobj), "dismiss", "()V");
	(*env)->CallVoidMethod(env, jobj, dismiss);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

/* a tap on the scrim, outside the content */
static void on_scrim_pressed(GtkGestureClick *gesture, int n_press, double x, double y, GtkWidget *dialog)
{
	if (g_object_get_data(G_OBJECT(dialog), "cancel-on-touch-outside"))
		dialog_dismiss(dialog);
}

void atl_dialog_set_child(GtkWidget *dialog, GtkWidget *child)
{
	GtkWidget *old = g_object_get_data(G_OBJECT(dialog), "content");
	if (old == child)
		return;
	if (old)
		gtk_overlay_remove_overlay(GTK_OVERLAY(dialog), old);
	int gravity = GPOINTER_TO_INT(g_object_get_data(G_OBJECT(dialog), "gravity"));
	gtk_overlay_add_overlay(GTK_OVERLAY(dialog), child);
	g_object_set_data(G_OBJECT(dialog), "content", child);
	/* Gravity: CENTER_HORIZONTAL 0x01, LEFT 0x03, RIGHT 0x05, CENTER_VERTICAL 0x10, TOP 0x30, BOTTOM 0x50 */
	int horizontal = gravity & 0x07, vertical = gravity & 0x70;
	gtk_widget_set_halign(child, horizontal == 0x03 ? GTK_ALIGN_START : horizontal == 0x05 ? GTK_ALIGN_END : GTK_ALIGN_CENTER);
	gtk_widget_set_valign(child, vertical == 0x30 ? GTK_ALIGN_START : vertical == 0x50 ? GTK_ALIGN_END : GTK_ALIGN_CENTER);
}

void atl_dialog_set_gravity(GtkWidget *dialog, int gravity)
{
	GtkWidget *child = g_object_get_data(G_OBJECT(dialog), "content");
	g_object_set_data(G_OBJECT(dialog), "gravity", GINT_TO_POINTER(gravity));
	if (child) {
		g_object_set_data(G_OBJECT(dialog), "content", NULL);
		gtk_overlay_remove_overlay(GTK_OVERLAY(dialog), g_object_ref(child));
		atl_dialog_set_child(dialog, child);
		g_object_unref(child);
	}
}

void atl_dialog_set_dim(GtkWidget *dialog, float dim_amount)
{
	gtk_widget_set_opacity(GTK_WIDGET(g_object_get_data(G_OBJECT(dialog), "scrim")), dim_amount);
}

gboolean atl_is_dialog(GtkWidget *widget)
{
	return widget && g_object_get_data(G_OBJECT(widget), "dialog-jobject") != NULL;
}

void atl_dialog_set_title_label(GtkWidget *dialog, GtkLabel *label)
{
	g_object_set_data_full(G_OBJECT(dialog), "title-label", g_object_ref(label), g_object_unref);
}

GtkWidget *atl_dialog_get_top(void)
{
	GtkWidget *top = NULL;
	for (GtkWidget *child = gtk_widget_get_first_child(GTK_WIDGET(dialog_overlay())); child; child = gtk_widget_get_next_sibling(child))
		if (g_object_get_data(G_OBJECT(child), "dialog-jobject") && gtk_widget_get_visible(child))
			top = child;
	return top;
}

JNIEXPORT jlong JNICALL Java_android_app_Dialog_nativeInit(JNIEnv *env, jobject this, jlong decor_view_ptr)
{
	GtkWidget *decor_view = GTK_WIDGET(_PTR(decor_view_ptr));
	GtkWidget *dialog = gtk_overlay_new();
	gtk_widget_add_css_class(dialog, "ATL-dialog");
	GtkWidget *scrim = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
	gtk_widget_add_css_class(scrim, "ATL-dialog-scrim");
	gtk_widget_set_opacity(scrim, 0);
	gtk_overlay_set_child(GTK_OVERLAY(dialog), scrim);
	g_object_set_data(G_OBJECT(dialog), "scrim", scrim);
	GtkGesture *gesture = gtk_gesture_click_new();
	g_signal_connect(gesture, "pressed", G_CALLBACK(on_scrim_pressed), dialog);
	gtk_widget_add_controller(scrim, GTK_EVENT_CONTROLLER(gesture));
	g_object_set_data(G_OBJECT(dialog), "dialog-jobject", _REF(this));
	atl_dialog_set_child(dialog, gtk_widget_get_parent(decor_view));
	return _INTPTR(g_object_ref_sink(dialog));
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetTitle(JNIEnv *env, jobject this, jlong ptr, jstring title)
{
	GtkLabel *label = g_object_get_data(G_OBJECT(_PTR(ptr)), "title-label");
	if (!label)
		return;
	const char *nativeTitle = (*env)->GetStringUTFChars(env, title, NULL);
	gtk_label_set_text(label, nativeTitle);
	gtk_widget_set_visible(GTK_WIDGET(label), *nativeTitle != '\0');
	(*env)->ReleaseStringUTFChars(env, title, nativeTitle);
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetContentView(JNIEnv *env, jobject this, jlong ptr, jlong widget_ptr)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(widget_ptr));
	atl_dialog_set_child(GTK_WIDGET(_PTR(ptr)), gtk_widget_get_parent(widget));
}

void back_button_set_sensitive(bool sensitive);

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeShow(JNIEnv *env, jobject this, jlong ptr)
{
	GtkWidget *dialog = GTK_WIDGET(_PTR(ptr));
	if (!gtk_widget_get_parent(dialog))
		gtk_overlay_add_overlay(dialog_overlay(), dialog);
	gtk_widget_set_visible(dialog, TRUE);
	back_button_set_sensitive(true); // back dismisses the dialog
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeClose(JNIEnv *env, jobject this, jlong ptr)
{
	GtkWidget *dialog = GTK_WIDGET(_PTR(ptr));
	if (gtk_widget_get_parent(dialog))
		gtk_overlay_remove_overlay(dialog_overlay(), dialog);
}

JNIEXPORT jboolean JNICALL Java_android_app_Dialog_nativeIsShowing(JNIEnv *env, jobject this, jlong ptr)
{
	GtkWidget *dialog = GTK_WIDGET(_PTR(ptr));
	return gtk_widget_get_parent(dialog) && gtk_widget_get_visible(dialog);
}

JNIEXPORT void JNICALL Java_android_app_Dialog_nativeSetCanceledOnTouchOutside(JNIEnv *env, jobject this, jlong ptr, jboolean cancel)
{
	g_object_set_data(G_OBJECT(_PTR(ptr)), "cancel-on-touch-outside", GINT_TO_POINTER(cancel));
}
