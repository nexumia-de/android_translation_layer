#!/bin/sh
# Run a Wayland app in an own headless cage and take a screenshot with grim,
# without touching the owner's Phosh session.
#
# Usage:
#   tools/headless-shot.sh [options] -- <command> [args...]
#
# Options:
#   -o FILE   output PNG (default: screenshot.png)
#   -d SEC    seconds to wait after starting the app (default: 15)
#   -l FILE   also write the app's stdout/stderr to FILE
#   -m WxH    output mode in physical pixels (default: 720x1440)
#   -s SCALE  output scale (default: 2, so 360x720 logical like Phosh)
#
# cage shows the app maximized, like Phosh. It renders on the GPU through
# the render node; set WLR_RENDERER=pixman to render in software. The app
# gets its own session bus. Exits non-zero if the app exits before the
# screenshot.
set -eu

out=screenshot.png
delay=15
applog=
mode=720x1440
scale=2

usage() { sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-0}"; }

while getopts "o:d:l:m:s:h" opt; do
	case $opt in
	o) out=$OPTARG ;;
	d) delay=$OPTARG ;;
	l) applog=$OPTARG ;;
	m) mode=$OPTARG ;;
	s) scale=$OPTARG ;;
	h) usage 0 ;;
	*) usage 1 ;;
	esac
done
shift $((OPTIND - 1))
[ "${1:-}" = "--" ] && shift
[ $# -gt 0 ] || usage 1

for tool in cage wlr-randr grim dbus-run-session; do
	command -v "$tool" >/dev/null || { echo "$tool not found" >&2; exit 1; }
done

run=$(mktemp -d)
cage_pid=
app_pid=
# cage exits when its own client ends, dbus-run-session (with the bus) when
# the app ends, so stop those two; both write their pid before exec.
stop() { [ -s "$run/$1.pid" ] && kill "$(cat "$run/$1.pid")" 2>/dev/null; }
cleanup() {
	stop app || true
	stop client || true
	wait 2>/dev/null || true
	# gvfs and the document portal mount FUSE file systems in the runtime
	# directory; their daemons die with the bus and leave them stale.
	for mount in $(awk -v dir="$run/" 'index($2, dir) == 1 { print $2 }' /proc/self/mounts); do
		fusermount3 -uz "$mount" 2>/dev/null || true
	done
	rm -rf "$run" 2>/dev/null || { sleep 1; rm -rf "$run"; }
}
trap cleanup EXIT INT TERM

# cage's own client only reports the socket name and keeps cage running;
# the app connects as a second client.
export XDG_RUNTIME_DIR="$run"
unset WAYLAND_DISPLAY DISPLAY
WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 \
	cage -- sh -c 'echo $$ >"$XDG_RUNTIME_DIR/client.pid"
		echo "$WAYLAND_DISPLAY" >"$XDG_RUNTIME_DIR/display"
		exec sleep infinity' \
	>"$run/cage.log" 2>&1 &
cage_pid=$!
i=0
until [ -s "$run/display" ]; do
	i=$((i + 1))
	[ $i -le 100 ] && kill -0 "$cage_pid" 2>/dev/null || {
		cat "$run/cage.log" >&2
		echo "cage did not start" >&2
		exit 1
	}
	sleep 0.1
done
WAYLAND_DISPLAY=$(cat "$run/display")
export WAYLAND_DISPLAY
wlr-randr --output HEADLESS-1 --custom-mode "$mode" --scale "$scale"

dbus-run-session -- sh -c 'echo $$ >"$0/app.pid"; exec "$@"' "$run" "$@" >"$run/app.log" 2>&1 &
app_pid=$!

# Wait, but stop early if the app dies.
i=0
while [ $i -lt "$delay" ] && kill -0 "$app_pid" 2>/dev/null; do
	sleep 1
	i=$((i + 1))
done
[ -n "$applog" ] && cp "$run/app.log" "$applog"
if ! kill -0 "$app_pid" 2>/dev/null; then
	wait "$app_pid" && status=0 || status=$?
	app_pid=
	echo "App exited with status $status before the screenshot. Last lines of its log:" >&2
	tail -n 30 "$run/app.log" >&2
	exit 1
fi
grim "$out"
echo "Saved $out ($mode, scale $scale)"
