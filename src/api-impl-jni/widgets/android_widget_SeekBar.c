#include <gtk/gtk.h>
#include <stdio.h>

#include "../defines.h"
#include "../util.h"

#include "WrapperWidget.h"

#include "../generated_headers/android_widget_SeekBar.h"
#include "jni.h"

JNIEXPORT jlong JNICALL Java_android_widget_SeekBar_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *scale = gtk_scale_new(GTK_ORIENTATION_HORIZONTAL, NULL);
	gtk_range_set_range(GTK_RANGE(scale), 0, 100);
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), scale);
	wrapper_widget_set_jobject(WRAPPER_WIDGET(wrapper), env, this);
	gtk_widget_set_name(scale, "SeekBar");
	return _INTPTR(scale);
}

JNIEXPORT void JNICALL Java_android_widget_SeekBar_native_1setMax(JNIEnv *env, jobject this, jlong widget_ptr, jint max)
{
	GtkRange *range = GTK_RANGE(_PTR(widget_ptr));
	gtk_range_set_range(range, 0, max);
}

JNIEXPORT void JNICALL Java_android_widget_SeekBar_native_1setProgress(JNIEnv *env, jobject this, jlong widget_ptr, jfloat progress)
{
	GtkRange *range = GTK_RANGE(_PTR(widget_ptr));
	gtk_range_set_value(range, progress);
}

static void on_change_value(GtkRange *self, GtkScrollType *scroll, gdouble value, jobject listener)
{
	JNIEnv *env = get_jni_env();
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(self)));
	J__SeekBar__OnSeekBarChangeListener__onStartTrackingTouch(env, listener, wrapper->jobj);
	J__SeekBar__OnSeekBarChangeListener__onProgressChanged(env, listener, wrapper->jobj, (int)value, TRUE);
	J__SeekBar__OnSeekBarChangeListener__onStopTrackingTouch(env, listener, wrapper->jobj);
}

JNIEXPORT void JNICALL Java_android_widget_SeekBar_native_1setOnSeekBarChangeListener(JNIEnv *env, jobject this, jobject listener)
{
	GtkRange *range = GTK_RANGE(_PTR(_GET_LONG_FIELD(this, "widget")));
	g_signal_handlers_disconnect_matched(range, G_SIGNAL_MATCH_FUNC, 0, 0, NULL, on_change_value, NULL);
	if (listener) {
		g_signal_connect(range, "change_value", G_CALLBACK(on_change_value), _REF(listener));
	}
}

JNIEXPORT jint JNICALL Java_android_widget_SeekBar_native_1getProgress(JNIEnv *env, jobject this, jlong widget_ptr)
{
	GtkRange *range = GTK_RANGE(_PTR(widget_ptr));
	return gtk_range_get_value(range);
}

static void add_provider_recursive(GtkWidget *widget, GtkStyleProvider *provider, GtkStyleProvider *old_provider)
{
	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);
	if (old_provider)
		gtk_style_context_remove_provider(style_context, old_provider);
	gtk_style_context_add_provider(style_context, provider, GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	for (GtkWidget *child = gtk_widget_get_first_child(widget); child; child = gtk_widget_get_next_sibling(child))
		add_provider_recursive(child, provider, old_provider);
}

/* the scale's trough, highlight and slider are child widgets: each gets the provider. Colours as
 * #rrggbbaa, as printf's floats would follow the locale */
JNIEXPORT void JNICALL Java_android_widget_SeekBar_native_1setColors(JNIEnv *env, jobject this, jlong widget_ptr, jint progress, jint thumb, jint track)
{
	GtkWidget *scale = GTK_WIDGET(_PTR(widget_ptr));
	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(scale), "tint_style_provider");
	GtkCssProvider *css_provider = gtk_css_provider_new();
	char *css_string = g_strdup_printf(
	    "trough { background: #%06x%02x; border-color: #%06x%02x; box-shadow: none; }"
	    "highlight { background: #%06x%02x; border-color: #%06x%02x; }"
	    "slider { background: #%06x%02x; border-color: #%06x%02x; box-shadow: none; }",
	    track & 0xffffff, (guint32)track >> 24, track & 0xffffff, (guint32)track >> 24, progress & 0xffffff, (guint32)progress >> 24,
	    progress & 0xffffff, (guint32)progress >> 24, thumb & 0xffffff, (guint32)thumb >> 24,
	    thumb & 0xffffff, (guint32)thumb >> 24);
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);
	add_provider_recursive(scale, GTK_STYLE_PROVIDER(css_provider), old_provider ? GTK_STYLE_PROVIDER(old_provider) : NULL);
	g_object_set_data_full(G_OBJECT(scale), "tint_style_provider", css_provider, g_object_unref);
}
