---
type: Plan
title: Base cost of ATL apps (memory first)
description: Goal, measures and order of work for lowering what every ATL app costs while it runs, memory first; nothing measured yet, the causes listed are hypotheses.
tags: [performance, memory, battery, background, gadgetbridge]
generated: { by: claude-code/2.1.319, at: 2026-11-08T12:00:00Z }
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
