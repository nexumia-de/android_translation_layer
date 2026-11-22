#!/bin/sh
# Measurement B of the #118 evaluation, item 4 on the branch rebased onto phosh:
# (1) first start after an update (fresh XDG_CACHE_HOME, image build) for both arms,
# (2) pair runs (Gadgetbridge first, Chooser second) alternating between the arms,
#     each arm with its own cache, so the boot image's file pages can be seen shared,
# (3) the smoke test of all apps on the branch.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-b
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
GB="$C $APK/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
mkdir -p $O; rm -rf $O/cache-boot $O/cache-cp; mkdir -p $O/cache-boot $O/cache-cp
run() { # arm name args...
	arm=$1; name=$2; shift 2
	if [ $arm = cp ]; then
		ATL_FRAMEWORK_ON_CLASSPATH=1 XDG_CACHE_HOME=$O/cache-cp $T/perf-run.sh -o $O/$name "$@" >>$O/suite.log 2>&1
	else
		XDG_CACHE_HOME=$O/cache-boot $T/perf-run.sh -o $O/$name "$@" >>$O/suite.log 2>&1
	fi
}
echo "$(date +%T) B start" >> $O/suite.log
run boot first-boot -s 30 -i 0 -- $CH
run cp first-cp -s 30 -i 0 -- $CH
for i in 1 2 3; do
	run boot pair-boot-$i -s 30 -i 0 -x "tap 24 23" -2 "$CH" -- $GB
	run cp pair-cp-$i -s 30 -i 0 -x "tap 24 23" -2 "$CH" -- $GB
done
echo "$(date +%T) smoke" >> $O/suite.log
sh $HOME/work/android_translation_layer/tools/app-smoke.sh -o $O/smoke > /dev/null 2>&1
echo "$(date +%T) B done" >> $O/suite.log
echo B-DONE >> $HOME/work/perf-runs/fixes.log
