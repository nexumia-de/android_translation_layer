#!/bin/sh
# ab-flags.sh: same build, Chooser start only, alternating new defaults
# (norelocate, no CheckJNI) and old ones (ATL_RELOCATE_BOOT_IMAGE=1
# ATL_CHECK_JNI=1), 4 rounds. Separates the flags from day-to-day drift. #118
C=$HOME/work/android_translation_layer
R=$HOME/work/perf-runs/ab-flags
A="-w 360 -h 720 --sdk-int 26 $HOME/work/apks/com.uravgcode.chooser_26.apk"
mkdir -p $R; cd $C
for i in 1 2 3 4; do
	$C/tools/perf-run.sh -o $R/new-$i -s 30 -i 0 -- $A >/dev/null 2>&1
	ATL_RELOCATE_BOOT_IMAGE=1 ATL_CHECK_JNI=1 $C/tools/perf-run.sh -o $R/old-$i -s 30 -i 0 -- $A >/dev/null 2>&1
	echo "$(date +%T) round $i" >> $R/log
done
echo "AB DONE $(date +%T)" >> $R/log
