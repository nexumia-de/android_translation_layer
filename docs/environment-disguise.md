---
type: Reference
title: Environment disguise (ATL_FAKE_MAPS, ATL_TRACE_OPEN)
description: Owner-configurable knobs that give an app an Android-like view of its process so client-side anti-tamper / RASP checks pass; off by default, set per app; what they do, how they stage, and how to build an Android-like /proc/self/maps.
tags: [atl, anti-tamper, rasp, disguise, securego, revolut, env]
status: draft
generated: { by: claude-code, at: 2026-11-28T17:40:00Z }
---

# Environment disguise

Some apps ship an anti-tamper / RASP library that inspects the process
before the app runs. Under ATL the process is a glibc/GTK Linux program
with host libraries and no `/system` or `/apex` tree, which such a library
can notice. These knobs give the app an Android-like view so its
client-side checks pass. They are **owner-configurable, off by default, and
set per app**; with them unset the engine behaves exactly as before. Policy:
[decision](decisions/gms-local-only.md).

| Variable | Effect |
|---|---|
| `ATL_TRACE_OPEN=1` | Log every path the app opens to stderr (`ATL_OPEN <path>` / `ATL_FOPEN <path>`). Use it to see what a protection library reads. |
| `ATL_FAKE_MAPS=<file>` | Serve `<file>` in place of this process's `/proc/self/maps` (covers `open` and `fopen`, and `/proc/<pid>/maps` for self). |

Set them per app through the Flatpak run, e.g.

```
flatpak run --env=ATL_TRACE_OPEN=1 --env=ATL_FAKE_MAPS=$HOME/fake-maps.txt <app-id>
```

Implemented in `bionic_translation` (`libc/libc-open-overrides.c`,
`bionic_open`/`bionic_fopen`), next to the existing `/proc/self/status`
`TracerPid` strip.

# How a maps check stages (VR SecureGo plus, 2026-11-28)

1. The protection library `libfiduciagad_securego_vr.so` reads
   `/proc/self/maps` with `fopen` right after loading; without a disguise it
   exits (status 101) at that read, before the keystore.
2. With `ATL_FAKE_MAPS` pointing at an Android-like map (host glibc/GTK
   entries removed, paths rewritten to `/data/app`, `/apex/com.android.art`,
   `/system/lib64`), it gets **past** that read and then `fopen`s a library
   named in the map (e.g. `/system/lib64/libc.so`) to inspect it.
3. So the next step is a library view it can actually open and that passes
   inspection — the files named in the fake map have to exist and look
   right. It also reads many `ro.build.*` properties (a further check).

# Building an Android-like /proc/self/maps

Capture the real ATL map of a running app and rewrite it: drop lines under
`/usr`, `/app` and host toolkits (GTK, GIO, Wayland, Mesa, Vulkan loader,
fontconfig, pango, cairo, codecs), rewrite the app's own library to
`/data/app/<pkg>-.../lib/arm64/`, ART to `/apex/com.android.art/lib64/`,
boot images to `/system/framework/arm64/`, and the bionic libs to
`/system/lib64/`. Keep anonymous regions, `[stack]`, `[heap]`, `[vdso]`.
A worked example lives in the owner's scratch (`~/scratch-securego/`).
