#!/bin/sh
# Step helper for perf-run.sh: close the app's window after $1 seconds so the app exits through its own shutdown (TU_DEBUG=bos prints at device destroy).
(sleep "$1"; wlrctl toplevel close) >/dev/null 2>&1 &
