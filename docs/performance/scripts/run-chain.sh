#!/bin/sh
# run-chain.sh NAME:REF ...: run-fix.sh for each pair in turn, VISIBLE=1,
# so all of them run under the same conditions. Issue #118.
cd "$HOME/work/perf-runs" || exit 1
for p in "$@"; do
	VISIBLE=1 ./run-fix.sh "${p%%:*}" "${p#*:}"
done
echo "CHAIN DONE $(date +%T)" >> fixes.log
