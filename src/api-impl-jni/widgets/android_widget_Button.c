#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "WrapperWidget.h"

#include "../generated_headers/android_widget_Button.h"

static GtkLabel *box_get_label(JNIEnv *env, GtkWidget *box)
{
	GtkWidget *label = gtk_widget_get_last_child(GTK_WIDGET(box));
	if (!GTK_IS_LABEL(label))
		label = gtk_widget_get_prev_sibling(label);
	return GTK_LABEL(label);
}

JNIEXPORT jlong JNICALL Java_android_widget_Button_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	const char *text = attribute_set_get_string(env, attrs, "text", NULL);

	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *button = gtk_button_new();
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
	gtk_widget_set_halign(box, GTK_ALIGN_CENTER); // the gravity of Android's button styles
	gtk_button_set_child(GTK_BUTTON(button), box);
	gtk_box_append(GTK_BOX(box), gtk_label_new(text));
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), button);
	wrapper_widget_set_jobject(WRAPPER_WIDGET(wrapper), env, this);

	return _INTPTR(button);
}

JNIEXPORT void JNICALL Java_android_widget_Button_native_1setText(JNIEnv *env, jobject this, jlong widget_ptr, jobject text)
{
	GtkButton *button = GTK_BUTTON(_PTR(widget_ptr));

	const char *nativeText = ((*env)->GetStringUTFChars(env, text, NULL));
	atl_safe_gtk_label_set_text(box_get_label(env, gtk_button_get_child(button)), nativeText);
	((*env)->ReleaseStringUTFChars(env, text, nativeText));
}

JNIEXPORT void JNICALL Java_android_widget_Button_setTextSize(JNIEnv *env, jobject this, jfloat size)
{
	GtkButton *button = GTK_BUTTON(_PTR(_GET_LONG_FIELD(this, "widget")));
	GtkLabel *label = box_get_label(env, gtk_button_get_child(button));
	PangoAttrList *attrs = pango_attr_list_new();
	pango_attr_list_insert(attrs, pango_attr_size_new_absolute(size * PANGO_SCALE));
	gtk_label_set_attributes(label, attrs);
	pango_attr_list_unref(attrs);
}

JNIEXPORT void JNICALL Java_android_widget_Button_setCompoundDrawablePadding(JNIEnv *env, jobject this, jint pad)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(_GET_LONG_FIELD(this, "widget")));
	if (!GTK_IS_BUTTON(widget)) // CompoundButton
		return;
	gtk_box_set_spacing(GTK_BOX(gtk_button_get_child(GTK_BUTTON(widget))), pad);
}

static void clicked_cb(GtkWidget *button, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(button));

	J__View__performClick(env, wrapper->jobj);

	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

JNIEXPORT void JNICALL Java_android_widget_Button_nativeSetOnClickListener(JNIEnv *env, jobject this, jlong widget_ptr)
{
	GtkWidget *button = GTK_WIDGET(_PTR(widget_ptr));
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(button));
	wrapper_widget_consume_touch_events(wrapper); // Android button consumes touch events
	g_signal_handlers_disconnect_matched(button, G_SIGNAL_MATCH_FUNC, 0, 0, NULL, clicked_cb, NULL);

	g_signal_connect(button, "clicked", G_CALLBACK(clicked_cb), NULL);
}

JNIEXPORT jobject JNICALL Java_android_widget_Button_getText(JNIEnv *env, jobject this)
{
	GtkButton *button = GTK_BUTTON(_PTR(_GET_LONG_FIELD(this, "widget")));
	return (*env)->NewStringUTF(env, gtk_label_get_text(box_get_label(env, gtk_button_get_child(button))));
}

JNIEXPORT void JNICALL Java_android_widget_Button_native_1setCompoundDrawables(JNIEnv *env, jobject this, jlong widget_ptr, jlong paintable_ptr)
{
	if (!GTK_IS_BUTTON(_PTR(widget_ptr))) // CompoundButton
		return;
	GtkButton *button = GTK_BUTTON(_PTR(widget_ptr));
	GdkPaintable *paintable = GDK_PAINTABLE(_PTR(paintable_ptr));
	GtkWidget *box = gtk_button_get_child(button);
	GtkWidget *picture = gtk_widget_get_first_child(box);
	if (GTK_IS_PICTURE(picture)) {
		gtk_picture_set_paintable(GTK_PICTURE(picture), paintable);
	} else if (paintable) {
		picture = gtk_picture_new_for_paintable(paintable);
		gtk_widget_insert_after(picture, box, NULL);
	}
}
