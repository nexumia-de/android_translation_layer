#!/bin/sh
# Step helper for perf-run.sh: per DRM file descriptor of the app, the memory fdinfo reports (one device or context each), into $PERF_OUT/drm-fds-$1.txt
P=$(cat "$XDG_RUNTIME_DIR/atl.pid")
for f in /proc/$P/fdinfo/*; do
	grep -q '^drm-driver' "$f" 2>/dev/null || continue
	echo "== fd $(basename $f) -> $(readlink /proc/$P/fd/$(basename $f))"
	grep '^drm-' "$f"
done > "$PERF_OUT/drm-fds-$1.txt"
