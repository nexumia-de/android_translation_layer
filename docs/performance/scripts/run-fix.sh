#!/bin/sh
# run-fix.sh NAME REF: check out REF in the phone checkout, build ATL into
# ~/.local, start every app once (app-smoke.sh), then measure Chooser and
# Gadgetbridge 3 times (perf-suite.sh, idle 600 s). The build runs under
# /tmp/atl-heavy.lock; headless-shot.sh takes the same lock per app run.
# Results in ~/work/perf-runs/fix-NAME/. VISIBLE=1 adds visible Chooser
# runs. Issue #118.
set -u
name=$1; ref=$2
R=$HOME/work/perf-runs/fix-$name
C=$HOME/work/android_translation_layer
mkdir -p "$R"
exec >>"$R/run.log" 2>&1
echo "$(date +%T) start $name $ref"
cd "$C" || exit 1
# a copy of app-smoke.sh from an earlier run is untracked; drop it
git ls-files --error-unmatch tools/app-smoke.sh >/dev/null 2>&1 || rm -f tools/app-smoke.sh
# builds regenerate JNI headers of hand-written ones; drop that change
git checkout -q -- src/api-impl-jni/generated_headers
git fetch -q origin && git checkout -q --detach "origin/$ref" || { echo "checkout failed"; exit 1; }
git log --oneline -1
[ -f tools/app-smoke.sh ] || cp "$HOME/work/perf-runs/app-smoke.sh" tools/app-smoke.sh
echo "$(date +%T) build"
flock /tmp/atl-heavy.lock tools/phone-setup.sh atl > "$R/build.log" 2>&1 || { echo "BUILD FAILED"; echo "FIX-$name FAILED" >> $HOME/work/perf-runs/fixes.log; exit 1; }
echo "$(date +%T) smoke"
sh tools/app-smoke.sh -o "$R/smoke" > /dev/null
echo "$(date +%T) suite"
tools/perf-suite.sh -o "$R/suite" -r 3 -i 600 -a "chooser gadgetbridge" > /dev/null
# VISIBLE=1: also 3 runs of Chooser with its window shown (not covered),
# 5 minutes idle each, for frame-timing changes (item 5)
if [ "${VISIBLE:-0}" = 1 ]; then
	echo "$(date +%T) visible"
	for i in 1 2 3; do
		tools/perf-run.sh -o "$R/suite/chooser-visible-$i" -s 30 -i 300 -- \
			-w 360 -h 720 --sdk-int 26 "$HOME/work/apks/com.uravgcode.chooser_26.apk" >> "$R/suite/suite.log" 2>&1
	done
fi
echo "$(date +%T) done"
echo "FIX-$name DONE $(date +%T)" >> $HOME/work/perf-runs/fixes.log
