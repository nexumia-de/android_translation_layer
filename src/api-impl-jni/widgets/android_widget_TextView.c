#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "WrapperWidget.h"

#include "../generated_headers/android_widget_TextView.h"

static GtkLabel *box_get_label(JNIEnv *env, GtkWidget *box)
{
	GtkWidget *label = gtk_widget_get_last_child(GTK_WIDGET(box));
	if (!GTK_IS_LABEL(label))
		label = gtk_widget_get_prev_sibling(label);
	return GTK_LABEL(label);
}

JNIEXPORT jlong JNICALL Java_android_widget_TextView_native_1constructor(JNIEnv *env, jobject this, jobject context, jobject attrs)
{
	const char *text = attribute_set_get_string(env, attrs, "text", NULL);

	//	_SET_OBJ_FIELD(this, "text", "Ljava/lang/String;", _JSTRING(text)); //TODO: sadly this might be needed, but it's not atm

	GtkWidget *wrapper = g_object_ref(wrapper_widget_new());
	GtkWidget *box = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
	GtkWidget *label = gtk_label_new(text);
	gtk_label_set_wrap(GTK_LABEL(label), TRUE);
	gtk_label_set_xalign(GTK_LABEL(label), 0.f);
	gtk_label_set_yalign(GTK_LABEL(label), 0.f);
	gtk_widget_set_hexpand(label, TRUE);
	gtk_box_append(GTK_BOX(box), label);
	wrapper_widget_set_child(WRAPPER_WIDGET(wrapper), box);
	wrapper_widget_set_jobject(WRAPPER_WIDGET(wrapper), env, this);

	PangoAttrList *pango_attrs = pango_attr_list_new();
	pango_attr_list_insert(pango_attrs, pango_attr_font_features_new("tnum"));
	gtk_label_set_attributes(GTK_LABEL(label), pango_attrs);
	pango_attr_list_unref(pango_attrs);

	return _INTPTR(box);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setFontFamily(JNIEnv *env, jobject this, jlong widget_ptr, jstring family_jstr)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(widget_ptr));
	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);

	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(widget), "font_style_provider");
	if (old_provider)
		gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));
	g_object_set_data(G_OBJECT(widget), "font_style_provider", NULL);
	if (!family_jstr)
		return;

	GtkCssProvider *css_provider = gtk_css_provider_new();
	const char *family = (*env)->GetStringUTFChars(env, family_jstr, NULL);
	char *css_string = g_strdup_printf("* { font-family: \"%s\"; }", family);
	(*env)->ReleaseStringUTFChars(env, family_jstr, family);
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);

	gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	g_object_set_data(G_OBJECT(widget), "font_style_provider", css_provider);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setGravity(JNIEnv *env, jobject this, jlong widget_ptr, jint gravity)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(widget_ptr));
	/* Gravity.AXIS_*: 1 center, 3 left or start, 5 right or end */
	int horizontal = gravity & 0x7;
	float xalign = horizontal == 1 ? 0.5f : horizontal == 5 ? 1.f : 0.f;
	/* Gravity: 0x10 center_vertical, 0x30 top, 0x50 bottom */
	int vertical = gravity & 0x70;
	float yalign = vertical == 0x10 ? 0.5f : vertical == 0x50 ? 1.f : 0.f;

	if (GTK_IS_BUTTON(widget)) {
		GtkWidget *box = gtk_button_get_child(GTK_BUTTON(widget));
		gtk_widget_set_halign(box, horizontal == 3 ? GTK_ALIGN_START : horizontal == 5 ? GTK_ALIGN_END : GTK_ALIGN_CENTER);
	} else if (GTK_IS_BOX(widget)) {
		GtkLabel *label = box_get_label(env, widget);
		gtk_label_set_xalign(label, xalign);
		gtk_label_set_yalign(label, yalign);
		gtk_label_set_justify(label, xalign == 0.5f ? GTK_JUSTIFY_CENTER : xalign == 1.f ? GTK_JUSTIFY_RIGHT : GTK_JUSTIFY_LEFT);
	}
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setMaxLines(JNIEnv *env, jobject this, jlong widget_ptr, jint max_lines)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(widget_ptr));
	if (!GTK_IS_BOX(widget)) // Button and EditText
		return;
	GtkLabel *label = box_get_label(env, widget);
	gtk_label_set_wrap(label, max_lines != 1);
	gtk_label_set_lines(label, max_lines);
	gtk_label_set_ellipsize(label, max_lines > 0 ? PANGO_ELLIPSIZE_END : PANGO_ELLIPSIZE_NONE);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setText(JNIEnv *env, jobject this, jobject charseq)
{
	const char *text = charseq ? (*env)->GetStringUTFChars(env, charseq, NULL) : NULL;
	atl_safe_gtk_label_set_text(box_get_label(env, _PTR(_GET_LONG_FIELD(this, "widget"))), text ?: "");
	if (text)
		(*env)->ReleaseStringUTFChars(env, charseq, text);
}

/* we kinda need per-widget css */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setTextColor(JNIEnv *env, jobject this, jint color)
{
	GtkWidget *widget = GTK_WIDGET(_PTR(_GET_LONG_FIELD(this, "widget")));

	GtkStyleContext *style_context = gtk_widget_get_style_context(widget);

	GtkCssProvider *old_provider = g_object_get_data(G_OBJECT(widget), "color_style_provider");
	if (old_provider)
		gtk_style_context_remove_provider(style_context, GTK_STYLE_PROVIDER(old_provider));

	GtkCssProvider *css_provider = gtk_css_provider_new();

	char *css_string = g_markup_printf_escaped("* { color: #%06x%02x; }", color & 0xFFFFFF, (color >> 24) & 0xFF);
	gtk_css_provider_load_from_string(css_provider, css_string);
	g_free(css_string);

	gtk_style_context_add_provider(style_context, GTK_STYLE_PROVIDER(css_provider), GTK_STYLE_PROVIDER_PRIORITY_APPLICATION);
	g_object_set_data(G_OBJECT(widget), "color_style_provider", css_provider);
}
#pragma GCC diagnostic pop

JNIEXPORT void JNICALL Java_android_widget_TextView_setTextSize(JNIEnv *env, jobject this, jfloat size)
{
	GtkLabel *label = box_get_label(env, _PTR(_GET_LONG_FIELD(this, "widget")));
	PangoAttrList *attrs;

	PangoAttrList *old_attrs = gtk_label_get_attributes(label);
	if (old_attrs)
		attrs = pango_attr_list_copy(old_attrs);
	else
		attrs = pango_attr_list_new();

	PangoAttribute *size_attr = pango_attr_size_new_absolute(size * PANGO_SCALE); // pixels, not points
	pango_attr_list_change(attrs, size_attr);
	gtk_label_set_attributes(label, attrs);

	pango_attr_list_unref(attrs);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1set_1markup(JNIEnv *env, jobject this, jint value)
{
	GtkLabel *label = box_get_label(env, _PTR(_GET_LONG_FIELD(this, "widget")));

	gtk_label_set_use_markup(label, value);
}

JNIEXPORT void JNICALL Java_android_widget_TextView_native_1setCompoundDrawables(JNIEnv *env, jobject this, jlong widget_ptr, jlong left, jlong top, jlong right, jlong bottom)
{
	GtkWidget *box = GTK_WIDGET(_PTR(widget_ptr));
	gtk_orientable_set_orientation(GTK_ORIENTABLE(box), (left || right) ? GTK_ORIENTATION_HORIZONTAL : GTK_ORIENTATION_VERTICAL);

	GdkPaintable *paintable = _PTR(left ?: top); // paintable before text
	GtkWidget *picture = gtk_widget_get_first_child(box);
	if (GTK_IS_PICTURE(picture)) {
		gtk_picture_set_paintable(GTK_PICTURE(picture), paintable);
	} else if (paintable) {
		picture = gtk_picture_new_for_paintable(paintable);
		gtk_widget_insert_after(picture, box, NULL);
	}

	paintable = _PTR(right ?: bottom); // paintable after text
	picture = gtk_widget_get_last_child(box);
	if (GTK_IS_PICTURE(picture)) {
		gtk_picture_set_paintable(GTK_PICTURE(picture), paintable);
	} else if (paintable) {
		picture = gtk_picture_new_for_paintable(paintable);
		gtk_widget_insert_before(picture, box, NULL);
	}
}
