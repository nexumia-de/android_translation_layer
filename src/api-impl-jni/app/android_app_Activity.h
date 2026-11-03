#include <glib.h>
#include <jni.h>

void activity_start(JNIEnv *env, jobject activity_object);
void activity_window_ready(void);
void activity_close_all(void);
gboolean activity_backlog_empty(void);
gboolean foreground_service_running(void);
void current_activity_back_pressed(void);
gboolean current_activity_back_available(void);
gboolean current_activity_back_key(int action);
void activities_screen_size_changed(JNIEnv *env);
