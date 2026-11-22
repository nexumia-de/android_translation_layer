#!/bin/sh
# Follow-up of A (#118): the GL context of the Bitmap path comes from GTK's dmabuf init (first GdkDmabufTexture
# of a display). Check with GDK_DISABLE=dmabuf: Gadgetbridge and Chooser, default against the flag, 2 rounds alternating.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-g
D=$HOME/work/perf-runs/drm-fds.sh
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
GB="$C $APK/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
echo "$(date +%T) G2 start" >> $O/suite.log
for i in 1 2; do
	for arm in default nodmabuf; do
		[ $arm = default ] && e=X=1 || e=GDK_DISABLE=dmabuf
		env $e $T/perf-run.sh -o $O/gadgetbridge-$arm-$i -s 30 -i 0 -x "tap 24 23" -x "$D start" -- $GB >> $O/suite.log 2>&1
		env $e $T/perf-run.sh -o $O/chooser-$arm-$i -s 30 -i 0 -x "$D start" -- $CH >> $O/suite.log 2>&1
	done
done
echo "$(date +%T) G2 done" >> $O/suite.log
echo G2-DONE >> $HOME/work/perf-runs/fixes.log
