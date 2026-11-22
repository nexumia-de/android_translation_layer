#!/bin/sh
# A/B for #118: transparent huge pages off for the ATL process (tools/perf-thp-off.c) against the system's
# setting ("always" on the phone). Gadgetbridge and Chooser, covered, 10 min idle, 2 rounds alternating.
# Raw smaps of start and end are needed (AnonHugePages): scripts/thp-idle-growth.py DIR...
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-thp
W=$HOME/work/perf-runs/bin/perf-thp-off
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
GB="$C $APK/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
mkdir -p $O
cc -O1 -o $W $T/perf-thp-off.c || exit 1
echo "$(date +%T) THP start, system: $(cat /sys/kernel/mm/transparent_hugepage/enabled)" >> $O/suite.log
for i in 1 2; do
	$T/perf-run.sh -o $O/gadgetbridge-default-$i -c -i 600 -x "tap 24 23" -- $GB >> $O/suite.log 2>&1
	PERF_CMD=$W $T/perf-run.sh -o $O/gadgetbridge-thpoff-$i -c -i 600 -x "tap 24 23" -- android-translation-layer $GB >> $O/suite.log 2>&1
	$T/perf-run.sh -o $O/chooser-default-$i -c -i 600 -- $CH >> $O/suite.log 2>&1
	PERF_CMD=$W $T/perf-run.sh -o $O/chooser-thpoff-$i -c -i 600 -- android-translation-layer $CH >> $O/suite.log 2>&1
done
echo "$(date +%T) THP done" >> $O/suite.log
echo THP-DONE >> $HOME/work/perf-runs/fixes.log
