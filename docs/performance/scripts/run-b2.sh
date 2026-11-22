#!/bin/sh
# B, follow-up: why is Gadgetbridge's compiled code not touched in the class path arm? -verbose:oat,class-linker,startup in both arms.
export PATH=$HOME/.local/bin:$PATH
T=$HOME/work/atl-perf-results/tools; O=$HOME/work/perf-runs/suite-b
GB="-w 360 -h 720 --sdk-int 26 $HOME/work/apks/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk"
XDG_CACHE_HOME=$O/cache-boot $T/perf-run.sh -o $O/verbose-boot -s 30 -i 0 -x "tap 24 23" -- -X -verbose:oat $GB >> $O/suite.log 2>&1
ATL_FRAMEWORK_ON_CLASSPATH=1 XDG_CACHE_HOME=$O/cache-cp $T/perf-run.sh -o $O/verbose-cp -s 30 -i 0 -x "tap 24 23" -- -X -verbose:oat $GB >> $O/suite.log 2>&1
echo B2-DONE >> $HOME/work/perf-runs/fixes.log
