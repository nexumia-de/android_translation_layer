#include <libspeechd.h>

#include "defines.h"
#include "util.h"

#include "generated_headers/android_speech_tts_TextToSpeech.h"

/* the callback doesn't take user data, so we look ip up via the client id */
static GHashTable *clientid_to_listener = NULL;
static GHashTable *msgid_to_javaid = NULL;

struct callback_data {
	size_t msg_id;
	size_t client_id;
};
gboolean _on_finished(struct callback_data *d)
{
	JNIEnv *env = get_jni_env();
	jobject utterance_id = g_hash_table_lookup(msgid_to_javaid, GSIZE_TO_POINTER(d->msg_id));
	if (utterance_id) {
		jobject listener = g_hash_table_lookup(clientid_to_listener, GSIZE_TO_POINTER(d->client_id));
		J__TextToSpeech__OnUtteranceCompletedListener__onUtteranceCompleted(env, listener, utterance_id);
		_UNREF(utterance_id);
		g_hash_table_remove(msgid_to_javaid, GSIZE_TO_POINTER(d->msg_id));
	}

	free(d);
	return G_SOURCE_REMOVE;
}

static void on_finished(size_t msg_id, size_t client_id, SPDNotificationType type)
{
	struct callback_data *callback_data = malloc(sizeof(struct callback_data));
	callback_data->msg_id = msg_id;
	callback_data->client_id = client_id;
	g_main_context_invoke(NULL, G_SOURCE_FUNC(_on_finished), callback_data);
}

JNIEXPORT jlong JNICALL Java_android_speech_tts_TextToSpeech_native_1initializer(JNIEnv *env, jclass this, jstring package_name)
{
	char *error = NULL;

	/* TODO - s/"ATL"/package_name/ */
	SPDConnection *connection = spd_open2(_CSTRING(package_name), NULL, NULL, SPD_MODE_THREADED, NULL, /* autospawn = */ true, &error);
	if (connection == NULL) {
		android_log_printf(ANDROID_LOG_ERROR, "Failed to connect to speechd: error: %s\n", error);
		free(error);
		return _INTPTR(NULL);
	}

	return _INTPTR(connection);
}

JNIEXPORT void JNICALL Java_android_speech_tts_TextToSpeech_native_1setOnUtteranceCompletedListener(JNIEnv *env, jclass this, jlong connection_ptr, jobject listener)
{
	SPDConnection *connection = _PTR(connection_ptr);

	if (!clientid_to_listener)
		clientid_to_listener = g_hash_table_new(NULL, NULL);

	if (!msgid_to_javaid)
		msgid_to_javaid = g_hash_table_new(NULL, NULL);

	jobject old_listener;
	if ((old_listener = g_hash_table_lookup(clientid_to_listener, GSIZE_TO_POINTER(spd_get_client_id(connection)))))
		_UNREF(old_listener);

	g_hash_table_replace(clientid_to_listener, GINT_TO_POINTER(spd_get_client_id(connection)), _REF(listener));

	connection->callback_end = on_finished;
	connection->callback_cancel = on_finished;

	spd_set_notification_on(connection, SPD_END);
	spd_set_notification_on(connection, SPD_CANCEL);
}

JNIEXPORT jboolean JNICALL Java_android_speech_tts_TextToSpeech_native_1setLanguage(JNIEnv *env, jclass this, jlong connection, jstring lang)
{
	return spd_set_language(_PTR(connection), _CSTRING(lang)) == 0;
}

JNIEXPORT jboolean JNICALL Java_android_speech_tts_TextToSpeech_native_1speak(JNIEnv *env, jclass this, jlong connection, jstring text, jstring utterance_id)
{
	size_t msg_id = spd_say(_PTR(connection), SPD_TEXT, _CSTRING(text));

	if (utterance_id)
		g_hash_table_insert(msgid_to_javaid, GSIZE_TO_POINTER(msg_id), _REF(utterance_id));

	return msg_id != -1;
}
