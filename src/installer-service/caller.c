/* Identifying a D-Bus caller by its sandbox (see caller.h). With a process fd from the broker the
 * pid cannot be reused by another process between the lookup and the check; brokers without one
 * (dbus-daemon before 1.15.6) give only the pid. */

#include <gio/gunixfdlist.h>
#include <glib/gstdio.h>

#include <poll.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include "caller.h"

static int pid_of_pidfd(int pidfd)
{
	char *path = g_strdup_printf("/proc/self/fdinfo/%d", pidfd);
	char *contents = NULL;
	int pid = -1;
	if (g_file_get_contents(path, &contents, NULL, NULL)) {
		char *line = strstr(contents, "\nPid:");
		if (line)
			pid = atoi(line + 5);
	}
	g_free(contents);
	g_free(path);
	return pid;
}

/* a pidfd becomes readable when its process has exited */
static gboolean pidfd_alive(int pidfd)
{
	struct pollfd pfd = {.fd = pidfd, .events = POLLIN};
	return poll(&pfd, 1, 0) == 0;
}

gboolean caller_identify(GDBusMethodInvocation *invocation, GKeyFile **info, GError **error)
{
	*info = NULL;
	GDBusConnection *bus = g_dbus_method_invocation_get_connection(invocation);
	GUnixFDList *fds = NULL;
	GVariant *reply = g_dbus_connection_call_with_unix_fd_list_sync(
	    bus, "org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "GetConnectionCredentials",
	    g_variant_new("(s)", g_dbus_method_invocation_get_sender(invocation)), G_VARIANT_TYPE("(a{sv})"),
	    G_DBUS_CALL_FLAGS_NONE, -1, NULL, &fds, NULL, error);
	if (!reply)
		return FALSE;
	GVariant *credentials = g_variant_get_child_value(reply, 0);
	guint32 pid = 0;
	gint32 fd_index = -1;
	int pidfd = -1;
	g_variant_lookup(credentials, "ProcessID", "u", &pid);
	if (g_variant_lookup(credentials, "ProcessFD", "h", &fd_index) && fds)
		pidfd = g_unix_fd_list_get(fds, fd_index, NULL);
	g_variant_unref(credentials);
	g_variant_unref(reply);
	g_clear_object(&fds);
	if (pidfd >= 0) {
		int fd_pid = pid_of_pidfd(pidfd);
		if (fd_pid > 0)
			pid = fd_pid;
	}
	if (!pid) {
		if (pidfd >= 0)
			close(pidfd);
		g_set_error(error, G_DBUS_ERROR, G_DBUS_ERROR_ACCESS_DENIED, "the caller's process is unknown");
		return FALSE;
	}

	char *path = g_strdup_printf("/proc/%u/root/.flatpak-info", pid);
	GKeyFile *key_file = g_key_file_new();
	GError *load_error = NULL;
	gboolean sandboxed = g_key_file_load_from_file(key_file, path, G_KEY_FILE_NONE, &load_error);
	/* a missing file means "not sandboxed" only while the process still lives */
	gboolean unreadable = !sandboxed && !g_error_matches(load_error, G_FILE_ERROR, G_FILE_ERROR_NOENT);
	g_clear_error(&load_error);
	g_free(path);
	gboolean alive = pidfd >= 0 ? pidfd_alive(pidfd) : kill(pid, 0) == 0;
	if (pidfd >= 0)
		close(pidfd);
	if (!alive || unreadable) {
		g_key_file_free(key_file);
		g_set_error(error, G_DBUS_ERROR, G_DBUS_ERROR_ACCESS_DENIED, "the caller could not be identified");
		return FALSE;
	}
	if (sandboxed)
		*info = key_file;
	else
		g_key_file_free(key_file);
	return TRUE;
}
