---
type: Plan
title: Base cost of ATL apps (memory first)
description: Goal, measures and order of work for lowering what every ATL app costs while it runs, memory first; code analysis of the base cost (boot image relocation, WebKit always loaded, two GPU stacks, classes outside the boot image, timer-driven frames, CheckJNI, bitmaps, threads) with what Android does instead, and what apps can share in per-app Flatpaks.
tags: [performance, memory, battery, background, gadgetbridge, art, boot-image, webkit, mesa, flatpak]
generated: { by: claude-code/2.1.319, at: 2026-11-08T15:00:00Z }
sources:
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
goal: run better than the same app on Android.

Most of the cost is expected in ATL itself (each app starts its own ART
runtime), so a gain helps every app. The [hardening roadmap](hardening-roadmap.md)
moves every app into its own Flatpak; whether apps can share memory
belongs in that design before the migration (phase 6), not after.

# What is known

Work item: issue #118. Nothing measured. [android-keyboard.md](android-keyboard.md) estimates
150 to 300 MB resident per ART process; Stevia, the native keyboard,
uses 78 MB RSS.

# Measures

Per app, headless on the phone as `claude`, results with date and ATL
commit in this file:

- Memory after start and after 30 minutes idle in the background:
  PSS and USS (`/proc/<pid>/smaps_rollup`), split into ART heap, boot
  image, native libraries, GPU/graphics buffers.
- Wake-ups and CPU time per minute while idle (`/proc/<pid>/stat`,
  scheduler statistics).
- Start time to the first drawn frame.
- Later, with the owner: battery per hour with the screen off.

Reference points: Amazfish (native app for the same watch) for
Gadgetbridge, and what Android reports for the same app where the owner
has a device.

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

