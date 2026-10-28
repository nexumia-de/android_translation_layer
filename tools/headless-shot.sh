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
#   -x STEP   shell command run after the delay (repeatable, in order), with
#             WAYLAND_DISPLAY set and these helpers:
#               tap X Y    click at X,Y in logical pixels
#               swipe X1 Y1 X2 Y2 [MS]  drag from X1,Y1 to X2,Y2
#               shot FILE  take a screenshot now
#             e.g. -x 'tap 330 70' -x 'shot page2.png'. tap and swipe use
#             wl-pointer (tools/phone-setup.sh tools); "wl-pointer -s WxH
#             ..." does more, and wlrctl can type text and keys
#   -w SEC    seconds to wait after each step (default: 2)
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

steps=
step_wait=2

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit "${1:-0}"; }

while getopts "o:d:l:m:s:x:w:h" opt; do
	case $opt in
	o) out=$OPTARG ;;
	d) delay=$OPTARG ;;
	l) applog=$OPTARG ;;
	m) mode=$OPTARG ;;
	s) scale=$OPTARG ;;
	x) steps="$steps$OPTARG
" ;;
	w) step_wait=$OPTARG ;;
	h) usage 0 ;;
	*) usage 1 ;;
	esac
done
shift $((OPTIND - 1))
[ "${1:-}" = "--" ] && shift
[ $# -gt 0 ] || usage 1

tools="cage wlr-randr grim dbus-run-session"
[ -n "$steps" ] && tools="$tools wl-pointer"
for tool in $tools; do
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

# Wait $1 seconds, but stop early if the app dies.
wait_alive() {
	i=0
	while [ $i -lt "$1" ] && kill -0 "$app_pid" 2>/dev/null; do
		sleep 1
		i=$((i + 1))
	done
	if ! kill -0 "$app_pid" 2>/dev/null; then
		if [ -n "$applog" ]; then cp "$run/app.log" "$applog"; fi
		wait "$app_pid" && status=0 || status=$?
		app_pid=
		echo "App exited with status $status before the screenshot. Last lines of its log:" >&2
		tail -n 30 "$run/app.log" >&2
		exit 1
	fi
}

# Coordinates are logical pixels: the output mode divided by the scale.
size=$(echo "$mode $scale" | awk '{ split($1, m, "x"); printf "%dx%d", m[1] / $2, m[2] / $2 }')
helpers="tap() { wl-pointer -s $size tap \"\$1\" \"\$2\"; }
swipe() { wl-pointer -s $size swipe \"\$1\" \"\$2\" \"\$3\" \"\$4\" \"\${5:-300}\"; }
shot() { grim \"\$1\" && echo \"Saved \$1\"; }"

wait_alive "$delay"
printf '%s' "$steps" >"$run/steps"
while IFS= read -r step; do
	echo "Step: $step"
	sh -c "$helpers
$step" || { echo "Step failed: $step" >&2; exit 1; }
	wait_alive "$step_wait"
done <"$run/steps"
if [ -n "$applog" ]; then cp "$run/app.log" "$applog"; fi
grim "$out"
echo "Saved $out ($mode, scale $scale)"
