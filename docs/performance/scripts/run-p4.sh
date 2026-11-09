#!/bin/sh
# Extra pass after run-all.sh: visible (uncovered) idle and GTK renderer variants.
T=$HOME/work/android_translation_layer/tools
O=$HOME/work/perf-runs/suite-p4
A=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $A/com.uravgcode.chooser_26.apk"
GB="$C $A/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
mkdir -p $O
until grep -q ALL-DONE $HOME/work/perf-runs/run-all.log 2>/dev/null; do sleep 60; done
echo "$(date +%H:%M:%S) p4 start" >> $O/suite.log
$T/perf-run.sh -o $O/chooser-visible-1 -s 30 -i 600 -- $CH >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/gadgetbridge-visible-1 -s 30 -i 600 -x "tap 24 23" -- $GB >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/chooser-visible-2 -s 30 -i 600 -- $CH >> $O/suite.log 2>&1
for r in cairo ngl vulkan; do
  GSK_RENDERER=$r $T/perf-run.sh -o $O/chooser-renderer-$r-1 -s 25 -i 60 -n 20 -c -- $CH >> $O/suite.log 2>&1
  GSK_RENDERER=$r $T/perf-run.sh -o $O/gadgetbridge-renderer-$r-1 -s 25 -i 60 -n 20 -c -x "tap 24 23" -- $GB >> $O/suite.log 2>&1
done
echo "$(date +%H:%M:%S) suite done: $O" >> $O/suite.log
echo P4-DONE >> $HOME/work/perf-runs/run-all.log
