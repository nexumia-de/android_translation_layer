#define _GNU_SOURCE /* memfd_create */
#include <jni.h>

#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

#include <xkbcommon/xkbcommon.h>

#include <gdk/wayland/gdkwayland.h>
#include <gtk/gtk.h>

#include "input-method-unstable-v2-client-protocol.h"
#include "virtual-keyboard-unstable-v1-client-protocol.h"

#include "defines.h"
#include "util.h"
#include "generated_headers/android_inputmethodservice_InputMethodService_ATLInputConnection.h"

#define INFO(x...)      android_log_printf(ANDROID_LOG_INFO, "ATLKeyboardIMS", x)
#define DEBUG(fmt, ...) android_log_printf(ANDROID_LOG_DEBUG, "ATLKeyboardIMS", "%s:%d: " fmt, __func__, __LINE__, ##__VA_ARGS__)

struct {
	struct wl_display *display;
	struct wl_registry *registry;
	struct wl_seat *seat;
	struct zwp_input_method_manager_v2 *input_method_manager;
	struct zwp_input_method_v2 *input_method;
	struct zwp_virtual_keyboard_manager_v1 *virtual_keyboard_manager;
	struct zwp_virtual_keyboard_v1 *virtual_keyboard;

	char compositing[4096];
	char surrounding[4096];
	guint text_len;
	guint cursor;
	guint anchor;

	/* double-buffered state, applied on "done" */
	gboolean pending_activate;
	gboolean pending_deactivate;
	guint32 content_hint;
	guint32 content_purpose;
	jobject connection; /* ATLInputConnection, global ref */
	gboolean keymap_sent;
	guint serial;
} osk = {0};

/* android_atl_ATLKeyboardDialog.c */
#ifdef ATL_HAS_OSK
extern void atlosk_set_visible(gboolean new_visible);
#else
static void atlosk_set_visible(gboolean new_visible)
{
	DEBUG("=%d\n", new_visible);
}
#endif

/*
 * input-method-unstable-v2
 */

static void
handle_activate(void *data,
                struct zwp_input_method_v2 *zwp_input_method_v2)
{
	/* a new text field: the old one's surrounding text and content type no longer apply */
	osk.text_len = 0;
	osk.cursor = 0;
	osk.anchor = 0;
	osk.surrounding[0] = '\0';
	osk.content_hint = 0;
	osk.content_purpose = 0;
	osk.pending_activate = TRUE;
	osk.pending_deactivate = FALSE;
}

static void
handle_deactivate(void *data,
                  struct zwp_input_method_v2 *zwp_input_method_v2)
{
	osk.pending_deactivate = TRUE;
	osk.pending_activate = FALSE;
}

static void
handle_surrounding_text(void *data,
                        struct zwp_input_method_v2 *zwp_input_method_v2,
                        const char *text,
                        uint32_t cursor,
                        uint32_t anchor)
{
	DEBUG("(cursor=%d, '%s')\n", cursor, text);
	osk.cursor = cursor;
	osk.anchor = anchor;
	osk.text_len = strnlen(text, sizeof(osk.surrounding));
	strncpy(osk.surrounding, text, sizeof(osk.surrounding));
}

static void
handle_text_change_cause(void *data,
                         struct zwp_input_method_v2 *zwp_input_method_v2,
                         uint32_t cause)
{
	//DEBUG("\n");
}

static void
handle_content_type(void *data,
                    struct zwp_input_method_v2 *zwp_input_method_v2,
                    uint32_t hint,
                    uint32_t purpose)
{
	osk.content_hint = hint;
	osk.content_purpose = purpose;
}

static void
handle_done(void *data,
            struct zwp_input_method_v2 *zwp_input_method_v2)
{
	osk.serial++;
	JNIEnv *env = get_jni_env();
	if (osk.pending_activate) {
		osk.pending_activate = FALSE;
		atlosk_set_visible(TRUE);
		if (osk.connection)
			J__InputMethodService__ATLInputConnection__onActivate(env, osk.connection, osk.content_hint, osk.content_purpose);
	} else if (osk.pending_deactivate) {
		osk.pending_deactivate = FALSE;
		atlosk_set_visible(FALSE);
		if (osk.connection)
			J__InputMethodService__ATLInputConnection__onDeactivate(env, osk.connection);
	}
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionDescribe(env);
		(*env)->ExceptionClear(env);
	}
}

static void
handle_unavailable(void *data,
                   struct zwp_input_method_v2 *zwp_input_method_v2)
{
	INFO("Input method unavailable");
}

static const struct zwp_input_method_v2_listener input_method_listener = {
	.activate = handle_activate,
	.deactivate = handle_deactivate,
	.surrounding_text = handle_surrounding_text,
	.text_change_cause = handle_text_change_cause,
	.content_type = handle_content_type,
	.done = handle_done,
	.unavailable = handle_unavailable,
};

static void
registry_handle_global(void *data,
                       struct wl_registry *registry,
                       uint32_t name,
                       const char *interface,
                       uint32_t version)
{
	if (!strcmp(interface, zwp_input_method_manager_v2_interface.name)) {
		osk.input_method_manager = wl_registry_bind(registry, name, &zwp_input_method_manager_v2_interface, 1);
		osk.input_method = zwp_input_method_manager_v2_get_input_method(osk.input_method_manager, osk.seat);
		zwp_input_method_v2_add_listener(osk.input_method, &input_method_listener, &osk);
	} else if (!strcmp(interface, zwp_virtual_keyboard_manager_v1_interface.name)) {
		osk.virtual_keyboard_manager = wl_registry_bind(registry, name, &zwp_virtual_keyboard_manager_v1_interface, 1);
		osk.virtual_keyboard = zwp_virtual_keyboard_manager_v1_create_virtual_keyboard(osk.virtual_keyboard_manager, osk.seat);
	}
}

static void
registry_handle_global_remove(void *data,
                              struct wl_registry *registry,
                              uint32_t name)
{
	INFO("Global %d removed but not handled", name);
}

static const struct wl_registry_listener registry_listener = {
	registry_handle_global,
	registry_handle_global_remove,
};

JNIEXPORT jlong JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeInit(JNIEnv *env, jobject this)
{
	GdkDisplay *gdk_display;
	GdkSeat *gdk_seat;
	extern GtkWindow *window; /* Main activity window. */

	INFO("Native init!");

	gdk_display = gtk_root_get_display(GTK_ROOT(window));
	if (gdk_display == NULL) {
		g_critical("ATLKeyboardIMS: Failed to get display: %m\n");
		return 0;
	}

	gdk_seat = gdk_display_get_default_seat(gdk_display);
	if (gdk_seat == NULL) {
		g_critical("ATLKeyboardIMS: Failed to get seat: %m\n");
		return 0;
	}

	osk.display = gdk_wayland_display_get_wl_display(gdk_display);
	osk.seat = gdk_wayland_seat_get_wl_seat(gdk_seat);
	osk.connection = _REF(this);
	osk.registry = wl_display_get_registry(osk.display);
	wl_registry_add_listener(osk.registry, &registry_listener, &osk);

	gtk_widget_set_visible(GTK_WIDGET(window), false);

	return 1;
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeSetCompositingText(JNIEnv *env, jobject this, jlong ptr, jstring text, jint newCursorPosition)
{
	const char *data = (*env)->GetStringUTFChars(env, text, NULL);

	INFO("nativeSetCompositingText('%s', cur=%d)\n", data, newCursorPosition);

	if (osk.input_method) {
		size_t text_len = strlen(data);
		int cursor;

		if (newCursorPosition > 0)
			cursor = text_len - newCursorPosition + 1;
		else
			cursor = -1 * newCursorPosition;

		zwp_input_method_v2_set_preedit_string(osk.input_method, data, cursor, cursor);
		strncpy(osk.compositing, data, sizeof(osk.compositing));
		zwp_input_method_v2_commit(osk.input_method, osk.serial);
	}

	return true;
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeSetCompositingRegion(JNIEnv *env, jobject this, jlong ptr, jint start, jint end)
{
	INFO("nativeSetCompositingRegion(start=%d, end=%d)\n", start, end);

	if (osk.input_method) {
		int beforeLength, afterLength, cursor = osk.cursor;
		char tmp[4096] = {0};

		if (start > end) {
			int tmp = end;
			end = start;
			start = tmp;
		}

		beforeLength = (end - start);
		afterLength = 0;

		strncpy(tmp, &osk.surrounding[cursor - beforeLength], beforeLength);
		cursor = strlen(tmp);

		zwp_input_method_v2_delete_surrounding_text(osk.input_method, beforeLength, afterLength);
		zwp_input_method_v2_set_preedit_string(osk.input_method, tmp, cursor, cursor);
		zwp_input_method_v2_commit(osk.input_method, osk.serial);
	}

	return true;
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeFinishComposingText(JNIEnv *env, jobject this, jlong ptr)
{
	INFO("nativeFinishCompositingText()\n");

	if (osk.input_method) {
		zwp_input_method_v2_commit_string(osk.input_method, osk.compositing);
		zwp_input_method_v2_commit(osk.input_method, osk.serial);
		osk.compositing[0] = '\0';
	}

	return true;
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeCommitText(JNIEnv *env, jobject this, jlong ptr, jstring text, jint newCursorPosition)
{
	const char *data = (*env)->GetStringUTFChars(env, text, NULL);

	INFO("nativeCommitText('%s', cur=%d)\n", data, newCursorPosition);

	if (osk.input_method) {
		zwp_input_method_v2_commit_string(osk.input_method, data);
		zwp_input_method_v2_commit(osk.input_method, osk.serial);
		osk.compositing[0] = '\0';
	}

	return true;
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeDeleteSurroundingText(JNIEnv *env, jobject this, jlong ptr, jint beforeLength, jint afterLength)
{
	INFO("nativeDeleteSurroundingText(before=%d, after=%d)\n", beforeLength, afterLength);

	if (osk.input_method) {
		zwp_input_method_v2_delete_surrounding_text(osk.input_method, beforeLength, afterLength);
		osk.cursor -= beforeLength;
		zwp_input_method_v2_commit(osk.input_method, osk.serial);
	}

	return true;
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeSetSelection(JNIEnv *env, jobject this, jlong ptr, jint start, jint end)
{
	INFO("nativeSetSelection(start=%d, end=%d)\n", start, end);

	if (osk.input_method) {
	}

	return true;
}

/* The virtual keyboard needs a keymap before its first key: the default (evdev, "us") one. */
static void send_keymap(void)
{
	struct xkb_context *ctx = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
	struct xkb_keymap *keymap = ctx ? xkb_keymap_new_from_names(ctx, NULL, XKB_KEYMAP_COMPILE_NO_FLAGS) : NULL;
	char *str = keymap ? xkb_keymap_get_as_string(keymap, XKB_KEYMAP_FORMAT_TEXT_V1) : NULL;
	if (str) {
		size_t size = strlen(str) + 1;
		int fd = memfd_create("atl-osk-keymap", MFD_CLOEXEC);
		if (fd >= 0 && write(fd, str, size) == (ssize_t)size) {
			zwp_virtual_keyboard_v1_keymap(osk.virtual_keyboard, XKB_KEYMAP_FORMAT_TEXT_V1, fd, size);
			osk.keymap_sent = TRUE;
		}
		if (fd >= 0)
			close(fd);
		free(str);
	}
	if (keymap)
		xkb_keymap_unref(keymap);
	if (ctx)
		xkb_context_unref(ctx);
}

/* Android key codes the keyboard sends as keys, to evdev codes */
static int evdev_code(long key)
{
	switch (key) {
		case 66: return 28;   /* ENTER */
		case 160: return 96;  /* NUMPAD_ENTER -> KP_ENTER */
		case 67: return 14;   /* DEL (backspace) */
		case 112: return 111; /* FORWARD_DEL */
		case 61: return 15;   /* TAB */
		case 111: return 1;   /* ESCAPE */
		case 21: return 105;  /* DPAD_LEFT */
		case 22: return 106;  /* DPAD_RIGHT */
		case 19: return 103;  /* DPAD_UP */
		case 20: return 108;  /* DPAD_DOWN */
		case 122: return 102; /* MOVE_HOME */
		case 123: return 107; /* MOVE_END */
		case 92: return 104;  /* PAGE_UP */
		case 93: return 109;  /* PAGE_DOWN */
		case 62: return 57;   /* SPACE */
		default: return -1;
	}
}

JNIEXPORT jboolean JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeSendKeyEvent(JNIEnv *env, jobject this, jlong ptr, jlong time, jlong key, jlong state)
{
	int code = evdev_code(key);
	DEBUG("key=%ld state=%ld evdev=%d\n", key, state, code);
	if (code < 0 || !osk.virtual_keyboard)
		return false;
	if (!osk.keymap_sent)
		send_keymap();
	/* the preedit is not in the field yet: commit it first, or Enter would act on the text without it */
	if (state == 0 && osk.input_method && osk.compositing[0]) {
		zwp_input_method_v2_commit_string(osk.input_method, osk.compositing);
		zwp_input_method_v2_commit(osk.input_method, osk.serial);
		osk.compositing[0] = '\0';
	}
	/* ACTION_DOWN 0 -> pressed, ACTION_UP 1 -> released */
	zwp_virtual_keyboard_v1_key(osk.virtual_keyboard, (uint32_t)time, code,
	                            state == 0 ? WL_KEYBOARD_KEY_STATE_PRESSED : WL_KEYBOARD_KEY_STATE_RELEASED);
	wl_display_flush(osk.display);
	return true;
}

JNIEXPORT jbyteArray JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeGetSurroundingText(JNIEnv *env, jobject this, jlong ptr)
{
	guint len = MIN(osk.text_len, sizeof(osk.surrounding) - 1);
	jbyteArray array = (*env)->NewByteArray(env, len);
	(*env)->SetByteArrayRegion(env, array, 0, len, (const jbyte *)osk.surrounding);
	return array;
}

JNIEXPORT jint JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeGetSurroundingCursor(JNIEnv *env, jobject this, jlong ptr)
{
	return osk.cursor;
}

JNIEXPORT jint JNICALL Java_android_inputmethodservice_InputMethodService_00024ATLInputConnection_nativeGetSurroundingAnchor(JNIEnv *env, jobject this, jlong ptr)
{
	return osk.anchor;
}
