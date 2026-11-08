#!/bin/sh
# Per-thread CPU time and wake-ups of a process (issue #118).
#
# Usage: perf-threads.sh PID
#
# Prints one tab-separated line per thread: tid, name (/proc/PID/task/TID/comm),
# CPU time in ms (user + system, /proc/PID/task/TID/stat), voluntary and
# involuntary context switches (/proc/PID/task/TID/status). Run it at the
# start and the end of an idle interval and subtract to see which threads
# wake up and burn CPU while the app does nothing. Own processes only.
set -u
pid=$1
hz=$(getconf CLK_TCK 2>/dev/null || echo 100)
printf 'tid\tname\tcpu_ms\tvol_cs\tnonvol_cs\n'
for t in /proc/"$pid"/task/*; do
	tid=${t##*/}
	name=$(cat "$t/comm" 2>/dev/null) || continue
	cpu=$(sed 's/^.*) //' "$t/stat" 2>/dev/null | awk -v hz="$hz" '{ printf "%d", ($12 + $13) * 1000 / hz }')
	cs=$(awk '/^voluntary_ctxt_switches/ { v = $2 } /^nonvoluntary_ctxt_switches/ { n = $2 } END { printf "%d\t%d", v, n }' "$t/status" 2>/dev/null)
	printf '%s\t%s\t%s\t%s\n' "$tid" "$name" "${cpu:-0}" "${cs:-0	0}"
done
