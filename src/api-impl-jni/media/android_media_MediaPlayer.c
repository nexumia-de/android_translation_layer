#include <glib.h>

#include "../audio/atl_player.h"
#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_media_MediaPlayer.h"

/* A MediaPlayer is an ATLPlayer (audio only). Its GStreamer pipeline (and
 * the threads that come with it) exists from the first call that needs it,
 * the Java side creates the player then, until a few seconds after playback
 * has ended. start() builds it again when it was released. */

struct media_player {
	ATLPlayer *player;
	jobject media_player; // global ref
	jobject listener;     // global ref or NULL
};

static void on_ended(ATLPlayer *player, gpointer user_data)
{
	struct media_player *mp = user_data;
	JNIEnv *env = get_jni_env();

	if (mp->listener)
		J__MediaPlayer__OnCompletionListener__onCompletion(env, mp->listener, mp->media_player);
}

JNIEXPORT jlong JNICALL Java_android_media_MediaPlayer_native_1create(JNIEnv *env, jobject this, jstring path_jstr)
{
	const char *path = _CSTRING(path_jstr);
	struct media_player *mp = g_new0(struct media_player, 1);

	mp->player = atl_player_new(path);
	mp->media_player = _REF(this);
	atl_player_set_ended_func(mp->player, on_ended, mp);
	(*env)->ReleaseStringUTFChars(env, path_jstr, path);
	return _INTPTR(mp);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1setOnCompletionListener(JNIEnv *env, jclass this, jlong ptr, jobject listener)
{
	struct media_player *mp = _PTR(ptr);

	if (mp->listener)
		_UNREF(mp->listener);
	mp->listener = listener ? _REF(listener) : NULL;
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1start(JNIEnv *env, jclass this, jlong ptr, jfloat volume)
{
	struct media_player *mp = _PTR(ptr);

	atl_player_set_volume(mp->player, volume);
	atl_player_play(mp->player, FALSE);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1pause(JNIEnv *env, jclass this, jlong ptr)
{
	atl_player_pause(((struct media_player *)_PTR(ptr))->player);
}

/* Android's stop() releases the decoders; start() after it needs prepare() first */
JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1stop(JNIEnv *env, jclass this, jlong ptr)
{
	atl_player_stop(((struct media_player *)_PTR(ptr))->player);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1seekTo(JNIEnv *env, jclass this, jlong ptr, jint msec)
{
	atl_player_seek(((struct media_player *)_PTR(ptr))->player, msec);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1setLooping(JNIEnv *env, jclass this, jlong ptr, jboolean looping)
{
	atl_player_set_loop(((struct media_player *)_PTR(ptr))->player, looping);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1setVolume(JNIEnv *env, jclass this, jlong ptr, jfloat volume)
{
	atl_player_set_volume(((struct media_player *)_PTR(ptr))->player, volume);
}

JNIEXPORT jboolean JNICALL Java_android_media_MediaPlayer_native_1isPlaying(JNIEnv *env, jclass this, jlong ptr)
{
	return atl_player_is_playing(((struct media_player *)_PTR(ptr))->player);
}

JNIEXPORT void JNICALL Java_android_media_MediaPlayer_native_1release(JNIEnv *env, jclass this, jlong ptr)
{
	struct media_player *mp = _PTR(ptr);

	atl_player_free(mp->player);
	if (mp->listener)
		_UNREF(mp->listener);
	_UNREF(mp->media_player);
	g_free(mp);
}

JNIEXPORT jint JNICALL Java_android_media_MediaPlayer_native_1getDuration(JNIEnv *env, jclass this, jlong ptr)
{
	return atl_player_get_duration(((struct media_player *)_PTR(ptr))->player);
}

JNIEXPORT jint JNICALL Java_android_media_MediaPlayer_native_1getCurrentPosition(JNIEnv *env, jclass this, jlong ptr)
{
	return atl_player_get_position(((struct media_player *)_PTR(ptr))->player);
}
