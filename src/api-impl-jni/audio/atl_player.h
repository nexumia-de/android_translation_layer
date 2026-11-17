#ifndef ATL_PLAYER_H
#define ATL_PLAYER_H

#include <glib.h>

/*
 * Plays the audio of a media file with GStreamer's playbin3. Used instead of
 * GtkMediaFile, which aborts in decodebin3 for mp3 files since GTK 4.22
 * ("mq_slot_handle_stream_start: assertion failed: (collection)").
 * The pipeline exists from the first call that needs it until a few seconds
 * after playback has ended. All functions are for the main thread.
 */
typedef struct _ATLPlayer ATLPlayer;
typedef void (*ATLPlayerEndedFunc)(ATLPlayer *player, gpointer user_data);

ATLPlayer *atl_player_new(const char *path);
void atl_player_free(ATLPlayer *player);
void atl_player_set_ended_func(ATLPlayer *player, ATLPlayerEndedFunc func, gpointer user_data);
/* restart: from the beginning, also when it is playing or paused */
void atl_player_play(ATLPlayer *player, gboolean restart);
void atl_player_pause(ATLPlayer *player);
/* releases the pipeline */
void atl_player_stop(ATLPlayer *player);
void atl_player_seek(ATLPlayer *player, gint64 msec);
void atl_player_set_loop(ATLPlayer *player, gboolean loop);
void atl_player_set_volume(ATLPlayer *player, double volume);
gboolean atl_player_is_playing(ATLPlayer *player);
gint64 atl_player_get_duration(ATLPlayer *player);
gint64 atl_player_get_position(ATLPlayer *player);

#endif
