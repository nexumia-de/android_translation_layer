#pragma once

#include <gio/gio.h>

/* confirming the owner for BiometricPrompt and keystore keys, see auth.c; the caller is already
 * identified as an ATL app (app_id) */
void auth_handle_authenticators(GDBusMethodInvocation *invocation);
void auth_handle_verify_fingerprint(GDBusMethodInvocation *invocation, const char *app_id);
void auth_handle_confirm_credential(GDBusMethodInvocation *invocation, const char *app_id);
void auth_handle_cancel(GDBusMethodInvocation *invocation, const char *app_id);
void auth_handle_device_locked(GDBusMethodInvocation *invocation);

/* from permissions.c: keep the service running while a confirmation is open */
void auth_hold(void);
void auth_release(void);
