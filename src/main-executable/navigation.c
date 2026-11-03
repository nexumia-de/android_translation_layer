/* Android navigation in ATL: back by key and by a swipe from the edge.
 * Both send KEYCODE_BACK like Android's navigation bar and gesture
 * navigation do, so dialogs, OnKeyListeners and Activity.onKeyDown see it
 * before onBackPressed. See docs/navigation.md. */

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "../api-impl-jni/app/android_app_Activity.h"
#include "../api-impl-jni/widgets/WrapperWidget.h"

#include "navigation.h"

#define EDGE_WIDTH 20       /* logical px from the edge where a back swipe may start */
#define TAKE_DISTANCE 16    /* inward movement after which the swipe takes the touch from the app */
#define TRIGGER_DISTANCE 72 /* inward movement after which releasing goes back */
#define HINT_SIZE 40

#define KEY_ACTION_DOWN 0
#define KEY_ACTION_UP 1

enum atl_navigation atl_navigation_mode(void)
{
	/* the settings app may later back this with GSettings */
	const char *mode = getenv("ATL_NAVIGATION");

	if (mode && !strcmp(mode, "buttons"))
		return ATL_NAVIGATION_BUTTONS;
	if (mode && !strcmp(mode, "gesture"))
		return ATL_NAVIGATION_GESTURE;
	if (mode && *mode && strcmp(mode, "both"))
		g_warning("ATL_NAVIGATION=%s: expected buttons, gesture or both; using both", mode);
	return ATL_NAVIGATION_BOTH;
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
	gboolean taken;
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
}

static void on_drag_begin(GtkGestureDrag *gesture, double x, double y, gpointer user_data)
{
	int width = gtk_widget_get_width(edge.content);

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
}

static void on_drag_update(GtkGestureDrag *gesture, double offset_x, double offset_y, gpointer user_data)
{
	double inward = offset_x * edge.side;

	if (!edge.side)
		return;
	if (!edge.taken) {
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
		/* GTK cancels the views' click and long-press gestures for this sequence */
		gtk_gesture_set_state(GTK_GESTURE(gesture), GTK_EVENT_SEQUENCE_CLAIMED);
		edge.taken = TRUE;
	}
	hint_update(inward, edge.start_y + offset_y);
}

static void on_drag_end(GtkGestureDrag *gesture, double offset_x, double offset_y, gpointer user_data)
{
	gboolean go_back = edge.taken && offset_x * edge.side >= TRIGGER_DISTANCE;

	edge_reset();
	if (go_back)
		back();
}

static void on_drag_cancel(GtkGesture *gesture, GdkEventSequence *sequence, gpointer user_data)
{
	edge_reset();
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
}
