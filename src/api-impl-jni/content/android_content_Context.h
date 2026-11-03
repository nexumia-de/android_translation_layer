#ifndef ANDROID_CONTEXT_H
#define ANDROID_CONTEXT_H

#include <gtk/gtk.h>

void update_config_for_window(GtkWindow *window);
void unifiedpush_export_connector(GDBusConnection *connection);
void unifiedpush_connector_drain(void);

#endif // ANDROID_CONTEXT_H
