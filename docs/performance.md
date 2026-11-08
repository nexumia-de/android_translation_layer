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
`1567-1583`). Almost every page of the image holds a pointer, so almost
every page becomes a private copy: the 32 MB measured as `art-image`
private. They are anonymous memory to the kernel, cannot be dropped
under pressure (only swapped), and are not shared between two ATL
processes even though both read the same file.

**Android.** The zygote loads and relocates the image once at boot and
every app process is a fork of it, so the relocated pages are shared
copy-on-write by all apps; the few an app writes become private. ATL
has no zygote: every app relocates its own copy.

**Expected gain.** Without relocation the image is mapped at its
compiled address and the patch pass is skipped (`diff` is 0,
`art/runtime/gc/space/image_space.cc:1729-1732`). Untouched pages stay
file-backed, clean and shared through the page cache by every ATL
process of the same user (or the same engine in Flatpak): around 25 to
30 of the 32 MB per app, plus the time the patch pass takes at start
(tens to a few hundred ms on the phone). What stays private is what the
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
show a WebView; DB Navigator does, for tickets.

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

