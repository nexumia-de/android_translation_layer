#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"
#include "ColorFilter.h"
#include "../generated_headers/android_graphics_drawable_Drawable.h"

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_Drawable_native_1paintable_1from_1path(JNIEnv *env, jclass class, jstring pathStr)
{
	const char *path = (*env)->GetStringUTFChars(env, pathStr, NULL);
	GdkTexture *texture = gdk_texture_new_from_filename(path, NULL);
	(*env)->ReleaseStringUTFChars(env, pathStr, path);
	return _INTPTR(texture);
}

struct _JavaPaintable {
	GObject parent_instance;
	jobject drawable;
};
G_DECLARE_FINAL_TYPE(JavaPaintable, java_paintable, JAVA, PAINTABLE, GObject)

static void java_paintable_snapshot(GdkPaintable *gdk_paintable, GdkSnapshot *snapshot, double width, double height)
{
	JNIEnv *env = get_jni_env();
	JavaPaintable *paintable = JAVA_PAINTABLE(gdk_paintable);
	jobject canvas = J_new__GskCanvas(env, _INTPTR(snapshot));
	J__Drawable__setBounds(env, paintable->drawable, 0, 0, (int)width, (int)height);
	J__Drawable__draw(env, paintable->drawable, canvas);
	if ((*env)->ExceptionCheck(env))
		(*env)->ExceptionDescribe(env);
}

static int java_paintable_get_intrinsic_width(GdkPaintable *gdk_paintable)
{
	JNIEnv *env = get_jni_env();
	JavaPaintable *paintable = JAVA_PAINTABLE(gdk_paintable);
	int width = J__Drawable__getIntrinsicWidth(env, paintable->drawable);
	return width > 0 ? width : 0;
}

static int java_paintable_get_intrinsic_height(GdkPaintable *gdk_paintable)
{
	JNIEnv *env = get_jni_env();
	JavaPaintable *paintable = JAVA_PAINTABLE(gdk_paintable);
	int height = J__Drawable__getIntrinsicHeight(env, paintable->drawable);
	return height > 0 ? height : 0;
}

static void java_paintable_init(JavaPaintable *java_paintable)
{
}

static void java_paintable_paintable_init(GdkPaintableInterface *iface)
{
	iface->snapshot = java_paintable_snapshot;
	iface->get_intrinsic_height = java_paintable_get_intrinsic_height;
	iface->get_intrinsic_width = java_paintable_get_intrinsic_width;
}

static void java_paintable_dispose(GObject *object)
{
	JavaPaintable *java_paintable = JAVA_PAINTABLE(object);
	JNIEnv *env = get_jni_env();
	_WEAK_UNREF(java_paintable->drawable);
}

static void java_paintable_class_init(JavaPaintableClass *class)
{
	G_OBJECT_CLASS(class)->dispose = java_paintable_dispose;
}

G_DEFINE_TYPE_WITH_CODE(JavaPaintable, java_paintable, G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(GDK_TYPE_PAINTABLE, java_paintable_paintable_init))

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_Drawable_native_1constructor(JNIEnv *env, jobject this)
{
	JavaPaintable *paintable = NULL;
	if (_METHOD(_CLASS(this), "draw", "(Landroid/graphics/Canvas;)V") != _CACHED_METHOD(Drawable__draw)) {
		paintable = g_object_new(java_paintable_get_type(), NULL);
		paintable->drawable = _WEAK_REF(this);
	}
	return _INTPTR(paintable);
}

static guint queue_invalidate_contents(GdkPaintable *paintable)
{
	gdk_paintable_invalidate_contents(paintable);
	g_object_unref(paintable);
	return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1invalidate(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	// GTK doesn't allow invalidating a paintable while it's being drawn, so we need to queue it up
	g_idle_add_full(G_PRIORITY_HIGH_IDLE + 20, G_SOURCE_FUNC(queue_invalidate_contents), g_object_ref(GDK_PAINTABLE(_PTR(paintable_ptr))), NULL);
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1draw(JNIEnv *env, jobject this, jlong paintable_ptr, jlong snapshot_ptr, jint width, jint height)
{
	GdkSnapshot *snapshot = (GdkSnapshot *)_PTR(snapshot_ptr);
	GdkPaintable *paintable = GDK_PAINTABLE(_PTR(paintable_ptr));
	if (!JAVA_IS_PAINTABLE(paintable))
		gdk_paintable_snapshot(paintable, snapshot, width, height);
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1ref(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	g_object_ref(G_OBJECT(_PTR(paintable_ptr)));
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1unref(JNIEnv *env, jobject this, jlong paintable_ptr)
{
	g_object_unref(G_OBJECT(_PTR(paintable_ptr)));
}

/* A paintable that draws a child paintable (a bitmap or nine-patch) through a color filter */
struct _FilterPaintable {
	GObject parent_instance;
	GdkPaintable *child;
	graphene_matrix_t color_matrix;
	graphene_vec4_t color_offset;
	bool use_color_filter;
};
G_DECLARE_FINAL_TYPE(FilterPaintable, filter_paintable, FILTER, PAINTABLE, GObject)

static void filter_paintable_snapshot(GdkPaintable *gdk_paintable, GdkSnapshot *snapshot, double width, double height)
{
	FilterPaintable *paintable = FILTER_PAINTABLE(gdk_paintable);
	if (paintable->use_color_filter)
		gtk_snapshot_push_color_matrix(snapshot, &paintable->color_matrix, &paintable->color_offset);
	gdk_paintable_snapshot(paintable->child, snapshot, width, height);
	if (paintable->use_color_filter)
		gtk_snapshot_pop(snapshot);
}

static double filter_paintable_get_intrinsic_aspect_ratio(GdkPaintable *gdk_paintable)
{
	return gdk_paintable_get_intrinsic_aspect_ratio(FILTER_PAINTABLE(gdk_paintable)->child);
}

static int filter_paintable_get_intrinsic_width(GdkPaintable *gdk_paintable)
{
	return gdk_paintable_get_intrinsic_width(FILTER_PAINTABLE(gdk_paintable)->child);
}

static int filter_paintable_get_intrinsic_height(GdkPaintable *gdk_paintable)
{
	return gdk_paintable_get_intrinsic_height(FILTER_PAINTABLE(gdk_paintable)->child);
}

static void filter_paintable_init(FilterPaintable *paintable)
{
}

static void filter_paintable_paintable_init(GdkPaintableInterface *iface)
{
	iface->snapshot = filter_paintable_snapshot;
	iface->get_intrinsic_aspect_ratio = filter_paintable_get_intrinsic_aspect_ratio;
	iface->get_intrinsic_width = filter_paintable_get_intrinsic_width;
	iface->get_intrinsic_height = filter_paintable_get_intrinsic_height;
}

G_DEFINE_TYPE_WITH_CODE(FilterPaintable, filter_paintable, G_TYPE_OBJECT,
                        G_IMPLEMENT_INTERFACE(GDK_TYPE_PAINTABLE, filter_paintable_paintable_init))

static void filter_paintable_dispose(GObject *object)
{
	g_clear_object(&FILTER_PAINTABLE(object)->child);
	G_OBJECT_CLASS(filter_paintable_parent_class)->dispose(object);
}

static void filter_paintable_class_init(FilterPaintableClass *class)
{
	G_OBJECT_CLASS(class)->dispose = filter_paintable_dispose;
}

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_Drawable_native_1filter_1paintable_1new(JNIEnv *env, jclass class, jlong child_ptr)
{
	FilterPaintable *paintable = g_object_new(filter_paintable_get_type(), NULL);
	paintable->child = g_object_ref(GDK_PAINTABLE(_PTR(child_ptr)));
	return _INTPTR(paintable);
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_Drawable_native_1set_1color_1filter(JNIEnv *env, jclass class, jlong paintable_ptr, jfloatArray matrix)
{
	FilterPaintable *paintable = FILTER_PAINTABLE(_PTR(paintable_ptr));
	paintable->use_color_filter = color_filter_from_java(env, matrix, &paintable->color_matrix, &paintable->color_offset);
}
