---
type: Playbook
title: Development environment
description: The owner's Phosh phone as build and test device - separate user, build into ~/.local, headless test display, required hosts.
tags: [development, phone, testing, network]
generated: { by: claude-code/2.1.308, at: 2026-10-28T09:20:00Z }
---

# Why the phone

ATL runs the app's ARM64 native libraries directly, so tests need an
aarch64 machine. The phone is real hardware with the real Phosh; the
cloud dev container is x86_64 and could only emulate aarch64 with
qemu-user, 5-20 times slower and without a GPU.

# Rules on the phone

The phone is the owner's daily driver.

- The agent runs as the user `claude`, which is in neither `wheel` nor
  `sudo`. The owner's home directory is `chmod 700`.
- The owner installs system packages; the agent asks with the exact
  command.
- ATL and its dependencies are built and installed into `~/.local` of
  the `claude` user.
- Apps are tested in a headless Wayland compositor started by the
  `claude` user, not in the owner's Phosh session.
- Long builds keep all cores busy: announce them so the phone is on its
  charger. Automatic suspend must be off while the agent works.

# Running the agent

```sh
sudo su - claude              # doas on postmarketOS
tmux new -s claude
cd ~/work/android_translation_layer && claude remote-control
```

Close the terminal window; the session keeps running and shows up in
the Claude Code app. Back in with `tmux attach -t claude`.

Checkouts in `~/work`: this fork and, next to it, `phosh-toolkit`
(`AGENTS.md` imports `../phosh-toolkit/AGENTS.md`):

```sh
git clone https://github.com/nexumia-de/android_translation_layer
git clone https://github.com/nexumia-de/phosh-toolkit
```

# The phone

Recorded by the first session on the phone, 2026-10-28:

| Property | Value |
|---|---|
| Device | Fairphone (Gen. 6), devicetree `fairphone,fp6` |
| SoC, GPU | Qualcomm `qcom,milos`, 8 cores, aarch64; Adreno 810 with Mesa 26.2.5 (freedreno) |
| Memory | 7.1 GiB RAM, 10.7 GiB swap; about 1-2 GiB free next to the owner's session |
| Distribution | Nura edge (`ID=nura`, `ID_LIKE=alpine`), based on postmarketOS edge |
| Kernel | `7.2.0-postmarketos-qcom-milos` |
| Shell | phosh `99990.58.0-r0` (0.58.0), phoc 0.58.0, wlroots 0.20.2, libadwaita 1.10.0 |
| apk repositories | postmarketOS main and systemd, Alpine edge main, community and testing, a Fairphone 6 repository (`forgejo.catcrafts.net`) |

The `claude` user has uid 1000 and only its own group. It is not in
`video` or `render`, but `/dev/dri/renderD128` is mode `0666`, so the
GPU is usable for rendering. `XDG_RUNTIME_DIR=/run/user/1000` exists.
The shell is busybox `ash`: scripts stay POSIX `sh`, and busybox tools
lack some GNU options (`readlink -e`, `ps -u`).

Alpine testing packages ATL and its dependencies
(`android-translation-layer`, `art_standalone`, `bionic_translation`,
`libopensles-standalone`). They are not used: ATL and its dependencies
are built from source into `~/.local`, so fixes can be tested.

# System packages

Installed by the owner on 2026-10-28 (`openjdk17-jdk` was added later,
see below); `tools/phone-setup.sh check`
lists any that are missing:

```sh
apk add build-base meson samurai bash zip python3 java-common java-cacerts openjdk8-jdk openjdk17-jdk maven \
  elfutils-dev libbsd-dev libucontext-dev mesa-dev \
  bsd-compat-headers expat-dev icu-dev libcap-dev libpng-dev libselinux-dev lz4-dev openssl-dev \
  valgrind-dev wolfssl-jni-dev xz-dev zlib-dev vixl-dev \
  libsndfile-dev sdl2-compat-dev \
  alsa-lib-dev ffmpeg-dev fontconfig-dev glib-dev gtk4.0-dev gtk4-layer-shell-dev libdrm-dev \
  libgudev-dev libportal-dev libsecret-dev openxr-dev vulkan-loader-dev wayland-protocols webkit2gtk-6.0-dev \
  cage grim wlr-randr
```

The list follows the `makedepends` of Alpine's APKBUILDs for the four
packages, with four differences:

- **No `libunwind-dev`.** It conflicts with `llvm-libunwind`, which
  `libc++` needs, and `libc++` is needed by `imsd` (VoLTE calls) and
  `fingerprintd`. Only `bionic_translation` needs it (`pkg-config
  libunwind >= 1.8.0`); art_standalone builds its own libunwind fork.
  The runtime package `libunwind` is installed, so
  `tools/phone-setup.sh libunwind` downloads `libunwind-dev` of the same
  version as the `claude` user (`apk fetch`), unpacks it into
  `~/.local/opt/libunwind-dev`, points its `.pc` files there and its
  `.so` links at `/usr/lib`. It leaves out the package's `unwind.h`:
  with `-I` it would shadow GCC's own `unwind.h`, which wins over
  `/usr/include` in a system-wide install. Its `-lucontext` needs
  `libucontext-dev`.
- **`sdl2-compat-dev` instead of `sdl2-dev`.** The phone has
  `sdl2-compat` installed, and `sdl2-dev` would pull the conflicting
  `sdl2`.
- **`maven` in addition.** ATL's `tools/jni_upcall_gen` (newer than
  Alpine's ATL package) needs the Java libraries asm, asm-tree and
  java-getopt. Alpine has no `xmvn5-resolve`, so ATL is configured with
  `-Ddownload_tool_deps=true` and `mvn` downloads them from Maven
  Central.

- **`openjdk17-jdk` in addition.** `tools/jni_upcall_gen` uses
  `Path.of` (Java 11) and `Files.mismatch` (Java 12) and does not
  compile with JDK 8. Upstream's nightly packages
  (`android_translation_layer/aports`, branch `atl-nightlies-atl`)
  build ATL with `openjdk17-jdk`; the Java sources still target 1.8
  (`-source 1.8 -target 1.8`). art_standalone is built with JDK 8 like
  Alpine's package. `tools/phone-setup.sh` sets `JAVA_HOME` and `PATH`
  per step, so the system's default JVM does not matter: installing
  `openjdk17-jdk` moved `/usr/lib/jvm/default-jvm` from JDK 8 to
  `java-17-openjdk`.

# Building into ~/.local

```sh
tools/phone-setup.sh            # check, libunwind, bionic, art, opensles, atl
tools/phone-setup.sh atl        # only rebuild ATL from this checkout
```

The script clones the dependencies into `~/work/deps` at pinned commits
(variables at its top) and installs everything into `~/.local`
(`PREFIX`). It runs `make` and `meson compile` with 4 jobs (`JOBS`):
with 8 jobs the build would compete with the owner's session for the
little free memory.

| Step | What | Time on the phone |
|---|---|---|
| `bionic` | `meson setup --prefix ~/.local --libdir lib`, compile, install | 6 s |
| `art` | `make ____PREFIX=~/.local ____LIBDIR=lib`, then `make ... install` | 18 min |
| `opensles` | meson as for `bionic` | 19 s |
| `atl` | meson as for `bionic`, with `-Ddownload_tool_deps=true` | 1 min (rebuild after a Java change: 47 s) |

Details that are not obvious:

- art_standalone's AOSP makefiles have no pkg-config support; they link
  `-ldl_bio` and rely on the compiler's default search paths. The script
  exports `LIBRARY_PATH` and `CPATH` for `~/.local`.
- bionic_translation reads its configuration from
  `$XDG_DATA_DIRS/bionic_translation/cfg.d`, so `~/.local/share` must be
  in `XDG_DATA_DIRS` at runtime.

# Running ATL from ~/.local

```sh
eval "$(tools/phone-setup.sh env)"
android-translation-layer path/to/app.apk [-l activity/Class]
```

`env` sets `PATH`, `LD_LIBRARY_PATH` (for `libdl_bio` and the other
bionic_translation libraries in `~/.local/lib`), `XDG_DATA_DIRS` and the
build variables. Without `-l`, ATL starts the APK's main activity.

- The launcher has a `RUNPATH` to `~/.local/lib/art` only. Without
  `LD_LIBRARY_PATH` it loads the bionic_translation libraries of the
  system package (next section), not those from `~/.local`; check with
  `ldd ~/.local/bin/android-translation-layer`.
- App data goes to `~/.local/share/android_translation_layer/<apk
  name>_/`, the dex2oat cache to `~/.cache/art/`.
- ATL opens the phone's vibrator (`/dev/input/event4`); as `claude` this
  fails with "Permission denied", which is harmless.

## Alpine's ATL packages on the phone

The owner installed Alpine's `android-translation-layer` on 2026-10-23
(in `/etc/apk/world`), which pulls `art_standalone`,
`bionic_translation` and `libopensles-standalone` into `/usr`. They do
not get in the way: with `tools/phone-setup.sh env`, `PATH` and
`LD_LIBRARY_PATH` find the `~/.local` build first. bionic_translation
reads `bionic_translation/cfg.d` from every `XDG_DATA_DIRS` entry, so it
also sees the system package's identical `bionic_translation.cfg`.

# Headless test display

Apps run in an own [cage](https://github.com/cage-kiosk/cage) instance
with the wlroots headless backend, never in the owner's Phosh session:

```sh
eval "$(tools/phone-setup.sh env)"
tools/headless-shot.sh -o shot.png -l app.log -- android-translation-layer app.apk
```

`tools/headless-shot.sh` (options in its header):

1. creates a temporary `XDG_RUNTIME_DIR`, so the Wayland socket and
   the session bus cannot collide with the owner's session;
2. starts `WLR_BACKENDS=headless cage` with a placeholder client that
   reports the socket name and keeps cage running;
3. sets the output to 720×1440 at scale 2 with `wlr-randr` (360×720
   logical, the Phosh target size); cage shows the app maximized like
   Phosh;
4. starts the app with `dbus-run-session`, waits (`-d`, default 15 s;
   GTK's first start on the phone took more than 5 s, and a screenshot
   taken earlier is black), and takes the screenshot with `grim`
   (wlr-screencopy);
5. stops the placeholder client (cage exits with it) and the app
   (`dbus-run-session` then ends the bus), and unmounts the FUSE
   mounts gvfs and the document portal leave in the runtime directory.

cage renders on the GPU through the render node (wlroots GLES2
renderer, Adreno 810); `WLR_RENDERER=pixman` switches to software
rendering. Verified with GNOME Calculator: it filled the 360×720
screen.

# GitHub access

`gh` is logged in on the phone as the owner's account `suitably` with a
fine-grained personal access token, and `~/.gitconfig` uses
`gh auth git-credential` as credential helper. Commits are authored as
`Claude (phone) <noreply@anthropic.com>`. The token has admin rights on
this repository and can see all of the owner's repositories
(`nexumia-de/*` and `suitably/*`), so it is broader than a deploy key
for this repository only. There is no deploy key.

# Required hosts

Tested on the phone on 2026-10-28:

| Host | Used for | State |
|---|---|---|
| `gitlab.com` | Upstream ATL, its dependencies, the upstream issue tracker API | reachable |
| `repo.maven.apache.org` | Java libraries for `tools/jni_upcall_gen` | reachable |
| `f-droid.org` | Test apps and their `.asc` signatures | reachable |
| `keys.openpgp.org` | F-Droid's signing key, to verify APKs | reachable |
| `github.com`, `api.github.com`, `raw.githubusercontent.com` | This fork, `gh`, Alpine's aports mirror | reachable |
| `dl-cdn.alpinelinux.org` | Alpine packages (`apk fetch`) | reachable |
| `gitlab.alpinelinux.org` | Alpine's aports (APKBUILDs) | connection timed out; the GitHub mirror `alpinelinux/aports` works |

Tested from the cloud dev container on 2026-10-28:

| Host | Used for | State |
|---|---|---|
| `gitlab.com` | Upstream ATL and its dependencies | reachable |
| `dl-cdn.alpinelinux.org` | Alpine packages (ATL is packaged in Alpine) | blocked by the proxy (`403`) |
| `mirror.postmarketos.org` | postmarketOS packages | connection failed |

The phone itself has unrestricted network access.
