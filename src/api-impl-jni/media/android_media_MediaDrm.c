/*
 * Widevine for MediaDrm, MediaCrypto and MediaCodec.queueSecureInputBuffer, through the Widevine
 * CDM that Chrome and Firefox use on Linux (Chromium's CDM interface 10).
 *
 * ATL does not ship the CDM: it is loaded from $ATL_WIDEVINE_CDM, or from
 * ~/.local/share/widevine/libwidevinecdm.so (an app Flatpak needs --filesystem=xdg-data/widevine:ro).
 * Without it, no DRM scheme is supported, as before.
 *
 * The CDM is a C++ object; its interface, and the host and buffer objects it calls back, are
 * written out here as vtables, in the order of content_decryption_module.h (Chromium, BSD). The
 * CDM is not thread safe: every call goes through cdm_lock. Its timers run on a thread of their own.
 * Only temporary (streaming) sessions are supported, with security level L3 and the decrypted
 * samples going to ATL's software decoders.
 */

#include <dlfcn.h>
#include <glib.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_media_MediaDrm.h"

#define CDM_INTERFACE_VERSION 10
#define HOST_INTERFACE_VERSION 10
#define WIDEVINE_KEY_SYSTEM "com.widevine.alpha"

enum { STATUS_SUCCESS = 0, STATUS_NO_KEY = 2, STATUS_DECRYPT_ERROR = 4 };
enum { SCHEME_UNENCRYPTED = 0, SCHEME_CENC = 1, SCHEME_CBCS = 2 };
enum { SESSION_TEMPORARY = 0 };
enum { INIT_DATA_CENC = 0 };
enum { LINK_TYPE_INTERNAL = 1 << 1 };

struct subsample_entry {
	uint32_t clear_bytes;
	uint32_t cipher_bytes;
};

struct input_buffer_2 {
	const uint8_t *data;
	uint32_t data_size;
	uint32_t encryption_scheme;
	const uint8_t *key_id;
	uint32_t key_id_size;
	uint32_t pad1;
	const uint8_t *iv;
	uint32_t iv_size;
	uint32_t pad2;
	const struct subsample_entry *subsamples;
	uint32_t num_subsamples;
	uint32_t pad3;
	uint32_t crypt_byte_block;
	uint32_t skip_byte_block;
	int64_t timestamp;
};
_Static_assert(sizeof(struct input_buffer_2) == 80, "InputBuffer_2 layout");

struct key_information {
	const uint8_t *key_id;
	uint32_t key_id_size;
	uint32_t status;
	uint32_t system_code;
};

/* ContentDecryptionModule_10 */
struct cdm;
struct cdm_vtable {
	void (*Initialize)(struct cdm *, bool allow_distinctive_identifier, bool allow_persistent_state, bool use_hw_secure_codecs);
	void (*GetStatusForPolicy)(struct cdm *, uint32_t promise_id, const void *policy);
	void (*SetServerCertificate)(struct cdm *, uint32_t promise_id, const uint8_t *data, uint32_t size);
	void (*CreateSessionAndGenerateRequest)(struct cdm *, uint32_t promise_id, uint32_t session_type, uint32_t init_data_type, const uint8_t *init_data, uint32_t size);
	void (*LoadSession)(struct cdm *, uint32_t promise_id, uint32_t session_type, const char *session_id, uint32_t size);
	void (*UpdateSession)(struct cdm *, uint32_t promise_id, const char *session_id, uint32_t session_id_size, const uint8_t *response, uint32_t response_size);
	void (*CloseSession)(struct cdm *, uint32_t promise_id, const char *session_id, uint32_t session_id_size);
	void (*RemoveSession)(struct cdm *, uint32_t promise_id, const char *session_id, uint32_t session_id_size);
	void (*TimerExpired)(struct cdm *, void *context);
	uint32_t (*Decrypt)(struct cdm *, const struct input_buffer_2 *encrypted, void *decrypted_block);
	void *InitializeAudioDecoder;
	void *InitializeVideoDecoder;
	void *DeinitializeDecoder;
	void *ResetDecoder;
	void *DecryptAndDecodeFrame;
	void *DecryptAndDecodeSamples;
	void (*OnPlatformChallengeResponse)(struct cdm *, const void *response);
	void (*OnQueryOutputProtectionStatus)(struct cdm *, uint32_t result, uint32_t link_mask, uint32_t output_protection_mask);
	void (*OnStorageId)(struct cdm *, uint32_t version, const uint8_t *storage_id, uint32_t storage_id_size);
	void (*Destroy)(struct cdm *);
};
struct cdm {
	const struct cdm_vtable *vtable;
};

/* cdm::Buffer */
struct cdm_buffer {
	const void *vtable;
	uint8_t *data;
	uint32_t capacity;
	uint32_t size;
};
static void buffer_destroy(struct cdm_buffer *self)
{
	free(self->data);
	free(self);
}
static uint32_t buffer_capacity(struct cdm_buffer *self) { return self->capacity; }
static uint8_t *buffer_data(struct cdm_buffer *self) { return self->data; }
static void buffer_set_size(struct cdm_buffer *self, uint32_t size) { self->size = size; }
static uint32_t buffer_size(struct cdm_buffer *self) { return self->size; }
static void buffer_dtor(struct cdm_buffer *self) {}
static void buffer_deleting_dtor(struct cdm_buffer *self) { buffer_destroy(self); }
static const void *const buffer_vtable[] = {
	buffer_destroy, buffer_capacity, buffer_data, buffer_set_size, buffer_size, buffer_dtor, buffer_deleting_dtor,
};

/* cdm::DecryptedBlock */
struct decrypted_block {
	const void *vtable;
	struct cdm_buffer *buffer;
	int64_t timestamp;
};
static void block_set_buffer(struct decrypted_block *self, struct cdm_buffer *buffer) { self->buffer = buffer; }
static struct cdm_buffer *block_buffer(struct decrypted_block *self) { return self->buffer; }
static void block_set_timestamp(struct decrypted_block *self, int64_t timestamp) { self->timestamp = timestamp; }
static int64_t block_timestamp(struct decrypted_block *self) { return self->timestamp; }
static void block_dtor(struct decrypted_block *self) {}
static const void *const block_vtable[] = {
	block_set_buffer, block_buffer, block_set_timestamp, block_timestamp, block_dtor, block_dtor,
};

/* ---- state ---- */

static GRecMutex cdm_lock;
static GMutex result_lock;
static GCond result_cond;
static struct cdm *cdm;
static void *cdm_library;
static gboolean load_tried;
static GMainContext *timer_context;

/* the outcome of the promise the caller waits for, and the newest message of each session */
struct promise_result {
	uint32_t promise_id;
	gboolean done;
	gboolean ok;
	char *session_id;
	char *error;
};
static struct promise_result pending;
static uint32_t next_promise_id = 1;
static GHashTable *session_messages; /* CDM session id -> GBytes */

/* ---- host (cdm::Host_10) ---- */

static struct cdm_buffer *host_allocate(void *self, uint32_t capacity)
{
	struct cdm_buffer *buffer = calloc(1, sizeof(*buffer));
	buffer->vtable = buffer_vtable;
	buffer->data = malloc(capacity ? capacity : 1);
	buffer->capacity = capacity;
	return buffer;
}

static gboolean timer_cb(gpointer context)
{
	g_rec_mutex_lock(&cdm_lock);
	if (cdm)
		cdm->vtable->TimerExpired(cdm, context);
	g_rec_mutex_unlock(&cdm_lock);
	return G_SOURCE_REMOVE;
}

static void host_set_timer(void *self, int64_t delay_ms, void *context)
{
	GSource *source = g_timeout_source_new(delay_ms < 0 ? 0 : delay_ms);
	g_source_set_callback(source, timer_cb, context, NULL);
	g_source_attach(source, timer_context);
	g_source_unref(source);
}

static double host_get_current_wall_time(void *self)
{
	return g_get_real_time() / 1000000.0;
}

static void finish_promise(uint32_t promise_id, gboolean ok, const char *session_id, uint32_t session_id_size, const char *error, uint32_t error_size)
{
	g_mutex_lock(&result_lock);
	if (pending.promise_id == promise_id) {
		pending.ok = ok;
		pending.session_id = session_id ? g_strndup(session_id, session_id_size) : NULL;
		pending.error = error ? g_strndup(error, error_size) : NULL;
		pending.done = TRUE;
		g_cond_broadcast(&result_cond);
	}
	g_mutex_unlock(&result_lock);
}

static void host_on_initialized(void *self, bool success)
{
	if (!success)
		fprintf(stderr, "Widevine: the CDM failed to initialize\n");
}

static void host_on_resolve_key_status_promise(void *self, uint32_t promise_id, uint32_t key_status)
{
	finish_promise(promise_id, TRUE, NULL, 0, NULL, 0);
}

static void host_on_resolve_new_session_promise(void *self, uint32_t promise_id, const char *session_id, uint32_t session_id_size)
{
	finish_promise(promise_id, TRUE, session_id, session_id_size, NULL, 0);
}

static void host_on_resolve_promise(void *self, uint32_t promise_id)
{
	finish_promise(promise_id, TRUE, NULL, 0, NULL, 0);
}

static void host_on_reject_promise(void *self, uint32_t promise_id, uint32_t exception, uint32_t system_code, const char *message, uint32_t message_size)
{
	fprintf(stderr, "Widevine: promise %u rejected (exception %u, system code %u): %.*s\n", promise_id, exception, system_code, (int)message_size, message);
	finish_promise(promise_id, FALSE, NULL, 0, message ? message : "rejected", message ? message_size : 8);
}

static void host_on_session_message(void *self, const char *session_id, uint32_t session_id_size, uint32_t message_type, const char *message, uint32_t message_size)
{
	g_mutex_lock(&result_lock);
	g_hash_table_replace(session_messages, g_strndup(session_id, session_id_size), g_bytes_new(message, message_size));
	g_cond_broadcast(&result_cond);
	g_mutex_unlock(&result_lock);
}

static void host_on_session_keys_change(void *self, const char *session_id, uint32_t session_id_size, bool has_additional_usable_key, const struct key_information *keys, uint32_t count)
{
	for (uint32_t i = 0; i < count; i++)
		if (keys[i].status != 0)
			fprintf(stderr, "Widevine: key %u of session %.*s has status %u\n", i, (int)session_id_size, session_id, keys[i].status);
}

static void host_on_expiration_change(void *self, const char *session_id, uint32_t session_id_size, double new_expiry_time) {}

static void host_on_session_closed(void *self, const char *session_id, uint32_t session_id_size) {}

static void host_send_platform_challenge(void *self, const char *service_id, uint32_t service_id_size, const char *challenge, uint32_t challenge_size) {}

static void host_enable_output_protection(void *self, uint32_t desired_protection_mask) {}

/* the screen is the phone's own: an internal link without protection, answered after the call returns */
static gboolean answer_output_protection(gpointer user_data)
{
	g_rec_mutex_lock(&cdm_lock);
	if (cdm)
		cdm->vtable->OnQueryOutputProtectionStatus(cdm, 0, LINK_TYPE_INTERNAL, 0);
	g_rec_mutex_unlock(&cdm_lock);
	return G_SOURCE_REMOVE;
}

static void host_query_output_protection_status(void *self)
{
	g_main_context_invoke(timer_context, answer_output_protection, NULL);
}

static void host_on_deferred_initialization_done(void *self, uint32_t stream_type, uint32_t decoder_status) {}

static void *host_create_file_io(void *self, void *client)
{
	/* only asked for with persistent state, which is not allowed */
	fprintf(stderr, "Widevine: CreateFileIO is not supported\n");
	return NULL;
}

static gboolean answer_storage_id(gpointer user_data)
{
	g_rec_mutex_lock(&cdm_lock);
	if (cdm)
		cdm->vtable->OnStorageId(cdm, GPOINTER_TO_UINT(user_data), NULL, 0);
	g_rec_mutex_unlock(&cdm_lock);
	return G_SOURCE_REMOVE;
}

static void host_request_storage_id(void *self, uint32_t version)
{
	g_main_context_invoke(timer_context, answer_storage_id, GUINT_TO_POINTER(version));
}

static void host_dtor(void *self) {}

static const void *const host_vtable[] = {
	host_allocate,
	host_set_timer,
	host_get_current_wall_time,
	host_on_initialized,
	host_on_resolve_key_status_promise,
	host_on_resolve_new_session_promise,
	host_on_resolve_promise,
	host_on_reject_promise,
	host_on_session_message,
	host_on_session_keys_change,
	host_on_expiration_change,
	host_on_session_closed,
	host_send_platform_challenge,
	host_enable_output_protection,
	host_query_output_protection_status,
	host_on_deferred_initialization_done,
	host_create_file_io,
	host_request_storage_id,
	host_dtor,
	host_dtor,
};
static struct {
	const void *vtable;
} host = {host_vtable};

static void *get_host(int host_interface_version, void *user_data)
{
	return host_interface_version == HOST_INTERFACE_VERSION ? &host : NULL;
}

static gpointer timer_thread(gpointer user_data)
{
	GMainLoop *loop = g_main_loop_new(timer_context, FALSE);
	g_main_loop_run(loop);
	return NULL;
}

static char *cdm_path(void)
{
	const char *env = g_getenv("ATL_WIDEVINE_CDM");
	if (env && *env)
		return g_strdup(env);
	/* the real home, as an app Flatpak's XDG_DATA_HOME points into its own data */
	return g_build_filename(g_get_home_dir(), ".local", "share", "widevine", "libwidevinecdm.so", NULL);
}

/* loads the CDM once; FALSE if it is not there or does not start */
static gboolean ensure_cdm(void)
{
	g_rec_mutex_lock(&cdm_lock);
	if (load_tried) {
		gboolean ok = cdm != NULL;
		g_rec_mutex_unlock(&cdm_lock);
		return ok;
	}
	load_tried = TRUE;
	char *path = cdm_path();
	cdm_library = dlopen(path, RTLD_NOW | RTLD_LOCAL);
	if (!cdm_library) {
		fprintf(stderr, "Widevine: no CDM (%s), so no DRM scheme is supported\n", dlerror());
		g_free(path);
		g_rec_mutex_unlock(&cdm_lock);
		return FALSE;
	}
	void (*initialize_module)(void) = dlsym(cdm_library, "InitializeCdmModule_4");
	void *(*create_instance)(int, const char *, uint32_t, void *(*)(int, void *), void *) = dlsym(cdm_library, "CreateCdmInstance");
	const char *(*get_version)(void) = dlsym(cdm_library, "GetCdmVersion");
	if (!initialize_module || !create_instance) {
		fprintf(stderr, "Widevine: %s is not a CDM of interface %d\n", path, CDM_INTERFACE_VERSION);
		g_free(path);
		g_rec_mutex_unlock(&cdm_lock);
		return FALSE;
	}
	session_messages = g_hash_table_new_full(g_str_hash, g_str_equal, g_free, (GDestroyNotify)g_bytes_unref);
	timer_context = g_main_context_new();
	g_thread_unref(g_thread_new("widevine-timers", timer_thread, NULL));
	initialize_module();
	cdm = create_instance(CDM_INTERFACE_VERSION, WIDEVINE_KEY_SYSTEM, strlen(WIDEVINE_KEY_SYSTEM), get_host, NULL);
	if (cdm) {
		fprintf(stderr, "Widevine: CDM %s loaded from %s\n", get_version ? get_version() : "?", path);
		cdm->vtable->Initialize(cdm, false, false, false);
	} else {
		fprintf(stderr, "Widevine: %s did not create a CDM\n", path);
	}
	g_free(path);
	gboolean ok = cdm != NULL;
	g_rec_mutex_unlock(&cdm_lock);
	return ok;
}

/* starts a promise; call with cdm_lock held, then wait_promise after the CDM call */
static uint32_t begin_promise(void)
{
	g_mutex_lock(&result_lock);
	g_free(pending.session_id);
	g_free(pending.error);
	memset(&pending, 0, sizeof(pending));
	pending.promise_id = next_promise_id++;
	uint32_t id = pending.promise_id;
	g_mutex_unlock(&result_lock);
	return id;
}

/* waits for the promise begun last; FALSE on rejection or after 10 seconds. Call without cdm_lock,
 * as the CDM may answer from a timer */
static gboolean wait_promise(char **session_id, char **error)
{
	gint64 end = g_get_monotonic_time() + 10 * G_TIME_SPAN_SECOND;
	g_mutex_lock(&result_lock);
	while (!pending.done)
		if (!g_cond_wait_until(&result_cond, &result_lock, end))
			break;
	gboolean ok = pending.done && pending.ok;
	if (session_id)
		*session_id = g_strdup(pending.session_id);
	if (error)
		*error = g_strdup(pending.done ? pending.error : "the CDM did not answer");
	g_mutex_unlock(&result_lock);
	return ok;
}

/* waits for the first message of a session after a request */
static GBytes *wait_message(const char *session_id)
{
	gint64 end = g_get_monotonic_time() + 10 * G_TIME_SPAN_SECOND;
	GBytes *message = NULL;
	g_mutex_lock(&result_lock);
	while (!(message = g_hash_table_lookup(session_messages, session_id)))
		if (!g_cond_wait_until(&result_cond, &result_lock, end))
			break;
	if (message) {
		g_bytes_ref(message);
		g_hash_table_remove(session_messages, session_id);
	}
	g_mutex_unlock(&result_lock);
	return message;
}

static jbyteArray bytes_to_java(JNIEnv *env, GBytes *bytes)
{
	gsize size;
	const void *data = g_bytes_get_data(bytes, &size);
	jbyteArray array = (*env)->NewByteArray(env, size);
	(*env)->SetByteArrayRegion(env, array, 0, size, data);
	return array;
}

static void throw_drm(JNIEnv *env, const char *class_name, const char *message)
{
	(*env)->ThrowNew(env, (*env)->FindClass(env, class_name), message);
}

/* ---- JNI ---- */

JNIEXPORT jboolean JNICALL Java_android_media_MediaDrm_native_1isAvailable(JNIEnv *env, jclass clazz)
{
	return ensure_cdm();
}

JNIEXPORT jstring JNICALL Java_android_media_MediaDrm_native_1getVersion(JNIEnv *env, jclass clazz)
{
	if (!ensure_cdm())
		return NULL;
	const char *(*get_version)(void) = dlsym(cdm_library, "GetCdmVersion");
	return get_version ? _JSTRING(get_version()) : NULL;
}

/* creates a CDM session for the init data and returns {session id, request}; the init data must be a pssh box */
JNIEXPORT jobjectArray JNICALL Java_android_media_MediaDrm_native_1generateRequest(JNIEnv *env, jclass clazz, jbyteArray init_data)
{
	if (!ensure_cdm()) {
		throw_drm(env, "java/lang/IllegalStateException", "no Widevine CDM");
		return NULL;
	}
	jsize size = (*env)->GetArrayLength(env, init_data);
	jbyte *data = (*env)->GetByteArrayElements(env, init_data, NULL);
	g_rec_mutex_lock(&cdm_lock);
	uint32_t promise = begin_promise();
	cdm->vtable->CreateSessionAndGenerateRequest(cdm, promise, SESSION_TEMPORARY, INIT_DATA_CENC, (const uint8_t *)data, size);
	g_rec_mutex_unlock(&cdm_lock);
	(*env)->ReleaseByteArrayElements(env, init_data, data, JNI_ABORT);

	char *session_id = NULL, *error = NULL;
	if (!wait_promise(&session_id, &error) || !session_id) {
		throw_drm(env, "android/media/NotProvisionedException", error ? error : "no session");
		g_free(session_id);
		g_free(error);
		return NULL;
	}
	g_free(error);
	GBytes *message = wait_message(session_id);
	if (!message) {
		throw_drm(env, "java/lang/IllegalStateException", "the CDM made no license request");
		g_free(session_id);
		return NULL;
	}
	jobjectArray result = (*env)->NewObjectArray(env, 2, (*env)->FindClass(env, "java/lang/Object"), NULL);
	(*env)->SetObjectArrayElement(env, result, 0, _JSTRING(session_id));
	(*env)->SetObjectArrayElement(env, result, 1, bytes_to_java(env, message));
	g_bytes_unref(message);
	g_free(session_id);
	return result;
}

/* a message the CDM sent for a session since the last call (license renewal), or null */
JNIEXPORT jbyteArray JNICALL Java_android_media_MediaDrm_native_1takeMessage(JNIEnv *env, jclass clazz, jstring session_id)
{
	if (!ensure_cdm())
		return NULL;
	const char *id = (*env)->GetStringUTFChars(env, session_id, NULL);
	g_mutex_lock(&result_lock);
	GBytes *message = g_hash_table_lookup(session_messages, id);
	if (message) {
		g_bytes_ref(message);
		g_hash_table_remove(session_messages, id);
	}
	g_mutex_unlock(&result_lock);
	(*env)->ReleaseStringUTFChars(env, session_id, id);
	if (!message)
		return NULL;
	jbyteArray array = bytes_to_java(env, message);
	g_bytes_unref(message);
	return array;
}

/* gives the CDM the license server's response; returns null or the reason it was refused */
JNIEXPORT jstring JNICALL Java_android_media_MediaDrm_native_1update(JNIEnv *env, jclass clazz, jstring session_id, jbyteArray response)
{
	if (!ensure_cdm())
		return _JSTRING("no Widevine CDM");
	const char *id = (*env)->GetStringUTFChars(env, session_id, NULL);
	jsize size = (*env)->GetArrayLength(env, response);
	jbyte *data = (*env)->GetByteArrayElements(env, response, NULL);
	g_rec_mutex_lock(&cdm_lock);
	uint32_t promise = begin_promise();
	cdm->vtable->UpdateSession(cdm, promise, id, strlen(id), (const uint8_t *)data, size);
	g_rec_mutex_unlock(&cdm_lock);
	(*env)->ReleaseByteArrayElements(env, response, data, JNI_ABORT);
	(*env)->ReleaseStringUTFChars(env, session_id, id);
	char *error = NULL;
	gboolean ok = wait_promise(NULL, &error);
	jstring ret = ok ? NULL : _JSTRING(error ? error : "refused");
	g_free(error);
	return ret;
}

JNIEXPORT void JNICALL Java_android_media_MediaDrm_native_1close(JNIEnv *env, jclass clazz, jstring session_id)
{
	if (!ensure_cdm())
		return;
	const char *id = (*env)->GetStringUTFChars(env, session_id, NULL);
	g_rec_mutex_lock(&cdm_lock);
	uint32_t promise = begin_promise();
	cdm->vtable->CloseSession(cdm, promise, id, strlen(id));
	g_rec_mutex_unlock(&cdm_lock);
	(*env)->ReleaseStringUTFChars(env, session_id, id);
	wait_promise(NULL, NULL);
	g_mutex_lock(&result_lock);
	const char *key = (*env)->GetStringUTFChars(env, session_id, NULL);
	g_hash_table_remove(session_messages, key);
	(*env)->ReleaseStringUTFChars(env, session_id, key);
	g_mutex_unlock(&result_lock);
}

/* decrypts size bytes at offset of a buffer in place; mode as MediaCodec.CryptoInfo (1 CTR = cenc,
 * 2 CBC = cbcs). Returns 0, or 2 when the key is not there (yet), or another CDM status */
JNIEXPORT jint JNICALL Java_android_media_MediaDrm_native_1decrypt(JNIEnv *env, jclass clazz, jobject buffer, jint offset, jint size,
                                                                    jbyteArray key_id, jbyteArray iv, jint mode, jintArray clear_bytes, jintArray cipher_bytes,
                                                                    jint num_subsamples, jint crypt_blocks, jint skip_blocks, jlong timestamp)
{
	if (!ensure_cdm())
		return STATUS_DECRYPT_ERROR;
	/* the arrays first: get_nio_buffer holds a heap buffer's array critical, no JNI calls after it */
	jbyte *key = key_id ? (*env)->GetByteArrayElements(env, key_id, NULL) : NULL;
	jbyte *ivp = iv ? (*env)->GetByteArrayElements(env, iv, NULL) : NULL;
	jint *clear = clear_bytes ? (*env)->GetIntArrayElements(env, clear_bytes, NULL) : NULL;
	jint *cipher = cipher_bytes ? (*env)->GetIntArrayElements(env, cipher_bytes, NULL) : NULL;
	struct subsample_entry *subsamples = NULL;
	if (num_subsamples > 0 && clear && cipher) {
		subsamples = g_new(struct subsample_entry, num_subsamples);
		for (int i = 0; i < num_subsamples; i++) {
			subsamples[i].clear_bytes = clear[i];
			subsamples[i].cipher_bytes = cipher[i];
		}
	}
	jsize key_size = key ? (*env)->GetArrayLength(env, key_id) : 0;
	jarray array_ref;
	jbyte *array;
	uint8_t *data = (uint8_t *)get_nio_buffer(env, buffer, &array_ref, &array);
	uint32_t status = STATUS_DECRYPT_ERROR;
	if (!data)
		goto out;
	data += offset;
	struct input_buffer_2 input = {
		.data = data,
		.data_size = size,
		.encryption_scheme = mode == 2 ? SCHEME_CBCS : mode == 1 ? SCHEME_CENC : SCHEME_UNENCRYPTED,
		.key_id = (const uint8_t *)key,
		.key_id_size = key_size,
		.iv = (const uint8_t *)ivp,
		.iv_size = ivp ? 16 : 0,
		.subsamples = subsamples,
		.num_subsamples = subsamples ? num_subsamples : 0,
		.crypt_byte_block = crypt_blocks,
		.skip_byte_block = skip_blocks,
		.timestamp = timestamp,
	};
	struct decrypted_block block = {block_vtable, NULL, 0};
	g_rec_mutex_lock(&cdm_lock);
	status = cdm->vtable->Decrypt(cdm, &input, &block);
	g_rec_mutex_unlock(&cdm_lock);
	if (status == STATUS_SUCCESS && block.buffer) {
		uint32_t n = MIN(block.buffer->size, (uint32_t)size);
		memcpy(data, block.buffer->data, n);
	}
	if (block.buffer)
		buffer_destroy(block.buffer);
	release_nio_buffer(env, array_ref, array);

out:
	g_free(subsamples);
	if (cipher)
		(*env)->ReleaseIntArrayElements(env, cipher_bytes, cipher, JNI_ABORT);
	if (clear)
		(*env)->ReleaseIntArrayElements(env, clear_bytes, clear, JNI_ABORT);
	if (ivp)
		(*env)->ReleaseByteArrayElements(env, iv, ivp, JNI_ABORT);
	if (key)
		(*env)->ReleaseByteArrayElements(env, key_id, key, JNI_ABORT);
	return status;
}
