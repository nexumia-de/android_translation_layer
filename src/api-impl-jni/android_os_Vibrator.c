#include <gio/gio.h>
#include <gudev/gudev.h>

#include <fcntl.h>
#include <stdbool.h>
#include <unistd.h>

#include <linux/input.h>

#include "defines.h"
#include "util.h"
#include "generated_headers/android_os_Vibrator.h"

/* the "fd" for vibrating through feedbackd instead of a device file */
#define VIBRATOR_FEEDBACKD -2

/* feedbackd's haptic interface (org.sigxcpu.Feedback.Haptic, feedbackd 0.4 and later) works inside
 * a Flatpak sandbox, which hides the input devices, and follows the user's feedback profile */
static GDBusConnection *feedbackd_bus = NULL;

/* feedbackd exports the haptic interface only when it found a vibrator */
static gboolean feedbackd_has_haptic(void)
{
	GVariant *reply = g_dbus_connection_call_sync(feedbackd_bus, "org.sigxcpu.Feedback", "/org/sigxcpu/Feedback",
	                                              "org.freedesktop.DBus.Introspectable", "Introspect", NULL, G_VARIANT_TYPE("(s)"),
	                                              G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL);
	if (!reply)
		return FALSE;
	const char *xml;
	g_variant_get(reply, "(&s)", &xml);
	GDBusNodeInfo *node = g_dbus_node_info_new_for_xml(xml, NULL);
	gboolean found = node && g_dbus_node_info_lookup_interface(node, "org.sigxcpu.Feedback.Haptic");
	if (node)
		g_dbus_node_info_unref(node);
	g_variant_unref(reply);
	return found;
}

/* takes a floating pattern of (amplitude 0..1, milliseconds) pairs; an empty one stops vibrating */
static void feedbackd_vibrate(GVariant *pattern)
{
	GApplication *app = g_application_get_default();
	const char *app_id = app ? g_application_get_application_id(app) : NULL;
	g_dbus_connection_call(feedbackd_bus, "org.sigxcpu.Feedback", "/org/sigxcpu/Feedback", "org.sigxcpu.Feedback.Haptic",
	                       "Vibrate", g_variant_new("(s@a(du))", app_id ? app_id : "android_translation_layer", pattern),
	                       G_VARIANT_TYPE("(b)"), G_DBUS_CALL_FLAGS_NONE, -1, NULL, NULL, NULL);
}

/* finds a feedbackd-recognized vibrator */
char *find_vibrator(void)
{
	char *device_file = NULL;

	GUdevClient *udev_client = g_udev_client_new(NULL);
	GList *udev_devices = g_udev_client_query_by_subsystem(udev_client, "input");
	for (GList *l = udev_devices; l != NULL; l = l->next) {
		GUdevDevice *device = l->data;
		if (!g_strcmp0(g_udev_device_get_property(device, "FEEDBACKD_TYPE"), "vibra")) {
			device_file = strdup(g_udev_device_get_device_file(device));
			break;
		}
	}

	g_list_free_full(udev_devices, g_object_unref);
	g_object_unref(udev_client);

	return device_file;
}

JNIEXPORT jint JNICALL Java_android_os_Vibrator_native_1constructor(JNIEnv *env, jobject this)
{
	char *device_file;

	/* if there are multiple instances of Vibrator for some reason, reuse the fd */
	static int fd = -1;
	if (fd != -1)
		return fd;

	feedbackd_bus = g_bus_get_sync(G_BUS_TYPE_SESSION, NULL, NULL);
	if (feedbackd_bus && feedbackd_has_haptic()) {
		fd = VIBRATOR_FEEDBACKD;
		return fd;
	}
	g_clear_object(&feedbackd_bus);

	device_file = find_vibrator();
	if (!device_file) {
		g_log(NULL, G_LOG_LEVEL_WARNING, "no feedbackd-recognized vibrator found");
		return -1;
	}

	fd = open(device_file, O_RDWR);
	if (fd < 0) {
		g_log(NULL, G_LOG_LEVEL_WARNING, "cannot open vibrator device '%s': %m", device_file);
		free(device_file);
		return -1;
	}
	free(device_file);

	struct input_event set_gain = {
		.type = EV_FF,
		.code = FF_GAIN,
		/* arbitrary, could possibly be improved */
		.value = 0xFFFFUL * 80 / 100,
	};

	if (write(fd, &set_gain, sizeof(set_gain)) < 0) {
		g_log(NULL, G_LOG_LEVEL_WARNING, "failed to set gain on vibrator: %m");
	}

	return fd;
}

JNIEXPORT void JNICALL Java_android_os_Vibrator_native_1vibrate(JNIEnv *env, jobject this, jint fd, jlong duration)
{
	if (fd == VIBRATOR_FEEDBACKD) {
		/* feedbackd plays at most 10 s per step */
		GVariantBuilder pattern;
		g_variant_builder_init(&pattern, G_VARIANT_TYPE("a(du)"));
		for (jlong left = duration; left > 0; left -= 10000)
			g_variant_builder_add(&pattern, "(du)", 1.0, (guint32)MIN(left, 10000));
		feedbackd_vibrate(g_variant_builder_end(&pattern));
		return;
	}

	/* FIXME: not thread-safe */
	static struct ff_effect effect = {.id = -1};

	if (effect.id != -1 && effect.replay.length != duration) {
		ioctl(fd, EVIOCRMFF, effect.id);
		effect.id = -1;
	}

	if (effect.id == -1) {
		/* arbitrary, could possibly be improved */
		effect.type = FF_PERIODIC;
		effect.id = -1;
		effect.u.periodic.waveform = FF_SINE;
		effect.u.periodic.period = 10;
		effect.u.periodic.magnitude = 0x7fff;
		effect.u.periodic.offset = 0;
		effect.u.periodic.phase = 0;
		effect.direction = 0x4000;
		effect.u.periodic.envelope.attack_length = 1000;
		effect.u.periodic.envelope.attack_level = 0x7fff;
		effect.u.periodic.envelope.fade_length = 1000;
		effect.u.periodic.envelope.fade_level = 0x7fff;
		effect.trigger.button = 0;
		effect.trigger.interval = 0;

		effect.replay.length = duration;
		effect.replay.delay = 0;

		ioctl(fd, EVIOCSFF, &effect);
	}

	struct input_event play = {
		.type = EV_FF,
		.code = effect.id,
		.value = 1,
	};

	if (write(fd, (const void *)&play, sizeof(play)) < 0) {
		g_log(NULL, G_LOG_LEVEL_WARNING, "failed to play vibraton: %m");
	}
}

JNIEXPORT void JNICALL Java_android_os_Vibrator_native_1cancel(JNIEnv *env, jobject this, jint fd)
{
	if (fd == VIBRATOR_FEEDBACKD)
		feedbackd_vibrate(g_variant_new_array(G_VARIANT_TYPE("(du)"), NULL, 0));
}
