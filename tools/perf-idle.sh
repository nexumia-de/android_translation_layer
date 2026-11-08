#!/bin/sh
# Sample an app's idle cost over time (issue #118).
#
# Usage: perf-idle.sh PID SECONDS [INTERVAL]
#
# Every INTERVAL seconds (default 60) prints one tab-separated line: elapsed
# seconds, CPU time of the process in ms (user, system, from /proc/PID/stat),
# the sum of voluntary and involuntary context switches over all its threads
# (/proc/PID/task/*/status; a voluntary switch is a thread going to sleep,
# so the delta per minute is the number of wake-ups), thread count, PSS, USS,
# RSS and swap in kB (/proc/PID/smaps_rollup), GPU memory of the process
# (drm-* keys in /proc/PID/fdinfo of its render node fds), and the battery's
# current and power (/sys/class/power_supply, whole phone, for context only).
# Works without root for the caller's own processes; stops when PID exits.
# No scheduler statistics (CONFIG_SCHEDSTATS) and no /proc/timer_list are
# available to an unprivileged user on the phone, hence the context switches.
set -u
pid=$1
total=$2
interval=${3:-60}
hz=$(getconf CLK_TCK 2>/dev/null || echo 100)
bat=/sys/class/power_supply/qcom-battmgr-bat

cpu_ms() {
	# fields 14 and 15 of /proc/PID/stat are utime and stime in clock ticks;
	# the command name (field 2) may contain spaces, so cut after the ')'.
	sed 's/^.*) //' "/proc/$pid/stat" | awk -v hz="$hz" '{ printf "%d\t%d", $12 * 1000 / hz, $13 * 1000 / hz }'
}
switches() {
	cat /proc/"$pid"/task/*/status 2>/dev/null | awk '
		/^voluntary_ctxt_switches/ { v += $2 }
		/^nonvoluntary_ctxt_switches/ { n += $2 }
		END { printf "%d\t%d", v, n }'
}
threads() { ls "/proc/$pid/task" 2>/dev/null | wc -l; }
mem() {
	awk '/^Pss:/ { pss = $2 } /^Private_Clean:|^Private_Dirty:/ { uss += $2 } /^Rss:/ { rss = $2 } /^Swap:/ { swap = $2 }
		END { printf "%d\t%d\t%d\t%d", pss, uss, rss, swap }' "/proc/$pid/smaps_rollup"
}
gpu() {
	# One line per render node fd in fdinfo; sum the memory keys (kB or MiB).
	cat /proc/"$pid"/fdinfo/* 2>/dev/null | awk '
		function kb(v, u) { if (u == "MiB") return v * 1024; if (u == "GiB") return v * 1048576; return v }
		/^drm-total-memory:/ { t += kb($2, $3) }
		/^drm-resident-memory:/ { r += kb($2, $3) }
		/^drm-total-vram:|^drm-total-system:|^drm-total-gtt:/ { t2 += kb($2, $3) }
		END { printf "%d\t%d", (t ? t : t2), r }'
}
battery() {
	c=$(cat $bat/current_now 2>/dev/null || echo 0)
	p=$(cat $bat/power_now 2>/dev/null || echo 0)
	s=$(cat $bat/status 2>/dev/null || echo unknown)
	printf '%d\t%d\t%s' "$((c / 1000))" "$((p / 1000))" "$s"
}

printf 'elapsed_s\tutime_ms\tstime_ms\tvol_cs\tnonvol_cs\tthreads\tpss_kb\tuss_kb\trss_kb\tswap_kb\tgpu_total_kb\tgpu_resident_kb\tbat_current_ma\tbat_power_mw\tbat_status\tclock\n'
start=$(date +%s)
while :; do
	[ -d "/proc/$pid" ] || { echo "# pid $pid gone" >&2; exit 1; }
	now=$(date +%s)
	printf '%d\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$((now - start))" "$(cpu_ms)" "$(switches)" "$(threads)" "$(mem)" "$(gpu)" "$(battery)" "$(date +%H:%M:%S)"
	[ $((now - start + interval)) -le "$total" ] || break
	sleep "$interval"
done
