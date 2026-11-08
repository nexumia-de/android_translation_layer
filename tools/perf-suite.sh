#!/bin/sh
# The whole baseline measurement of issue #118, so it can be repeated after
# every fix: start time, memory after start and after idling in the
# background, idle CPU and wake-ups, for each app and for two apps at once.
#
# Usage: perf-suite.sh [-o DIR] [-r REPEATS] [-i IDLE_SECONDS] [-a APPS]
#
#   -o DIR      output root (default ~/work/perf-runs/suite-<date>)
#   -r N        repeats of every start and idle run (default 3)
#   -i SECONDS  idle time of the background runs (default 1800)
#   -a APPS     space-separated subset of: chooser gadgetbridge heliboard
#               dbnavigator pair (default all)
#
# Apps run as the owner's phone runs them (F-Droid/Aurora installs:
# -w 360 -h 720 --sdk-int 26). Per app and repeat: a short run for the start
# time (settle 25 s, no idle), then a background run (settle 30 s, covered by
# foot, idle sampled every 60 s). "pair" starts Chooser next to Gadgetbridge
# to see what two ATL processes share. Each run is a directory from
# perf-run.sh; a summary is made on the laptop with perf-report.py.
#
# First screens (2026-11-08, data of the claude user): Gadgetbridge opens
# its welcome pages, back (header button at 24,23) leads to the main
# screen; DB Navigator shows its three-page tour every start, three times
# the arrow (331,683) and Close (180,683) lead to the journey search;
# Chooser and HeliBoard start on their main screen.
set -u
tools=$(cd "$(dirname "$0")" && pwd)
out=
repeats=3
idle=1800
apps="chooser gadgetbridge heliboard dbnavigator pair"
while getopts "o:r:i:a:h" opt; do
	case $opt in
	o) out=$OPTARG ;;
	r) repeats=$OPTARG ;;
	i) idle=$OPTARG ;;
	a) apps=$OPTARG ;;
	h) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
	*) exit 1 ;;
	esac
done
[ -n "$out" ] || out=$HOME/work/perf-runs/suite-$(date +%Y%m%d-%H%M)
mkdir -p "$out"
apks=$HOME/work/apks
common="-w 360 -h 720 --sdk-int 26"

args_for() {
	case $1 in
	chooser) echo "$common $apks/com.uravgcode.chooser_26.apk" ;;
	gadgetbridge) echo "$common $apks/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk" ;;
	heliboard) echo "$common $apks/helium314.keyboard/helium314.keyboard_4201.apk" ;;
	dbnavigator) echo "$common $apks/de.hafas.android.db_240000860/base.apk $apks/de.hafas.android.db_240000860/config.arm64_v8a.apk $apks/de.hafas.android.db_240000860/config.xxhdpi.apk" ;;
	esac
}

log() { echo "$(date +%H:%M:%S) $*" | tee -a "$out/suite.log"; }

# run_app APP DIR PERF-RUN-OPTIONS...: perf-run.sh with the app's steps
run_app() {
	app=$1; dir=$2; shift 2
	case $app in
	gadgetbridge) set -- "$@" -x "tap 24 23" ;;
	dbnavigator) set -- "$@" -x "tap 331 683" -x "tap 331 683" -x "tap 331 683" -x "tap 180 683" ;;
	esac
	# shellcheck disable=SC2046
	"$tools/perf-run.sh" -o "$dir" "$@" -- $(args_for "$app") >>"$out/suite.log" 2>&1
}

for app in $apps; do
	n=1
	while [ $n -le "$repeats" ]; do
		if [ "$app" = pair ]; then
			log "pair $n: gadgetbridge + chooser, idle $idle s"
			run_app gadgetbridge "$out/pair-$n" -s 30 -i "$idle" -2 "$(args_for chooser)" || log "pair $n failed"
		else
			log "$app start $n"
			run_app "$app" "$out/$app-start-$n" -s 25 -i 0 || log "$app start $n failed"
			log "$app background $n, idle $idle s"
			run_app "$app" "$out/$app-bg-$n" -s 30 -i "$idle" -c || log "$app background $n failed"
		fi
		n=$((n + 1))
	done
done
log "suite done: $out"
