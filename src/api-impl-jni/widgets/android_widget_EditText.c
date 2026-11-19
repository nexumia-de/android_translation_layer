#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "WrapperWidget.h"

#include "../generated_headers/android_widget_EditText.h"

static void changed_cb(GtkEditable *self)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(self)));
	JNIEnv *env = get_jni_env();

	const char *text = gtk_editable_get_text(self);
	jobject text_obj = J_new__SpannableStringBuilder(env, _JSTRING(text));
	J__TextView__onTextChanged(env, wrapper->jobj, text_obj, 0, 0, strlen(text));
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static void on_activate(GtkText *gtk_text)
{
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(GTK_WIDGET(gtk_text)));
	JNIEnv *env = get_jni_env();

	J__TextView__onEditorAction(env, wrapper->jobj, 0);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

JNIEXPORT jlong JNICALL Java_android_widget_EditText_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *gtk_text = gtk_text_new();
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), gtk_text);
	wrapper_widget_set_jobject(WRAPPER_WIDGET(wrapper), env, this);
	wrapper_widget_register_invalidation_listener(WRAPPER_WIDGET(wrapper));
	g_signal_connect(gtk_text, "changed", G_CALLBACK(changed_cb), NULL);
	g_signal_connect(gtk_text, "activate", G_CALLBACK(on_activate), NULL);
	return _INTPTR(gtk_text);
}

JNIEXPORT jstring JNICALL Java_android_widget_EditText_native_1getText(JNIEnv *env, jobject this, jlong widget_ptr)
{
	GtkText *gtk_text = GTK_TEXT(_PTR(widget_ptr));
	const char *text = gtk_entry_buffer_get_text(gtk_text_get_buffer(gtk_text));
	return _JSTRING(text);
}

JNIEXPORT void JNICALL Java_android_widget_EditText_native_1setText(JNIEnv *env, jobject this, jlong widget_ptr, jstring text_jstr)
{
	const char *gtk_text = (*env)->GetStringUTFChars(env, text_jstr, NULL);
	jsize length = (*env)->GetStringUTFLength(env, text_jstr);
	gtk_entry_buffer_set_text(gtk_text_get_buffer(GTK_TEXT(_PTR(widget_ptr))), gtk_text, length);
	(*env)->ReleaseStringUTFChars(env, text_jstr, gtk_text);
}

JNIEXPORT void JNICALL Java_android_widget_EditText_native_1setHint(JNIEnv *env, jobject this, jlong widget_ptr, jstring text_jstr)
{
	const char *text = (*env)->GetStringUTFChars(env, text_jstr, NULL);
	gtk_text_set_placeholder_text(GTK_TEXT(_PTR(widget_ptr)), text);
	(*env)->ReleaseStringUTFChars(env, text_jstr, text);
}

JNIEXPORT jstring JNICALL Java_android_widget_EditText_native_1getHint(JNIEnv *env, jobject this, jlong widget_ptr)
{
	GtkText *gtk_text = GTK_TEXT(_PTR(widget_ptr));
	const char *text = gtk_text_get_placeholder_text(gtk_text);
	return _JSTRING(text);
}
