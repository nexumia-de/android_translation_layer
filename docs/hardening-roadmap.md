---
type: Concept
title: Hardening roadmap
description: Threat model, current state and phased plan to run every Android app as its own Flatpak generated on the phone at install time - ATL engine for Flatpak, APK-to-Flatpak installer on the host, brokers, permission mapping, portals and APK signature checks.
tags: [security, sandbox, permissions, flatpak, portal, dbus, installer, roadmap]
status: draft
generated: { by: claude-code/2.1.308, at: 2026-10-29T17:40:00Z }
sources:
  - id: android-permissions
    resource: https://developer.android.com/guide/topics/permissions/overview
    title: Android developers, Permissions on Android (install-time, runtime and special permissions)
  - id: flatpak-under-the-hood
    resource: https://docs.flatpak.org/en/latest/under-the-hood.html
    title: Flatpak documentation, Under the hood (bubblewrap, seccomp, D-Bus proxy)
  - id: flatpak-perms
    resource: https://docs.flatpak.org/en/latest/sandbox-permissions.html
    title: Flatpak documentation, Sandbox permissions
  - id: flatpak-commands
    resource: https://docs.flatpak.org/en/latest/flatpak-command-reference.html
    title: Flatpak command reference (build-init, build-finish, build-export, override, spawn --sandbox)
  - id: flatpak-extensions
    resource: https://docs.flatpak.org/en/latest/extension.html
    title: Flatpak documentation, Extensions
  - id: flatpak-conventions
    resource: https://docs.flatpak.org/en/latest/conventions.html
    title: Flatpak documentation, Requirements and conventions (application ids, exported files)
  - id: libflatpak
    resource: https://docs.flatpak.org/en/latest/libflatpak-api-reference.html
    title: libflatpak API reference
  - id: portals
    resource: https://docs.flatpak.org/en/latest/portals.html
    title: Flatpak documentation, Portals
  - id: portal-secret
    resource: https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Secret.html
    title: xdg-desktop-portal, Secret portal
  - id: portal-camera
    resource: https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Camera.html
    title: xdg-desktop-portal, Camera portal
  - id: flatseal
    resource: https://github.com/flathub/com.github.tchx84.Flatseal
    title: Flatseal, graphical editor for Flatpak permission overrides
  - id: apk-signing
    resource: https://source.android.com/docs/security/features/apksigning
    title: Android Open Source Project, Application signing (APK signature schemes v1-v4)
---

# Goal

Every Android app runs as **its own Flatpak**, generated on the phone when
the app is installed. Each app gets its own sandbox, its own data
directory, its own identity towards portals and its own permissions,
which the user can see and change in Flatseal, GNOME Settings or a
long-press **Permissions** page. Enforcement is Flatpak's (bubblewrap,
filtered D-Bus proxy, seccomp, portals),[^flatpak-under-the-hood] not
ATL's Java code. The decision and the options considered are in
[decisions/app-sandbox.md](decisions/app-sandbox.md).

# Status

The agent updates this table after every phase (state, pull request,
date), so a new session knows where to continue.

| Phase | State | Pull requests | Updated |
|---|---|---|---|
| 0 Facts, sign-off, prototype | facts done; sandbox works with upstream's engine, app needs the fork's engine (phase 1) | this branch | 2026-10-29 |
| T Escape test | `flatpak/escape-test.sh`: 18 checks, all blocked for a generated app; APK-level checks open | this branch | 2026-10-29 |
| 1 ATL engine as Flatpak | manifest written; build needs the system package `flatpak-builder` | this branch | 2026-10-29 |
| 2 Installer and broker on the host | not started | - | - |
| 3 ATL inside a per-app Flatpak | not started | - | - |
| 4 Permissions | not started | - | - |
| 5 Install integrity | not started | - | - |
| 6 Migration and default | not started | - | - |

# Threat model

| Asset | Example |
|---|---|
| The owner's files | `~/Documents`, `~/.ssh`, photos, other apps' data |
| Secrets | GNOME keyring (Secret Service): Wi-Fi and account passwords |
| Session integrity | autostart files, `~/.local/share/applications`, `~/.profile`, D-Bus services that start programs |
| Privacy | location, microphone, camera, sensors, network identity |
| Other Android apps | their data and keystore entries |

Adversaries, most likely first:

1. **Tracking and analytics SDKs in normal apps**: they read what they
   may, phone home, fingerprint the device.
2. **An app exploited through content** (WebView, media parsers, native
   libraries).
3. **A malicious APK** from a store or opened by hand.

Out of scope: kernel exploits, root, physical access, Google Play
Services, bugs in Flatpak itself. ATL (ART, `bionic_translation`, the API
implementation) runs **inside** each app's sandbox, so ATL bugs do not
widen access. The trusted part on the host is small: the installer and
broker (phase 2).

# Current state (2026-10-29)

ATL runs every app as the owner's user with full access:

| Area | Today | File |
|---|---|---|
| `checkPermission` | fixed list; location and microphone only with `ATL_UGLY_ENABLE_LOCATION` / `ATL_UGLY_ENABLE_MICROPHONE` | `src/api-impl/android/content/pm/PackageManager.java` |
| `requestPermissions` | logs "not handled", never calls `onRequestPermissionsResult` | `src/api-impl/android/app/Activity.java` |
| Files | app data in `~/.local/share/android_translation_layer/<apk>_/`; `/storage/emulated/0/` rewritten there by a libc path override (native code can ignore it) | `src/main-executable/libc_bio_path_overrides.c` |
| Launchers, D-Bus services, installs | written by the app's own process (`~/.local/share/applications`, `dbus-1/services`, `_installed_apks_`), `update-desktop-database` via `system()` | `src/main-executable/main.c` |
| Audio | ALSA `default`, playback and capture | `src/api-impl-jni/audio/` |
| Vibration | writes feedbackd's vibra `/dev/input/event*` | `src/api-impl-jni/android_os_Vibrator.c` |
| Sensors | reads `/sys/bus/iio/devices` | `src/api-impl-jni/sensors/` |
| Keystore | Secret Service via libsecret | `src/api-impl-jni/security/` |
| Flatpak | upstream can run ATL itself as **one** Flatpak (`FLATPAK_ID` checks, dynamic launcher portal); all apps then share one sandbox | `src/main-executable/main.c` |
| APK signatures | not verified | `PackageInstaller.java` |

Any app can read `~/.ssh`, query every keyring secret, write autostart
files that run outside ATL, and record audio.

# Target architecture

```
 flatpak.nexumia.de (CI, aarch64)
   └─ ATL engine: ART, bionic_translation, libopensles, ATL
      built against org.gnome.Platform, as extension or own runtime

 phone, host side (aports package, unsandboxed, small, trusted)
   └─ ATL installer and broker  (D-Bus name for stores and apps)
        - install page, signature checks, permission mapping
        - APK ──► build dir ──► local OSTree repo ──► flatpak install --user
        - uninstall, update, list installed apps, relay intents,
          per-app settings page (flatpak overrides)

 per Android app: Flatpak  de.nexumia.atl.<package>
   └─ ATL engine + APK, own sandbox, data in ~/.var/app/<id>/
      portals: location, camera, file chooser, documents, open URI,
               notifications, secret
      talks only to: portals, the broker, feedbackd, granted services
```

Principles:

- **Reuse Flatpak, don't build a sandbox.** The sandbox, D-Bus filter,
  seccomp filter, permission overrides, portal identity and data
  directories are Flatpak's.[^flatpak-under-the-hood][^flatpak-perms]
- **One Flatpak per app**, so permissions, portal decisions and data are
  per app. Generating a Flatpak compiles nothing: it copies the APK and
  writes metadata (`build-init`, `build-finish`, `build-export`, or the
  same through libflatpak).[^flatpak-commands][^libflatpak]
- **App ids with a prefix**: `de.nexumia.atl.<package>`. Android package
  names collide with real Flatpaks (Android Firefox is
  `org.mozilla.firefox`, like the Flathub app).[^flatpak-conventions]
- **Broker, don't widen.** Everything that touches the host (install,
  uninstall, other apps, settings) goes through the broker on the host,
  which identifies the caller by its Flatpak app id the way portals do,
  and asks the user where Android would. Stores never get
  `org.freedesktop.Flatpak` or `flatpak-spawn --host`: either would be a
  full escape.
- **Ask at the moment of use**, like Android:[^android-permissions]
  portal-backed permissions through the portal at runtime; static ones
  (network, audio socket) as Flatpak permissions, changed in the settings
  and applied at the next start.
- **Least privilege in `finish-args`**: Wayland only, `--device=dri`, no
  `--filesystem`, no `--device=all`, no X11, no host D-Bus names except
  the ones listed below.

# Roadmap

Sizes: S = a day, M = a few days, L = a week or more of agent work plus
phone tests. Every phase ends with a phone test by the owner and an entry
in [log.md](log.md) and the status table above.

## Phase 0 - Facts, sign-off and prototype (M)

Go or no-go for the whole plan.

- Owner: confirm the threat model; accept or change the
  [decision record](decisions/app-sandbox.md).
- Phone facts, read-only as `user`: `flatpak --version`,
  `flatpak remotes --user`, `flatpak list --runtime` (is
  `org.gnome.Platform` there, which branch), `xdg-desktop-portal` and
  backend versions (`apk info -v | grep portal`), whether Phosh's portal
  backend shows location and camera dialogs.
- **Prototype by hand**: one ATL engine build inside a Flatpak, one app
  (Chooser, then a WebView app) as its own Flatpak installed with
  `--user` from a local repo. Measure: starts, graphics, touch, audio,
  WebView (WebKit spawns its web processes through the Flatpak portal),
  launcher in Phosh's app grid (`X-Purism-FormFactor`), start time
  compared to today, disk use.
- Choose how the engine is shipped (recorded in the decision record):
  - **extension** `de.nexumia.atl.Engine` that each generated app
    declares and mounts (apps may declare extension points outside their
    own id, as with `org.freedesktop.Platform.ffmpeg-full`);[^flatpak-extensions]
  - **own runtime** derived from `org.gnome.Platform`, heavier to build
    and to host.

Done when: the prototype app works on the phone in its own sandbox, the
numbers are in [apps.md](apps.md), and the decision record is accepted.

Results (2026-10-29, details in [dev-environment.md](dev-environment.md#app-flatpaks)):

- Kernel and Flatpak facts: user namespaces and unprivileged `bwrap`
  work, Flatpak 1.18.4, GNOME 50 runtime installed, portals 1.22.1. No
  Landlock, which this plan no longer needs.
- Upstream already uses this model: Flathub has
  `io.gitlab.android_translation_layer.BaseApp` (ATL as a base for
  per-app Flatpaks) and NewPipe built on it. The difference here is
  generating the app on the phone instead of in CI.
- Prototype: Chooser generated by hand from upstream's BaseApp (no
  compiling, 83.6 MB installed, mostly the shared engine, which OSTree
  stores once), installed with `--user`, started with `flatpak run`:
  sandbox, D-Bus proxy, portals, data in `~/.var/app/<id>/` and dex2oat
  into the app's own cache all work; the app reaches `onResume` and then
  crashes in `SoundPool.load` (`AssetManager.extractFromAPK` with a null
  `ZipEntry`), which upstream's older ATL lacks fixes for. Next: the same
  with the fork's engine.
- Engine shipping: generated apps copy the base app's `/app`, like
  upstream's per-app Flatpaks; an extension or own runtime is not needed
  for now. An engine update regenerates the apps (no compiling).

## Phase T - Escape test APK (S, then alongside every phase)

A small APK in the fork that tries each escape and prints the results:
read `~/.ssh` and another app's data, write `~/.config/autostart` and
`~/.local/share/applications`, call `org.freedesktop.secrets` and
`org.freedesktop.Flatpak`, open a socket while network is off, record
audio and get a location without the grant, start another app without
the broker. Run it headless after every phase and before every aports
pin. It is the objective "done" check for phases 3 and 4.

## Phase 1 - ATL engine as Flatpak (L)

- flatpak-builder manifest in the fork for `art_standalone`,
  `bionic_translation`, `libopensles-standalone` and ATL against a pinned
  `org.gnome.Platform` branch (WebKitGTK 6 and libadwaita come from the
  runtime).
- Built for aarch64 in CI with the phosh-toolkit Flatpak workflow,
  published in the owner's repository (`flatpak.nexumia.de`).
- Engine version and runtime branch go into each generated app's
  metadata; a runtime bump regenerates the apps (metadata only).

Done when: the engine installs from the owner's repository and the
prototype app from phase 0 runs on it.

## Phase 2 - Installer and broker on the host (L)

The only trusted component; ATL's host package (aports) shrinks to it.

- **Install / update**: the existing `--install-ask` page, then: read the
  manifest, map permissions (phase 4 table), write the build directory
  (APK, split APKs, icons, desktop file with `X-Purism-FormFactor`, MIME
  types and URL schemes from intent filters, D-Bus service), export into
  a local repo under `~/.local/share/android_translation_layer/repo`,
  install or update with libflatpak into the user installation. No root.
- **Uninstall**: `--delete-data` after the existing confirmation page.
- **D-Bus API for sandboxed callers**: install a staged APK (stores; the
  user grants "install apps" per store on first use), uninstall, list
  installed packages (only for callers with `QUERY_ALL_PACKAGES`, like
  Android 11+ package visibility), start an activity of another app
  (intent relay with chooser), open the caller's own settings page. The
  caller's app id comes from its Flatpak identity, never from a
  parameter.
- **Settings page** (`--app-settings <package>`): switches for the
  mapped permissions, written as Flatpak overrides (the same files
  Flatseal edits),[^flatseal] storage and "clear data", uninstall.

Done when: Aurora and F-Droid, each as their own Flatpak, install,
update and uninstall apps only through the broker, and phase T shows no
host write from any app.

## Phase 3 - ATL inside a per-app Flatpak (M-L)

Changes in ATL for running sandboxed:

- GApplication id and D-Bus name = `FLATPAK_ID`; Flatpak lets an app own
  its id and names below it.
- Data directory in the app's `~/.var/app/<id>/`; `/storage/emulated/0`
  inside it.
- No launcher, D-Bus service or `update-desktop-database` writes from
  the app; `PackageInstaller`, `PackageManager` listing and
  `startActivity` to other packages go to the broker.
- `ACTION_VIEW` of URLs and files: OpenURI portal.[^portals]
- Vibration: feedbackd over D-Bus (`--talk-name=org.sigxcpu.Feedback`).
- Sensors: iio-sensor-proxy on the system bus, only when granted.
- Keystore: libsecret uses the Secret portal inside Flatpak (one secret
  per app, no access to other keyring items).[^portal-secret]
- WebView: WebKitGTK's own sandbox through the Flatpak portal; test on
  the phone.

Done when: all apps in [apps.md](apps.md) work as Flatpaks at least as
well as before, and phase T passes.

## Phase 4 - Permissions (M)

Mapping, applied by the installer and the settings page:

| Android | Flatpak | When it changes |
|---|---|---|
| `INTERNET` (normal on Android, a switch here) | `--share=network` | next start |
| Location | location portal | at once (portal) |
| Camera | Camera portal (PipeWire remote limited to cameras)[^portal-camera] | at once |
| Microphone | audio socket (`--socket=pulseaudio`, also playback); without the grant playback must work another way, see open questions | next start |
| Notifications (API 33+) | notification portal | at once |
| Storage, "all files" | none; file chooser and documents portals (Storage Access Framework) | - |
| Sensors | system bus name of iio-sensor-proxy | next start |
| Install apps | broker grant per store | at once |
| Contacts, calendar | not implemented by ATL; denied | - |

In ATL:

- `checkPermission` answers from the effective Flatpak permissions
  (`/.flatpak-info`) and the app's recorded portal answers.
- `requestPermissions` asks the portal for portal-backed permissions
  (one dialog, the portal's); for static ones it shows "needs permission
  - open settings", which calls the broker; then
  `onRequestPermissionsResult`.
- The install page lists the mapped permissions.
- Launcher action **Permissions**: exported desktop actions run inside
  the app's sandbox, so the action asks the broker over D-Bus to open the
  settings page for the calling app.

Done when: denying and granting each row works as in the table, and the
app sees the result as on Android.

## Phase 5 - Install integrity (M)

In the broker: verify APK signatures (v1 to v3, v4 optional), refuse an
update signed by a different certificate, confirm downgrades, remember
the installing store per app and accept updates only from it or from the
user, show signer and permission changes on the update
page.[^apk-signing]

## Phase 6 - Migration and default (S-M)

- Move apps installed the old way into their Flatpaks once, with their
  data, and remove the old launchers and D-Bus services.
- Make the Flatpak path the only install path; the old unsandboxed run
  stays only as a developer option.

# Order

```
0 ──► 1 ──► 2 ──► 3 ──► 4 ──► 6
            └──► 5 (after 2)
T from phase 0 on, run after each phase
```

# What this plan does not give

- Per-host network rules; "only this time" for static permissions.
- A microphone separate from playback as long as both use the audio
  socket.
- Signed app repositories: the local repo is trusted because it is
  local and written only by the broker.

# Open questions

- Engine as extension or own runtime (phase 0).
- Whether Phosh's portal backend implements the location and camera
  dialogs; otherwise ATL needs its own dialog before the portal call.
- Audio playback without the microphone: PipeWire's handling of
  sandboxed clients, or a playback-only path.
- Upstream: ATL's maintainers plan their own sandboxing (TODO in
  `PackageManager.checkPermission`) and already have the one-Flatpak
  mode. Per [the upstream rules](fork-workflow.md), ask in one issue
  before phase 3; nothing is sent without the owner.

[^android-permissions]: Android developers, Permissions on Android
[^flatpak-under-the-hood]: Flatpak documentation, Under the hood
[^flatpak-perms]: Flatpak documentation, Sandbox permissions
[^flatpak-commands]: Flatpak command reference
[^flatpak-extensions]: Flatpak documentation, Extensions
[^flatpak-conventions]: Flatpak documentation, Requirements and conventions
[^libflatpak]: libflatpak API reference
[^portals]: Flatpak documentation, Portals
[^portal-secret]: xdg-desktop-portal, Secret portal
[^portal-camera]: xdg-desktop-portal, Camera portal
[^flatseal]: Flatseal
[^apk-signing]: AOSP, Application signing
