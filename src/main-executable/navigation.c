/* Android navigation in ATL: back by key and by a swipe from the edge.
 * Both send KEYCODE_BACK like Android's navigation bar and gesture
 * navigation do, so dialogs, OnKeyListeners and Activity.onKeyDown see it
 * before onBackPressed. See docs/navigation.md. */

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "../api-impl-jni/app/android_app_Activity.h"
#include "../api-impl-jni/app/dialog.h"
#include "../api-impl-jni/widgets/WrapperWidget.h"

#include "navigation.h"

#define EDGE_WIDTH 20       /* logical px from the edge where a back swipe may start */
#define TAKE_DISTANCE 16    /* inward movement after which the swipe takes the touch from the app */
#define TRIGGER_DISTANCE 72 /* inward movement after which releasing goes back */
#define HINT_SIZE 40

#define KEY_ACTION_DOWN 0
#define KEY_ACTION_UP 1

/* the "Android apps" page's choice (--android-apps), for ATL on the host; apps in their own Flatpak
 * get it as ATL_NAVIGATION from a Flatpak override */
static char *settings_file(void)
{
	return g_build_filename(g_get_user_config_dir(), "android_translation_layer", "settings.ini", NULL);
}

static char *saved_mode(void)
{
	char *path = settings_file();
	GKeyFile *key_file = g_key_file_new();
	char *mode = NULL;
	if (g_key_file_load_from_file(key_file, path, G_KEY_FILE_NONE, NULL))
		mode = g_key_file_get_string(key_file, "navigation", "mode", NULL);
	g_key_file_free(key_file);
	g_free(path);
	return mode;
}

gboolean atl_navigation_set_mode(const char *mode, GError **error)
{
	char *path = settings_file();
	char *dir = g_path_get_dirname(path);
	g_mkdir_with_parents(dir, 0755);
	GKeyFile *key_file = g_key_file_new();
	g_key_file_load_from_file(key_file, path, G_KEY_FILE_KEEP_COMMENTS, NULL);
	g_key_file_set_string(key_file, "navigation", "mode", mode);
	gboolean ok = g_key_file_save_to_file(key_file, path, error);
	g_key_file_free(key_file);
	g_free(dir);
	g_free(path);
	return ok;
}

enum atl_navigation atl_navigation_mode(void)
{
	/* the environment first (set per app Flatpak, or by hand), then the page's setting */
	char *saved = NULL;
	const char *mode = getenv("ATL_NAVIGATION");
	if (!mode || !*mode)
		mode = saved = saved_mode();
	enum atl_navigation result = ATL_NAVIGATION_BOTH;
	if (mode && !strcmp(mode, "buttons"))
		result = ATL_NAVIGATION_BUTTONS;
	else if (mode && !strcmp(mode, "gesture"))
		result = ATL_NAVIGATION_GESTURE;
	else if (mode && *mode && strcmp(mode, "both"))
		g_warning("ATL_NAVIGATION=%s: expected buttons, gesture or both; using both", mode);
	g_free(saved);
	return result;
}

static void back(void)
{
	current_activity_back_key(KEY_ACTION_DOWN);
	current_activity_back_key(KEY_ACTION_UP);
}

/* -- keys: Escape (Android's fallback for it), Alt+Left (browsers, GNOME) and XF86Back -- */

static guint back_keyval = 0;

static gboolean is_back_key(guint keyval, GdkModifierType state)
{
	state &= gtk_accelerator_get_default_mod_mask();
	switch (keyval) {
		case GDK_KEY_Escape:
			return state == 0;
		case GDK_KEY_Left:
		case GDK_KEY_KP_Left:
			return state == GDK_ALT_MASK;
		case GDK_KEY_Back:
			return TRUE;
		default:
			return FALSE;
	}
}

/* bubble phase: a focused view that handles the key (WrapperWidget.c) has had it first */
static gboolean on_key_pressed(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, gpointer user_data)
{
	if (back_keyval)
		return keyval == back_keyval; /* auto-repeat */
	if (!is_back_key(keyval, state) || !current_activity_back_available())
		return FALSE;
	back_keyval = keyval;
	current_activity_back_key(KEY_ACTION_DOWN);
	return TRUE;
}

static void on_key_released(GtkEventControllerKey *controller, guint keyval, guint keycode, GdkModifierType state, gpointer user_data)
{
	if (!back_keyval || keyval != back_keyval)
		return;
	back_keyval = 0;
	current_activity_back_key(KEY_ACTION_UP);
}

/* -- edge swipe -- */

static struct {
	GtkWidget *content;
	GtkWidget *hint;
	int side; /* 1: from the left edge, -1: from the right */
	double start_x, start_y;
	GtkWidget *dialog; /* the top dialog when the swipe started */
	/* once taken: the sequence, and its surface position and offset at that moment */
	gboolean taken;
	GdkEventSequence *sequence;
	double taken_x, taken_y, taken_offset_x, taken_offset_y;
} edge;

static void hint_update(double inward, double y)
{
	if (!edge.hint) {
		edge.hint = gtk_button_new_from_icon_name(edge.side > 0 ? "go-previous-symbolic" : "go-next-symbolic");
		gtk_widget_add_css_class(edge.hint, "osd");
		gtk_widget_add_css_class(edge.hint, "circular");
		gtk_widget_set_can_target(edge.hint, FALSE);
		gtk_widget_set_focusable(edge.hint, FALSE);
		gtk_widget_set_size_request(edge.hint, HINT_SIZE, HINT_SIZE);
		gtk_widget_set_halign(edge.hint, edge.side > 0 ? GTK_ALIGN_START : GTK_ALIGN_END);
		gtk_widget_set_valign(edge.hint, GTK_ALIGN_START);
		gtk_overlay_add_overlay(GTK_OVERLAY(edge.content), edge.hint);
	}
	double progress = fmin(inward / TRIGGER_DISTANCE, 1);
	int margin = (int)(progress * HINT_SIZE / 2);
	int height = gtk_widget_get_height(edge.content);
	gtk_widget_set_margin_start(edge.hint, edge.side > 0 ? margin : 0);
	gtk_widget_set_margin_end(edge.hint, edge.side < 0 ? margin : 0);
	gtk_widget_set_margin_top(edge.hint, (int)CLAMP(y - HINT_SIZE / 2, 0, MAX(height - HINT_SIZE, 0)));
	gtk_widget_set_opacity(edge.hint, 0.3 + 0.7 * progress);
	if (progress >= 1)
		gtk_widget_add_css_class(edge.hint, "suggested-action");
	else
		gtk_widget_remove_css_class(edge.hint, "suggested-action");
}

static void edge_reset(void)
{
	if (edge.hint)
		gtk_overlay_remove_overlay(GTK_OVERLAY(edge.content), edge.hint);
	edge.hint = NULL;
	edge.side = 0;
	edge.taken = FALSE;
	edge.sequence = NULL;
	edge.dialog = NULL;
}

static void on_drag_begin(GtkGestureDrag *gesture, double x, double y, gpointer user_data)
{
	int width = gtk_widget_get_width(edge.content);

	if (edge.taken) { /* another finger during a taken swipe */
		gtk_gesture_set_state(GTK_GESTURE(gesture), GTK_EVENT_SEQUENCE_DENIED);
		return;
	}
	edge_reset();
	if (x < EDGE_WIDTH)
		edge.side = 1;
	else if (x >= width - EDGE_WIDTH)
		edge.side = -1;
	/* not from the edge: the touch stays the app's (ViewPager, drawers) */
	if (!edge.side || !current_activity_back_available()) {
		edge.side = 0;
		gtk_gesture_set_state(GTK_GESTURE(gesture), GTK_EVENT_SEQUENCE_DENIED);
		return;
	}
	edge.start_x = x;
	edge.start_y = y;
	edge.dialog = atl_dialog_get_top();
}

static void on_drag_update(GtkGestureDrag *gesture, double offset_x, double offset_y, gpointer user_data)
{
	double inward = offset_x * edge.side;

	if (!edge.side || edge.taken) /* once taken, on_taken_event follows the finger */
		return;
	/* until it is clearly an inward swipe the app sees the touch, so taps and
	 * vertical scrolls at the edge keep working */
	if (inward < -TAKE_DISTANCE || fabs(offset_y) > MAX(TAKE_DISTANCE, inward)) {
		edge.side = 0;
		gtk_gesture_set_state(GTK_GESTURE(gesture), GTK_EVENT_SEQUENCE_DENIED);
		return;
	}
	if (inward < TAKE_DISTANCE)
		return;
	GdkEventSequence *sequence = gtk_gesture_single_get_current_sequence(GTK_GESTURE_SINGLE(gesture));
	GdkEvent *event = gtk_gesture_get_last_event(GTK_GESTURE(gesture), sequence);
	GtkWidget *target = gtk_widget_pick(edge.content, edge.start_x, edge.start_y, GTK_PICK_DEFAULT);
	if (event && target)
		view_cancel_touch_sequence(target, event);
	if (event)
		gdk_event_get_position(event, &edge.taken_x, &edge.taken_y);
	edge.taken_offset_x = offset_x;
	edge.taken_offset_y = offset_y;
	edge.sequence = sequence;
	edge.taken = TRUE;
	/* GTK cancels the views' click and long-press gestures for this sequence */
	gtk_gesture_set_state(GTK_GESTURE(gesture), GTK_EVENT_SEQUENCE_CLAIMED);
	hint_update(inward, edge.start_y + offset_y);
}

/* the drag gesture also ends when a second finger comes; a taken swipe ends
 * only with its own sequence (on_taken_event) */
static void on_drag_end(GtkGestureDrag *gesture, double offset_x, double offset_y, gpointer user_data)
{
	if (!edge.taken)
		edge_reset();
}

static void on_drag_cancel(GtkGesture *gesture, GdkEventSequence *sequence, gpointer user_data)
{
	if (!edge.taken)
		edge_reset();
}

/* capture phase, before the views: after the swipe took the touch, the rest of
 * its sequence is the swipe's alone (the views already got ACTION_CANCEL);
 * other fingers still reach the app */
static gboolean on_taken_event(GtkEventControllerLegacy *controller, GdkEvent *event, gpointer user_data)
{
	if (!edge.taken || gdk_event_get_event_sequence(event) != edge.sequence)
		return FALSE;

	double x, y;
	GdkEventType type = gdk_event_get_event_type(event);
	/* a mouse swipe has no sequence: only its motion and the left button's
	 * release belong to it, other buttons, keys and scrolling stay the app's */
	if (!edge.sequence && type != GDK_MOTION_NOTIFY &&
	    !(type == GDK_BUTTON_RELEASE && gdk_button_event_get_button(event) == GDK_BUTTON_PRIMARY))
		return FALSE;
	switch (type) {
		case GDK_TOUCH_UPDATE:
		case GDK_MOTION_NOTIFY:
		case GDK_TOUCH_END:
		case GDK_BUTTON_RELEASE:
			break;
		case GDK_TOUCH_CANCEL:
			edge_reset();
			return TRUE;
		default:
			return TRUE;
	}
	if (!gdk_event_get_position(event, &x, &y))
		return TRUE;
	double offset_x = edge.taken_offset_x + x - edge.taken_x;
	double offset_y = edge.taken_offset_y + y - edge.taken_y;
	double inward = offset_x * edge.side;
	if (type == GDK_TOUCH_UPDATE || type == GDK_MOTION_NOTIFY) {
		hint_update(inward, edge.start_y + offset_y);
		return TRUE;
	}
	/* a press on a dialog's scrim may have dismissed it already: that was the back */
	gboolean go_back = inward >= TRIGGER_DISTANCE && atl_dialog_get_top() == edge.dialog;
	edge_reset();
	if (go_back)
		back();
	return TRUE;
}

void navigation_setup(GtkWindow *window, GtkWidget *back_button)
{
	enum atl_navigation mode = atl_navigation_mode();

	GtkEventController *keys = gtk_event_controller_key_new();
	g_signal_connect(keys, "key-pressed", G_CALLBACK(on_key_pressed), NULL);
	g_signal_connect(keys, "key-released", G_CALLBACK(on_key_released), NULL);
	gtk_widget_add_controller(GTK_WIDGET(window), keys);

	gtk_widget_set_visible(back_button, !!(mode & ATL_NAVIGATION_BUTTONS));

	if (!(mode & ATL_NAVIGATION_GESTURE))
		return;
	/* the content below the header bar: activities and dialogs (see main.c) */
	edge.content = g_object_get_data(G_OBJECT(window), "dialog-overlay");
	GtkGesture *drag = gtk_gesture_drag_new();
	gtk_event_controller_set_propagation_phase(GTK_EVENT_CONTROLLER(drag), GTK_PHASE_CAPTURE);
	g_signal_connect(drag, "drag-begin", G_CALLBACK(on_drag_begin), NULL);
	g_signal_connect(drag, "drag-update", G_CALLBACK(on_drag_update), NULL);
	g_signal_connect(drag, "drag-end", G_CALLBACK(on_drag_end), NULL);
	g_signal_connect(drag, "cancel", G_CALLBACK(on_drag_cancel), NULL);
	gtk_widget_add_controller(edge.content, GTK_EVENT_CONTROLLER(drag));
	/* after the drag gesture, so it still sees its sequence end */
	GtkEventController *taken = gtk_event_controller_legacy_new();
	gtk_event_controller_set_propagation_phase(taken, GTK_PHASE_CAPTURE);
	g_signal_connect(taken, "event", G_CALLBACK(on_taken_event), NULL);
	gtk_widget_add_controller(edge.content, taken);
}
