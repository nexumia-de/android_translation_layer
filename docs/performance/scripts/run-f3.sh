#!/bin/sh
# F, follow-up: smoke test of all apps with GDK_DISABLE=gl, to see which ones need GL (SurfaceView, video).
export PATH=$HOME/.local/bin:$PATH
export GDK_DISABLE=gl
sh $HOME/work/android_translation_layer/tools/app-smoke.sh -o $HOME/work/perf-runs/suite-f/smoke-nogl > /dev/null 2>&1
echo F3-DONE >> $HOME/work/perf-runs/fixes.log
