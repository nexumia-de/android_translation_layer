#include <glib.h>

#include "../defines.h"
#include "../generated_headers/android_media_SoundPool.h"
#include "atl_player.h"

JNIEXPORT jlong JNICALL Java_android_media_SoundPool_native_1constructor(JNIEnv *env, jclass)
{
	GArray *sound_pool_array = g_array_new(FALSE, FALSE, sizeof(ATLPlayer *));
	return _INTPTR(sound_pool_array);
}

JNIEXPORT jint JNICALL Java_android_media_SoundPool_nativeLoad(JNIEnv *env, jclass, jlong pool, jstring path)
{
	GArray *sound_pool_array = _PTR(pool);
	const char *nativePath = (*env)->GetStringUTFChars(env, path, NULL);
	ATLPlayer *player = atl_player_new(nativePath);
	(*env)->ReleaseStringUTFChars(env, path, nativePath);
	return g_array_append_val(sound_pool_array, player)->len - 1;
}

JNIEXPORT jint JNICALL Java_android_media_SoundPool_nativePlay(JNIEnv *env, jclass, jlong pool, jint soundID)
{
	GArray *sound_pool_array = _PTR(pool);
	ATLPlayer *player = g_array_index(sound_pool_array, ATLPlayer *, soundID);
	atl_player_play(player, TRUE);
	return 0;
}
