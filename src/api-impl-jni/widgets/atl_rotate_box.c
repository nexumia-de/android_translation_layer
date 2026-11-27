#include "atl_rotate_box.h"

struct _AtlRotateBox {
	GtkWidget parent;
	GtkWidget *child;
	gboolean rotated;
};

G_DEFINE_TYPE(AtlRotateBox, atl_rotate_box, GTK_TYPE_WIDGET)

AtlRotateBox *atl_window_rotate_box;

static void atl_rotate_box_measure(GtkWidget *widget, GtkOrientation orientation, int for_size,
                                   int *minimum, int *natural, int *minimum_baseline, int *natural_baseline)
{
	AtlRotateBox *self = ATL_ROTATE_BOX(widget);
	*minimum = *natural = 0;
	*minimum_baseline = *natural_baseline = -1;
	if (!self->child)
		return;
	/* turned, the child's width is the box's height */
	GtkOrientation child_orientation = self->rotated ? (orientation == GTK_ORIENTATION_HORIZONTAL ? GTK_ORIENTATION_VERTICAL : GTK_ORIENTATION_HORIZONTAL) : orientation;
	gtk_widget_measure(self->child, child_orientation, self->rotated ? -1 : for_size, minimum, natural, NULL, NULL);
}

static void atl_rotate_box_size_allocate(GtkWidget *widget, int width, int height, int baseline)
{
	AtlRotateBox *self = ATL_ROTATE_BOX(widget);
	if (!self->child)
		return;
	if (!self->rotated) {
		gtk_widget_allocate(self->child, width, height, baseline, NULL);
		return;
	}
	/* the child's top edge along the right edge of the screen */
	GskTransform *transform = gsk_transform_translate(NULL, &GRAPHENE_POINT_INIT(width, 0));
	transform = gsk_transform_rotate(transform, 90);
	gtk_widget_allocate(self->child, height, width, -1, transform);
}

static void atl_rotate_box_dispose(GObject *object)
{
	AtlRotateBox *self = ATL_ROTATE_BOX(object);
	g_clear_pointer(&self->child, gtk_widget_unparent);
	G_OBJECT_CLASS(atl_rotate_box_parent_class)->dispose(object);
}

static void atl_rotate_box_class_init(AtlRotateBoxClass *klass)
{
	G_OBJECT_CLASS(klass)->dispose = atl_rotate_box_dispose;
	GTK_WIDGET_CLASS(klass)->measure = atl_rotate_box_measure;
	GTK_WIDGET_CLASS(klass)->size_allocate = atl_rotate_box_size_allocate;
}

static void atl_rotate_box_init(AtlRotateBox *self) {}

GtkWidget *atl_rotate_box_new(GtkWidget *child)
{
	AtlRotateBox *self = g_object_new(ATL_TYPE_ROTATE_BOX, NULL);
	self->child = child;
	gtk_widget_set_parent(child, GTK_WIDGET(self));
	return GTK_WIDGET(self);
}

void atl_rotate_box_set_rotated(AtlRotateBox *self, gboolean rotated)
{
	if (self->rotated == rotated)
		return;
	self->rotated = rotated;
	gtk_widget_queue_resize(GTK_WIDGET(self));
}

gboolean atl_rotate_box_get_rotated(AtlRotateBox *self)
{
	return self && self->rotated;
}
