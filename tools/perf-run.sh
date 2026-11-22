#!/bin/sh
# One measurement run of an ATL app in the headless display (issue #118).
#
# Usage: perf-run.sh [options] -- <android-translation-layer arguments>
#
# Options:
#   -o DIR       output directory (default ~/work/perf-runs/<date>-<time>)
#   -s SECONDS   settle time after the start before the first snapshot (30)
#   -i SECONDS   idle time to sample after the first snapshot (1800); 0 skips
#   -n SECONDS   sampling interval of perf-idle.sh (60)
#   -c           cover the app's window with another client (foot) during the
#                idle time: the app is "in the background" as under Phosh.
#                foot runs fullscreen: maximized it rounds its height to text
#                lines and leaves a strip of the app visible, whose frames
#                then go on
#   -2 'CMD'     start a second ATL app (its arguments) after the first
#                snapshot, in the same display, and snapshot it too: shows
#                what two ATL processes share (pair-*.tsv)
#   -x STEP      extra headless-shot.sh step before the first snapshot, e.g.
#                -x 'tap 294 695' to close a dialog (repeatable)
#
# Writes into DIR: meta.txt (date, ATL commit, kernel, free memory, the
# command), start.tsv (perf-start.py: time to the first frame), start.png,
# smaps-start.tsv and threads-start.tsv (perf-smaps.py, perf-threads.sh after
# the settle time), covered.png, idle.tsv (perf-idle.sh), smaps-end.tsv and
# threads-end.tsv, end.png, app.log, and the raw smaps as smaps-*.txt.gz.
# PERF_CMD=prog runs another program instead of ATL (the GTK floor of
# tools/perf-gtk-floor.c); no arguments are needed then.
# Runs ATL from ~/.local with the environment of tools/phone-setup.sh env,
# under flock /tmp/atl-heavy.lock like every other heavy job on the phone.
set -eu

tools=$(cd "$(dirname "$0")" && pwd)
atl=$(cd "$tools/.." && pwd)
out=
settle=30
idle=1800
interval=60
cover=
second=
steps=
while getopts "o:s:i:n:c2:x:h" opt; do
	case $opt in
	o) out=$OPTARG ;;
	s) settle=$OPTARG ;;
	i) idle=$OPTARG ;;
	n) interval=$OPTARG ;;
	c) cover=1 ;;
	2) second=$OPTARG ;;
	x) steps="$steps -x \"$OPTARG\"" ;;
	h) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
	*) exit 1 ;;
	esac
done
shift $((OPTIND - 1))
[ "${1:-}" = "--" ] && shift
[ $# -gt 0 ] || [ -n "${PERF_CMD:-}" ] || { echo "no ATL arguments" >&2; exit 1; }

[ -n "$out" ] || out=$HOME/work/perf-runs/$(date +%Y%m%d-%H%M%S)
mkdir -p "$out"
out=$(cd "$out" && pwd)
eval "$("$tools/phone-setup.sh" env)"

{
	echo "date: $(date -Iseconds)"
	echo "atl_commit: $(git -C "$atl" rev-parse --short HEAD) ($(git -C "$atl" log -1 --format=%cd --date=short))"
	echo "atl_binary: $(ls -l "$(command -v android-translation-layer)" | awk '{ print $6, $7, $8 }')"
	echo "kernel: $(uname -r)"
	echo "cpu_governor: $(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)"
	echo "settle: $settle idle: $idle interval: $interval cover: ${cover:-0}"
	echo "command: ${PERF_CMD:-android-translation-layer} $*"
	[ -n "$second" ] && echo "second: android-translation-layer $second"
	echo "free_before:"; free -m
	echo "load_before: $(cat /proc/loadavg)"
} >"$out/meta.txt"

snapshot() { # PID LABEL
	python3 "$tools/perf-smaps.py" "$1" "$2" >"$out/smaps-$2.tsv"
	gzip -c "/proc/$1/smaps" >"$out/smaps-$2.txt.gz"
	"$tools/perf-threads.sh" "$1" >"$out/threads-$2.tsv"
	grep -E '^(MemFree|MemAvailable|Cached|AnonPages|Shmem|Mapped|SwapFree)' /proc/meminfo >"$out/meminfo-$2.txt"
}
# The steps run in headless-shot.sh's step shell (WAYLAND_DISPLAY and
# XDG_RUNTIME_DIR set); they need these definitions and the output dir.
export PERF_OUT=$out PERF_TOOLS=$tools PERF_IDLE=$idle PERF_INTERVAL=$interval PERF_SECOND="$second"
snap='snapshot() { python3 "$PERF_TOOLS/perf-smaps.py" "$1" "$2" >"$PERF_OUT/smaps-$2.tsv"; gzip -c "/proc/$1/smaps" >"$PERF_OUT/smaps-$2.txt.gz"; "$PERF_TOOLS/perf-threads.sh" "$1" >"$PERF_OUT/threads-$2.tsv"; grep -E "^(MemFree|MemAvailable|Cached|AnonPages|Shmem|Mapped|SwapFree)" /proc/meminfo >"$PERF_OUT/meminfo-$2.txt"; }; P=$(cat "$XDG_RUNTIME_DIR/atl.pid")'

s1="$snap; shot \"\$PERF_OUT/start.png\"; snapshot \$P start"
s2=
[ -n "$second" ] && s2="$snap; dbus-run-session -- sh -c 'echo \$\$ >\"\$XDG_RUNTIME_DIR/atl2.pid\"; exec android-translation-layer $second' >\"\$PERF_OUT/app2.log\" 2>&1 & sleep 30; P2=\$(cat \"\$XDG_RUNTIME_DIR/atl2.pid\"); shot \"\$PERF_OUT/pair.png\"; snapshot \$P2 pair-second; snapshot \$P pair-first"
s3=
[ -n "$cover" ] && s3="foot -F -T cover sh -c 'sleep 2147483647' >\"\$PERF_OUT/foot.log\" 2>&1 & echo \$! >\"\$XDG_RUNTIME_DIR/foot.pid\"; sleep 4; shot \"\$PERF_OUT/covered.png\""
s4="$snap; if [ \"\$PERF_IDLE\" -gt 0 ]; then \"\$PERF_TOOLS/perf-idle.sh\" \$P \"\$PERF_IDLE\" \"\$PERF_INTERVAL\" >\"\$PERF_OUT/idle.tsv\" & I=\$!; if [ -s \"\$XDG_RUNTIME_DIR/atl2.pid\" ]; then \"\$PERF_TOOLS/perf-idle.sh\" \$(cat \"\$XDG_RUNTIME_DIR/atl2.pid\") \"\$PERF_IDLE\" \"\$PERF_INTERVAL\" >\"\$PERF_OUT/idle-second.tsv\"; fi; wait \$I; fi; snapshot \$P end; if [ -s \"\$XDG_RUNTIME_DIR/atl2.pid\" ]; then snapshot \$(cat \"\$XDG_RUNTIME_DIR/atl2.pid\") pair-second-end; kill \$(cat \"\$XDG_RUNTIME_DIR/atl2.pid\") 2>/dev/null; fi; [ -s \"\$XDG_RUNTIME_DIR/foot.pid\" ] && kill \$(cat \"\$XDG_RUNTIME_DIR/foot.pid\") 2>/dev/null; sleep 1; true"

status=0
eval flock /tmp/atl-heavy.lock "$tools/headless-shot.sh" -d "$settle" -w 1 -o "$out/end.png" -l "$out/app.log" $steps \
	-x "\"\$s1\"" ${second:+-x "\"\$s2\""} ${cover:+-x "\"\$s3\""} -x "\"\$s4\"" \
	-- python3 "$tools/perf-start.py" "$out/start.tsv" "${PERF_CMD:-android-translation-layer}" "\"\$@\"" >"$out/harness.log" 2>&1 || status=$?
{
	echo "free_after:"; free -m
	echo "load_after: $(cat /proc/loadavg)"
	echo "status: $status"
} >>"$out/meta.txt"
[ $status -eq 0 ] || { echo "run failed ($status), see $out/harness.log" >&2; tail -5 "$out/harness.log" >&2; }
echo "$out"
exit $status
