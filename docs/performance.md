---
type: Plan
title: Base cost of ATL apps (memory first)
description: Goal, how the cost of an ATL app is measured on the phone, the baseline of 2026-11-08 (Chooser, Gadgetbridge, HeliBoard, DB Navigator, two apps at once), the code analysis of the base cost with each estimate checked against the measurements, what apps can share in per-app Flatpaks, the fixes measured so far (items 1, 2 and 6 merged, 3, 5 and 7 rejected, 4 measured in an A/B run, 11 merged and not yet measured), the evaluation of all measurements with the ranked list of what can still be implemented (GPU memory first, then the app image and the split of the anonymous memory), and the handoff.
tags: [performance, memory, battery, background, gadgetbridge, measurement, art, boot-image, webkit, mesa, gpu-memory, app-image, flatpak]
generated: { by: claude-code/2.1.319, at: 2026-11-09T19:30:00Z }
sources:
  - "on-device: tools/perf-suite.sh runs in ~/work/perf-runs/suite-p1..p5 (p1 and p2 done), small result files under performance/"
  - id: gtk-renderer
    resource: https://gitlab.gnome.org/GNOME/gtk/-/blob/4.20.0/gsk/gskrenderer.c
    title: GTK 4.20.0, gsk/gskrenderer.c (renderer_possibilities, Vulkan before GL)
  - id: gtk-dmabuf
    resource: https://gitlab.gnome.org/GNOME/gtk/-/blob/4.20.0/gdk/gdkdisplay.c
    title: GTK 4.20.0, gdk/gdkdisplay.c (gdk_display_init_dmabuf initialises Vulkan and EGL)
  - id: gtk-gpu-cache
    resource: https://gitlab.gnome.org/GNOME/gtk/-/blob/4.20.0/gsk/gpu/gskgpudevice.c
    title: GTK 4.20.0, gsk/gpu/gskgpudevice.c (CACHE_TIMEOUT 15 s) and gskgpurenderer.c (render_texture downloads into a memory texture)
  - id: alpine-mesa
    resource: https://gitlab.alpinelinux.org/alpine/aports/-/blob/master/main/mesa/APKBUILD
    title: Alpine aports, main/mesa/APKBUILD (gallium drivers llvmpipe and freedreno in one library, LLVM 23)
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

# What is known

Work item: issue #118. Baseline: see [Results](#results) (passes 1 and 2
of 5). Before it, [android-keyboard.md](android-keyboard.md) estimates
150 to 300 MB resident per ART process; Stevia, the native keyboard,
uses 78 MB RSS.

# Order of work

1. Measure Gadgetbridge (connected and idle) and one simple app
   (Chooser) as the baseline of ATL alone.
2. Analyse the profiles and measurements with the most capable model
   (Fable): where the memory goes across ART, bionic_translation, the
   GTK backend and the app.
3. Fix one cause at a time and measure before and after each change.

# Hypotheses to check

Unverified; listed so the analysis checks them, not as facts:

- ART runs app code interpreted or JIT-compiled instead of compiled
  ahead of time at install.
- Nothing is shared between ATL processes; Android shares a preloaded
  runtime (zygote) and boot image between all apps.
- A hidden or minimised window keeps rendering and holds graphics
  buffers.
- Timers or polling in ATL wake the CPU while idle.
- Better than Android looks possible system-wide where Android carries
  Google Play services and system_server and ATL does not; per app it
  is harder, because Android has the zygote, a precompiled boot image
  and freezes cached apps.

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
  client (`foot`, fullscreen) in the same compositor for the idle
  time, as a background app under phoc is covered by the foreground
  app. The app gets no frame callbacks then (until 2026-11-09 a strip
  stayed visible, see "The covered runs were not covered"). A run without a cover (the app visible
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

Baseline of 2026-11-08, ATL `cbd9da5e`, measured with `tools/perf-suite.sh`
as described above. The series ran unattended on the phone
(`~/work/perf-runs/run-all.sh`, one pass after the other) and **stopped
at 16:28 during pass 3**, so it is incomplete:

| Pass | What | Status |
|---|---|---|
| 1 (`suite-p1`) | All four apps and the pair, 1 start and 1 background run each, idle 30 min | done, 11:55 to 14:26 |
| 2 (`suite-p2`) | Chooser and Gadgetbridge, 2 more start and background runs each, idle 30 min | done, 14:26 to 16:27 |
| 3 (`suite-p3`) | HeliBoard, DB Navigator and the pair, 2 more runs each, idle 10 min | **open**: stopped at 16:28 in HeliBoard's first background run (last line of `suite.log` 16:28:09, the run's files last written 16:31, no end snapshot), no usable result |
| 4 (`suite-p4`) | Visible (uncovered) idle of Chooser and Gadgetbridge, GTK renderers cairo, ngl and vulkan | **open**: never started (waits for pass 3) |
| 5 (`suite-p5`) | Verification runs for the code analysis: `-Xnorelocate`, `GDK_DISABLE=vulkan`/`gl`, GTK debug logs, quit after start | **open**: never started (waits for pass 4) |

**Cause of the stop (verified 2026-11-08 from the journal):** the
laptop's SSH connection timed out at 16:31:01, and logind, which has
`KillUserProcesses=yes` on the phone, killed the login's session scope
with the series in it; `nohup setsid` does not leave the scope. Linger
alone does not help; long runs now start as user units with
`systemd-run --user` (see [development environment](dev-environment.md#agent-on-a-laptop-over-ssh)).

So Chooser and Gadgetbridge have 3 runs each (6 start values, since
every background run also records its start), HeliBoard, DB Navigator
and the pair one run each (2 start values). Single runs carry no
spread and are only indicative. Result files: [performance/suite-p1](performance/suite-p1/)
and [performance/suite-p2](performance/suite-p2/) (copied with
`tools/perf-report.py suite-p1 suite-p2 -o docs/performance`; the
screenshots, logs and full `smaps` dumps stay on the phone).

What stands out, before any analysis:

- **Chooser burns CPU in the background**: about 9.8 s of CPU time and
  3600 wake-ups per minute while covered, almost all in its `main`
  thread (284 s of 286 s over 29 minutes), in all three runs. The other
  three apps use 3 to 33 ms per minute. This is the case
  [item 5](#5-frames-run-on-a-timer-not-on-the-display) is about.
- **Memory floor**: PSS after start is 200 MB (HeliBoard, Chooser) to
  300 MB (DB Navigator); USS 175 to 260 MB. The categories every app
  pays about equally (`art-image`, `lib-gtk`, `lib-mesa`, `lib-other`,
  `lib-art`) add up to 120 to 142 MB PSS per app.
- **Two apps share about 106 MB**: with Chooser started next to
  Gadgetbridge, Gadgetbridge's PSS drops from 256 to 203 MB and
  Chooser's PSS is 152 MB (USS 83 MB); one run only.
- PSS grows by 0 to 17 MB during 30 minutes of idling.
- DB Navigator's wake-up count is negative because about 60 threads
  ended in its first idle minute (153 to 93) and took their counts with
  them. From the second sample on (28 minutes) it is about 35 wake-ups
  and 31 ms CPU per minute (computed by hand from `idle.tsv`).
- No app except Gadgetbridge drew ten frames within the start run, so
  the "10th frame" column is empty for them.

The tables below are the output of `perf-report.py` (median, min-max and
n in brackets when there is more than one run).

## Start and memory per app

| App | First frame (ms) | 10th frame (ms) | PSS after start (MB) | USS after start (MB) | PSS after idle (MB) | USS after idle (MB) | Idle (min) | Threads |
|---|---|---|---|---|---|---|---|---|
| chooser | 2496 (2232-3151, n=6) | - | 204 (179-209, n=6) | 179 (154-184, n=6) | 222 (187-223, n=3) | 197 (162-198, n=3) | 29 (29-29, n=3) | 91 (91-91, n=3) |
| dbnavigator | 5517 (5427-5607, n=2) | - | 299 (297-302, n=2) | 256 (250-261, n=2) | 311 | 270 | 29 | 92 |
| gadgetbridge | 2374 (2311-2446, n=6) | 2537 (2464-2609, n=6) | 254 (249-257, n=6) | 218 (216-220, n=6) | 263 (253-266, n=3) | 226 (225-230, n=3) | 29 (29-29, n=3) | 46 (46-48, n=3) |
| heliboard | 1726 (1714-1737, n=2) | - | 201 (200-203, n=2) | 176 (175-178, n=2) | 199 | 175 | 29 | 37 |

## Idle cost in the background (covered window)

The window was not fully covered in these runs (see "The covered runs
were not covered" under Fixes): Chooser's CPU and wake-ups are those of
a visible window.

| App | CPU ms per minute | Wake-ups per minute (voluntary switches) | PSS growth during idle (MB) | GPU memory total / resident (MB) |
|---|---|---|---|---|
| chooser | 9815 (9697-10286, n=3) | 3578 (3575-3580, n=3) | +12.8 (+7.4-+17.1, n=3) | 99 (99-99, n=3) / 75 (75-75, n=3) |
| dbnavigator | 33 | -116 | +11.4 | 100 / 76 |
| gadgetbridge | 6 (6-6, n=3) | 7 (7-8, n=3) | +6.6 (+4.5-+9.7, n=3) | 63 (63-63, n=3) / 51 (51-51, n=3) |
| heliboard | 3 | 4 | -0.3 | 62 / 50 |

## Memory by category after idle (PSS MB, median of the background runs; USS in brackets)

| Category | chooser | dbnavigator | gadgetbridge | heliboard |
|---|---|---|---|---|
| native-anon | 44.9 (44.9) | 94.8 (94.8) | 75.2 (75.2) | 34.8 (34.8) |
| art-image | 31.5 (31.5) | 38.1 (38.0) | 40.0 (39.9) | 42.4 (42.4) |
| lib-gtk | 36.2 (32.5) | 22.4 (8.0) | 32.4 (29.2) | 32.2 (29.2) |
| lib-mesa | 22.5 (7.9) | 20.1 (7.8) | 22.5 (7.9) | 22.5 (7.9) |
| lib-other | 34.5 (32.7) | 25.7 (21.7) | 32.9 (31.1) | 32.4 (30.9) |
| stacks | 26.2 (26.2) | 21.8 (21.8) | 10.5 (10.5) | 9.1 (9.1) |
| lib-art | 13.6 (13.2) | 13.6 (13.2) | 13.7 (13.3) | 12.9 (12.5) |
| art-heap | 6.8 (6.8) | 29.3 (29.3) | 14.6 (14.6) | 5.4 (5.4) |
| app-dex | 2.4 (0.3) | 35.6 (32.4) | 10.9 (0.3) | 4.3 (1.2) |
| icu-data | 2.0 (0.0) | 5.3 (0.0) | 9.8 (4.2) | 2.0 (0.0) |
| art-jit | 0.2 (0.1) | 0.9 (0.4) | 0.4 (0.2) | 0.0 (0.0) |
| lib-atl | 0.6 (0.6) | 0.6 (0.6) | 0.6 (0.6) | 0.6 (0.6) |
| lib-bionic | 0.3 (0.3) | 0.3 (0.3) | 0.3 (0.3) | 0.3 (0.3) |
| malloc | 0.3 (0.3) | 0.4 (0.4) | 0.4 (0.4) | 0.2 (0.2) |
| fonts | 0.2 (0.0) | 0.3 (0.1) | 0.3 (0.0) | 0.2 (0.0) |
| graphics | - | - | - | - |
| other-file | 0.1 (0.1) | 0.3 (0.1) | 0.1 (0.1) | 0.1 (0.1) |
| other-anon | 0.0 (0.0) | 1.4 (1.4) | 0.0 (0.0) | 0.0 (0.0) |
| icons-pixbuf | 0.0 (0.0) | 0.0 (0.0) | 0.0 (0.0) | 0.0 (0.0) |
| TOTAL | 222.2 (197.2) | 310.9 (270.2) | 262.5 (225.7) | 199.5 (174.5) |

## Threads with the most CPU while idle (ms over the idle time, median run per app)

- **chooser** (chooser-bg-1, 285920 ms in all threads): main 284000 ms / 104027 wake-ups, HeapTaskDaemon 1190 ms / 44 wake-ups, FinalizerDaemon 460 ms / 21 wake-ups, Jit thread pool 160 ms / 11 wake-ups, gdbus 70 ms / 58 wake-ups, ReferenceQueueD 30 ms / 20 wake-ups
- **dbnavigator** (dbnavigator-bg-1, 890 ms in all threads): gdbus 190 ms / 132 wake-ups, PressureMonitor 160 ms / 350 wake-ups, main 150 ms / 69 wake-ups, Jit thread pool 140 ms / 58 wake-ups, WM.task-4 100 ms / 46 wake-ups, BlueZ signals 60 ms / 77 wake-ups
- **gadgetbridge** (gadgetbridge-bg-1, 250 ms in all threads): gdbus 120 ms / 87 wake-ups, main 110 ms / 24 wake-ups, BlueZ signals 20 ms / 29 wake-ups
- **heliboard** (heliboard-bg-1, 200 ms in all threads): main 100 ms / 33 wake-ups, gdbus 90 ms / 59 wake-ups, DefaultDispatch 10 ms / 29 wake-ups

## Two apps at once (Gadgetbridge first, Chooser second)

| Run | PSS first alone (MB) | PSS first with second (MB) | USS first alone | USS first with second | PSS second (MB) | USS second (MB) | Shared between the two (MB) |
|---|---|---|---|---|---|---|---|
| pair-1 | 256 | 203 | 219 | 122 | 152 | 83 | ~106 |


## Fixes

Each fix is measured like the baseline: `~/work/perf-runs/run-fix.sh`
builds the branch on the phone, starts every app of [apps.md](apps.md)
once (`tools/app-smoke.sh`), then runs `tools/perf-suite.sh -r 3 -i 600
-a "chooser gadgetbridge"` (3 start and 3 covered 10-minute idle runs
per app). Median of the runs, Chooser / Gadgetbridge. Since the night of
2026-11-08 the agent runs on the phone itself; whole-phone numbers and
start times of later fixes are compared only with a "before" that also
ran with the agent on the phone. Per-process memory and CPU are barely
affected.

### Items 1 and 6: `-Xnorelocate`, CheckJNI opt-in

Branch `claude/perf-runtime-flags-118`. Before: `fix-0-phosh`
(`43d35a19`), after: `fix-1b-flags-no3` (`5f20712c`).

| | First frame (ms) | PSS after start (MB) | PSS after idle (MB) | CPU ms/min covered | Wake-ups/min covered | GPU MB |
|---|---|---|---|---|---|---|
| before | 2529 / 2438 | 205 / 255 | 220 / 263 | 8317 / 4 | 3617 / 5 | 99 / 63 |
| after | 2430 / 2228 | 208 / 255 | 225 / 264 | 6610 / 3 | 3618 / 5 | 99 / 63 |

- **CPU**: Chooser's covered idle drops by 21 %, all in its main
  thread; the wake-ups stay (that is item 5). First frame 4 % / 9 %
  earlier.
- **Memory**: the `boot*.art` pages go from 2.4 MB to 0.5 MB private
  dirty per process (item 1); the rest of `art-image` was never
  relocated memory (see the correction in item 1). Chooser's PSS
  differed by +3 MB between the two series; an A/B run on the same
  build (`~/work/perf-runs/ab-flags`, Chooser start only, new and old
  defaults alternating 4 times, old ones with `ATL_RELOCATE_BOOT_IMAGE=1
  ATL_CHECK_JNI=1`) gives a median PSS of 207.3 MB new against 208.2 MB
  old, and first frame 2337 against 2498 ms. The difference was drift
  between the series: the `stacks` and `native-anon` categories swing by
  up to 8 MB from run to run, because `perf-smaps.py` counts an
  anonymous mapping after a guard page as a stack, which also matches
  mappings that are no stacks.
- **Smoke test**: the same as before (WhatsApp exits early, Duolingo's
  dex2oat crashes on an invalid dex while the app runs on, Super
  Productivity aborts: all already on `phosh`).

Merged. Item 3 (`GDK_DISABLE=vulkan`) was on the branch first and was
reverted, see item 3.

### Item 2: WebKit loaded with the first page

Branch `claude/perf-webkit-lazy-118`. Before: `fix-1b-flags-no3`
(`5f20712c`, the code of `phosh` after items 1 and 6), after:
`fix-2-webkit` (`0e21d572`). Both series ran with the agent on the
phone and before a second agent session (account `user`) started on
it on 2026-11-09 at about 08:40; fixes after this one need a new
"before" measured with both agents running.

| | First frame (ms) | PSS after start (MB) | USS after start (MB) | PSS after idle (MB) | CPU ms/min covered | Wake-ups/min covered | GPU MB |
|---|---|---|---|---|---|---|---|
| before | 2430 / 2228 | 208 / 255 | 182 / 218 | 225 / 264 | 6610 / 3 | 3618 / 5 | 99 / 63 |
| after | 2498 / 2298 | 171 / 217 | 146 / 180 | 189 / 228 | 6608 / 3 | 3619 / 5 | 99 / 63 |

- **Memory**: 36 to 38 MB less PSS and USS per process, almost all
  private. By category after idle (Chooser): `lib-gtk` 36.5 -> 8.9 MB
  (the category also holds the WebKit libraries), `lib-other` 34.9 ->
  25.7 MB (libsoup, libflite with its voices, libenchant, libhyphen,
  libmanette, libavif, libxslt and other WebKit dependencies are no
  longer mapped). The analysis estimated 10 to 20 MB.
- **CPU, GPU**: unchanged.
- **Start**: the first frame is 3 % later in both apps, inside the
  drift between series seen in the A/B run of items 1 and 6 (161 ms);
  each range overlaps the other series' median.
- **WebView still works**: `grep -c webkit /proc/<pid>/maps` is 0 for
  Chooser and Gadgetbridge; Post & DHL (onboarding page) and DB
  Navigator (already at start) load `libtranslation_layer_webkit.so`
  and WebKit, start their web processes and show the page as before.
  komoot does not open a WebView at start; WhatsApp exits before its
  first screen (already on `phosh`).
- **Smoke test**: the same as before.

Merged.

### The covered runs were not covered

Found on 2026-11-09 while measuring items 5 and 7: `perf-run.sh -c`
covered the app with a maximized foot, which rounds its height to whole
text lines and stayed 694 of 720 logical pixels high. A strip of the
app's window at the bottom stayed visible, the compositor kept sending
it frame callbacks, and an app that redraws itself went on drawing
(`GDK_DEBUG=frames`: about 53 frames per second for Chooser, visible or
"covered"). With foot fullscreen (`foot -F`, now in `perf-run.sh`) GTK
stops drawing as soon as the window is hidden: Chooser on `phosh`
(`fe78b717`) used 10 ms CPU and about 8 wake-ups per minute over
2 covered minutes (`~/work/perf-runs/check-cover-fs`), against 6 to 8 s
CPU and 3620 wake-ups per minute in the "covered" runs before.

So every "covered" CPU and wake-up number above (baseline, items 1, 2,
5 and 7) is the cost of a partly visible window. For Chooser that is
its own redraw loop: it draws at the display rate as long as it is
shown, as it would on Android. Its real background cost is near zero.
The memory numbers are not affected. The CPU gain of items 1 and 6
(-21 %) holds for the visible window.

### Items 5 and 7: measured, not merged

Before: `fix-3-phosh` (`fe78b717`), after: `fix-5-vsync` (`4256e495`)
and `fix-7-bitmap` (`4967e110`), all three in one chain with the second
agent session on the phone, each with 3 visible Chooser runs (5 min
idle, `chooser-visible-N`) next to the suite.

| | PSS after start (MB) | USS after start (MB) | USS after idle (MB) | CPU ms/min "covered" | CPU ms/min visible | Wake-ups/min visible |
|---|---|---|---|---|---|---|
| before | 178 / 218 | 150 / 185 | 156 / 185 | 7635 / 5 | 5570 | 3633 |
| item 5 | 178 / 211 | 150 / 177 | 156 / 181 | 6354 / 4 | 6085 | 3629 |
| item 7 | 170 / 208 | 146 / 178 | 152 / 179 | 6471 / 3 | 6635 | 3626 |

- **Item 5 (frames on GTK's frame clock)**: wake-ups unchanged, CPU
  within the drift of these series (the "covered" one is better, the
  visible one worse, in both item 5 and item 7). Since GTK already
  stops drawing a hidden window and Chooser's frames come from GTK's
  drawing, not from the Choreographer's timer, there is nothing left
  for this change to save. Rejected; branch `claude/perf-vsync-118`
  kept for apps that animate through the Choreographer without
  drawing.
- **Item 7 (one off-screen renderer)**: 4 to 7 MB less USS, mostly in
  the `stacks` category, which swings by that much between series;
  the number of Mesa threads is the same (10 `gdrv`), so the per-draw
  renderers left no contexts behind. No clear gain; branch
  `claude/perf-bitmap-renderer-118` kept, to measure again with an app
  that draws many bitmaps.
- **`lib-mesa`** swings between 22.6 and 28 MB PSS from series to
  series while its USS stays at 9.5 MB: the PSS of shared libraries
  depends on how many other processes map them at that moment. Compare
  USS for library categories.
- **Smoke test**: the same as before in both.

### Item 4: framework on the boot class path (A/B measured, not merged)

Branch `claude/perf-framework-boot-118` (`a276340f`, on top of #130):
`api-impl.jar` is put on the boot class path through the `BOOTCLASSPATH`
environment (ART's ten default jars plus `api-impl.jar`), so the
framework is compiled into the boot image at the first start after an
ATL update (7 s, 25 s CPU); `ATL_FRAMEWORK_ON_CLASSPATH=1` switches it
off. `-Xbootclasspath/a:` does not work: the dex2oat processes ART
starts for the apps' dex files pass only `--boot-image`, read ART's
default boot class path, take the image for stale and delete it, so it
was built again at every start. `main.c` also passes a null class
loader to `Runtime.loadLibrary` when the framework's loader is the
`BootClassLoader`, which finds no libraries.

A/B on one build (`performance/ab-boot`, `scripts/ab-boot.sh`): each
arm with its own `XDG_CACHE_HOME`, one warm-up start, then four Chooser
starts alternating between the arms, 30 s settle, no idle. Totals from
the raw smaps (`scripts/pd.py`), first frame from `start.tsv`:

| Arm | Private dirty (MB) | Private clean (MB) | PSS (MB) | First frame (ms) |
|---|---|---|---|---|
| boot image (branch) | 64.4 / 64.4 / 65.1 / 65.1 | 85.7 / 85.4 / 85.5 / 85.5 | 173.6 / 173.5 / 174.2 / 174.1 | 2203 / 2195 / 2236 / 2195 |
| class path (today) | 67.1 / 71.0 / 70.9 / 70.9 | 75.6 / 75.7 / 75.9 / 75.5 | 166.4 / 170.4 / 170.4 / 170.0 | 2173 / 2361 / 2434 / 2336 |

- **Memory**: about 6 MB less private dirty memory per app (medians
  64.8 against 70.9 MB), the framework's class metadata that is now in
  the image file. 10 MB more private *clean* pages: the framework's
  part of the boot image, which is file-backed and shared by every ATL
  process that runs at the same time. With one process they count as
  private and raise PSS by 3.6 MB; with two apps the second pays
  nothing for them, so the gain per additional app is the 6 MB. What
  each extra app costs is private dirty memory; USS misleads with one
  ATL process, since shared file pages count as USS there.
- **Start**: first frame about 150 ms earlier (medians 2199 against
  2348 ms).
- **Open**: a pair run (`perf-run.sh -2`) to show the clean pages shared,
  the smoke test of all apps on the branch, and the start time of the
  first start after an update (the image build). `boot-api-impl.art` is
  only 244 kB, so few framework classes are in the image itself; an
  image class list or a profile for `GenerateImage` in art_standalone
  could put more there. Side finding: `api-impl.jar` stays on
  `java.class.path` too (`ATLPaths` and `AssetManager` read it from
  there), so ART also tries to compile it as class path dex at the
  first start and fails without harm.

Not merged: the pair run and the smoke test need the phone.

### Item 11: MediaPlayer pipeline on first use, released after playback

Implemented 2026-11-09 in the cloud (no phone), **not yet measured**.
Commit `api-impl: MediaPlayer: open the GStreamer pipeline on first
use, release it after playback` (upstream-ready, touches only
`MediaPlayer.java`, its JNI file and generated header):

- `setDataSource()` and `prepare()` keep only the path. The
  `GtkMediaFile` is created on the first call that needs the stream
  (`start()`, `seekTo()`, `getDuration()`, `prepareAsync()`), so a
  player that an app creates for a sound effect and never plays costs
  no pipeline, no GL context and no threads.
- The muted play-through in the `prepared` callback is gone. Upstream
  removed the same hack from `SoundPool` on 2026-10-25 ("no longer
  needed"; PipeWire restores a stream's muted state at the next start,
  so the hack could mute every stream of the app). It also decoded every
  file in full at start.
- The `prepare()` hack that marked the stream prepared with duration 0
  is gone too: it made GTK's backend skip its own `stream_prepared`
  call, so `getDuration()` stayed 0 for the player's lifetime.
- Five seconds after playback has ended (not while looping), the file
  is closed (`gtk_media_file_clear`), which stops and frees the
  `GstPlay` pipeline with its threads and GL context; `start()` and
  `seekTo()` open the file again, with the known duration. `stop()`
  closes it at once, as Android's `stop()` releases the decoders.
- The completion listener is stored on the stream and replaced instead
  of connecting a new handler per `setDataSource()`.

Checked here: the C file compiles against GTK 4.14 and JNI headers; the
Java changes compile only in ATL's build (the JDK lacks libcore). To
measure on the phone, Chooser `-i 0` against `phosh`: `threads-start.tsv`
should have no `GstPlay`, `gstglcontext`, `typefind*`, `multiqueue*`,
`aqueue:src` or `oggdemux*` threads (21 of Chooser's 92), fewer Mesa
threads (`gldisplay-event`, `gdrv`, `traceq`, `ir3q`, `disk$`: 46 in
Chooser, 17 in HeliBoard), and `stacks` plus the GStreamer, Opus,
Vorbis, Ogg and PulseAudio libraries in `smaps-start.tsv` should drop
(estimated 12 to 15 MB); `start.tsv` gives the start time. Then the
owner test: Chooser's choice sound plays, also twice in a row and again
after more than five seconds; Duolingo's GET STARTED still works
(`MediaPlayer.setDataSource(AssetFileDescriptor)`), and the smoke test.

# Code analysis

Read from the code only, on 2026-11-08, while the baseline suite ran on
the phone (ATL `phosh` 52fe6d6, art_standalone `phosh` 9447fac8,
bionic_translation `phosh` 0b2e6e8). The numbers it starts from are the
calibration run of Chooser: PSS 185 MB, of which Java heap 4 MB, native
anonymous memory 42 MB, boot image relocated privately 32 MB, GTK and
WebKit 29 MB, Mesa with LLVM and the Vulkan driver 23 MB, 105 thread
stacks 16 MB; about 100 MB GPU memory; the main thread at about 16 % of
a core while idle. The other apps: HeliBoard 179 MB, Gadgetbridge
243 MB, DB Navigator 341 MB. HeliBoard, a small app, sits at the same
level as Chooser: about 175 MB is the floor every ATL app pays before
its own code counts. The items below explain that floor.

Each item: the cause with the lines that show it, how Android avoids
it, the expected gain (an estimate from the code, to be replaced by
measurements), the size (S = a day, M = a few days, L = a week or more,
as in the [hardening roadmap](hardening-roadmap.md)), and how to verify
it on the phone with the scripts of the baseline branch
(`tools/perf-run.sh -i 0 -- <ATL arguments>` takes one snapshot after
30 s; compare the categories in `smaps-start.tsv`). Line numbers refer
to the commits above. Nothing here is implemented; the order is the
expected gain for every app, largest first.

## Not the cause

Two hypotheses from above are wrong, one is harmless:

- **App code interpreted or only JIT-compiled.** No: this fork of ART
  compiles every dex file ahead of time at first start. The runtime
  itself runs `dex2oat` for a dex file without an up-to-date oat
  (`art/runtime/oat_file_assistant.cc:207-237`, a fork change; upstream
  Android leaves that to the installer) and passes no compiler filter,
  so dex2oat's default `speed` applies (`art/runtime/compiler_filter.h:45`);
  `quicken` is only added under `-Xint` (`art/runtime/runtime.cc:2592-2596`).
  That is why the first start of an app takes long and why `app.log`
  shows `Compiling dex file:` lines. The JIT still exists for methods
  without compiled code (`art/runtime/runtime_options.def:76`, one
  worker thread `art/runtime/jit/jit.cc:702`, code cache from 64 KB up to
  64 MB `art/runtime/jit/jit_code_cache.h:87-90`), which is the `art-jit`
  category.
- **GC threads.** ART creates no GC thread pool here:
  `kDefaultEnableParallelGC` is false (`art/runtime/gc/heap.h:145`), so
  `ParallelGCThreads` is 0 (`art/runtime/parsed_options.cc:621-622`) and
  `Heap::CreateThreadPool` makes nothing (`art/runtime/gc/heap.cc:1002-1006`).
- **The 10 ms timer at start** (`src/main-executable/main.c:2109`) polls
  only until the window has a width, then removes itself
  (`src/main-executable/main.c:174-182`).


## 1. The boot image is relocated privately in every process

**Cause.** ART maps the boot image (`boot.art`, the pre-initialised
classes and objects of the boot class path) read-write and private at a
random address and then rewrites every pointer in it. Relocation is on
by default (`kDefaultMustRelocate = true`,
`art/runtime/runtime_globals.h:83`, `art/runtime/runtime_options.def:93`);
ATL passes nothing against it (`src/main-executable/main.c:97-156`). The
loader reserves a random address (`ART_BASE_ADDRESS + random delta`,
`art/runtime/gc/space/image_space.cc:1875`), maps the file there with
`PROT_READ | PROT_WRITE, MAP_PRIVATE`
(`art/runtime/gc/space/image_space.cc:936-946`) and, because the address
differs from the one the image was compiled for, patches all objects,
methods and tables in place (`MaybeRelocateSpaces` and
`DoRelocateSpaces`, `art/runtime/gc/space/image_space.cc:1721-1740`,
`1567-1583`). The patched pages become private copies that cannot be
dropped under pressure (only swapped) and are not shared between two
ATL processes.

**Correction (measured 2026-11-08, see [Fixes](#items-1-and-6--xnorelocate-checkjni-opt-in)):**
the analysis first took the 32 MB of `art-image` for relocated pages.
They are not: `art-image` is almost all clean, file-backed `.oat`,
`.vdex` and `.dex` pages (`boot.oat` 9 MB, `boot.vdex` 4 MB,
`api-impl` dex and vdex 7 MB, ...), which count as private only because
a single ATL process maps them; a second ATL process shares them
through the page cache. The `boot*.art` files themselves are 2.5 MB
PSS, of which 2.4 MB private dirty with relocation and 0.5 MB without.

**Android.** The zygote loads and relocates the image once at boot and
every app process is a fork of it, so the relocated pages are shared
copy-on-write by all apps; the few an app writes become private. ATL
has no zygote: every app relocates its own copy.

**Expected gain.** Without relocation the image is mapped at its
compiled address and the patch pass is skipped (`diff` is 0,
`art/runtime/gc/space/image_space.cc:1729-1732`). Untouched pages stay
file-backed, clean and shared through the page cache by every ATL
process of the same user (or the same engine in Flatpak). Estimated
first as 25 to 30 MB per app; measured: about 1.9 MB private dirty
less per app (see the correction above); the start time gain could not
be separated from item 6's. What stays private is what the
runtime writes into the image at run time (class status, dex caches,
intern table), a few MB on Android.

**Size.** S. One runtime option, `-Xnorelocate`, in `create_vm`
(`src/main-executable/main.c:133`), with a fallback if the compiled
address is taken (ART then fails to load the image; the random
`--base` chosen when the image was generated,
`art/runtime/gc/space/image_space.cc:166-168`, lies in the low 2 GB,
which nothing else in an ATL process uses). The trade is address space
randomisation of the image, which Android also gives up per boot. A
second step for later is to generate the image once per installation
instead of once per user (item 9).

**Verify.** `tools/perf-run.sh -i 0 -- -X -Xnorelocate <Chooser arguments>`
against the baseline: `art-image` PSS and USS in `smaps-start.tsv`
should drop by most of the 32 MB, `start.tsv` should show a shorter
time to the first frame, and in a pair run (`-2`) both processes should
share the `boot.art` pages (`shared_kb` of `art-image`). If ART logs
`Failed to map image` the address was taken and the fallback is needed.

## 2. WebKit is linked into every app process

**Cause.** The WebView glue (`src/api-impl-jni/widgets/android_webkit_WebView.c`)
is compiled into `libtranslationlayer.so`, and that library lists
`webkitgtk-6.0` as a dependency (`meson.build:299`). The dynamic linker
therefore loads `libwebkitgtk-6.0.so`, `libjavascriptcoregtk-6.0.so`
and their dependencies (libsoup, GStreamer core and plugin libraries,
libenchant, libhyphen, libwoff2, libmanette, libavif and more) when the
process starts, although nothing calls into WebKit before the first
`webkit_web_view_new()` (`src/api-impl-jni/widgets/android_webkit_WebView.c:124`).
musl binds every symbol at load time, so the cost is paid up front:
the relocation of some hundred thousand entries (start time), their
GOT and `.data.rel.ro` pages (private, written), and the symbol-table
pages read to resolve them. Chooser, HeliBoard and Gadgetbridge never
show a WebView; DB Navigator creates one already at start, Post & DHL
on its onboarding page.

**Android.** The WebView implementation is a separate APK (Chromium)
that `WebViewFactory` loads into the app only when the first `WebView`
is constructed, and its renderer runs in separate sandboxed processes.
Apps without a WebView never map it.

**Expected gain.** The WebKit share of the 29 MB measured as `lib-gtk`
(GTK itself is needed and stays), estimated at 10 to 20 MB per process
from the size of the libraries, plus a noticeable part of the start
time. The estimate has the widest range of all items; the baseline's
`smaps-start.tsv` settles it (sum the mappings named `libwebkitgtk`,
`libjavascriptcoregtk`, `libgst*`, `libsoup`, `libenchant`,
`libhyphen`, `libwoff2`, `libmanette`, `libavif` in the biggest-mappings
table, or in the raw `smaps-start.txt.gz`).

**Result.** Done (fix "Item 2" in Results): 36 to 38 MB less per
process without a WebView, more than estimated, because the WebKit
dependencies in `lib-other` go too.

**Size.** S to M. Move the WebView glue into its own shared library
(`libtranslationlayer_webkit.so`, following the one-job-per-file rule)
that `android_webkit_WebView.c` opens with `dlopen` on the first
WebView, or resolve the dozen WebKit functions it uses through
`g_module_symbol`. The Java side (`src/api-impl/android/webkit/WebView.java`)
does not change. Upstream-ready as one commit.

**Verify.** A build with the lazy library: run Chooser and compare the
`lib-gtk` category and the named WebKit mappings in `smaps-start.tsv`;
they should be absent until DB Navigator opens a ticket. `start.tsv`
gives the start-time difference. Also check with `LD_DEBUG`-free
means that the libraries are not pulled in by another path:
`grep -c webkit /proc/<pid>/maps` must be 0 for Chooser.
## 3. Two GPU stacks are loaded: GL with LLVM, and Vulkan

**Cause.** Two things add up on the phone.

- Alpine builds Mesa's GL drivers as one library that contains
  `llvmpipe` next to `freedreno`, and that library links LLVM 23
  (`main/mesa/APKBUILD`: `_gallium_drivers` with `llvmpipe` at lines
  85 and 95, `-Dgallium-drivers` at 246).[^alpine-mesa] Any process that
  initialises EGL on this phone maps `libLLVM-23.so` and relocates it,
  even though the Adreno driver never calls into LLVM. ATL initialises
  EGL: `libtranslationlayer.so` depends on `gl` and `egl`
  (`meson.build:297`), `libandroid.so` on `vulkan` (`meson.build:173`),
  and SurfaceView and GL apps get a `GdkGLContext`
  (`src/libandroid/egl.c:223`, `500`).
- GTK 4.20 (libadwaita 1.10 on the phone) tries the Vulkan renderer
  before the GL renderer on Wayland (`renderer_possibilities` in
  `gsk/gskrenderer.c`),[^gtk-renderer] so the app's window is drawn by
  the Vulkan driver (`libvulkan_freedreno.so`, Turnip). The GL stack is
  initialised anyway: `gdk_display_init_dmabuf` sets up both Vulkan and
  EGL to learn the dmabuf formats (`gdk/gdkdisplay.c:2027-2043`),[^gtk-dmabuf]
  and the renderer selection itself calls `gdk_display_prepare_gl`
  when it checks the GL fallback.

So one process carries the GL driver with LLVM and the Vulkan driver,
each with its own device state, shader compilers and caches: the 23 MB
measured as `lib-mesa`, plus GPU memory for two devices (item 7).

**Android.** One driver stack per process, chosen by the app: HWUI uses
either GL or Vulkan, never both, and the vendor GL driver has no
software rasteriser compiled in.

**Expected gain.** Dropping one stack: the Vulkan driver is the smaller
part (a few MB private), LLVM the bigger (its relocated data alone is
several MB). Estimated 8 to 15 MB per process. The LLVM part is a
system packaging matter (a Mesa build without `llvmpipe` in the
gallium library, or a split as Fedora and Debian do), which only the
owner or the distribution can change; ATL can only decide which stack
it needs.

**Size.** S for the choice: set `GDK_DISABLE=vulkan` before `gtk_init`
when ATL will need GL anyway (every app may create a SurfaceView), or
`GDK_DISABLE=gl` for apps that are known not to use GL. GTK's own
`GSK_RENDERER=gl` is not enough, because `gdk_display_init_dmabuf`
still initialises Vulkan. A Mesa without `llvmpipe` is a "Needs owner"
item in [apps.md](apps.md) once the measurement confirms LLVM's share.

**Verify.** Three runs of Chooser with `-i 0`: default,
`GDK_DISABLE=vulkan`, `GDK_DISABLE=gl`; compare `lib-mesa` PSS, the
`libLLVM` and `libvulkan_freedreno` rows of the biggest mappings, and
`gpu_total_kb` in `idle.tsv`. `GDK_DEBUG=opengl,vulkan` in `app.log`
shows which stack GTK initialised and why.

**Result (2026-11-08): rejected.** With `GDK_DISABLE=vulkan` GTK
renders with GL: GPU memory rose from 99 to 136 MB (Chooser) and 63 to
148 MB (Gadgetbridge), the first frame came 0.3 to 0.5 s later, and one
run crashed in Mesa's GL driver (`libgallium`) under
`gsk_renderer_render` (run `fix-1-flags`). Reverted on the branch.

## 4. The Android framework (api-impl) is loaded from dex in every process

**Cause.** ATL's re-implementation of the Android framework,
`api-impl.jar` (930 Java files, `src/api-impl/meson.build`), is passed
as `-Djava.class.path` (`src/main-executable/main.c:128-129`), not as
part of the boot class path. The boot image holds only libcore and its
companions (`core-oj`, `core-libart`, `okhttp`, `bouncycastle`,
`apache-xml`, `apachehttp`, `wolfssljni`, `junit`, `hamcrest`; the list
in `flatpak/de.nexumia.atl.BaseApp.yml:205-216` is the same one the
runtime generates from, `art/runtime/gc/space/image_space.cc:107-183`).
So every process loads the framework classes from dex at start: the
class linker parses them, allocates their `Class` objects on the Java
heap and their methods, fields, vtables and dex caches in `LinearAlloc`,
which is anonymous memory from a `MemMapArenaPool`
(`art/runtime/runtime.cc:1436-1437`), counted in `native-anon` because
it has no name and sits above 4 GiB. The app's classes are handled the
same way, which is unavoidable, but the framework's are the same in
every process. Class initialisation (`<clinit>`) of the framework runs
at every start too.

**Android.** The framework jars are on the boot class path and in the
boot image: classes come pre-linked and partly pre-initialised,
file-backed and shared; the zygote additionally preloads some
thousand classes before forking.

**Expected gain.** Part of the 42 MB `native-anon` and part of the
start time. With roughly 1,000 to 1,500 classes and their methods the
metadata is estimated at 3 to 8 MB per process; the heap part of the
`Class` objects and strings is small. Confidence in the number is
low until the SIGQUIT dump (below) gives the class count and the
measurement the delta.

**Size.** M. Add `api-impl.jar` to the boot class path
(`-Xbootclasspath:` with the libcore jars and the image generated
from that list, `art/runtime/gc/space/image_space.cc:146-149`;
`GetBootClassPath()` feeds `GenerateImage`). To check first: the
framework's JNI library must be loadable by the boot class loader
(`java.library.path` is given, `src/main-executable/main.c:123-125`),
`ATLPaths` relies on `api-impl.jar` being the first class path entry
(`src/main-executable/main.c:128`), classes that look up the app's
resources through their own class loader need a look, and the Flatpak
engine's precompiled `api-impl.odex`
(`flatpak/de.nexumia.atl.BaseApp.yml:219-224`) becomes part of the boot
image instead. An image containing api-impl must be regenerated for
every ATL build (it already is for libcore changes).

**Verify.** `kill -QUIT <pid>` writes ART's dump into `app.log`:
`Zygote loaded classes=... post zygote classes=...` and the loaded dex
files (`art/runtime/runtime.cc:1986-1998`, `art/runtime/class_linker.cc:9268-9276`);
the post-zygote count is what each process pays for today. After the
change, compare `native-anon` and `art-heap` in `smaps-start.tsv` and
the start time; the framework's classes should appear under the boot
class loader.

## 5. Frames run on a timer, not on the display

**Cause.** ATL's `Choreographer` is Android's class in its no-vsync
mode: `USE_VSYNC = false` (`src/api-impl/android/view/Choreographer.java:93`),
a fixed 60 Hz frame interval (`147-149`) and a frame delay of 10 ms
(`77-79`). `scheduleFrameLocked` posts the next frame as a Handler
message `sFrameDelay` after the last one (`403-415`). Whenever an app
keeps a frame callback posted (an animation, Compose's frame loop while
it has pending work, a `ValueAnimator`, a progress indicator), the main
thread wakes up to 100 times per second and runs `doFrame`, including
the JNI round trips into GTK, regardless of whether GTK will draw a
frame, whether the window is covered, or whether the screen is off.
GTK's own frame clock behaves correctly: it pauses when the compositor
sends no frame callbacks, so a covered window is not drawn; but ATL's
view invalidation goes through `gtk_widget_queue_draw` and idle sources
(`src/api-impl-jni/widgets/WrapperWidget.c:255-262`), which run even
when nothing is drawn. The main looper itself is event driven and not
at fault (`src/api-impl-jni/android_os_MessageQueue.c:74-90` hands the
timeout to a GLib source). Candidate for the 16 % of a core the main
thread used while Chooser sat idle, and for CPU in the background.

**Android.** The Choreographer wakes only on a vsync event from the
display (`USE_VSYNC = true`), SurfaceFlinger stops sending them to
apps that are not visible, and a cached app is frozen altogether.

**Expected gain.** CPU and battery, no memory: the idle main-thread
time of apps that animate, down to near zero while covered; a visible
idle app runs at most at the display rate. The share of the 16 % that
this explains is unknown until the per-thread numbers of the baseline
(`threads-*.tsv`, main thread delta between start and end) and a Java
stack dump during idle (`kill -QUIT`, look for `doFrame`) show where
the main thread spends its time.

**Result (2026-11-09): rejected.** The 16 % came from Chooser
redrawing itself while a strip of its window stayed visible in the
"covered" runs; fully covered, GTK stops drawing and the main thread
sleeps without this change. With it, wake-ups and CPU stayed the same
(Fixes, "Items 5 and 7").

**Size.** M. Drive `doFrame` from GTK's frame clock
(`gtk_widget_add_tick_callback` on the activity's window, as
`src/api-impl-jni/util.c:215-242` already does for other deferred
work), which gives the display's rate and stops when the surface is
hidden; keep a timer fallback only while no window is mapped. Also
make `Choreographer` report the real refresh rate. Upstream-ready.

**Verify.** `perf-idle.sh` columns `utime_ms` and `vol_cs` per minute
for the visible and the covered run of Chooser, and the main thread's
line in `threads-end.tsv` minus `threads-start.tsv`, before and after.
A covered app should show wake-ups per minute in the low hundreds (GLib
and ART housekeeping) instead of thousands.

## 6. CheckJNI is always on

**Cause.** `create_vm` passes `-Xcheck:jni` unconditionally
(`src/main-executable/main.c:133`). CheckJNI wraps every JNI function in
argument, reference and thread-state checks. ATL is built on JNI: every
view, draw call, text measurement and event crosses it, often several
times per frame, so the overhead lands on the main thread and on the
start time. It also forces ART's slow path for some JNI transitions.

**Android.** CheckJNI is off for release apps; it is enabled only for
debuggable apps or with `debug.checkjni`.

**Expected gain.** CPU only: a measurable part of the main-thread time
and of the start time (CheckJNI roughly doubles the cost of a JNI
call; how much of ATL's time is JNI calls is unknown). No memory.

**Size.** S. Make it opt-in (`ATL_CHECK_JNI=1` or `-X -Xcheck:jni`),
default off. Upstream-ready, but upstream may want it on for
development builds; a meson option covers both.

**Verify.** Chooser with and without, `start.tsv` and the main
thread's CPU delta in `threads-*.tsv`; `-X -Xcheck:jni` cannot be
removed from the command line today, so this needs a build with the
default changed.

**Result (2026-11-08).** Default off, `ATL_CHECK_JNI=1` turns it on:
Chooser's covered idle CPU fell from 8.3 to 6.6 s per minute, the first
frame came about 6 % earlier ([Fixes](#items-1-and-6--xnorelocate-checkjni-opt-in)).

## 7. Bitmaps exist twice, and each draw makes a GPU renderer

**Cause.** An Android `Bitmap` that is drawn into (a `Canvas` on a
bitmap) is rendered in `native_create_texture`
(`src/api-impl-jni/graphics/android_graphics_Bitmap.c:23-66`): on the
main thread ATL opens a **second** `GdkDisplay` (`gdk_display_open(NULL)`,
line 38; a second Wayland connection with its own Vulkan device, EGL
setup and GTK caches), then for **every call** creates a new
`GskRenderer`, realises it, renders, and unrealises it (lines 46-57).
GTK's GPU renderer renders into a GPU image and downloads it into a
`GdkMemoryTexture` (`gsk_gpu_renderer_render_texture`,
`gsk/gpu/gskgpurenderer.c`),[^gtk-gpu-cache] so the bitmap's pixels
live in process memory (`native-anon`); when a view draws that texture,
the window's renderer uploads it again and keeps the GPU copy in its
cache until the texture dies or 15 s pass unused (`CACHE_TIMEOUT`,
`gsk/gpu/gskgpudevice.c:12`). Decoded images (`BitmapFactory`,
`src/api-impl-jni/graphics/android_graphics_BitmapFactory.c:61-63`,
drawables from files, `android_graphics_drawable_Drawable.c:11`) are
memory textures too. For a 720×1440 screen one full-size bitmap is
4 MB in RAM and 4 MB on the GPU, and the renderer created per draw
allocates its command buffers, staging buffers and descriptor pools
each time; the driver's buffer cache keeps what was freed for reuse.
Together a plausible source of a large part of the 100 MB GPU memory
and of a part of `native-anon`, and churn on the main thread. Untested
assumption: Chooser (Compose) draws through bitmaps at all; the
counts below settle it.

**Android.** HWUI keeps bitmaps once (in the Java heap or ashmem) and
uploads them into a texture cache with a fixed budget that is trimmed
on `onTrimMemory`; the renderer and its thread live for the window's
lifetime.

**Expected gain.** GPU memory and process memory in the tens of MB for
apps that draw into bitmaps, less for Compose apps; less main-thread
time per bitmap draw. Uncertain until counted.

**Result (2026-11-09).** One kept renderer (branch
`claude/perf-bitmap-renderer-118`) gave no clear gain for Chooser and
Gadgetbridge (Fixes, "Items 5 and 7"); not merged.

**Size.** M. Keep one off-screen renderer per display (or realise the
window's renderer once for off-screen work) instead of one per call;
drop the second `GdkDisplay` once GTK issue 7848 (the workaround at
lines 35-37) no longer needs it; consider `gdk_dmabuf_texture` or
`GdkGLTexture` for bitmaps that are only drawn, so they exist once on
the GPU. Partly upstream-ready, partly waits on GTK.

**Verify.** Count calls: `GSK_DEBUG=renderer` in `app.log` logs every
renderer realised ("Using renderer"), and `GDK_DEBUG=dmabuf,opengl`
the second display's initialisation. `gpu_total_kb` in `idle.tsv` over
time, and the `graphics` category of `smaps-start.tsv` (CPU mappings
of GPU buffers), before and after. A quick upper bound of what bitmaps
cost: `grep -c 'Using renderer' app.log` against the number of
bitmaps the app creates (ATL could log it under `ATL_DEBUG`).
## 8. 105 threads: mostly the app's and ATL's Java threads

**Cause.** The code accounts for about a dozen threads before the app
runs: ART's main thread, `Signal Catcher`, the four libcore daemons
(`ReferenceQueueDaemon`, `FinalizerDaemon`, `FinalizerWatchdogDaemon`,
`HeapTaskDaemon`, `libcore/libart/src/main/java/java/lang/Daemons.java:43-46`),
one JIT worker (`art/runtime/jit/jit.cc:702`) and a runtime pool of
`min(cores, 4)` workers with 64 KB stacks (`art/runtime/runtime.cc:1006-1015`);
GLib adds `gmain` and `gdbus`, Mesa a few driver queues. Every Java
thread gets its requested size plus 1 MB of stack
(`FixStackSize`, `art/runtime/thread.cc:670-680`), as on Android; only
touched pages count, so the 16 MB for 105 stacks is about 150 KB each.
The rest, some 90 threads, come from the app (Compose and Kotlin
coroutine dispatchers, which open up to 64 I/O threads on demand) and
from ATL's framework classes when the app uses them (`AsyncTask` pool
of `cores + 1`, `src/api-impl/android/os/AsyncTask.java:180-199`;
executors per GATT connection, `src/api-impl/android/bluetooth/BluetoothGatt.java:91-100`).
Interpreted code uses more native stack than compiled code (each Java
frame is a `ShadowFrame` plus the interpreter's own frame), so threads
running the JIT's not-yet-compiled methods touch more pages early on.

**Android.** The same threads exist; a Java thread costs the same
there. Android has fewer only where the zygote preloads work that ATL
does at start, and the kernel's `CONFIG_ANON_VMA_NAME` would at least
let smaps name them.

**Expected gain.** Small. Fewer or thinner threads in ATL's own
classes would save a few MB at most; the app's threads are the app's.
Listed because the count looked alarming, not because it pays.

**Size.** S for anything found in ATL's classes (lazy executors).

**Verify.** `threads-start.tsv` of the baseline names every thread
(`comm`); group them by name (`Thread-N`, `DefaultDispatcher-worker`,
`pool-N-thread-M`, ART names, `gmain`, Mesa names) to see whose they
are, and compare Chooser with HeliBoard.
## 9. The boot image is built per user, in full, at first start

**Cause.** Without a system image next to `libart.so`
(`<libdir>/art/../java/dex/art/oat/<isa>/boot.art`, the default image
location this fork derives, `art/runtime/parsed_options.cc:689-712`),
the runtime generates one into `~/.cache/art/<isa>/`
(`art/libartbase/base/file_utils.cc:245-251`,
`art/runtime/gc/space/image_space.cc:2075-2082`) at the first start of
any ATL app for that user. `GenerateImage` compiles all boot jars
(`art/runtime/gc/space/image_space.cc:146-149`) with dex2oat's default
filter `speed` and no profile, so every method of libcore, Bouncy
Castle, OkHttp, Apache HTTP, JUnit and Hamcrest is compiled and every
class is in the image. The result is a large `boot.oat` and `boot.art`
(the 32 MB of item 1 is mostly the `.art` part), minutes of dex2oat at
the first start (the owner's `user` and `claude` each pay it), and no
sharing between users. The Flatpak engine already does it right: the
image is built once at engine build time with a fixed base
(`flatpak/de.nexumia.atl.BaseApp.yml:192-217`), together with
`api-impl.odex` (`219-224`).

**Android.** The boot image is built with the system image from a
boot-image profile (`speed-profile`): only hot methods compiled, only
needed classes in the image, which keeps it at a fraction of the
size, and it ships read-only in `/system`.

**Expected gain.** Start time of the first run per user (minutes) and
disk; with item 1 in place, a smaller image also means fewer
file-backed pages to fault in. Memory gain beyond item 1 is small.

**Size.** S to M. For the aports package: generate the image at
install time into the system location (`/usr/lib/java/dex/art/oat/arm64/`,
an apk trigger or a post-install script, as the Flatpak manifest does),
so all users share it and no user waits. Optionally a profile or
`--image-classes` for the boot jars (M, needs a representative set of
apps; low priority). Dropping JUnit and Hamcrest from the boot class
path is upstream's call.

**Verify.** `ls -la ~/.cache/art/arm64/` on the phone for sizes;
`start.tsv` of the very first run of a fresh user against a later one;
after the change, `smaps-start.tsv` should list the image under the
system path and `art-image` should be shared in the pair run.
## 10. bionic_translation and the larger apps

**bionic_translation.** Nothing in it adds to the base cost of
Chooser, Gadgetbridge or HeliBoard: they have no Android native
libraries, so the bionic linker only loads its own small libraries.
For apps with native code (DB Navigator: Hermes, Akamai, maps) the
linker maps segments privately from the file (`linker/linker.c:1189-1191`)
and relocates them like any dynamic linker; read-only segments are made
writable during relocation and remembered for re-protection
(`linker/linker.c:1286-1293`), which only costs private pages where
relocations land (the GOT and `.data.rel.ro`), the same as on Android.
The one-page-per-missing-function stubs (`linker/linker.c:1665-1690`)
exist only with `LINKER_DIE_AT_RUNTIME` set (`1850`). The pthread
wrapper passes stack sizes through unchanged
(`pthread_wrapper/libpthread.c:359-369`), so a bionic thread without an
explicit size gets musl's 128 KB default instead of bionic's 1 MB:
less memory, not more (a risk for deep native stacks, not a cost).

**Where the extra memory of the larger apps goes.** The baseline's
`smaps-start.tsv` per app answers this; from the code the candidates
are:

- Gadgetbridge (+58 MB over Chooser): a much larger dex (more classes
  and metadata in `native-anon` and `art-heap`, item 4 does not help
  with the app's own), its database and device support classes, and
  Bluetooth threads (two executors per GATT connection).
- DB Navigator (+156 MB): several APKs with large dex files
  (`app-dex`, and the `speed`-compiled oat of each is mapped as
  `art-image`), React Native with Hermes and Akamai through the bionic
  linker (`lib-other` and their private data), and a WebView once a
  ticket is shown (item 2 does not reduce that case).
- HeliBoard (-6 MB): the smallest app; its number is the floor of
  items 1 to 7.

**Size.** None here; the per-app parts are the apps' own, as on
Android.

## 11. A GStreamer pipeline per MediaPlayer, built and run at start

**Cause.** `MediaPlayer` is implemented on `GtkMediaFile`. `setDataSource`
calls `gtk_media_file_new_for_filename` at once
(`src/api-impl-jni/media/android_media_MediaPlayer.c:14-23`), and GTK's
GStreamer backend builds a `GstPlay` with a `playbin` pipeline for the
file right there, not at the first `start()`. As soon as the stream
reports `prepared`, ATL plays it once muted "to ensure file is fully
loaded" (`on_prepared`, lines 7-12, from the 2025-03-04 commit that
moved MediaPlayer to GtkMediaStream), so every player decodes its whole
file at start. `prepare()` then only flags the stream as prepared with a
duration of 0 (`native_prepare`, lines 41-49). `MediaPlayer.create()`
(`src/api-impl/android/media/MediaPlayer.java:48-60`) is the path apps
take for sound effects: extract the raw resource, `setDataSource`,
`prepare`. Chooser creates three players at start, one per sound.

Each pipeline brings its threads and stays alive after the muted
playback (GTK keeps the pipeline paused, GStreamer keeps a paused
pipeline's streaming threads): in the Chooser snapshot
`threads-start.tsv` lists 3 × `GstPlay`, 3 × `gstglcontext`,
3 × `typefind:sink`, 3 × `typefindelement`, 3 × `aqueue:src` and 6 ×
`multiqueue*:src`, 21 threads for three Ogg/Opus files. The
`gstglcontext` threads show that GTK's video sink opens a GL context
per pipeline although the files have no video; **inference:** the
extra Mesa threads of Chooser come from those contexts, since Chooser
has 46 Mesa threads (`android-:gdrv` 10, `android-:ir3q` 8,
`androi:traceq` 12, `android:disk$` 10, `gldisplay-event` 6) where
HeliBoard, with no media, has 17. Chooser has 92 threads in all,
HeliBoard 35; the three pipelines with their GL contexts account for
about 50 of the difference.

**Android.** `MediaPlayer.create()` also prepares the player
synchronously, but the decoder is opened in the media server and
prerolls one buffer; it does not play the file through, and a
prepared-but-idle player holds no rendering threads in the app.

**Measured.** Libraries of the audio stack mapped only because of the
pipelines (GStreamer core and plugins, orc, opus, vorbis, ogg,
PulseAudio client): 5.8 MB PSS in Chooser against 1.4 MB in HeliBoard
(those come in through WebKit), so about 4.4 MB; the 21 GStreamer
threads and the roughly 29 extra Mesa threads about 150 KB of touched
stack each, 7 to 8 MB; the three GL contexts' driver state is in
`native-anon` and GPU memory and cannot be separated there (the P5
`GDK_DEBUG` run lists the contexts). CPU while idle is small: the
GStreamer threads used 160 ms in the first 30 s and nothing measurable
afterwards. Estimated total 12 to 15 MB of Chooser's 185 MB, plus the
start-time cost of decoding three files muted.

**Expected gain.** That memory and the threads for every app that
creates sound effects at start (a common pattern: games, timers,
keyboards with key sounds), and a shorter start. Nothing for apps
without media.

**Size.** S. Create the `GtkMediaFile` on the first `start()` (or on the
first call that needs the stream: `getDuration`, `seekTo`) and keep only
the path until then; drop the muted play-through in `on_prepared`, or
replace it with `gtk_media_stream_realize` if a display is what the
file needs; after `ended`, set the stream back so GStreamer releases
the pipeline (`gtk_media_stream_pause` keeps it; unrealizing or
re-creating per playback drops the threads). Upstream-ready as one
commit; `MediaPlayer.create()` still returns a usable player, since
`prepare()` already does not wait for GStreamer.

**Done.** Implemented 2026-11-09, see [Item 11](#item-11-mediaplayer-pipeline-on-first-use-released-after-playback)
under Fixes; the GL context per pipeline comes from GTK's
`GtkGstPaintable`, which creates a GDK GL context for the video sink of
every `GstPlay` it renders, audio-only or not.

**Verify.** Chooser `-i 0`: the `GstPlay`, `gstglcontext`, `typefind`,
`multiqueue` and `aqueue` threads must be absent from
`threads-start.tsv` until a sound is played, the Mesa thread count
should fall towards HeliBoard's, and `stacks` and the audio libraries
in `smaps-start.tsv` should drop by the amounts above; `start.tsv`
gives the start-time difference. A sound must still play on the
phone (owner test, Chooser's choice sound).

## Can ATL apps share memory, and what it means for the Flatpak plan

Android shares between apps by **forking**: the zygote has the runtime,
the relocated boot image, the preloaded framework classes and the
graphics libraries in memory, and every app starts as a copy-on-write
child of it. ATL has no zygote, and the per-app Flatpak plan in the
[hardening roadmap](hardening-roadmap.md) rules one out: a fork cannot
cross a sandbox boundary, each `flatpak run` is a fresh `bwrap`
process, and a long-lived zygote process on the host would be exactly
the shared trust domain the plan removes. So **sharing by fork is not
available**; it should not be planned for, in Flatpak or outside it.

What remains is **sharing through the page cache**: two processes that
map the same file read-only, or private but unwritten, use the same
physical pages. That covers, if the items above are fixed:

| Memory | Today | Shareable | Condition |
|---|---|---|---|
| Boot image `boot.art` (32 MB) | private per process (item 1) | yes | `-Xnorelocate`; the same image file for all apps |
| Compiled code `boot.oat`, `api-impl` oat, the app's oat | file-backed, shared | yes | already so |
| Shared libraries (GTK, WebKit, Mesa, ART, ATL) | file-backed, shared with every process that maps the same file | yes | same files: on the host with every GTK app, in Flatpak only with apps on the same runtime and engine |
| Framework class metadata (part of the 42 MB) | private per process (item 4) | yes, if in the boot image | item 4 |
| Java heap, the app's class metadata, JIT code, GTK and Mesa private state, thread stacks, GPU buffers | private | no | per app on Android too |

The private part that no fix removes is the **per-app floor**:
estimated from the code at 100 to 130 MB PSS for a small app after
items 1 to 7, down from 185 MB (the measurements will replace this
estimate). An app that runs all the time costs at least that; two
apps at once cost twice that minus nothing, since what they share is
already counted once in PSS.

For the Flatpak plan this means:

1. **The engine must carry the shared files**, and the apps must use
   them unchanged. The engine already ships a precompiled boot image
   and `api-impl.odex` (`flatpak/de.nexumia.atl.BaseApp.yml:192-224`).
   Generated apps copy the engine's `/app`; OSTree stores identical
   files once, so every app maps the same inode and the pages are
   shared. An extension or a runtime would give the same; a per-app
   copy on plain disk would not. Keep this property when the engine
   shipping is decided (roadmap, open question "extension or own
   runtime").
2. **Relocation must be off** (item 1), otherwise the shared image is
   copied privately in every sandbox and the sharing is lost exactly
   where it matters most.
3. **The app's own dex2oat stays per app** in
   `~/.var/app/<id>/cache/art`; that is correct, it is the app's code.
4. **Library pages are shared only within the runtime.** Flatpak apps on
   `org.gnome.Platform` do not share GTK, Mesa or WebKit pages with the
   host's Phosh and its apps; ATL apps share them with each other and
   with other Flatpaks on the same runtime. The first ATL Flatpak on a
   phone therefore costs the runtime's library pages once more than the
   host build does; the second is cheaper. Items 2 and 3 reduce what is
   mapped at all, which helps in both worlds.
5. **Per-app process overhead of Flatpak itself**: `bwrap` and
   `xdg-dbus-proxy` per app, a few MB each, plus the portal's work. It
   is small next to the floor but belongs in the before-and-after
   measurement of phase 6 (a Chooser Flatpak against the host Chooser,
   same scripts).
6. **Memory pressure handling** is absent in both worlds: Android frees
   cached apps and tells apps to trim; ATL apps keep everything. For
   apps that run all the time, a trim path (drop GTK's texture cache,
   ART `trim`, release bitmaps) on the low-memory signal is a later
   item, after the floor is lower.

The order to fix, by gain per effort: 1 (S, biggest), 6 (S, CPU), 3
(S, choice of stack), 2 (S-M), 11 (S, apps with sounds), 5 (M, CPU and
battery), 4 (M), 7 (M), 9 (S-M, first start), 8 (nothing unless the
thread names say so).
Each fix is measured with the baseline scripts before the next one
starts, as [the order of work](#order-of-work) says.

[^gtk-renderer]: GTK 4.20.0, gsk/gskrenderer.c
[^gtk-dmabuf]: GTK 4.20.0, gdk/gdkdisplay.c
[^gtk-gpu-cache]: GTK 4.20.0, gsk/gpu/gskgpudevice.c and gskgpurenderer.c
[^alpine-mesa]: Alpine aports, main/mesa/APKBUILD

# Evaluation of the measurements (2026-11-09)

Read from the data in [performance/](performance/) (baseline `suite-p1`,
`suite-p2`; the fix series `fix-0` to `fix-7`; the raw smaps of
`ab-boot`), without the phone. It answers step 2 of the plan: where
the memory goes now, what the fixes gave, and what can still be
implemented. Numbers are Chooser / Gadgetbridge unless said otherwise,
medians of the three covered 10-minute runs of `fix-3-phosh`
(`fe78b717`, the code of `phosh` after items 1, 2 and 6; items 4 and
11 are not in it).

## What an ATL app costs today

| | Chooser | Gadgetbridge | HeliBoard (baseline, with WebKit) | DB Navigator (baseline) |
|---|---|---|---|---|
| PSS after 10 min idle (MB) | 183 | 218 | 199 | 311 |
| USS (MB) | 155 | 184 | 174 | 270 |
| GPU memory total / resident (MB, `fdinfo`) | 98 / 74 | 63 / 51 | 62 / 50 | 100 / 76 |
| Threads | 91 | 47 | 37 | 92 |
| CPU covered (ms/min) | 10 (fully covered, `check-cover-fs`) | 5 | 3 (not fully covered) | 33 (not fully covered) |
| First frame (ms) | 2300 | 2300 | 1700 | 5500 |

HeliBoard without WebKit (item 2) should be about 36 MB lower, like
Chooser; it has not been measured since. DB Navigator shows a WebView
at start and keeps WebKit.

**GPU memory is not in PSS.** The GEM buffers a process holds through
its DRM file are counted by `fdinfo` only; `perf-smaps.py`'s `graphics`
category is empty in every run because Turnip maps them only
transiently. So the real footprint of an ATL app is PSS plus GPU
memory: about 280 MB for Chooser and Gadgetbridge alike, 360 MB for
HeliBoard with WebKit, 410 MB for DB Navigator.

**What is private and what is shared.** From the raw smaps of the
Chooser A/B (`ab-boot/cp-1`, today's code path, 30 s after start, PSS
170 MB):

| Kind | MB | What it is | Who shares it |
|---|---|---|---|
| Private dirty | 71 | anonymous memory 40 (`native-anon`), thread stacks 12, Java heap 4.5, the writable data of the libraries (relocated `.data.rel.ro`, `.data`, `.bss`) about 14 | nobody: this is what each process costs for sure |
| Private clean | 76 | boot image, `boot.oat`/`.vdex`, `api-impl` oat, `libart.so` and the other files only ATL processes map | every other ATL app running at the same time (the pair run shows 106 MB shared between Gadgetbridge and Chooser) |
| Shared clean | 105 | GTK, GLib, Mesa, LLVM, ICU data, fonts, the app's APK | already shared with Phosh and its GTK apps on the host; inside Flatpak only with apps on the same runtime |

So of Chooser's 170 MB PSS only 71 MB is unavoidably per process at
start, and PSS overstates the cost of a second app: the pair run gives
the second ATL app 156 MB PSS but 85 MB USS. Over ten minutes the
private part grows (Chooser's `stacks` from 12 to 20 to 33 MB in the
three runs, see finding 3), so the running cost of Chooser is 85 to
100 MB private CPU memory **plus 98 MB GPU memory**; for Gadgetbridge
about 125 MB private (`native-anon` 85, stacks 9, Java heap 15,
library data about 15) **plus 63 MB GPU memory**. GPU memory is
between a third and a half of what each app really costs, and nothing
has looked at it yet.

## What the fixes gave

| Item | Result | State |
|---|---|---|
| 1 `-Xnorelocate` | 1.9 MB private dirty per app; the 32 MB were a misreading of clean file pages | merged |
| 6 CheckJNI off | -21 % CPU while drawing, first frame 4 to 9 % earlier | merged |
| 2 WebKit lazy | -36 to -38 MB PSS and USS per app without WebView | merged |
| 3 GL instead of Vulkan | +37 / +85 MB GPU memory, later first frame, one Mesa crash | rejected |
| 5 frames on GTK's clock | nothing: GTK already stops drawing a hidden window; the background CPU seen before was a cover that left a strip visible | rejected, branch kept |
| 7 one bitmap renderer | 4 to 7 MB USS, within the swing of the series | branch kept |
| 4 framework in the boot image | -6 MB private dirty per app, +10 MB shareable clean pages, first frame -150 ms | A/B done, pair run and smoke test open |
| 11 MediaPlayer pipeline on first use | expected -12 to -15 MB and -50 threads for Chooser, nothing for the other three | merged, not measured |

Chooser went from 222 to 183 MB PSS, Gadgetbridge from 263 to 218;
with items 4 and 11 Chooser should land near 160 and Gadgetbridge near
210. The background CPU problem the baseline seemed to show did not
exist: fully covered, all four apps use 3 to 10 ms CPU and under ten
wake-ups per minute, which is as good as Android's idle. Gadgetbridge
without a watch is at 5 ms/min; with a watch it is not measured
(owner test, [apps.md](apps.md#needs-owner)).

## Findings

1. **GPU memory, 62 to 100 MB per app, is the biggest block nobody has
   examined.** It is the same 62 to 63 MB for HeliBoard and
   Gadgetbridge, which draw little, so most of it is a floor of GTK's
   Vulkan renderer and Turnip on this display (720x1440 at scale 2: one
   window buffer is 4 MB, GTK keeps a swapchain of several, plus its
   texture atlas, glyph cache and per-frame buffers; Turnip adds
   command stream and suballocator pools). Chooser and DB Navigator
   add 36 MB on top (bitmaps, offscreen textures; item 7 did not change
   it). With GL instead (item 3) it was 136 to 148 MB. It also does
   not shrink while the window is covered (98 MB throughout the idle
   runs), although GTK's GPU cache has a 15 s timeout
   ([^gtk-gpu-cache]). Nothing is known about its composition; it is
   the first thing to measure.
2. **`native-anon` is the biggest CPU-memory block and still
   unexplained**: 40 MB Chooser, 36 HeliBoard, 85 Gadgetbridge, 97
   DB Navigator. The raw smaps show it is not a few large regions: in
   Chooser eight mappings over 2 MB hold 13 MB (a 38 MB region after
   `libjavacore.so` with 4.4 MB resident, two 2 MB regions next to
   `api-impl.jar` and the Turnip device, 2.7 MB next to the DRM
   mappings), the other 27 MB sit in about 200 mappings of 0.1 to 2 MB
   that are nearly fully resident: musl's malloc groups, that is
   ordinary heap of ART (class metadata, dex caches, verifier data),
   GTK, Pango, fontconfig, Mesa and the app's native code. It grows
   with the app: Gadgetbridge's extra 45 MB over Chooser are its own
   classes and data. Item 4 shows what the framework's class metadata
   costs (6 MB); the app's own classes are compiled with `speed` but
   their metadata is rebuilt in every process, because ATL's dex2oat
   makes no **app image**: no `…@classes.art` is mapped in any run,
   only `…@classes.dex` (oat) and `.vdex`. On Android an app image
   holds the app's loaded classes pre-linked and file-backed and is
   known to cut start time and class-loading cost. Without the kernel's
   `CONFIG_ANON_VMA_NAME` the rest cannot be named from smaps; it needs
   either the differential measurement or a malloc accounting tool
   (see the table).
3. **Thread stacks**: 12 MB at 30 s in Chooser (103 stack-like
   mappings, 158 kB each), 9 MB in Gadgetbridge and HeliBoard, but 20
   to 33 MB in Chooser after ten minutes of drawing: a thread's touched
   stack pages stay resident once its deepest call has happened, and
   interpreted code before the JIT warms up makes deep frames. Also,
   `perf-smaps.py` counts any anonymous mapping behind a guard page as
   a stack. Small for the always-on apps, unclear for Chooser; not a
   first target.
4. **Shared libraries are not where the memory is.** `lib-mesa` shows
   25 to 29 MB PSS but only 10 MB USS; libLLVM alone is 173 MB mapped,
   22 MB resident, 11 MB PSS, 1.4 MB private. Those pages are shared
   with Phosh. What a process pays privately for libraries is their
   relocated data, about 14 MB in Chooser, of which Turnip's
   `libvulkan_freedreno.so` holds 5.8 MB (a system package matter).
   Loading LLVM and `libgallium` at all (GTK initialises EGL for dmabuf
   formats, item 3) still costs start time, 6 `gldisplay-event` and
   several `gdrv`/`disk$` threads and 2.7 MB private; `GDK_DISABLE=gl`
   (Vulkan only, the opposite of what item 3 tested) is untested.
5. **The ART side is cheap**: Java heap 4.5 to 15 MB (30 for DB
   Navigator), JIT cache under 1 MB, `libart.so` 7 MB resident but
   shared between ATL apps, boot image and oat files clean and shared.
   The ART heap reserves 256 MB (and tables for it) but touches little.
6. **No leak in ten minutes**: PSS changes by -2 to +3 MB per 10-minute
   run apart from the two outliers (Chooser stacks +13 MB in one run,
   Gadgetbridge `native-anon` +9 MB in its first run); the 30-minute
   baseline runs grew 0 to 17 MB. Whether an all-day Gadgetbridge
   stays flat is not known: it needs one long run.
7. **Start time** is 1.7 to 2.5 s to the first frame for the small
   apps and 5.5 s for DB Navigator, with no breakdown yet (ART start,
   framework class loading, GTK and Vulkan init, the app's own
   `onCreate`). Item 4 takes 150 ms off; an app image would take more;
   the rest is unmeasured.
8. **Item 8 (Mesa threads) is not worth it**: Gadgetbridge's 9 `disk$`
   (shader cache), 4 `traceq`, 2 `ir3q` and 2 `gdrv` threads cost about
   2 MB; disabling the shader cache would cost compile time at every
   start. Chooser's 46 Mesa threads come with the GL contexts of its
   audio pipelines (item 11) and should go with them.

## What can be implemented

By expected gain for an app that runs all day, with how sure the gain
is and the size of the work:

| # | What | Expected gain | Confidence | Size | Next step |
|---|---|---|---|---|---|
| A | **GPU memory**: find what the 62 MB floor and Chooser's extra 36 MB are, then fix in ATL's use of GTK (renderer per draw, bitmaps as textures, offscreen nodes) or GTK/Turnip settings | 20 to 50 MB per app (real memory, not PSS) | low until measured; the block is certain, its composition unknown | measurement S, fix S to M | on the phone: Chooser and Gadgetbridge with `TU_DEBUG=bos` (Turnip prints its buffers by name and size when the device is destroyed) and `GSK_DEBUG=cache`; a 20-line GTK4 Vulkan program as the floor of GTK alone; then decide |
| B | **Item 4**: framework in the boot image | 6 MB private per app, 150 ms start | high (A/B done) | S | pair run and smoke test on the phone, PR from `claude/perf-framework-boot-118` |
| C | **Item 11**: measure what is merged | 12 to 15 MB and 50 threads for apps with sounds (Chooser) | medium | S | Chooser `-i 0` against `fe78b717`, smoke test, sound test |
| D | **App image for the app's own classes**: let the dex2oat ATL runs at first start write `--app-image-file` next to the oat (`…@classes.art`; the runtime loads `<oat>.art` on its own through `OatFileAssistant::OpenImageSpace`); a change in art_standalone's `OatFileAssistant::GenerateOatFileNoChecks` or in the compiler options ATL passes | part of the app's class metadata in `native-anon` becomes file-backed and start gets faster; for Gadgetbridge perhaps 10 to 25 MB of its 85 | low: not measured, and whether `speed` app images keep all classes must be checked | M | `kill -QUIT` on Gadgetbridge for the class count; try `-Xcompiler-option --app-image-file=…` by hand on one app; measure `native-anon` and first frame |
| E | **Split `native-anon`** so items can be chosen with numbers | none directly; decides D and what follows | - | S for the differential, M for the tool | differential: `perf-smaps.py` on a plain GTK4 Vulkan program and on ART running a hello world (`art_standalone`'s launcher) on the phone; the remainder is ATL and the app. Tool: an `LD_PRELOAD` malloc accounting shim in `tools/` (musl hides its allocator, so the shim brings its own and attributes by the caller's library through `dladdr`), dump on `SIGUSR2` |
| F | **Vulkan only** (`GDK_DISABLE=gl`): do not load LLVM and `libgallium` in a process that renders with Vulkan | 2.7 MB private, 16 MB PSS share, 6 to 15 threads, start time | medium; risk: dmabuf import for video and WebKit falls back | S | A/B like `ab-flags.sh`: Chooser and DB Navigator (WebView) with and without, check video in DB Navigator's pages |
| G | **Long run**: Gadgetbridge for 12 hours covered, with a watch if the owner connects one | knowledge: leak or not; the owner's real complaint was an app running all day | - | S | `systemd-run --user`, `perf-run.sh -c -i 43200` |
| H | **Start-time breakdown** | knowledge for the next items | - | S | timestamps from ART (`-verbose:startup`), ATL's log and `perf-start.py` in one timeline |
| I | **Stacks**: see whether Chooser's growth is the app's threads or ATL's; trim is not available (ART has none, Android has none) | 0 to 20 MB for Chooser, little for the others | low | S to look, M to change | compare `threads-start` with `threads-end` stack mappings per thread in one run |

Not worth doing now: item 8 (2 MB, costs start time), item 7 (no
clear gain; keep the branch for an app that draws many bitmaps), a
smaller ART heap reservation (it touches little), anything on the
shared libraries' text (shared already).

**Order**: A's measurement first, because it is the largest block and
cheap to look at; B and C are ready and go to the phone in the same
session; then E's differential, which decides between D and A's fix;
F alongside as an A/B. G runs overnight whenever the phone is free.

**Reference for the stretch goal** (better than Android): no Android
number for Gadgetbridge on this phone exists. If Waydroid is still
installed, `dumpsys meminfo nodomain.freeyourgadget.gadgetbridge` in
it gives PSS and the Dalvik/native/graphics split of the same app on
the same hardware; without it, the comparison stays an estimate
([apps.md](apps.md#needs-owner)).

# Handoff for the analysis

State of 2026-11-09, where the measured data is and what is open:
[performance/handoff.md](performance/handoff.md).

<!-- HANDOFF -->
