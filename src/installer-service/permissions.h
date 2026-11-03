#pragma once

#include <gio/gio.h>

/* de.nexumia.atl.Permissions, see permissions.c; hold and release keep the service from exiting
 * while a question is open */
void permissions_register(GDBusConnection *connection, void (*hold)(void), void (*release)(void));
