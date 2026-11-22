#!/bin/sh
# A, second pass: GPU memory per DRM client (fdinfo), tool fixed to count each drm-client-id once.
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-a
D=$HOME/work/perf-runs/drm-fds.sh
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
GB="$C $APK/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
export PATH=$HOME/.local/bin:$PATH
PERF_CMD=$HOME/work/perf-runs/bin/perf-gtk-floor $T/perf-run.sh -o $O/floor-fds -s 20 -i 40 -n 20 -x "$D start" >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/gadgetbridge-fds -s 30 -i 40 -n 20 -x "tap 24 23" -x "$D start" -- $GB >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/chooser-fds2 -s 30 -i 40 -n 20 -x "$D start" -- $CH >> $O/suite.log 2>&1
echo A2-DONE >> $HOME/work/perf-runs/fixes.log
