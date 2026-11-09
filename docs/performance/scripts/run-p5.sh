#!/bin/sh
# Verification runs for the Code analysis items (after run-p4.sh): -Xnorelocate, GDK_DISABLE, debug logs, SIGQUIT dumps.
T=$HOME/work/android_translation_layer/tools
O=$HOME/work/perf-runs/suite-p5
A=$HOME/work/apks
Q=$HOME/work/perf-runs/quit-later.sh
C="-w 360 -h 720 --sdk-int 26"
CH="$C $A/com.uravgcode.chooser_26.apk"
GB="$C $A/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
mkdir -p $O
until grep -q P4-DONE $HOME/work/perf-runs/run-all.log 2>/dev/null; do sleep 60; done
echo "$(date +%H:%M:%S) p5 start" >> $O/suite.log
$T/perf-run.sh -o $O/chooser-norelocate-1 -s 30 -i 60 -n 20 -c -- -X -Xnorelocate $CH >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/chooser-norelocate-2 -s 30 -i 60 -n 20 -c -- -X -Xnorelocate $CH >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/gadgetbridge-norelocate-1 -s 30 -i 60 -n 20 -c -x "tap 24 23" -- -X -Xnorelocate $GB >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/pair-norelocate-1 -s 30 -i 60 -n 20 -x "tap 24 23" -2 "-X -Xnorelocate $CH" -- -X -Xnorelocate $GB >> $O/suite.log 2>&1
GDK_DISABLE=vulkan $T/perf-run.sh -o $O/chooser-novulkan-1 -s 30 -i 60 -n 20 -c -- $CH >> $O/suite.log 2>&1
GDK_DISABLE=gl $T/perf-run.sh -o $O/chooser-nogl-1 -s 30 -i 60 -n 20 -c -- $CH >> $O/suite.log 2>&1
GDK_DISABLE=vulkan $T/perf-run.sh -o $O/gadgetbridge-novulkan-1 -s 30 -i 60 -n 20 -c -x "tap 24 23" -- $GB >> $O/suite.log 2>&1
GDK_DEBUG=opengl,vulkan GSK_DEBUG=renderer $T/perf-run.sh -o $O/chooser-debug-1 -s 30 -i 60 -n 20 -c -- $CH >> $O/suite.log 2>&1
GDK_DEBUG=opengl,vulkan GSK_DEBUG=renderer $T/perf-run.sh -o $O/gadgetbridge-debug-1 -s 30 -i 60 -n 20 -c -x "tap 24 23" -- $GB >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/chooser-quit-1 -s 30 -i 90 -n 30 -c -x "$Q 50" -- $CH >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/gadgetbridge-quit-1 -s 30 -i 90 -n 30 -c -x "tap 24 23" -x "$Q 50" -- $GB >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/heliboard-quit-1 -s 30 -i 90 -n 30 -c -x "$Q 50" -- $C $A/helium314.keyboard/helium314.keyboard_4201.apk >> $O/suite.log 2>&1
echo "$(date +%H:%M:%S) suite done: $O" >> $O/suite.log
echo P5-DONE >> $HOME/work/perf-runs/run-all.log
