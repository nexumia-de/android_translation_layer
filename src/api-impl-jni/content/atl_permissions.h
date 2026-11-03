#pragma once

#include <glib.h>

/* in the app's own Flatpak: whether the user said yes to this permission in the installer
 * service's dialog (android_atl_ATLPermissions.c); FALSE outside a Flatpak or without the service */
gboolean atl_permission_granted(const char *permission);
