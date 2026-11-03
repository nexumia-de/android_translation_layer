#pragma once

#include <gtk/gtk.h>

/* --android-apps: the "Android apps" settings page (android_apps.c); prgname is this program, for
 * App info */
void android_apps_show(GtkApplication *app, const char *prgname);
