#!/bin/sh
# Start every app of docs/apps.md once in an own headless cage and report
# whether it is still running after the delay, with a screenshot each, so a
# change can be checked for "the apps still start" (issue #118).
#
# Usage: tools/app-smoke.sh [-o DIR] [-d SECONDS] [APP...]
#
#   -o DIR      output directory (default ~/work/perf-runs/smoke-<date>)
#   -d SECONDS  time from exec to the screenshot (default 45)
#   APP         subset of the names below (default all)
#
# Prints one line per app: name, result, seconds, and the first crash line
# of the log if there is one. Results: ok (running at the screenshot, no
# crash line), crash (running but a crash line in the log), exited (the
# app was gone before the screenshot), missing (APK not in ~/work/apks).
# Look at DIR/<app>.png to see what the app showed. APKs are the ones in
# ~/work/apks (F-Droid, Aurora Store and direct downloads), started as
# perf-suite.sh starts them: -w 360 -h 720 --sdk-int 26.
set -u
tools=$(cd "$(dirname "$0")" && pwd)
out=$HOME/work/perf-runs/smoke-$(date +%Y%m%d-%H%M)
delay=45
while getopts "o:d:h" opt; do
	case $opt in
	o) out=$OPTARG ;;
	d) delay=$OPTARG ;;
	h) awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; exit 0 ;;
	*) exit 1 ;;
	esac
done
shift $((OPTIND - 1))
a=$HOME/work/apks
all="chooser fdroid aurora gadgetbridge comaps heliboard gpslogger whatsapp fluffychat blokish komoot dbnavigator dhl duolingo superproductivity owntracks"
[ $# -gt 0 ] && all="$*"

# split APKs: base first, then the configs
splits() { echo "$a/$1/base.apk $(ls "$a/$1"/config.*.apk | tr '\n' ' ')"; }

apk_for() {
	case $1 in
	chooser) echo "$a/com.uravgcode.chooser_26.apk" ;;
	fdroid) echo "$a/org.fdroid.fdroid_2.0.2.apk" ;;
	aurora) echo "$a/com.aurora.store_76.apk" ;;
	gadgetbridge) echo "$a/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk" ;;
	comaps) echo "$a/app.comaps.fdroid/app.comaps.fdroid_26100519.apk" ;;
	heliboard) echo "$a/helium314.keyboard/helium314.keyboard_4201.apk" ;;
	gpslogger) echo "$a/com.mendhak.gpslogger_138.apk" ;;
	whatsapp) echo "$a/com.whatsapp/com.whatsapp_264307238.apk" ;;
	fluffychat) echo "$a/chat.fluffy.fluffychat/chat.fluffy.fluffychat_3574.apk" ;;
	blokish) echo "$a/org.scoutant.blokish_24.apk" ;;
	komoot) splits de.komoot.android_264028003 ;;
	dbnavigator) splits de.hafas.android.db_240000860 ;;
	dhl) splits de.dhl.paket_921502244 ;;
	duolingo) splits com.duolingo_2488 ;;
	superproductivity) echo "$a/com.superproductivity.superproductivity/com.superproductivity.superproductivity_1903019000.apk" ;;
	owntracks) echo "$a/org.owntracks.android_420509001.apk" ;;
	esac
}

mkdir -p "$out"
crash='FATAL EXCEPTION|Fatal signal|SIGSEGV|SIGABRT|Unable to Launch JVM|Failed to map image|java.lang.UnsatisfiedLinkError|Aborted'
for app in $all; do
	apks=$(apk_for "$app")
	first=${apks%% *}
	if [ -z "$apks" ] || [ ! -f "$first" ]; then
		printf '%s\tmissing\t-\t-\n' "$app"
		continue
	fi
	t0=$(date +%s)
	# shellcheck disable=SC2086
	if "$tools/headless-shot.sh" -d "$delay" -o "$out/$app.png" -l "$out/$app.log" -- \
		android-translation-layer -w 360 -h 720 --sdk-int 26 $apks >"$out/$app.harness.log" 2>&1; then
		result=ok
	else
		result=exited
	fi
	line=$(grep -m1 -E "$crash" "$out/$app.log" 2>/dev/null | cut -c1-160)
	[ "$result" = ok ] && [ -n "$line" ] && result=crash
	printf '%s\t%s\t%s\t%s\n' "$app" "$result" "$(($(date +%s) - t0))" "${line:--}"
done | tee "$out/smoke.tsv"
