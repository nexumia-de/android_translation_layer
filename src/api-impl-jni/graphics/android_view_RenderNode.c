#include <gsk/gsk.h>
#include <gtk/gtk.h>

#include "../defines.h"

#include "../generated_headers/android_view_RenderNode.h"

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeCreateSnapshot(JNIEnv *env, jobject this)
{
	return _INTPTR(gtk_snapshot_new());
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeCreateNode(JNIEnv *env, jobject this, jlong snapshot_ptr)
{
	GskRenderNode *node = gtk_snapshot_free_to_node(GTK_SNAPSHOT(_PTR(snapshot_ptr)));
	if (!node)
		node = gsk_container_node_new(NULL, 0);
	return _INTPTR(node);
}

struct patch {
	jlong *old_children;
	jlong *new_children;
	jsize n_children;
	GHashTable *index; // old child -> its position + 1, for many children
};

/*
 * Returns the node with every old child replaced by its new one. The node of a child
 * is never looked into, changed or not: a child is drawn at this level only, and its
 * subtree (with those of its own children) is most of the tree.
 */
static GskRenderNode *patch_node(GskRenderNode *node, const struct patch *patch)
{
	if (patch->index) {
		jsize i = GPOINTER_TO_INT(g_hash_table_lookup(patch->index, node));
		if (i)
			return gsk_render_node_ref(_PTR(patch->new_children[i - 1]));
	} else {
		for (jsize i = 0; i < patch->n_children; i++) {
			if (_PTR(patch->old_children[i]) == node)
				return gsk_render_node_ref(_PTR(patch->new_children[i]));
		}
	}
	GskRenderNode *new_node;
	GskRenderNodeType type = gsk_render_node_get_node_type(node);
	switch (type) {
		case GSK_CONTAINER_NODE: {
			guint n_children = gsk_container_node_get_n_children(node);
			GskRenderNode *children[n_children];
			gboolean modified = FALSE;
			for (guint i = 0; i < n_children; i++) {
				children[i] = patch_node(gsk_container_node_get_child(node, i), patch);
				modified |= children[i] != gsk_container_node_get_child(node, i);
			}
			if (modified)
				new_node = gsk_container_node_new(children, n_children);
			else
				new_node = gsk_render_node_ref(node);
			for (guint i = 0; i < n_children; i++)
				gsk_render_node_unref(children[i]);
			break;
		}
		case GSK_TRANSFORM_NODE: {
			GskRenderNode *child = patch_node(gsk_transform_node_get_child(node), patch);
			if (child != gsk_transform_node_get_child(node))
				new_node = gsk_transform_node_new(child, gsk_transform_node_get_transform(node));
			else
				new_node = gsk_render_node_ref(node);
			gsk_render_node_unref(child);
			break;
		}
		case GSK_CLIP_NODE: {
			GskRenderNode *child = patch_node(gsk_clip_node_get_child(node), patch);
			if (child != gsk_clip_node_get_child(node))
				new_node = gsk_clip_node_new(child, gsk_clip_node_get_clip(node));
			else
				new_node = gsk_render_node_ref(node);
			gsk_render_node_unref(child);
			break;
		}
		case GSK_OPACITY_NODE: {
			GskRenderNode *child = patch_node(gsk_opacity_node_get_child(node), patch);
			if (child != gsk_opacity_node_get_child(node))
				new_node = gsk_opacity_node_new(child, gsk_opacity_node_get_opacity(node));
			else
				new_node = gsk_render_node_ref(node);
			gsk_render_node_unref(child);
			break;
		}
		default:
			new_node = gsk_render_node_ref(node);
	}
	return new_node;
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativePatchNode(JNIEnv *env, jobject this, jlong node_ptr, jlongArray old_children, jlongArray new_children)
{
	GskRenderNode *old_node = _PTR(node_ptr);
	struct patch patch = {
		.old_children = (*env)->GetLongArrayElements(env, old_children, NULL),
		.new_children = (*env)->GetLongArrayElements(env, new_children, NULL),
		.n_children = (*env)->GetArrayLength(env, old_children),
	};
	if (patch.n_children > 8) {
		patch.index = g_hash_table_new(NULL, NULL);
		/* backwards: a node drawn twice is found at its first position, as without the table */
		for (jsize i = patch.n_children; i > 0; i--)
			g_hash_table_insert(patch.index, _PTR(patch.old_children[i - 1]), GINT_TO_POINTER(i));
	}
	GskRenderNode *new_node = patch_node(old_node, &patch);
	if (patch.index)
		g_hash_table_unref(patch.index);
	(*env)->ReleaseLongArrayElements(env, old_children, patch.old_children, JNI_ABORT);
	(*env)->ReleaseLongArrayElements(env, new_children, patch.new_children, JNI_ABORT);
	gsk_render_node_unref(old_node);
	return _INTPTR(new_node);
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeTransform(JNIEnv *env, jobject this, jlong node_ptr, jfloat scale_x, jfloat scale_y, jfloat translate_x, jfloat translate_y, jfloat rotate, jfloat pivot_x, jfloat pivot_y)
{
	GskRenderNode *node = _PTR(node_ptr);
	GskTransform *transform = NULL;
	GskRenderNode *transformed_node;
	transform = gsk_transform_translate(transform, &GRAPHENE_POINT_INIT(-pivot_x, -pivot_y));
	transform = gsk_transform_scale(transform, scale_x, scale_y);
	transform = gsk_transform_rotate(transform, rotate);
	transform = gsk_transform_translate(transform, &GRAPHENE_POINT_INIT(translate_x + pivot_x, translate_y + pivot_y));
	if (transform) {
		transformed_node = gsk_transform_node_new(node, transform);
		gsk_transform_unref(transform);
	} else {
		transformed_node = gsk_render_node_ref(node);
	}
	return _INTPTR(transformed_node);
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeClip(JNIEnv *env, jobject this, jlong node_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom)
{
	GskRenderNode *node = _PTR(node_ptr);
	GskRenderNode *clipped_node = gsk_clip_node_new(node, &GRAPHENE_RECT_INIT(left, top, right - left, bottom - top));
	gsk_render_node_unref(node);
	return _INTPTR(clipped_node);
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeClipRounded(JNIEnv *env, jobject this, jlong node_ptr, jfloat left, jfloat top, jfloat right, jfloat bottom, jfloat radius)
{
	GskRenderNode *node = _PTR(node_ptr);
	GskRoundedRect rect;
	gsk_rounded_rect_init_from_rect(&rect, &GRAPHENE_RECT_INIT(left, top, right - left, bottom - top), radius);
	GskRenderNode *clipped_node = gsk_rounded_clip_node_new(node, &rect);
	gsk_render_node_unref(node);
	return _INTPTR(clipped_node);
}

/* clips to an outline set with Outline.setPath(), e.g. a shape with only the top corners rounded */
JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeClipPath(JNIEnv *env, jobject this, jlong node_ptr, jlong path_ptr, jint fill_rule)
{
	GskRenderNode *node = _PTR(node_ptr);
	graphene_rect_t bounds;
	gsk_path_get_bounds(_PTR(path_ptr), &bounds);
	GskRenderNode *color = gsk_color_node_new(&(GdkRGBA){1, 1, 1, 1}, &bounds);
	GskRenderNode *mask = gsk_fill_node_new(color, _PTR(path_ptr), fill_rule);
	GskRenderNode *clipped_node = gsk_mask_node_new(node, mask, GSK_MASK_MODE_ALPHA);
	gsk_render_node_unref(mask);
	gsk_render_node_unref(color);
	gsk_render_node_unref(node);
	return _INTPTR(clipped_node);
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeOpacity(JNIEnv *env, jobject this, jlong node_ptr, jfloat alpha)
{
	GskRenderNode *node = _PTR(node_ptr);
	GskRenderNode *opacity_node = gsk_opacity_node_new(node, alpha);
	gsk_render_node_unref(node);
	return _INTPTR(opacity_node);
}

JNIEXPORT void JNICALL Java_android_view_RenderNode_nativeUnref(JNIEnv *env, jobject this, jlong node_ptr)
{
	if (node_ptr != 0)
		gsk_render_node_unref(_PTR(node_ptr));
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeAddStubNode(JNIEnv *env, jobject this, jlong snapshot_ptr)
{
	GtkSnapshot *snapshot = _PTR(snapshot_ptr);
	GdkRGBA rgba = {
		.red = 0.f,
		.green = 0.f,
		.blue = 0.f,
		.alpha = 0.f,
	};
	GskRenderNode *stub_node = gsk_color_node_new(&rgba, &GRAPHENE_RECT_INIT(0.0f, 0.0f, 0.0f, 0.0f));
	gtk_snapshot_append_node(snapshot, stub_node);
	gsk_render_node_unref(stub_node);
	return _INTPTR(stub_node);
}

JNIEXPORT jlong JNICALL Java_android_view_RenderNode_nativeCreateEmpyNode(JNIEnv *env, jobject this)
{
	return _INTPTR(gsk_container_node_new(NULL, 0));
}
