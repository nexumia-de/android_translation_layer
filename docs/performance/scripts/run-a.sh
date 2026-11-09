#!/bin/sh
# Measurement A of the #118 evaluation: what the GPU memory floor is.
# Floor (plain GTK4 window), Chooser and Gadgetbridge with TU_DEBUG=bos and
# GSK_DEBUG=cache, each with 20 s idle samples so idle.tsv has gpu_total_kb.
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-a
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
GB="$C $APK/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
export PATH=$HOME/.local/bin:$PATH
mkdir -p $O
echo "$(date +%T) A start" >> $O/suite.log
PERF_CMD=$HOME/work/perf-runs/bin/perf-gtk-floor $T/perf-run.sh -o $O/floor-1 -s 20 -i 60 -n 20 >> $O/suite.log 2>&1
PERF_CMD=$HOME/work/perf-runs/bin/perf-gtk-floor GSK_DEBUG=cache TU_DEBUG=bos $T/perf-run.sh -o $O/floor-bos -s 20 -i 60 -n 20 >> $O/suite.log 2>&1
$T/perf-run.sh -o $O/chooser-plain -s 30 -i 60 -n 20 -- $CH >> $O/suite.log 2>&1
GSK_DEBUG=cache TU_DEBUG=bos $T/perf-run.sh -o $O/chooser-bos -s 30 -i 60 -n 20 -- $CH >> $O/suite.log 2>&1
GSK_DEBUG=cache TU_DEBUG=bos $T/perf-run.sh -o $O/gadgetbridge-bos -s 30 -i 60 -n 20 -x "tap 24 23" -- $GB >> $O/suite.log 2>&1
echo "$(date +%T) A done" >> $O/suite.log
echo A-DONE >> $HOME/work/perf-runs/fixes.log
