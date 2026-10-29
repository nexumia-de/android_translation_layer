#!/bin/sh
# Escape test for one generated app Flatpak (docs/hardening-roadmap.md,
# phase T). Native code in an Android app can do whatever a shell in its
# sandbox can, so this runs the escape attempts as a shell inside the
# app's sandbox and reports PASS (blocked) or FAIL (escaped) for each.
#
# Usage: flatpak/escape-test.sh <app id>
#
# Needs a session bus (run it under dbus-run-session when there is none).
# Exits non-zero if any attempt escaped.
set -eu

[ $# -eq 1 ] || { echo "usage: $0 <app id>" >&2; exit 2; }
app=$1

# canaries outside the sandbox, removed again at the end
canary_dir=$HOME/.atl-escape-canary
other=$HOME/.var/app/de.nexumia.atl.escape-test-other
mkdir -p "$canary_dir" "$other"
echo secret > "$canary_dir/secret"
echo secret > "$other/secret"
trap 'rm -rf "$canary_dir" "$other" "$HOME/.config/autostart/atl-escape.desktop" "$HOME/.local/share/applications/atl-escape.desktop"' EXIT

set +e
flatpak run --user --command=bash "$app" -s -- "$HOME" <<'EOF'
home=$1
fail=0
pass() { echo "PASS  $1"; }
escaped() { echo "FAIL  $1"; fail=1; }

# controls: what must work, so that a broken test cannot look green
gdbus call --session --dest org.freedesktop.portal.Desktop --object-path /org/freedesktop/portal/desktop \
	--method org.freedesktop.DBus.Peer.Ping >/dev/null 2>&1 \
	|| { echo "BROKEN  cannot reach the portal, D-Bus checks would be meaningless"; exit 3; }
echo ok > "$XDG_DATA_HOME/atl-escape-control" && rm "$XDG_DATA_HOME/atl-escape-control" \
	|| { echo "BROKEN  cannot write the app's own data"; exit 3; }

# files
cat "$home/.atl-escape-canary/secret" >/dev/null 2>&1 && escaped "reads files in \$HOME" || pass "no access to files in \$HOME"
cat "$home/.var/app/de.nexumia.atl.escape-test-other/secret" >/dev/null 2>&1 && escaped "reads another app's data" || pass "no access to other apps' data"
ls "$home/.ssh" >/dev/null 2>&1 && escaped "lists ~/.ssh" || pass "no ~/.ssh"
# writes land in the sandbox's private home at best; checked from outside below
(mkdir -p "$home/.config/autostart" && echo x > "$home/.config/autostart/atl-escape.desktop") 2>/dev/null
(mkdir -p "$home/.local/share/applications" && echo x > "$home/.local/share/applications/atl-escape.desktop") 2>/dev/null
echo x > /usr/atl-escape 2>/dev/null && escaped "writes /usr" || pass "/usr read-only"
[ -e /run/host/etc/shadow ] && cat /run/host/etc/shadow >/dev/null 2>&1 && escaped "reads host /etc/shadow" || pass "no host /etc/shadow"

# devices and processes
ls /dev/input/event* >/dev/null 2>&1 && escaped "sees input devices" || pass "no input devices"
[ "$(ls -d /proc/[0-9]* | wc -l)" -lt 20 ] && pass "own pid namespace" || escaped "sees host processes"

# audio
[ -S "$XDG_RUNTIME_DIR/pulse/native" ] && echo "INFO  has the audio socket (playback and microphone)" || pass "no audio socket"
[ -S "$XDG_RUNTIME_DIR/pipewire-0" ] && escaped "has the PipeWire socket (camera, screen, microphone)" || pass "no PipeWire socket"

# D-Bus: names outside the app's policy must be unreachable
for name in org.freedesktop.secrets org.freedesktop.Flatpak org.freedesktop.systemd1 org.gnome.keyring org.freedesktop.impl.portal.PermissionStore; do
	if gdbus call --session --dest "$name" --object-path / --method org.freedesktop.DBus.Peer.Ping >/dev/null 2>&1; then
		escaped "reaches $name on the session bus"
	else
		pass "cannot reach $name"
	fi
done
gdbus call --system --dest org.freedesktop.DBus --object-path /org/freedesktop/DBus --method org.freedesktop.DBus.ListNames >/dev/null 2>&1 \
	&& echo "INFO  reaches the system bus (only granted names)" || pass "no system bus"

# network (only when the app has no network permission)
if grep -q '^shared=.*network' /.flatpak-info; then
	echo "INFO  has network permission"
elif (exec 3<>/dev/tcp/1.1.1.1/443) 2>/dev/null; then
	escaped "opens network sockets without network permission"
else
	pass "no network"
fi

exit $fail
EOF
status=$?

for f in .config/autostart/atl-escape.desktop .local/share/applications/atl-escape.desktop; do
	if [ -e "$HOME/$f" ]; then
		echo "FAIL  wrote ~/$f"
		status=1
	else
		echo "PASS  cannot write ~/$f"
	fi
done
exit $status
