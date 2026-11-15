#!/usr/bin/env python3
# Stand-in for the Secret portal backend (org.freedesktop.secrets) in a
# headless test bus: forwards only
# org.freedesktop.impl.portal.Secret.RetrieveSecret to the session bus in
# REAL_BUS, so a Flatpak app copied from the owner gets its own portal
# secret (and its login) without unlocking a keyring in the test bus. It
# never reads or logs the secret. Started through a D-Bus service file, see
# docs/dev-environment.md.
import os
import sys
import gi
gi.require_version('Gio', '2.0')
from gi.repository import Gio, GLib

XML = """<node><interface name="org.freedesktop.impl.portal.Secret">
<method name="RetrieveSecret"><arg type="o" name="handle" direction="in"/><arg type="s" name="app_id" direction="in"/>
<arg type="h" name="fd" direction="in"/><arg type="a{sv}" name="options" direction="in"/>
<arg type="u" name="response" direction="out"/><arg type="a{sv}" name="results" direction="out"/></method>
<property name="version" type="u" access="read"/></interface></node>"""

real = Gio.DBusConnection.new_for_address_sync(os.environ['REAL_BUS'],
    Gio.DBusConnectionFlags.AUTHENTICATION_CLIENT | Gio.DBusConnectionFlags.MESSAGE_BUS_CONNECTION, None, None)
info = Gio.DBusNodeInfo.new_for_xml(XML).interfaces[0]

def on_call(conn, sender, path, iface, method, params, invocation):
    if method != 'RetrieveSecret':
        invocation.return_dbus_error('org.freedesktop.DBus.Error.UnknownMethod', method)
        return
    handle, app_id, fd_index, options = params.unpack()
    fds = invocation.get_message().get_unix_fd_list()
    out = Gio.UnixFDList.new()
    out.append(fds.get(fd_index))
    print('forwarding RetrieveSecret for', app_id, file=sys.stderr)
    try:
        res, _ = real.call_with_unix_fd_list_sync('org.freedesktop.secrets', '/org/freedesktop/portal/desktop',
            'org.freedesktop.impl.portal.Secret', 'RetrieveSecret',
            GLib.Variant('(osha{sv})', (handle, app_id, 0, options)), GLib.VariantType('(ua{sv})'),
            Gio.DBusCallFlags.NONE, 60000, out, None)
        invocation.return_value(res)
    except GLib.Error as e:
        print('forward failed:', e.message, file=sys.stderr)
        invocation.return_gerror(e)

def on_get(conn, sender, path, iface, prop):
    return GLib.Variant('u', 1)

def on_bus(conn, name):
    conn.register_object('/org/freedesktop/portal/desktop', info, on_call, on_get, None)

Gio.bus_own_name(Gio.BusType.SESSION, 'org.freedesktop.secrets', Gio.BusNameOwnerFlags.NONE, on_bus, None, lambda *a: sys.exit(1))
GLib.MainLoop().run()
