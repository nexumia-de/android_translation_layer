// SPDX-License-Identifier: GPL-3.0-only
//
// wl-pointer: click and swipe in a wlroots compositor (the headless cage of
// tools/headless-shot.sh) through one virtual pointer.
//
// wlrctl creates a new virtual pointer for every call. The seat then loses
// and regains its pointer capability, GTK binds a new wl_pointer that never
// got an enter event, and a click without motion is dropped. This tool keeps
// one device for all commands and waits until clients have bound it.
//
// Usage: wl-pointer [-s WxH] COMMAND...
//   -s WxH               coordinate space (default 360x720, the logical size)
//   move X Y             move to X,Y
//   down | up            press or release the left button
//   click                down and up
//   tap X Y              move and click
//   swipe X1 Y1 X2 Y2 MS press at X1,Y1, move to X2,Y2 in MS ms, release
//   wait MS              wait MS milliseconds
//   key NAME             press and release a key on a virtual keyboard, NAME an
//                        xkb keysym name with optional modifiers: Escape,
//                        alt+Left, ctrl+shift+Tab, XF86Back

#define _GNU_SOURCE
#include <linux/input-event-codes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <wayland-client.h>

#include <sys/mman.h>
#include <unistd.h>
#include <xkbcommon/xkbcommon.h>

#include "virtual-keyboard-unstable-v1-client-protocol.h"
#include "wlr-virtual-pointer-unstable-v1-client-protocol.h"

static struct wl_seat *seat;
static struct zwlr_virtual_pointer_manager_v1 *manager;
static struct zwlr_virtual_pointer_v1 *pointer;
static struct zwp_virtual_keyboard_manager_v1 *keyboard_manager;
static struct zwp_virtual_keyboard_v1 *keyboard;
static struct xkb_keymap *keymap;
static struct wl_display *display;
static unsigned int width = 360, height = 720;

static void registry_global(void *data, struct wl_registry *registry, uint32_t name, const char *interface, uint32_t version)
{
	if (!strcmp(interface, wl_seat_interface.name) && !seat)
		seat = wl_registry_bind(registry, name, &wl_seat_interface, 1);
	else if (!strcmp(interface, zwlr_virtual_pointer_manager_v1_interface.name))
		manager = wl_registry_bind(registry, name, &zwlr_virtual_pointer_manager_v1_interface, 1);
	else if (!strcmp(interface, zwp_virtual_keyboard_manager_v1_interface.name))
		keyboard_manager = wl_registry_bind(registry, name, &zwp_virtual_keyboard_manager_v1_interface, 1);
}

static void registry_global_remove(void *data, struct wl_registry *registry, uint32_t name)
{
}

static const struct wl_registry_listener registry_listener = {
	.global = registry_global,
	.global_remove = registry_global_remove,
};

static uint32_t now_ms(void)
{
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static void sleep_ms(long ms)
{
	struct timespec ts = {ms / 1000, (ms % 1000) * 1000000};
	nanosleep(&ts, NULL);
}

static void flush(void)
{
	zwlr_virtual_pointer_v1_frame(pointer);
	wl_display_roundtrip(display);
}

static void move(unsigned int x, unsigned int y)
{
	if (x >= width || y >= height) {
		fprintf(stderr, "wl-pointer: %u,%u is outside %ux%u\n", x, y, width, height);
		exit(1);
	}
	zwlr_virtual_pointer_v1_motion_absolute(pointer, now_ms(), x, y, width, height);
	flush();
}

static void button(int pressed)
{
	zwlr_virtual_pointer_v1_button(pointer, now_ms(), BTN_LEFT,
	                               pressed ? WL_POINTER_BUTTON_STATE_PRESSED : WL_POINTER_BUTTON_STATE_RELEASED);
	flush();
}

/* one keyboard for all key commands, with the default (us) keymap */
static void keyboard_create(void)
{
	if (!keyboard_manager) {
		fprintf(stderr, "wl-pointer: the compositor has no zwp_virtual_keyboard_manager_v1\n");
		exit(1);
	}
	struct xkb_context *context = xkb_context_new(XKB_CONTEXT_NO_FLAGS);
	keymap = xkb_keymap_new_from_names(context, NULL, XKB_KEYMAP_COMPILE_NO_FLAGS);
	char *text = xkb_keymap_get_as_string(keymap, XKB_KEYMAP_FORMAT_TEXT_V1);
	size_t size = strlen(text) + 1;
	int fd = memfd_create("wl-pointer-keymap", MFD_CLOEXEC);
	if (fd < 0 || write(fd, text, size) != (ssize_t)size) {
		perror("wl-pointer: keymap");
		exit(1);
	}
	keyboard = zwp_virtual_keyboard_manager_v1_create_virtual_keyboard(keyboard_manager, seat);
	zwp_virtual_keyboard_v1_keymap(keyboard, WL_KEYBOARD_KEYMAP_FORMAT_XKB_V1, fd, size);
	wl_display_roundtrip(display);
	close(fd);
	free(text);
	sleep_ms(300);
}

static xkb_keycode_t keycode_for(xkb_keysym_t sym)
{
	for (xkb_keycode_t code = xkb_keymap_min_keycode(keymap); code <= xkb_keymap_max_keycode(keymap); code++) {
		const xkb_keysym_t *syms;
		if (xkb_keymap_key_get_syms_by_level(keymap, code, 0, 0, &syms) > 0 && syms[0] == sym)
			return code;
	}
	return 0;
}

static void key(const char *spec)
{
	static const char *const mods[][2] = {{"shift+", XKB_MOD_NAME_SHIFT}, {"ctrl+", XKB_MOD_NAME_CTRL}, {"alt+", XKB_MOD_NAME_ALT}, {"super+", XKB_MOD_NAME_LOGO}};
	uint32_t depressed = 0;
	for (int found = 1; found;) {
		found = 0;
		for (int m = 0; m < 4; m++)
			if (!strncasecmp(spec, mods[m][0], strlen(mods[m][0]))) {
				depressed |= 1u << xkb_keymap_mod_get_index(keymap, mods[m][1]);
				spec += strlen(mods[m][0]);
				found = 1;
			}
	}
	xkb_keysym_t sym = xkb_keysym_from_name(spec, XKB_KEYSYM_CASE_INSENSITIVE);
	xkb_keycode_t code = sym ? keycode_for(sym) : 0;
	if (!code) {
		fprintf(stderr, "wl-pointer: no key %s in the keymap\n", spec);
		exit(1);
	}
	zwp_virtual_keyboard_v1_modifiers(keyboard, depressed, 0, 0, 0);
	zwp_virtual_keyboard_v1_key(keyboard, now_ms(), code - 8, WL_KEYBOARD_KEY_STATE_PRESSED);
	wl_display_roundtrip(display);
	sleep_ms(50);
	zwp_virtual_keyboard_v1_key(keyboard, now_ms(), code - 8, WL_KEYBOARD_KEY_STATE_RELEASED);
	zwp_virtual_keyboard_v1_modifiers(keyboard, 0, 0, 0, 0);
	wl_display_roundtrip(display);
}

static unsigned int arg(char **argv, int argc, int i)
{
	if (i >= argc) {
		fprintf(stderr, "wl-pointer: %s needs more arguments\n", argv[i - 1]);
		exit(1);
	}
	return strtoul(argv[i], NULL, 10);
}

int main(int argc, char **argv)
{
	int i = 1;
	if (argc > 2 && !strcmp(argv[1], "-s")) {
		if (sscanf(argv[2], "%ux%u", &width, &height) != 2 || !width || !height) {
			fprintf(stderr, "wl-pointer: -s takes WxH\n");
			return 1;
		}
		i = 3;
	}
	if (i >= argc) {
		fprintf(stderr, "usage: wl-pointer [-s WxH] move X Y | down | up | click | tap X Y | swipe X1 Y1 X2 Y2 MS | wait MS | key NAME ...\n");
		return 1;
	}

	display = wl_display_connect(NULL);
	if (!display) {
		fprintf(stderr, "wl-pointer: cannot connect to the Wayland display\n");
		return 1;
	}
	struct wl_registry *registry = wl_display_get_registry(display);
	wl_registry_add_listener(registry, &registry_listener, NULL);
	wl_display_roundtrip(display);
	if (!manager) {
		fprintf(stderr, "wl-pointer: the compositor has no zwlr_virtual_pointer_manager_v1\n");
		return 1;
	}
	pointer = zwlr_virtual_pointer_manager_v1_create_virtual_pointer(manager, seat);
	wl_display_roundtrip(display);
	// Give clients time to bind the seat's new pointer capability.
	sleep_ms(300);

	while (i < argc) {
		const char *cmd = argv[i++];
		if (!strcmp(cmd, "move")) {
			unsigned int x = arg(argv, argc, i), y = arg(argv, argc, i + 1);
			i += 2;
			move(x, y);
		} else if (!strcmp(cmd, "down")) {
			button(1);
		} else if (!strcmp(cmd, "up")) {
			button(0);
		} else if (!strcmp(cmd, "click")) {
			button(1);
			sleep_ms(50);
			button(0);
		} else if (!strcmp(cmd, "tap")) {
			unsigned int x = arg(argv, argc, i), y = arg(argv, argc, i + 1);
			i += 2;
			move(x, y);
			sleep_ms(50);
			button(1);
			sleep_ms(50);
			button(0);
		} else if (!strcmp(cmd, "swipe")) {
			unsigned int x1 = arg(argv, argc, i), y1 = arg(argv, argc, i + 1);
			unsigned int x2 = arg(argv, argc, i + 2), y2 = arg(argv, argc, i + 3);
			unsigned int ms = arg(argv, argc, i + 4);
			int steps = ms / 16 > 1 ? ms / 16 : 1;
			i += 5;
			move(x1, y1);
			button(1);
			for (int s = 1; s <= steps; s++) {
				sleep_ms(ms / steps);
				move(x1 + ((int)x2 - (int)x1) * s / steps, y1 + ((int)y2 - (int)y1) * s / steps);
			}
			button(0);
		} else if (!strcmp(cmd, "key")) {
			if (i >= argc) {
				fprintf(stderr, "wl-pointer: key needs a name\n");
				return 1;
			}
			if (!keyboard)
				keyboard_create();
			key(argv[i++]);
		} else if (!strcmp(cmd, "wait")) {
			sleep_ms(arg(argv, argc, i++));
		} else {
			fprintf(stderr, "wl-pointer: unknown command %s\n", cmd);
			return 1;
		}
	}

	sleep_ms(100);
	zwlr_virtual_pointer_v1_destroy(pointer);
	wl_display_roundtrip(display);
	wl_display_disconnect(display);
	return 0;
}
