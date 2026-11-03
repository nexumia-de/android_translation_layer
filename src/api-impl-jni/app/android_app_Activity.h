#include <jni.h>

void activity_start(JNIEnv *env, jobject activity_object);
void activity_window_ready(void);
void activity_close_all(void);
void current_activity_back_pressed(void);
void activities_screen_size_changed(JNIEnv *env);
