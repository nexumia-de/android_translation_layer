#include <gtk/gtk.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_media_MediaPlayer.h"

/* A MediaPlayer is a GtkMediaFile. Its GStreamer pipeline (and the threads
 * and GL context that come with it) exists only while the file is open: it
 * is opened when the player is created, which the Java side does on the
 * first call that needs it, and released a few seconds after playback has
 * ended. start() opens the file again when it was released. */

#define RELEASE_DELAY_SECONDS 5

static void cancel_release(GtkMediaStream *media_stream)
{
	guint source = GPOINTER_TO_UINT(g_object_get_data(G_OBJECT(media_stream), "release_source"));

	if (source) {
		g_source_remove(source);
		g_object_set_data(G_OBJECT(media_stream), "release_source", NULL);
	}
}

static gboolean release_pipeline(gpointer user_data)
{
	GtkMediaStream *media_stream = user_data;

	g_object_set_data(G_OBJECT(media_stream), "release_source", NULL);
	if (!gtk_media_stream_get_playing(media_stream))
		gtk_media_file_clear(GTK_MEDIA_FILE(media_stream));
	return G_SOURCE_REMOVE;
}

static void schedule_release(GtkMediaStream *media_stream)
{
	cancel_release(media_stream);
	g_object_set_data(G_OBJECT(media_stream), "release_source",
	                  GUINT_TO_POINTER(g_timeout_add_seconds_full(G_PRIORITY_DEFAULT, RELEASE_DELAY_SECONDS, release_pipeline,
	                                                              g_object_ref(media_stream), g_object_unref)));
}

/* opens the file again after release_pipeline() closed it */
static void ensure_open(GtkMediaStream *media_stream)
{
	if (!gtk_media_file_get_file(GTK_MEDIA_FILE(media_stream)))
		gtk_media_file_set_filename(GTK_MEDIA_FILE(media_stream), g_object_get_data(G_OBJECT(media_stream), "path"));
}

static void on_ended(GtkMediaStream *media_stream, GParamSpec *pspec, gpointer user_data)
{
	JNIEnv *env = get_jni_env();
	jobject listener;

	if (!gtk_media_stream_get_ended(media_stream))
		return;
	schedule_release(media_stream);
	listener = g_object_get_data(G_OBJECT(media_stream), "listener");
	if (listener)
		J__MediaPlayer__OnCompletionListener__onCompletion(env, listener, g_object_get_data(G_OBJECT(media_stream), "media_player"));
}

JNIEXPORT jlong JNICALL Java_android_media_MediaPlayer_native_1create(JNIEnv *env, jobject this, jstring path_jstr)
{
	const char *path = _CSTRING(path_jstr);

	GtkMediaStream *media_stream = gtk_media_file_new_for_filename(path);

	g_object_set_data_full(G_OBJECT(media_stream), "path", g_strdup(path), g_free);
	g_object_set_data(G_OBJECT(media_stream), "media_player", _REF(this));
	g_signal_connect(media_stream, "notify::ended", G_CALLBACK(on_ended), NULL);
	(*env)->ReleaseStringUTFChars(env, path_jstr, path);
	return _INTPTR(media_stream);
}

static void unref_listener(gpointer listener)
{
	JNIEnv *env = get_jni_env();

	_UNREF(listener);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1setOnCompletionListener(JNIEnv *env, jclass this, jlong media_stream_ptr, jobject listener)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	g_object_set_data_full(G_OBJECT(media_stream), "listener", listener ? _REF(listener) : NULL, unref_listener);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1start(JNIEnv *env, jclass this, jlong media_stream_ptr, jfloat volume)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	cancel_release(media_stream);
	ensure_open(media_stream);
	gtk_media_stream_set_volume(media_stream, volume);
	gtk_media_stream_play(media_stream);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1pause(JNIEnv *env, jclass this, jlong media_stream_ptr)
{
	gtk_media_stream_pause(GTK_MEDIA_STREAM(_PTR(media_stream_ptr)));
}

/* Android's stop() releases the decoders; start() after it needs prepare() first */
JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1stop(JNIEnv *env, jclass this, jlong media_stream_ptr)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	gtk_media_stream_pause(media_stream);
	cancel_release(media_stream);
	gtk_media_file_clear(GTK_MEDIA_FILE(media_stream));
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1seekTo(JNIEnv *env, jclass this, jlong media_stream_ptr, jint msec)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	ensure_open(media_stream);
	// convert from milliseconds to microseconds
	gtk_media_stream_seek(media_stream, msec * (gint64)1000);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1setLooping(JNIEnv *env, jclass this, jlong media_stream_ptr, jboolean looping)
{
	gtk_media_stream_set_loop(GTK_MEDIA_STREAM(_PTR(media_stream_ptr)), looping);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1setVolume(JNIEnv *env, jclass this, jlong media_stream_ptr, jfloat volume)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	/* without an open file there is no pipeline to tell; start() sets the volume */
	if (gtk_media_file_get_file(GTK_MEDIA_FILE(media_stream)))
		gtk_media_stream_set_volume(media_stream, volume);
}

JNIEXPORT jboolean JNICALL Java_android_media_MediaPlayer_native_1isPlaying(JNIEnv *env, jclass this, jlong media_stream_ptr)
{
	return gtk_media_stream_get_playing(GTK_MEDIA_STREAM(_PTR(media_stream_ptr)));
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1release(JNIEnv *env, jclass this, jlong media_stream_ptr)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	cancel_release(media_stream);
	gtk_media_stream_pause(media_stream);
	g_signal_handlers_disconnect_by_func(media_stream, on_ended, NULL);
	g_object_set_data(G_OBJECT(media_stream), "listener", NULL);
	_UNREF(g_object_get_data(G_OBJECT(media_stream), "media_player"));
	g_object_set_data(G_OBJECT(media_stream), "media_player", NULL);
	g_object_unref(media_stream);
}

JNIEXPORT jint JNICALL Java_android_media_MediaPlayer_native_1getDuration(JNIEnv *env, jclass this, jlong media_stream_ptr)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	// convert from microseconds to milliseconds
	return gtk_media_stream_get_duration(media_stream) / 1000;
}

JNIEXPORT jint JNICALL Java_android_media_MediaPlayer_native_1getCurrentPosition(JNIEnv *env, jclass this, jlong media_stream_ptr)
{
	GtkMediaStream *media_stream = _PTR(media_stream_ptr);

	// convert from microseconds to milliseconds
	return gtk_media_stream_get_timestamp(media_stream) / 1000;
}
