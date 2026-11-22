#!/bin/sh
# Measurement F of the #118 evaluation: GDK_DISABLE=gl against the default on one build (origin/phosh),
# Chooser and DB Navigator (WebView), start only, 3 rounds alternating after a warm-up of each.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools
O=$HOME/work/perf-runs/suite-f
D=$HOME/work/perf-runs/drm-fds.sh
APK=$HOME/work/apks
C="-w 360 -h 720 --sdk-int 26"
CH="$C $APK/com.uravgcode.chooser_26.apk"
DB="$C $APK/de.hafas.android.db_240000860/base.apk $APK/de.hafas.android.db_240000860/config.arm64_v8a.apk $APK/de.hafas.android.db_240000860/config.xxhdpi.apk"
DBX='-x "tap 331 683" -x "tap 331 683" -x "tap 331 683" -x "tap 180 683"'
mkdir -p $O; echo "$(date +%T) F start $(git -C $HOME/work/android_translation_layer log --oneline -1)" >> $O/suite.log
one() { # name env app
	name=$1; envv=$2; app=$3
	if [ $app = chooser ]; then
		env $envv $T/perf-run.sh -o $O/$name -s 30 -i 0 -x "$D start" -- $CH >>$O/suite.log 2>&1
	else
		env $envv sh -c "$T/perf-run.sh -o $O/$name -s 40 -i 0 $DBX -x '$D start' -- $DB" >>$O/suite.log 2>&1
	fi
}
for app in chooser dbnavigator; do
	one $app-warm-default X=1 $app
	one $app-warm-nogl GDK_DISABLE=gl $app
	for i in 1 2 3; do
		one $app-default-$i X=1 $app
		one $app-nogl-$i GDK_DISABLE=gl $app
	done
done
echo "$(date +%T) F done" >> $O/suite.log
echo F-DONE >> $HOME/work/perf-runs/fixes.log
