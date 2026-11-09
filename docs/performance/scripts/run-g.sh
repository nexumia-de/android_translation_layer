#!/bin/sh
# Follow-up of measurement A (#118): who opens each DRM render node (= one DRM client of fdinfo each).
# Floor, WebKit floor, Chooser, Gadgetbridge and DB Navigator once with tools/perf-drm-open.c preloaded.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-g
D=$HOME/work/perf-runs/drm-fds.sh
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
GB="$C $APK/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
DB="$C $APK/de.hafas.android.db_240000860/base.apk $APK/de.hafas.android.db_240000860/config.arm64_v8a.apk $APK/de.hafas.android.db_240000860/config.xxhdpi.apk"
SO=$HOME/work/perf-runs/bin/perf-drm-open.so
mkdir -p $O; echo "$(date +%T) G start" >> $O/suite.log
run() { # name, rest: perf-run arguments
	name=$1; shift
	mkdir -p $O/$name/opens
	PERF_DRM_OPEN_DIR=$O/$name/opens LD_PRELOAD=$SO "$@" >> $O/suite.log 2>&1
}
PERF_CMD=$HOME/work/perf-runs/bin/perf-gtk-floor run floor $T/perf-run.sh -o $O/floor -s 15 -i 0 -x "$D start"
PERF_CMD=$HOME/work/perf-runs/bin/perf-webkit-floor run webkit $T/perf-run.sh -o $O/webkit -s 20 -i 0 -x "$D start"
run chooser $T/perf-run.sh -o $O/chooser -s 30 -i 0 -x "$D start" -- $CH
run gadgetbridge $T/perf-run.sh -o $O/gadgetbridge -s 30 -i 0 -x "tap 24 23" -x "$D start" -- $GB
run dbnavigator $T/perf-run.sh -o $O/dbnavigator -s 40 -i 0 -x "tap 331 683" -x "tap 331 683" -x "tap 331 683" -x "tap 180 683" -x "$D start" -- $DB
echo "$(date +%T) G done" >> $O/suite.log
echo G-DONE >> $HOME/work/perf-runs/fixes.log
