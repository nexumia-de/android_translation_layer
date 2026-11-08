---
type: Plan
title: Base cost of ATL apps (memory first)
description: Goal, how the cost of an ATL app is measured on the phone, the baseline of 2026-11-08 (Chooser, Gadgetbridge, HeliBoard, DB Navigator, two apps at once) and the handoff for the analysis; nothing fixed yet.
tags: [performance, memory, battery, background, gadgetbridge, measurement]
generated: { by: claude-code/2.1.319, at: 2026-11-08T13:00:00Z }
sources:
  - "on-device: tools/perf-suite.sh runs in ~/work/perf-runs/suite-p1..p3, raw files under performance/"
---

# Goal

The owner dropped Gadgetbridge because its base cost on the phone was
too high, and named memory as the likely bottleneck (2026-11-08). The
goal is to lower everything an ATL app costs while it runs, so that
apps meant to run all the time (Gadgetbridge, GPSLogger, FluffyChat and
WhatsApp push, HeliBoard as keyboard) become usable. The owner's stretch
goal: run better than the same app on Android. Not only Gadgetbridge:
every app should get cheaper, so the baseline covers four apps and two
apps running at once.

Most of the cost is expected in ATL itself (each app starts its own ART
runtime), so a gain helps every app. The [hardening roadmap](hardening-roadmap.md)
moves every app into its own Flatpak; whether apps can share memory
belongs in that design before the migration (phase 6), not after.

Work item: issue #118.

# How it is measured

Everything runs headless on the phone as `claude`, driven from the
laptop over SSH ([development environment](dev-environment.md#agent-on-a-laptop-over-ssh)),
with the `~/.local` build of this checkout. The scripts live in
`tools/` so the same measurement can be repeated after every fix:

| Script | What it measures | Source |
|---|---|---|
| `perf-smaps.py PID [LABEL]` | Memory by category: PSS, USS, RSS, swap per category and the biggest mappings | `/proc/PID/smaps`, `/proc/PID/task/*/syscall` |
| `perf-idle.sh PID SECONDS [INTERVAL]` | Over time: CPU time (user, system), context switches of all threads, PSS/USS/RSS, GPU memory, battery current and power | `/proc/PID/stat`, `task/*/status`, `smaps_rollup`, `fdinfo` (`drm-*`), `/sys/class/power_supply` |
| `perf-threads.sh PID` | Per thread: CPU time and context switches, to subtract start from end | `task/TID/stat`, `status`, `comm` |
| `perf-start.py RESULT CMD...` | Time from exec to the first Wayland message, the first `xdg_toplevel.configure`, the first and the tenth `wl_surface.commit` with a buffer | `WAYLAND_DEBUG=client` |
| `perf-run.sh [-c] [-2 CMD] [-x STEP] -- ARGS` | One run: start, settle, snapshot, optional cover window or second app, idle sampling, snapshot | `headless-shot.sh` (cage) |
| `perf-suite.sh` | The whole baseline: per app 3 start runs and 3 background runs, plus the pair | |
| `perf-report.py SUITE... [-o DIR]` | Markdown tables (medians, min-max) and copies of the small result files | |

Conditions, chosen to match the owner's phone:

- Apps start as F-Droid and Aurora Store install them:
  `-w 360 -h 720 --sdk-int 26`; the display is 720×1440 at scale 2.
- "After start" is 30 s after exec (25 s in the start runs), once the
  first screen is up and dex2oat is done; DB Navigator's tour and
  Gadgetbridge's welcome page are left with taps first
  (`perf-suite.sh`).
- "In the background" means the app's window is covered by another
  client (`foot`) in the same compositor for the idle time, as a
  background app under phoc is covered by the foreground app. The app
  gets no frame callbacks then. A run without a cover (the app visible
  and idle) is the other condition.
- Idle time 30 minutes (10 for the extra repeats of HeliBoard, DB
  Navigator and the pair), one sample per minute.
- Everything runs next to the owner's Phosh session and its daemons, so
  battery numbers are for the whole phone and only context; the
  `claude` login's own session daemons (dbus, keyring, gvfs, pipewire)
  are not counted.
- Wake-ups are voluntary context switches of the app's threads per
  minute: a thread that goes to sleep and is woken counts once. The
  kernel has no `CONFIG_SCHEDSTATS`, `perf` is not installed and
  `/proc/timer_list` is root-only, so this is the only wake-up count an
  unprivileged user gets; [app status](apps.md#needs-owner) lists what
  the owner can install to do better.

How the memory categories are told apart, since the kernel has no
`CONFIG_ANON_VMA_NAME` and anonymous memory carries no names:

- `art-heap`: anonymous memory below 4 GiB. ART keeps its heap spaces
  there because object references are 32 bit; the main space starts at
  `0x12c00000` as on Android.
- `stacks`: the mapping holding each thread's saved stack pointer, plus
  every anonymous `rw` mapping above 4 GiB behind a `PROT_NONE` guard
  mapping (musl's pthread stack layout).
- `native-anon`: the rest of the anonymous memory above 4 GiB: musl's
  malloc for ART's metadata (class tables, dex caches, linear alloc),
  GTK, Mesa and the app's native libraries. It cannot be split further
  without names; see the handoff.
- `art-image`: the boot image and compiled code in `~/.cache/art/arm64`
  (`boot.oat`, `boot.art`, `boot-*.oat`, the app's `…@classes.dex` oat
  and `.vdex`); `art-jit`: the JIT code cache (`memfd:jit-cache`).
- `app-dex`: the APKs, splits and jars mapped directly; `lib-art`,
  `lib-bionic`, `lib-atl`, `lib-gtk` (GTK, GLib, Pango, Cairo, WebKit,
  …), `lib-mesa` (GL, Vulkan, LLVM, libdrm), `lib-other`: shared
  libraries by group; `icu-data`: `icudt78l.dat`; `graphics`: CPU
  mappings of GEM buffers (GPU memory itself is read from `fdinfo`).

# Results

<!-- RESULTS -->

# Handoff for the analysis

<!-- HANDOFF -->
