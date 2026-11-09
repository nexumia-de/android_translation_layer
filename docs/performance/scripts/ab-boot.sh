#!/bin/sh
# A/B for item 4 of #118 on one build: framework on the boot class path
# (default) vs ATL_FRAMEWORK_ON_CLASSPATH=1, each with its own
# XDG_CACHE_HOME so the boot images do not replace each other. Chooser
# start only (30 s settle), 4 rounds alternating, after one warm-up each.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/android_translation_layer/tools
O=$HOME/work/perf-runs/ab-boot
A="-w 360 -h 720 --sdk-int 26 $HOME/work/apks/com.uravgcode.chooser_26.apk"
mkdir -p $O/cache-boot $O/cache-cp
run() { # name round
	if [ $1 = cp ]; then
		ATL_FRAMEWORK_ON_CLASSPATH=1 XDG_CACHE_HOME=$O/cache-cp $T/perf-run.sh -o $O/$1-$2 -s 30 -i 0 -- $A >>$O/log 2>&1
	else
		XDG_CACHE_HOME=$O/cache-boot $T/perf-run.sh -o $O/$1-$2 -s 30 -i 0 -- $A >>$O/log 2>&1
	fi
}
run boot warm; run cp warm
for i in 1 2 3 4; do run boot $i; run cp $i; done
echo "AB-BOOT DONE $(date +%T)" >> $HOME/work/perf-runs/fixes.log
