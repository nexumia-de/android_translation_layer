#pragma once

#include <gio/gio.h>

/* Who is calling, from the D-Bus broker's credentials, never from a parameter. On success *info is
 * the caller's /.flatpak-info (Flatpak writes it, the app cannot change it), or NULL for a caller
 * outside a sandbox. Fails if the caller cannot be identified, e.g. because it exited. */
gboolean caller_identify(GDBusMethodInvocation *invocation, GKeyFile **info, GError **error);
