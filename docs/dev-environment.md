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
| Sandboxing (2026-10-29) | unprivileged user namespaces work (`unshare -U`, `bwrap` 0.13.1 without setuid); LSMs `capability,bpf` only, no Landlock, AppArmor or SELinux; seccomp available |
| Flatpak (2026-10-29) | Flatpak 1.18.4, system remote `flathub` with `org.gnome.Platform` 49 and 50; xdg-desktop-portal 1.22.1 with the phosh, gtk and wlr backends; PipeWire 1.6.9, WirePlumber 0.5.18, xdg-dbus-proxy 0.1.9, feedbackd 0.8.10, iio-sensor-proxy 3.9 |

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
  libadwaita-dev libgudev-dev libportal-dev libsecret-dev openxr-dev vulkan-loader-dev wayland-protocols webkit2gtk-6.0-dev \
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
tools/phone-setup.sh            # check, libunwind, bionic, art, opensles, atl, tools
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
| `tools` | `wayland-scanner` and `cc` for `tools/wl-pointer` | 1 s |

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

- The launcher's `RUNPATH` covers only `~/.local/lib/art` and ATL's
  `natives` directory, not `~/.local/lib`. Without `LD_LIBRARY_PATH` it
  fails with `Error loading shared library libdl_bio.so.0`; check with
  `ldd ~/.local/bin/android-translation-layer`.
- App data goes to `~/.local/share/android_translation_layer/<apk
  name>_/`, the dex2oat cache to `~/.cache/art/`.
- ATL opens the phone's vibrator (`/dev/input/event4`); as `claude` this
  fails with "Permission denied", which is harmles## Alpine's ATL packages

The owner had installed Alpine's `android-translation-layer` on
2026-10-23, which pulled `art_standalone`, `bionic_translation` and
`libopensles-standalone` into `/usr`. They hid the missing
`LD_LIBRARY_PATH` above (the launcher found `/usr/lib/libdl_bio.so.0`),
and bionic_translation reads `bionic_translation/cfg.d` from every
`XDG_DATA_DIRS` entry, so both copies were in play. The owner removed
them on 2026-10-28 (`apk del android-translation-layer`); the Blokish
smoke test gave the same screenshot afterwards. Do not install them
again next to the `~/.local` build.

`.

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
5. before that runs the `-x` steps in order, with helpers `tap X Y`,
   `swipe X1 Y1 X2 Y2 [MS]` and `shot FILE` (see below), waiting `-w`
   seconds after each;
6. stops the placeholder client (cage exits with it) and the app
   (`dbus-run-session` then ends the bus), and unmounts the FUSE
   mounts gvfs and the document portal leave in the runtime directory.

## Input

cage supports the wlroots virtual pointer and keyboard protocols.
`tools/wl-pointer` (built by `tools/phone-setup.sh tools` into
`~/.local/bin`) clicks and drags through one virtual pointer, in
logical pixels with absolute positions:

```sh
tools/headless-shot.sh -d 45 -w 3 -x 'tap 320 695' -x 'shot page2.png' -- android-translation-layer app.apk
# inside a step, for a long press with a screenshot while holding:
#   -x 'wl-pointer -s 360x720 move 180 380 down wait 9000 up & sleep 4; shot hold.png; wait'
```

ATL turns the pointer into single-finger touch events, so taps, long
presses and swipes work. There is no virtual touch protocol, so
gestures with several fingers cannot be tested headless.

`wlrctl` (installed on 2026-10-28) does not work for clicks: every call
creates its own virtual pointer and destroys it on exit, so the seat
loses and regains its pointer capability. GTK then binds a new
`wl_pointer` that never got an enter event and drops the click; in GNOME
Calculator, `wlrctl pointer move` plus `wlrctl pointer click` typed
nothing, `wl-pointer tap` typed the digits. `wl-pointer` keeps one
device for all its commands and waits 300 ms after creating it until
clients have bound it.

`wlrctl keyboard type` reaches GTK dialogs (gnome-keyring's prompt got
the text), but on 2026-10-28 text typed into DB Navigator's station
field did not arrive; keyboard input into ATL apps is unverified
headless. The first tap after a new window often only focuses it; tap
twice when a tap seems lost.

## Keyring

Apps using the Android keystore (Tink, androidx.security) make ATL
store keys through the Secret Service. In the test display's own
session bus gnome-keyring then asks for a password for a new "Default
keyring", and that prompt did not take typed text. Create an
unencrypted default keyring in the test's `XDG_DATA_HOME` first, then no
prompt appears. D-Bus starts gnome-keyring with the session's environment,
so set `XDG_DATA_HOME` for the whole session (`env XDG_DATA_HOME=...
dbus-run-session -- phoc ...`), not only for the app:

```sh
mkdir -p "$XDG_DATA_HOME/keyrings" && cd "$XDG_DATA_HOME/keyrings"
printf 'Default_keyring' > default
printf '[keyring]\ndisplay-name=Default keyring\nctime=0\nmtime=0\nlock-on-idle=false\nlock-after=false\n' > Default_keyring.keyring
chmod 600 default Default_keyring.keyring
```

Runs need the environment from `tools/phone-setup.sh env`
(`LD_LIBRARY_PATH`): the phone has Alpine's `bionic_translation` in
`/usr/lib` again (a dependency of `android-translation-layer@nexumia`),
and without it ATL loads that copy instead of the `~/.local` build.

cage renders on the GPU through the render node (wlroots GLES2
renderer, Adreno 810); `WLR_RENDERER=pixman` switches to software
rendering. Verified with GNOME Calculator: it filled the 360×720
screen.

## Headless phoc

cage shows windows as they are, but the phone runs phoc, which in its
phone mode maximizes every toplevel and does not draw what is below a
maximized window. Bugs with dialogs or other extra windows (e.g. a
transparent window showing the accent colour) only show under phoc,
which also runs headless (`/usr/sbin/phoc`, 2026-10-29):

```sh
cat > phoc.ini <<EOF
[output:HEADLESS-1]
mode=720x1440
scale=2
EOF
R=$(mktemp -d)
env XDG_RUNTIME_DIR=$R WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 WLR_RENDERER=pixman \
  dbus-run-session -- phoc -C phoc.ini -E ./steps.sh
```

WebKit needs a short `XDG_RUNTIME_DIR` (its bus proxy socket must fit
into a Unix socket path, or the app aborts): use `mktemp -d -p
/run/user/1000`, not the scratchpad. With `WLR_RENDERER=pixman` a
WebView stays dark unless the app runs with
`WEBKIT_DISABLE_COMPOSITING_MODE=1 WEBKIT_DISABLE_DMABUF_RENDERER=1`.

`steps.sh` starts the app in the background, then uses `grim FILE` and
`wl-pointer -s 360x720 tap X Y` like `headless-shot.sh`'s steps. The
window has a title bar of about 46 logical px. For the owner's look set
`gsettings set org.gnome.desktop.interface accent-color green` and
`color-scheme prefer-dark` for `claude` (settings reach the app through
the portal).

## Which app methods ran

When an app hangs with all threads idle, ART's method tracing shows how far
it got. With streaming output every method's name is written into the
trace the first time it runs, so `strings` is enough to read it:

```sh
android-translation-layer -X -Xmethod-trace -X -Xmethod-trace-stream \
  -X -Xmethod-trace-file:$PWD/trace.bin app.apk
strings -n 6 trace.bin | grep -P '\tSomeClass\t'
```

The app runs much slower and the file grows by about 10 MB a second.
Obfuscated class names come from disassembling the APK's `classes*.dex`
with art_standalone's `dexdump2` (`make dexdump2` in art_standalone; it
needs `#include <algorithm>` in `art/dexdump/dexdump.cc` and
`LD_LIBRARY_PATH=out/host/linux-x86/lib64`). `Thread.getAllStackTraces()`
from a temporary hook shows where threads wait; SIGQUIT gives no dump and
there is no JDWP agent.

# App Flatpaks

For the [hardening roadmap](hardening-roadmap.md) apps run as their own
Flatpaks in the `claude` user's own installation (`flatpak --user`, no
root):

- `flathub` is added as a user remote. Installed for `claude`: the GNOME
  50 SDK and `org.freedesktop.Sdk.Extension.openjdk17//25.08` to build
  the engine, and upstream's `io.gitlab.android_translation_layer.BaseApp`
  pinned to commit `86e04253` (2026-09-13, the last one on GNOME 50;
  newer ones need GNOME 51, a 416 MB download).
- `flatpak/de.nexumia.atl.BaseApp.yml` builds the engine from this
  checkout and the dependency forks with `flatpak-builder` (system
  package `flatpak-builder`). Keep the state and build directories on
  disk: `/tmp` is a 3.6 GiB tmpfs in RAM, and art_standalone filled it
  (`No space left on device` while linking dex2oat):

  ```sh
  C=~/.cache/atl-flatpak
  flatpak-builder --user --jobs=4 --disable-rofiles-fuse --force-clean \
    --state-dir=$C/state --repo=~/.local/share/android_translation_layer/flatpak-repo \
    $C/build flatpak/de.nexumia.atl.BaseApp.yml
  ```
- The engine CI publishes it to the owner's repository. To use that
  build instead of a local one (the local one is in the remote
  `atl-local` on the same branch `master`):

  ```sh
  flatpak remote-add --user --if-not-exists nexumia https://flatpak.nexumia.de/nexumia.flatpakrepo
  flatpak uninstall --user -y de.nexumia.atl.BaseApp
  flatpak install --user -y nexumia de.nexumia.atl.BaseApp
  ```

  Generated apps copy the engine, so after an engine update run
  `make-app.sh` again for each app.
- `flatpak/make-app.sh -p <package> -n <name> <apk>` generates
  `de.nexumia.atl.<package>` from an APK, exports it into
  `~/.local/share/android_translation_layer/flatpak-repo` (user remote
  `atl-local`, unsigned) and installs or updates it. `-b` picks another
  base app, `-f` adds permissions.
- `dbus-run-session -- flatpak/escape-test.sh <app id>` tries each escape
  from inside the app's sandbox and fails if one works. Its controls (the
  portal reachable, the app's own data writable) stop a broken test from
  looking green. Inside a Flatpak `$HOME` is private, so writes into it
  are checked from outside.
- Headless: `tools/headless-shot.sh -- flatpak run --user <app id>`;
  `flatpak run` starts the portals in the test's own session bus.

# ATL for the owner

The owner uses ATL in Phosh from the package `android-translation-layer`
of [nexumia-de/aports](https://github.com/nexumia-de/aports), which pins
a commit of `phosh` and publishes a signed apk repository at
`https://apk.nexumia.de/edge` (tag `@nexumia`); how it works is in that
repository's `docs/repository.md`. After a fix is merged into `phosh`,
its daily update check (or `scripts/update.sh android-translation-layer`
there) moves the pin. Alpine's own `android-translation-layer` lacks the
fork's fixes. The owner runs root commands themselves with `sudo`.
# Tests with the owner on the real screen

The headless display has one pointer and no hardware: several fingers at
once, the vibrator and the owner's Phosh session are out of its reach,
and as `claude` ATL cannot open the vibrator (`/dev/input/event4`) nor
reach the owner's Wayland socket (`/run/user/10000`). For those tests
the owner starts the app as their user `user` in a terminal, with the
log in `/tmp` where `claude` can read it:

```sh
android-translation-layer /home/claude/work/apks/<app>.apk 2>&1 | tee /tmp/<app>.log
```

This uses the installed package (`/usr`). To try a fix before it is in
the apk repository, the owner runs the `~/.local` build of `claude`
instead:

```sh
env PATH=/home/claude/.local/bin:$PATH LD_LIBRARY_PATH=/home/claude/.local/lib \
  XDG_DATA_DIRS=/home/claude/.local/share:/usr/local/share:/usr/share \
  android-translation-layer /home/claude/work/apks/<app>.apk 2>&1 | tee /tmp/<app>.log
```

For that, `~/.local` and `~/.local/share` of `claude` are `o+x` only
(traverse, not list) and `bin`, `lib`, `share/atl` and
`share/bionic_translation` are world-readable; app data and keyrings
stay private. The agent says what to do in the app and reads the log
when the owner reports back. `apks/` is world-readable.

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
