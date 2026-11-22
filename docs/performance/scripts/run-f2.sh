#!/bin/sh
# F, follow-up: does WebKitGTK still draw with GDK_DISABLE=gl? A plain WebKit window (tools/perf-webkit-floor.c), default and nogl, with the per-client GPU memory.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools; O=$HOME/work/perf-runs/suite-f; D=$HOME/work/perf-runs/drm-fds.sh
W=$HOME/work/perf-runs/bin/perf-webkit-floor
PERF_CMD=$W $T/perf-run.sh -o $O/webkit-default -s 15 -i 0 -x "$D start" >> $O/suite.log 2>&1
GDK_DISABLE=gl PERF_CMD=$W $T/perf-run.sh -o $O/webkit-nogl -s 15 -i 0 -x "$D start" >> $O/suite.log 2>&1
echo F2-DONE >> $HOME/work/perf-runs/fixes.log
