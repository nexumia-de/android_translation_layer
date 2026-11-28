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
| `ATL_FAKE_SYSROOT=<dir>` | Serve an Android-like `/system` and `/apex` tree from `<dir>`: any `/system/*` or `/apex/*` path the app opens is redirected to `<dir>/<path>` when that file exists, so a RASP that re-opens the modules it listed in the fake maps can read them. Redirect only; `<dir>/<path>` may be a symlink to the real mapped object. |

Set them per app through the Flatpak run, e.g.

```
flatpak run --env=ATL_TRACE_OPEN=1 --env=ATL_FAKE_MAPS=$HOME/fake-maps.txt <app-id>
```

`ATL_TRACE_OPEN` / `ATL_FAKE_MAPS` are implemented in `bionic_translation`
(`libc/libc-open-overrides.c`, `bionic_open`/`bionic_fopen`), next to the
existing `/proc/self/status` `TracerPid` strip. `ATL_FAKE_SYSROOT` is
implemented one layer up, in `apply_path_overrides`
(`src/main-executable/libc_bio_path_overrides.c`), next to the existing
`/system/fonts` and `/system/etc/security/cacerts` rewrites, so it covers
`open`, `fopen` and the `stat` family.

# What the SecureGo plus RASP checks — and why it still does not pass (2026-11-28)

Black-box probe on the owner's device, app copy and fork
(`~/scratch-securego/`, PRs #222 `ATL_FAKE_MAPS`, #223 `ATL_FAKE_SYSROOT`):

1. The protection library `libfiduciagad_securego_vr.so` (7.4 MB, stripped,
   string-encrypted — a commercial anti-tamper) reads `/proc/self/maps`
   with `fopen` right after loading; without `ATL_FAKE_MAPS` it exits
   (status 101) at that read, before the keystore.
2. With `ATL_FAKE_MAPS` it gets **past** the maps read and then `fopen`s
   exactly one module from the map, `/system/lib64/libc.so`, to inspect it.
   Without the file it exits 101.
3. `ATL_FAKE_SYSROOT` makes that `fopen` succeed. This **advances** the RASP
   through two more stages (shown with a full `strace -f`, 2026-11-28):
   - With ATL's own `libc_bio.so.0` behind `libc.so`, the RASP reads the ELF
     header, section headers, build-id / `.gnu_debuglink`, and the **dynsym**,
     then `exit_group(104)` — a *content* check on libc (104 ≠ the maps
     stage's 101, so the sysroot did move it forward). ATL's bionic shim is
     not a real Android libc, so it is rejected.
   - With a **real Android 33 bionic `libc.so`** behind `libc.so` (dumped
     from the Waydroid image's `/system/lib64/bootstrap/libc.so`), the
     content check **passes** (no 104); the RASP continues, then computes a
     function pointer from the libc data and `blr`s to it — faulting
     `SIGSEGV`/`SEGV_ACCERR` at a non-executable address (`pc==x8`), the
     call made **from inside `libfiduciagad…so`** (the link register lands in
     the RASP's own mapping).
4. Conclusion — a precise, *structural* wall, not a bigger-fake-tree problem.
   The RASP uses libc as its root of trust: it reads libc from disk **and**
   executes addresses it computes from that disk image against the **live**
   libc. It only succeeds when disk libc == in-memory libc == real Android
   bionic. Under ATL the in-memory libc is the translation shim
   (`libc_bio`/`libpthread_bio` bridging Android libc calls to host glibc),
   so the two libc cases are mutually exclusive: a memory-consistent
   `libc_bio` fails the content check (104); a content-valid real Android
   libc makes the computed call land in the wrong place and crash. No
   on-disk `/system` fakery can satisfy both — the app needs a real Android
   libc actually mapped and executing (Waydroid / GrapheneOS), which is
   exactly what a translation layer is not. (There is **no** hidden
   `ptrace`/env-probe syscall; the strace shows none — the earlier guess of
   a non-file probe was wrong.)

`ATL_FAKE_SYSROOT` remains a correct, reusable disguise primitive (an app
that only needs to *read* an Android `/system`/`/apex` view benefits), and
it got SecureGo plus two stages further; it cannot get past a RASP that
validates the live libc itself.

# Building an Android-like /proc/self/maps

Capture the real ATL map of a running app and rewrite it: drop lines under
`/usr`, `/app` and host toolkits (GTK, GIO, Wayland, Mesa, Vulkan loader,
fontconfig, pango, cairo, codecs), rewrite the app's own library to
`/data/app/<pkg>-.../lib/arm64/`, ART to `/apex/com.android.art/lib64/`,
boot images to `/system/framework/arm64/`, and the bionic libs to
`/system/lib64/`. Keep anonymous regions, `[stack]`, `[heap]`, `[vdso]`.
A worked example lives in the owner's scratch (`~/scratch-securego/`).
