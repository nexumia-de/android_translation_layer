#!/bin/sh
# Headless check of the microphone rules: runs its own PipeWire,
# WirePlumber and pipewire-pulse (never the owner's), a virtual
# microphone and sink, and two fake ATL app Flatpaks (bwrap with a
# /.flatpak-info, which is how PipeWire recognizes Flatpak clients):
# de.nexumia.atl.denied must play but not record (microphone and sink
# monitor), de.nexumia.atl.allowed must do both. Exit status 0 when so.
set -u
here=$(cd "$(dirname "$0")" && pwd)
run=/tmp/atl-mic-test-$(id -u)  # short: socket paths are limited to 108 bytes
rm -rf "$run"; mkdir -p "$run/cfg/wireplumber/wireplumber.conf.d" "$run/cfg/pipewire/pipewire-pulse.conf.d" "$run/state"
chmod 700 "$run"
sed '/^  #{/,/^  #}/d' "$here/wireplumber-90-atl-microphone.conf" | sed 's/^]$//' > "$run/wp.conf"
cat >> "$run/wp.conf" <<'X'
  {
    matches = [ { pipewire.access.portal.app_id = "de.nexumia.atl.allowed" } ]
    actions = { update-props = { permission_manager_name = "atl-microphone" } }
  }
]
X
mv "$run/wp.conf" "$run/cfg/wireplumber/wireplumber.conf.d/90-atl-microphone.conf"
sed '/^  #{/,/^  #}/d' "$here/pipewire-pulse-90-atl-microphone.conf" | sed 's/^]$//' > "$run/pp.conf"
cat >> "$run/pp.conf" <<'X'
  {
    matches = [ { pipewire.access.portal.app_id = "de.nexumia.atl.allowed" } ]
    actions = { quirks = [ ] }
  }
]
X
mv "$run/pp.conf" "$run/cfg/pipewire/pipewire-pulse.conf.d/90-atl-microphone.conf"

export XDG_RUNTIME_DIR="$run" XDG_CONFIG_HOME="$run/cfg" XDG_STATE_HOME="$run/state" \
	DBUS_SESSION_BUS_ADDRESS=disabled: PULSE_SERVER="unix:$run/pulse/native"
pipewire > "$run/pipewire.log" 2>&1 & pw=$!
sleep 1
wireplumber > "$run/wireplumber.log" 2>&1 & wp=$!
pipewire-pulse > "$run/pipewire-pulse.log" 2>&1 & pp=$!
trap 'kill $pp $wp $pw 2>/dev/null' EXIT
sleep 3
pw-cli create-node adapter '{ factory.name=support.null-audio-sink node.name=testmic media.class=Audio/Source/Virtual audio.position=[MONO] object.linger=true }' >/dev/null
pw-cli create-node adapter '{ factory.name=support.null-audio-sink node.name=testsink media.class=Audio/Sink audio.position=[FL FR] object.linger=true }' >/dev/null
sleep 1

sandboxed() {
	printf '[Application]\nname=%s\n' "$1" > "$run/info-$1"
	id=$1; shift
	bwrap --ro-bind /usr /usr --symlink usr/lib /lib --symlink usr/bin /bin --symlink usr/sbin /sbin \
		--ro-bind /etc /etc --bind "$run" "$run" --proc /proc --dev /dev \
		--ro-bind "$run/info-$id" /.flatpak-info -- "$@"
}
# 0 when the stream worked for two seconds (timeout ends it with 124)
works() { "$@" >/dev/null 2>&1; [ $? -eq 124 ]; }

fail=0
check() { # expected, what, command...
	want=$1 what=$2; shift 2
	if works "$@"; then got=yes; else got=no; fi
	[ "$got" = "$want" ] && r=ok || { r=FAIL; fail=1; }
	echo "$r: $what: $got"
}
for app in denied allowed; do
	id=de.nexumia.atl.$app
	[ $app = allowed ] && rec=yes || rec=no
	check yes "$app plays" sandboxed $id timeout 2 pacat -d testsink --raw /dev/zero
	check $rec "$app records the microphone" sandboxed $id timeout 2 parecord -d testmic --raw /dev/null
	check $rec "$app records the sink monitor" sandboxed $id timeout 2 parecord -d testsink.monitor --raw /dev/null
done
exit $fail
