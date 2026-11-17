#include <gst/gst.h>

#include "atl_player.h"

#define RELEASE_DELAY_SECONDS 5
#define PREROLL_TIMEOUT (2 * GST_SECOND)
#define PLAY_FLAG_AUDIO 0x2 // GstPlayFlags of playbin: no video, text or visualisation

struct _ATLPlayer {
	char *uri;
	GstElement *playbin;
	guint bus_watch;
	guint release_source;
	gboolean playing;
	gboolean ended;
	gboolean loop;
	double volume;
	ATLPlayerEndedFunc ended_func;
	gpointer ended_data;
};

static void cancel_release(ATLPlayer *player)
{
	g_clear_handle_id(&player->release_source, g_source_remove);
}

static void release_pipeline(ATLPlayer *player)
{
	cancel_release(player);
	g_clear_handle_id(&player->bus_watch, g_source_remove);
	if (player->playbin) {
		gst_element_set_state(player->playbin, GST_STATE_NULL);
		g_clear_object(&player->playbin);
	}
	player->playing = FALSE;
}

static gboolean release_timeout(gpointer user_data)
{
	ATLPlayer *player = user_data;

	player->release_source = 0;
	if (!player->playing)
		release_pipeline(player);
	return G_SOURCE_REMOVE;
}

static void playback_ended(ATLPlayer *player)
{
	player->playing = FALSE;
	player->ended = TRUE;
	gst_element_set_state(player->playbin, GST_STATE_PAUSED);
	cancel_release(player);
	player->release_source = g_timeout_add_seconds(RELEASE_DELAY_SECONDS, release_timeout, player);
	if (player->ended_func)
		player->ended_func(player, player->ended_data);
}

static gboolean on_bus_message(GstBus *bus, GstMessage *message, gpointer user_data)
{
	ATLPlayer *player = user_data;

	switch (GST_MESSAGE_TYPE(message)) {
		case GST_MESSAGE_EOS:
			if (player->loop && player->playing)
				gst_element_seek_simple(player->playbin, GST_FORMAT_TIME, GST_SEEK_FLAG_FLUSH, 0);
			else
				playback_ended(player);
			break;
		case GST_MESSAGE_ERROR: {
			GError *error = NULL;

			gst_message_parse_error(message, &error, NULL);
			g_warning("atl_player: %s: %s", player->uri, error->message);
			g_error_free(error);
			/* as Android without an OnErrorListener: the playback is over */
			playback_ended(player);
			break;
		}
		default:
			break;
	}
	return G_SOURCE_CONTINUE;
}

static gboolean ensure_pipeline(ATLPlayer *player)
{
	GstBus *bus;

	if (player->playbin)
		return TRUE;
	if (!player->uri || !gst_init_check(NULL, NULL, NULL))
		return FALSE;
	player->playbin = gst_element_factory_make("playbin3", NULL);
	if (!player->playbin) {
		g_warning("atl_player: GStreamer has no playbin3 element");
		return FALSE;
	}
	g_object_set(player->playbin, "uri", player->uri, "flags", PLAY_FLAG_AUDIO, "volume", player->volume, NULL);
	bus = gst_element_get_bus(player->playbin);
	player->bus_watch = gst_bus_add_watch(bus, on_bus_message, player);
	gst_object_unref(bus);
	player->ended = FALSE;
	return TRUE;
}

/* seeking and the duration need the pipeline to have read the start of the file */
static gboolean ensure_prerolled(ATLPlayer *player)
{
	GstState state;

	if (!ensure_pipeline(player))
		return FALSE;
	gst_element_get_state(player->playbin, &state, NULL, 0);
	if (state < GST_STATE_PAUSED && !player->playing)
		gst_element_set_state(player->playbin, GST_STATE_PAUSED);
	return gst_element_get_state(player->playbin, NULL, NULL, PREROLL_TIMEOUT) != GST_STATE_CHANGE_FAILURE;
}

ATLPlayer *atl_player_new(const char *path)
{
	ATLPlayer *player = g_new0(ATLPlayer, 1);

	player->uri = g_filename_to_uri(path, NULL, NULL);
	player->volume = 1.0;
	return player;
}

void atl_player_free(ATLPlayer *player)
{
	release_pipeline(player);
	g_free(player->uri);
	g_free(player);
}

void atl_player_set_ended_func(ATLPlayer *player, ATLPlayerEndedFunc func, gpointer user_data)
{
	player->ended_func = func;
	player->ended_data = user_data;
}

void atl_player_play(ATLPlayer *player, gboolean restart)
{
	gboolean existed = player->playbin != NULL;

	cancel_release(player);
	if (!ensure_pipeline(player))
		return;
	if (existed && (restart || player->ended))
		gst_element_seek_simple(player->playbin, GST_FORMAT_TIME, GST_SEEK_FLAG_FLUSH, 0);
	player->ended = FALSE;
	player->playing = TRUE;
	gst_element_set_state(player->playbin, GST_STATE_PLAYING);
}

void atl_player_pause(ATLPlayer *player)
{
	player->playing = FALSE;
	if (player->playbin)
		gst_element_set_state(player->playbin, GST_STATE_PAUSED);
}

void atl_player_stop(ATLPlayer *player)
{
	release_pipeline(player);
}

void atl_player_seek(ATLPlayer *player, gint64 msec)
{
	cancel_release(player);
	if (!ensure_prerolled(player))
		return;
	gst_element_seek_simple(player->playbin, GST_FORMAT_TIME, GST_SEEK_FLAG_FLUSH | GST_SEEK_FLAG_KEY_UNIT, msec * GST_MSECOND);
	player->ended = FALSE;
}

void atl_player_set_loop(ATLPlayer *player, gboolean loop)
{
	player->loop = loop;
}

void atl_player_set_volume(ATLPlayer *player, double volume)
{
	player->volume = volume;
	if (player->playbin)
		g_object_set(player->playbin, "volume", volume, NULL);
}

gboolean atl_player_is_playing(ATLPlayer *player)
{
	return player->playing;
}

gint64 atl_player_get_duration(ATLPlayer *player)
{
	gint64 duration = 0;
	gboolean had_pipeline = player->playbin != NULL;

	if (ensure_prerolled(player))
		gst_element_query_duration(player->playbin, GST_FORMAT_TIME, &duration);
	if (!had_pipeline)
		release_pipeline(player);
	return duration / GST_MSECOND;
}

gint64 atl_player_get_position(ATLPlayer *player)
{
	gint64 position = 0;

	if (player->playbin)
		gst_element_query_position(player->playbin, GST_FORMAT_TIME, &position);
	return position / GST_MSECOND;
}
