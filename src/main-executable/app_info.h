#pragma once

#include <gtk/gtk.h>

/* --app-info: the App info window of an app installed as its own Flatpak (app_info.c); prgname is
 * this program, for the uninstall page */
void app_info_show(GtkApplication *app, const char *package, const char *prgname);
