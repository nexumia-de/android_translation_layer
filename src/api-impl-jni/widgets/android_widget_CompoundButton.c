#include <gtk/gtk.h>
#include <string.h>

#include "../util.h"
#include "WrapperWidget.h"

#include "../generated_headers/android_widget_CompoundButton.h"
#include "jni.h"

static void on_checked_state_changed(GObject *self, GParamSpec *pspec, WrapperWidget *wrapper)
{
	J__View__refreshDrawableState(get_jni_env(), wrapper->jobj);
}

JNIEXPORT jlong JNICALL Java_android_widget_CompoundButton_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
	GtkWidget *label = gtk_label_new("");
	gtk_widget_set_hexpand(label, TRUE);
	gtk_widget_set_halign(label, GTK_ALIGN_START);
	gtk_box_append(GTK_BOX(box), label);
	GtkWidget *switcher = gtk_switch_new();
	gtk_box_append(GTK_BOX(box), switcher);
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), box);
	wrapper_widget_set_jobject(WRAPPER_WIDGET(wrapper), env, this);
	g_signal_connect(switcher, "notify::active", G_CALLBACK(on_checked_state_changed), wrapper);
	return _INTPTR(box);
}

JNIEXPORT void JNICALL Java_android_widget_CompoundButton_setChecked(JNIEnv *env, jobject this, jboolean checked)
{
	gtk_switch_set_active(GTK_SWITCH(gtk_widget_get_last_child(_PTR(_GET_LONG_FIELD(this, "widget")))), checked);
}

JNIEXPORT jboolean JNICALL Java_android_widget_CompoundButton_isChecked(JNIEnv *env, jobject this)
{
	return gtk_switch_get_active(GTK_SWITCH(gtk_widget_get_last_child(_PTR(_GET_LONG_FIELD(this, "widget")))));
}

static gboolean on_state_set(GtkSwitch *self, gboolean state, jobject listener)
{
	JNIEnv *env = get_jni_env();
	WrapperWidget *wrapper = WRAPPER_WIDGET(gtk_widget_get_parent(gtk_widget_get_parent(GTK_WIDGET(self))));
	J__CompoundButton__OnCheckedChangeListener__onCheckedChanged(env, listener, wrapper->jobj, state);
	return FALSE;
}

JNIEXPORT void JNICALL Java_android_widget_CompoundButton_setOnCheckedChangeListener(JNIEnv *env, jobject this, jobject listener)
{
	GtkSwitch *switcher = GTK_SWITCH(gtk_widget_get_last_child(_PTR(_GET_LONG_FIELD(this, "widget"))));

	g_signal_handlers_disconnect_matched(switcher, G_SIGNAL_MATCH_FUNC, 0, 0, NULL, on_state_set, NULL);

	if (listener) {
		g_signal_connect(switcher, "state-set", G_CALLBACK(on_state_set), _REF(listener));
	}
}

JNIEXPORT void JNICALL Java_android_widget_CompoundButton_native_1setText(JNIEnv *env, jobject this, jlong widget_ptr, jstring text)
{
	GtkLabel *label = GTK_LABEL(gtk_widget_get_first_child(_PTR(widget_ptr)));
	const char *nativeText = (*env)->GetStringUTFChars(env, text, NULL);
	gtk_label_set_text(label, nativeText);
	(*env)->ReleaseStringUTFChars(env, text, nativeText);
}

/* CheckBox and RadioButton are GtkCheckButtons; their indicator is a child widget named "check" or
 * "radio", which gets the colours (a Switch has none and keeps GTK's look) */
JNIEXPORT void JNICALL Java_android_widget_CompoundButton_native_1setButtonTint(JNIEnv *env, jobject this, jlong widget_ptr, jint checked, jint unchecked)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(widget_ptr));
	if (!GTK_IS_CHECK_BUTTON(widget))
		return;
	for (GtkWidget *child = gtk_widget_get_first_child(widget); child; child = gtk_widget_get_next_sibling(child)) {
		const char *name = gtk_widget_get_css_name(child);
		if (strcmp(name, "check") && strcmp(name, "radio"))
			continue;
		GtkStyleContext *style_context = gtk_widget_get_style_context(child);
		GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(child), "tint_style_provider");
		if (old_provider)
			gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));
		GtkCssProvider *css_provider = gtk_css_provider_new();
		/* #rrggbbaa, no floats: printf would follow the locale's decimal separator. The theme colours
		 * the indicator with a background image, so the shorthand is needed */
		char *css_string = g_strdup_printf(
		    "%s:checked { background: #%06x%02x; border-color: #%06x%02x; color: white; box-shadow: none; }"
		    "%s:not(:checked) { background: transparent; border-color: #%06x%02x; box-shadow: none; }",
		    name, checked & 0xffffff, ((guint32)checked >> 24), checked & 0xffffff, ((guint32)checked >> 24),
		    name, unchecked & 0xffffff, ((guint32)unchecked >> 24));
		gtk_css_provider_load_from_string(css_provider, css_string);
		g_free(css_string);
		gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
		g_object_set_data_full(G_OBJECT(child), "tint_style_provider", css_provider, g_object_unref);
	}
}

/* the generic CompoundButton is a box with a label and a GtkSwitch; a subclass drawing itself only
 * keeps the (hidden) switch for its checked state */
JNIEXPORT void JNICALL Java_android_widget_CompoundButton_native_1hideButton(JNIEnv *env, jobject this, jlong widget_ptr)
{
	GtkWidget *box = GTK_WIDGET(_PTR(widget_ptr));
	for (GtkWidget *child = gtk_widget_get_first_child(box); child; child = gtk_widget_get_next_sibling(child))
		gtk_widget_set_visible(child, FALSE);
}
