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

#include <linux/input-event-codes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <wayland-client.h>

#include "wlr-virtual-pointer-unstable-v1-client-protocol.h"

static struct wl_seat *seat;
static struct zwlr_virtual_pointer_manager_v1 *manager;
static struct zwlr_virtual_pointer_v1 *pointer;
static struct wl_display *display;
static unsigned int width = 360, height = 720;

static void registry_global(void *data, struct wl_registry *registry, uint32_t name, const char *interface, uint32_t version)
{
	if (!strcmp(interface, wl_seat_interface.name) && !seat)
		seat = wl_registry_bind(registry, name, &wl_seat_interface, 1);
	else if (!strcmp(interface, zwlr_virtual_pointer_manager_v1_interface.name))
		manager = wl_registry_bind(registry, name, &zwlr_virtual_pointer_manager_v1_interface, 1);
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
		fprintf(stderr, "usage: wl-pointer [-s WxH] move X Y | down | up | click | tap X Y | swipe X1 Y1 X2 Y2 MS | wait MS ...\n");
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
