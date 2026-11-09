#!/bin/sh
# Baseline of issue #118, 2026-11-08: three passes, see tools/perf-suite.sh.
T=$HOME/work/android_translation_layer/tools
$T/perf-suite.sh -o $HOME/work/perf-runs/suite-p1 -r 1 -i 1800
$T/perf-suite.sh -o $HOME/work/perf-runs/suite-p2 -r 2 -i 1800 -a "chooser gadgetbridge"
$T/perf-suite.sh -o $HOME/work/perf-runs/suite-p3 -r 2 -i 600 -a "heliboard dbnavigator pair"
echo ALL-DONE
