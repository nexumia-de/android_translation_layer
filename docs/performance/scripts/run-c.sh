#!/bin/sh
# Measurement C of the #118 evaluation, item 11: Chooser start only (-i 0) on
# fe78b717 (before) and on origin/phosh 069812e0 (after), 4 starts each after a
# warm-up, each build with its own XDG_CACHE_HOME; then the smoke test on phosh.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools
C=$HOME/work/android_translation_layer
O=$HOME/work/perf-runs/suite-c
CH="-w 360 -h 720 --sdk-int 26 $HOME/work/apks/com.uravgcode.chooser_26.apk"
mkdir -p $O
build() { # name ref
	cd $C
	git checkout -q -- src/api-impl-jni/generated_headers
	git checkout -q --detach $2 || return 1
	echo "$(date +%T) build $1 $(git log --oneline -1)" >> $O/suite.log
	flock /tmp/atl-heavy.lock tools/phone-setup.sh atl > $O/build-$1.log 2>&1
}
runs() { # name
	rm -rf $O/cache-$1; mkdir -p $O/cache-$1
	XDG_CACHE_HOME=$O/cache-$1 $T/perf-run.sh -o $O/$1-warm -s 30 -i 0 -- $CH >>$O/suite.log 2>&1
	for i in 1 2 3 4; do
		XDG_CACHE_HOME=$O/cache-$1 $T/perf-run.sh -o $O/$1-$i -s 30 -i 0 -- $CH >>$O/suite.log 2>&1
	done
}
build before fe78b717 || { echo "BUILD before FAILED" >> $O/suite.log; exit 1; }
runs before
build after origin/phosh || { echo "BUILD after FAILED" >> $O/suite.log; exit 1; }
runs after
echo "$(date +%T) smoke" >> $O/suite.log
sh $C/tools/app-smoke.sh -o $O/smoke > /dev/null 2>&1
echo "$(date +%T) C done" >> $O/suite.log
echo C-DONE >> $HOME/work/perf-runs/fixes.log
