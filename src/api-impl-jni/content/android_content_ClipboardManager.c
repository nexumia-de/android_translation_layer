#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_content_ClipboardManager.h"

extern GtkWindow *window; // TODO: get this in a better way

JNIEXPORT void JNICALL Java_android_content_ClipboardManager_native_1set_1clipboard(JNIEnv *env, jclass class, jstring text_jstring)
{
	const char *text = (*env)->GetStringUTFChars(env, text_jstring, NULL);
	GdkClipboard *clipboard = gdk_display_get_clipboard(gtk_root_get_display(GTK_ROOT(window)));
	gdk_clipboard_set_text(clipboard, text);
	(*env)->ReleaseStringUTFChars(env, text_jstring, text);
}

/* Android reads the clipboard synchronously, GDK only asynchronously: the text is read
 * whenever the clipboard changes (also when another program sets it and the app gets
 * the focus) and kept for getPrimaryClip */
static char *clipboard_text; /* guarded by clipboard_lock */
static GMutex clipboard_lock;

static void set_clipboard_text(char *text)
{
	g_mutex_lock(&clipboard_lock);
	g_free(clipboard_text);
	clipboard_text = text;
	g_mutex_unlock(&clipboard_lock);
}

static jstring get_clipboard_text(JNIEnv *env)
{
	g_mutex_lock(&clipboard_lock);
	jstring ret = clipboard_text ? _JSTRING(clipboard_text) : NULL;
	g_mutex_unlock(&clipboard_lock);
	return ret;
}

static void clipboard_read_done(GObject *source, GAsyncResult *result, gpointer user_data)
{
	char *text = gdk_clipboard_read_text_finish(GDK_CLIPBOARD(source), result, NULL);
	if (text)
		set_clipboard_text(text);
}

static void clipboard_changed(GdkClipboard *clipboard, gpointer user_data)
{
	GdkContentFormats *formats = gdk_clipboard_get_formats(clipboard);
	if (formats && gdk_content_formats_contain_gtype(formats, G_TYPE_STRING)) {
		gdk_clipboard_read_text_async(clipboard, NULL, clipboard_read_done, NULL);
	} else {
		set_clipboard_text(NULL);
	}
}

static GdkClipboard *watched_clipboard(void)
{
	static GdkClipboard *clipboard;
	if (!clipboard) {
		GdkDisplay *display = gdk_display_get_default();
		if (!display)
			return NULL;
		clipboard = gdk_display_get_clipboard(display);
		g_signal_connect(clipboard, "changed", G_CALLBACK(clipboard_changed), NULL);
		clipboard_changed(clipboard, NULL);
	}
	return clipboard;
}

static gboolean watch_clipboard_idle(gpointer user_data)
{
	watched_clipboard();
	return G_SOURCE_REMOVE;
}

/* GTK may only be used on the main thread; apps also get the service on other threads */
static gboolean on_main_thread(void)
{
	return g_main_context_is_owner(g_main_context_default());
}

JNIEXPORT void JNICALL Java_android_content_ClipboardManager_native_1watch_1clipboard(JNIEnv *env, jclass class)
{
	if (on_main_thread())
		watched_clipboard();
	else
		g_idle_add(watch_clipboard_idle, NULL);
}

JNIEXPORT jstring JNICALL Java_android_content_ClipboardManager_native_1get_1clipboard(JNIEnv *env, jclass class)
{
	if (!on_main_thread())
		return get_clipboard_text(env);
	GdkClipboard *clipboard = watched_clipboard();
	if (!clipboard)
		return NULL;
	/* a clip set by this app is known at once */
	GdkContentProvider *content = gdk_clipboard_get_content(clipboard);
	if (content) {
		GValue value = G_VALUE_INIT;
		g_value_init(&value, G_TYPE_STRING);
		if (gdk_content_provider_get_value(content, &value, NULL)) {
			jstring ret = _JSTRING(g_value_get_string(&value));
			g_value_unset(&value);
			return ret;
		}
		g_value_unset(&value);
	}
	return get_clipboard_text(env);
}
