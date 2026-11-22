/* A plain GTK4 window as the floor of what an ATL app costs (issue #118):
 * one 360x720 window with a label, no ATL, no ART. Run it through
 * perf-run.sh with PERF_CMD=/path/to/perf-gtk-floor to get the same
 * smaps, thread and GPU memory numbers as for an app.
 *
 * gcc -o perf-gtk-floor tools/perf-gtk-floor.c $(pkg-config --cflags --libs gtk4)
 */
#include <gtk/gtk.h>

static void activate(GtkApplication *app, gpointer data)
{
	GtkWidget *win = gtk_application_window_new(app);
	gtk_window_set_default_size(GTK_WINDOW(win), 360, 720);
	gtk_window_set_child(GTK_WINDOW(win), gtk_label_new("GTK4 floor"));
	gtk_window_present(GTK_WINDOW(win));
}

int main(int argc, char **argv)
{
	GtkApplication *app = gtk_application_new("de.nexumia.PerfGtkFloor", G_APPLICATION_NON_UNIQUE);
	g_signal_connect(app, "activate", G_CALLBACK(activate), NULL);
	return g_application_run(G_APPLICATION(app), argc, argv);
}
