#!/bin/sh
# Headless check of the microphone separation: runs its own PipeWire,
# WirePlumber, pipewire-pulse and the no-record pipewire-pulse
# (atl-pulse-norecord.conf; never the owner's), a virtual microphone and
# sink, and two fake ATL app Flatpaks (bwrap with a /.flatpak-info):
# "denied" sees only the no-record socket, as make-app-flatpak gives it,
# and must play but not record the microphone or a sink monitor, also when
# it rewrites its client properties; "allowed" has the normal socket and
# does all. Exit status 0 when so.
# With the shared lock: flock -o /tmp/atl-heavy.lock flatpak/microphone/test.sh
# (-o, so the daemons started here cannot inherit and keep the lock); they
# are stopped when the script ends, also when it is interrupted.
set -u
here=$(cd "$(dirname "$0")" && pwd)
run=/tmp/atl-mic-test-$(id -u)  # short: socket paths are limited to 108 bytes
rm -rf "$run"
mkdir -p "$run/rt/atl-pulse-norecord" "$run/cfg/pipewire/atl-pulse-norecord.conf.d" "$run/state"
chmod 700 "$run/rt"
ln -s /usr/share/pipewire/pipewire-pulse.conf "$run/cfg/pipewire/atl-pulse-norecord.conf"
sed "s|@RUNTIME_DIR@|$run/rt|" "$here/atl-pulse-norecord.conf" > "$run/cfg/pipewire/atl-pulse-norecord.conf.d/50-atl.conf"

export XDG_RUNTIME_DIR="$run/rt" XDG_CONFIG_HOME="$run/cfg" XDG_STATE_HOME="$run/state" DBUS_SESSION_BUS_ADDRESS=disabled:
pids=
trap 'kill $pids 2>/dev/null; wait 2>/dev/null' EXIT
trap 'exit 1' INT TERM HUP
pipewire > "$run/pipewire.log" 2>&1 & pids="$pids $!"
sleep 1
wireplumber > "$run/wireplumber.log" 2>&1 & pids="$pids $!"
pipewire-pulse > "$run/pipewire-pulse.log" 2>&1 & pids="$pids $!"
pipewire-pulse -c atl-pulse-norecord.conf > "$run/norecord.log" 2>&1 & pids="$pids $!"
sleep 3
pw-cli create-node adapter '{ factory.name=support.null-audio-sink node.name=testmic media.class=Audio/Source/Virtual audio.position=[MONO] object.linger=true }' >/dev/null
pw-cli create-node adapter '{ factory.name=support.null-audio-sink node.name=testsink media.class=Audio/Sink audio.position=[FL FR] object.linger=true }' >/dev/null
sleep 1

# like the app's Flatpak: only the one socket directory the app was given
sandboxed() {
	app=$1; shift
	case $app in
	denied) socket_dir=$run/rt/atl-pulse-norecord ;;
	*) socket_dir=$run/rt/pulse ;;
	esac
	printf '[Application]\nname=de.nexumia.atl.%s\n' "$app" > "$run/info-$app"
	bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin \
		--ro-bind /etc /etc --proc /proc --dev /dev --tmpfs /tmp --tmpfs "$run" \
		--ro-bind "$socket_dir" "$socket_dir" --ro-bind "$run/info-$app" /.flatpak-info \
		--setenv PULSE_SERVER "unix:$socket_dir/native" -- "$@"
}
# 0 when the stream ran until timeout stopped it after three seconds
# (pacat and parecord exit 0 on SIGTERM; a refused stream ends at once)
works() {
	start=$(date +%s)
	"$@" >/dev/null 2>&1
	[ $(($(date +%s) - start)) -ge 2 ]
}
fail=0
check() { # expected, what, command...
	want=$1 what=$2; shift 2
	if works "$@"; then got=yes; else got=no; fi
	[ "$got" = "$want" ] && r=ok || { r=FAIL; fail=1; }
	echo "$r: $what: $got"
}
for app in denied allowed; do
	[ $app = allowed ] && rec=yes || rec=no
	check yes "$app plays" sandboxed $app timeout 3 pacat -d testsink --raw /dev/zero
	check $rec "$app records the microphone" sandboxed $app timeout 3 parecord -d testmic --raw /dev/null
	check $rec "$app records the sink monitor" sandboxed $app timeout 3 parecord -d testsink.monitor --raw /dev/null
done
# the review's escape: a client rewriting the properties rules match on
check no "denied with a forged app id records" sandboxed denied env PULSE_PROP=pipewire.access.portal.app_id=de.nexumia.atl.allowed timeout 3 parecord -d testmic --raw /dev/null
check no "denied without application.name records" sandboxed denied env PULSE_PROP=application.name= timeout 3 parecord -d testmic --raw /dev/null
check no "denied reaches the normal socket" sandboxed denied sh -c "test -S $run/rt/pulse/native && sleep 3"
# server-side recording through modules (security review): must be refused
check yes "control: allowed (normal server) loads a module" sandboxed allowed sh -c "pactl load-module module-null-sink sink_name=control >/dev/null && sleep 3"
check no "denied loads a module" sandboxed denied sh -c "pactl load-module module-null-sink sink_name=leak >/dev/null && sleep 3"
check no "denied records through a loopback into a pipe" sandboxed denied sh -c "pactl load-module module-loopback source=testmic sink=testsink >/dev/null && sleep 3"
# the socket directory is read-only: an app cannot put its own socket there for other apps
check no "denied replaces the shared socket" sandboxed denied sh -c "rm -f $run/rt/atl-pulse-norecord/native && sleep 3"
exit $fail
