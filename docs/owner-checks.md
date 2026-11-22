---
type: Checklist
title: Checks only the phone can do
description: Short list of measurements and tests that need the owner's phone, left by cloud sessions that could not run them; each entry says what to run, what to look at and which change waits for it.
tags: [phone, measurement, performance, owner]
generated: { by: claude-code/2.1.319, at: 2026-11-22T12:00:00Z }
sources:
  - "issue #118 and docs/performance.md"
---

# Checks only the phone can do

Cloud sessions have no phone. What they could not measure or test is
listed here; an agent on the phone (or the owner) works through it and
moves the result into the linked document. Remove an entry once done.
Nothing here has been run.

## Issue #118: base cost of ATL apps

Setup and how to run long series without the login being killed:
[dev-environment.md](dev-environment.md),
[performance.md](performance.md#how-it-is-measured).

1. **Transparent huge pages off (R1), before merging it.** Build the
   branch of the pull request that adds `disable_transparent_huge_pages()`
   (`src/main-executable/memory.c`) into `~/.local`, then run the A/B
   (about 50 minutes):
   `systemd-run --user --unit=perf-thp --collect ~/work/perf-runs/run-thp.sh`
   (copy of `docs/performance/scripts/run-thp.sh`; its "default" arm
   keeps huge pages with `ATL_TRANSPARENT_HUGE_PAGES=1`).
   - Confirm the flag: `grep THP_enabled /proc/<atl pid>/status` is `0`
     without the variable and `1` with it.
   - Compare with `scripts/thp-idle-growth.py`: anonymous memory at start
     and after 10 minutes and `AnonHugePages` (expected: the 7 to 20 MB
     idle growth gone, `AnonHugePages` 0), first frame in `start.tsv`
     and CPU in `idle.tsv` (must not get worse beyond the ~15 % drift).
   - Smoke test of all apps in [apps.md](apps.md) on the branch.
   - Merge only if memory falls and start/CPU do not get worse; write the
     numbers into performance.md under "Transparent huge pages off".
2. **Item 11 (MediaPlayer pipeline on first use), merged but not
   measured**: Chooser `-i 0` against the commit before it, then the
   sound test (performance.md, "Item 11").
3. **Long run (G)**: Gadgetbridge covered for 12 hours,
   `perf-run.sh -c -i 43200`, to see whether memory keeps growing over a
   day (with R1 merged, so the growth is not `khugepaged`'s).
4. **Reference on Android (stretch goal)**: if Waydroid is still
   installed, `dumpsys meminfo nodomain.freeyourgadget.gadgetbridge`
   in it.
