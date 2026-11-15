#include <gtk/gtk.h>

G_DECLARE_FINAL_TYPE(SurfaceViewWidget, surface_view_widget, SURFACE_VIEW, WIDGET, GtkWidget)

struct _SurfaceViewWidget {
	GtkWidget parent_instance;
	GdkTexture *texture;
	gboolean needs_flip;
	void (*frame_callback)(SurfaceViewWidget *surface_view_widget);
	gpointer frame_callback_data;
	/* called on the main thread for every new texture (a TextureView tells its Java side) */
	void (*texture_callback)(SurfaceViewWidget *surface_view_widget, gpointer data);
	gpointer texture_callback_data;
};

struct _SurfaceViewWidgetClass {
	GtkWidgetClass parent_class;
};

void surface_view_widget_set_texture(SurfaceViewWidget *surface_view_widget, GdkTexture *texture, gboolean needs_flip);
