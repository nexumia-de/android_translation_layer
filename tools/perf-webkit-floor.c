/* A plain WebKitGTK 6 window with a page that has script and a canvas, to see
 * whether WebKit still draws with GDK_DISABLE=gl (issue #118, measurement F).
 * Like perf-gtk-floor.c, run it through perf-run.sh with PERF_CMD.
 *
 * gcc -o perf-webkit-floor tools/perf-webkit-floor.c $(pkg-config --cflags --libs gtk4 webkitgtk-6.0)
 */
#include <gtk/gtk.h>
#include <webkit/webkit.h>

static const char *page =
	"<html><body style='background:#fff;font:32px sans-serif'><h1 id=h>script off</h1>"
	"<canvas id=c width=300 height=200 style='border:1px solid'></canvas>"
	"<div style='width:300px;height:100px;background:linear-gradient(red,blue);transform:rotate(3deg)'></div>"
	"<script>document.getElementById('h').textContent='script on';"
	"var x=document.getElementById('c').getContext('2d');x.fillStyle='green';x.fillRect(20,20,200,100);</script>"
	"</body></html>";

static void activate(GtkApplication *app, gpointer data)
{
	GtkWidget *win = gtk_application_window_new(app);
	GtkWidget *view = webkit_web_view_new();
	gtk_window_set_default_size(GTK_WINDOW(win), 360, 720);
	gtk_window_set_child(GTK_WINDOW(win), view);
	webkit_web_view_load_html(WEBKIT_WEB_VIEW(view), page, "about:blank");
	gtk_window_present(GTK_WINDOW(win));
}

int main(int argc, char **argv)
{
	GtkApplication *app = gtk_application_new("de.nexumia.PerfWebkitFloor", G_APPLICATION_NON_UNIQUE);
	g_signal_connect(app, "activate", G_CALLBACK(activate), NULL);
	return g_application_run(G_APPLICATION(app), argc, argv);
}
