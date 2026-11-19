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
	/* when a redraw was last asked for with a new texture (monotonic time) */
	gint64 last_redraw;
};

struct _SurfaceViewWidgetClass {
	GtkWidgetClass parent_class;
};

/* redraw: also call surface_view_widget_redraw */
void surface_view_widget_set_texture(SurfaceViewWidget *surface_view_widget, GdkTexture *texture, gboolean needs_flip, gboolean redraw);
/* draw the widget again and tell a TextureView about the new frame */
void surface_view_widget_redraw(SurfaceViewWidget *surface_view_widget);
