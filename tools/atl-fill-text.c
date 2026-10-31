/* Headless tests only: keys do not reach ATL's text fields there, so this fills every EditText
 * 5 s after it is created. It uses GTK's own setter, which fires "changed" just as typing does.
 *
 *   cc -shared -fPIC -o atl-fill-text.so tools/atl-fill-text.c $(pkg-config --cflags --libs gtk4)
 *   LD_PRELOAD=$PWD/atl-fill-text.so ATL_TEST_TEXT=randomuser123 android-translation-layer ... */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdlib.h>
#include <gtk/gtk.h>

static gboolean fill(gpointer w)
{
	const char *t = getenv("ATL_TEST_TEXT");
	gtk_editable_set_text(GTK_EDITABLE(w), t ? t : "randomuser123");
	g_object_unref(w);
	return G_SOURCE_REMOVE;
}

GtkWidget *gtk_text_new(void)
{
	GtkWidget *(*real)(void) = dlsym(RTLD_NEXT, "gtk_text_new");
	GtkWidget *w = real();
	g_timeout_add_seconds(5, fill, g_object_ref(w));
	return w;
}
