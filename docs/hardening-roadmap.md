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
| 0 Facts, sign-off, prototype | prototype works: Chooser runs in its own Flatpak on the fork's engine ([screenshot](screenshots/chooser-flatpak-2026-10-29.png)); tested by the owner on the real screen; open: owner sign-off, a WebView app, start time | this branch | 2026-10-29 |
| T Escape test | `flatpak/escape-test.sh`: 18 checks, all blocked for a generated app; APK-level checks open | this branch | 2026-10-29 |
| 1 ATL engine as Flatpak | done: CI build on `ubuntu-24.04-arm` (`.github/workflows/engine-flatpak.yml`), published through the release `engine` and phosh-apps to `flatpak.nexumia.de`; signed install from there verified on the phone, Chooser runs on it | #27, #29 | 2026-10-30 |
| 2 Installer and broker on the host | `--install-flatpak` (#30); installer service `de.nexumia.atl.Installer` (install, uninstall, list, launch; only for APKs requesting `REQUEST_INSTALL_PACKAGES`), `--install-ask --install-flatpak` and `--uninstall-flatpak` pages ([installing apps](installing-apps.md#app-stores-in-their-own-flatpak)); opening an APK file uses the Flatpak path (2026-11-04); updates without asking for the installer of record, allowed per store in App info ([installing apps](installing-apps.md#updates-without-asking), 2026-11-04); open: SDK 31 for stores' installer API, update checks in the background | this branch | 2026-11-04 |
| 3 ATL inside a per-app Flatpak | vibration through feedbackd (#31); install, uninstall, installed apps and starting other apps go to the installer service; links out (OpenURI) and in (D-Bus activation) checked headless; open: network state from the portal (F-Droid's "No internet"), a real login, sensors, WebView | this branch | 2026-10-30 |
| 4 Permissions | decision record and mapping (below); next: runtime prompts, App info, Android apps page | this branch | 2026-11-03 |
| 5 Install integrity | signers verified over the whole APK (v3 with key rotation, v2, v1 of every entry; AOSP's verifier); unsigned, changed and foreign-signed APKs and downgrades refused; open: installer of record, signer changes on the update page, v4 | this branch | 2026-10-30 |
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
  `ZipEntry`), which upstream's older ATL lacks fixes for.
- With the fork's engine (phase 1) the same generated Chooser runs: the
  onboarding renders and reacts to taps
  ([page 1](screenshots/chooser-flatpak-2026-10-29.png),
  [page 2](screenshots/chooser-flatpak-page2-2026-10-29.png)), and the
  escape test blocks all 18 attempts. Installed size 84.1 MB, of which
  the engine is 82.6 MB and stored once for all apps.
- Owner's test on the real screen (2026-10-29, as `user`, bundle from
  `flatpak build-bundle`): Chooser runs, choosing with several fingers
  works, no permission dialog (it requests none that needs one), the
  escape test passes for the owner's user too. Gaps, all expected: no
  vibration (phase 3: feedbackd), no sound (no audio socket; playback
  must be allowed by default, see phase 4), no icon and a test name
  (the dev script; the installer takes both from the APK, phase 2).
- Building the engine on the phone needed three fixes to the manifest:
  state and build directories off `/tmp` (a RAM tmpfs), libportal (not in
  the GNOME 50 SDK), and the Java libraries of `tools/jni_upcall_gen`
  with a build-time `xmvn5-resolve` stand-in (upstream's Flathub base app
  predates that tool).
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

CI (2026-10-29): `.github/workflows/engine-flatpak.yml` builds the
engine and its source bundle (`de.nexumia.atl.BaseApp.Sources`) for
aarch64 on every push and pull request to `phosh` that changes more
than docs. It does not use phosh-toolkit's reusable workflow, which
builds x86_64 too, runs 30 minutes at most and checks app metadata the
engine does not have. A push to `phosh` replaces the bundles of the
rolling pre-release `engine`; nexumia-de/phosh-apps lists
`nexumia-de/android_translation_layer engine` in its `apps.txt`, signs
the bundles with its existing key and publishes them; base apps without
metainfo stay off its page and out of GNOME Software. The secret
`PUBLISH_TOKEN` in this repository triggers phosh-apps at once;
without it phosh-apps publishes on its daily schedule.

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
- First step (2026-10-29): `--install-flatpak` reads the APK without
  running its code and builds the Flatpak with the `flatpak` command
  (`make-app-flatpak`) rather than libflatpak; libflatpak can replace
  it when the broker needs progress or finer errors.
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
  The sandbox hides `/dev/input`, so the direct write to the vibra
  device failed (`no feedbackd-recognized vibrator found`, Chooser on the
  phone). Done 2026-10-30: `Vibrator` uses `org.sigxcpu.Feedback.Haptic`
  `Vibrate(app_id, a(du))` when feedbackd exports it (only with a
  vibrator; checked by introspection, which has no side effect), and
  the device otherwise. feedbackd caps a step at 10 s, so longer ones
  are split; `cancel()` sends the empty pattern; the user's feedback
  profile applies (silent: no vibration).
- Sensors: iio-sensor-proxy on the system bus, only when granted.
- Keystore: libsecret uses the Secret portal inside Flatpak (one secret
  per app, no access to other keyring items).[^portal-secret]
- WebView: WebKitGTK's own sandbox through the Flatpak portal; test on
  the phone.

Done when: all apps in [apps.md](apps.md) work as Flatpaks at least as
well as before, and phase T passes.

## Phase 4 - Permissions (M)

### Decision Record: where permissions are shown and changed

Accepted by the owner on 2026-11-02 (places) and 2026-11-03 (internet).

**Context.** Android shows one "App info" page per app with its
permissions. On Phosh there are already GNOME Settings (Apps panel) and
Phosh Mobile Settings; a third settings app in the app grid would split
the same answers over three places.

**Decision.** No settings app of our own in the app grid. Three places:

1. **GNOME Settings → Apps** (unchanged): everything a portal backs
   (location, camera, notifications) plus storage and default apps. It
   finds our apps by the `X-Flatpak` key that `flatpak` writes into the
   exported launcher and reads the answers from the xdg permission store
   (`location`/`location`, `devices`/`camera`, `devices`/`microphone`,
   `background`/`background`) and from
   `org.gnome.desktop.notifications.application`; static Flatpak
   permissions it lists read-only. ATL therefore asks through the
   portals, so the answers land where Settings looks.
2. **"Android apps" page**: navigation mode (`ATL_NAVIGATION`: buttons,
   gesture or both), which apps may install others, signature checks,
   the installed Android apps with a link to their App info. A Phosh
   Mobile Settings plugin was the first choice, but 0.58 has no public
   plugin API (facts below), so the page is a **window of the installer
   service**, opened from the installer's own launcher and from App info.
3. **App info** per app, a window of the installer service on the host
   (never inside the sandbox, where the app could draw over it), opened
   from the launcher action **App info** (long press) and from page 2:
   the permissions without a portal (table below), internet, source,
   signer and version, clear data, uninstall, and **More permissions**,
   which opens `gnome-control-center applications <app id>`.

**Internet is asked, not granted** (owner, 2026-11-03, like GrapheneOS'
Network permission). Flatpak network access is static per app, so:

- `make-app-flatpak` never writes `--share=network` into the app's
  metadata. Apps without `android.permission.INTERNET` never get
  network and are never asked.
- **Install page** (the installer service's, also for installs a store
  starts): for apps with `INTERNET` a question "Allow internet?" with
  no preselected answer; Install stays disabled until it is answered.
  The answer becomes a user override (`flatpak override --user
  --share=network` or `--unshare=network`), the same file Flatseal edits.
  Updates keep the override; uninstall resets it (`flatpak override
  --user --reset`), since `--delete-data` is not documented to.
- **Later**: the switch in App info writes the override and, if the app
  runs, offers "Restart now" (`flatpak kill`, then start it again);
  Flatpak applies static permissions only at the next start.
- **Apps installed before this** (with `--share=network` in their
  metadata) keep network until their next update regenerates them; the
  update page then asks.
- Rejected: asking at first start (the app is already running in a
  sandbox without network and would need a restart at once), and a
  default "yes" (what Android does, and what the owner does not want).

**Own dialogs** for permissions without a portal: the app asks the
installer service over D-Bus, the service shows the question through
`org.freedesktop.impl.portal.Access` (on Phosh the shell's own system
dialog, the one the location and camera portals use) and stores the
answer in the permission store table `de.nexumia.atl` (id = Android
permission, app = Flatpak app id, value `yes`, `no` or `ask`). The
service identifies the caller by its sandbox, never by a parameter.
Every generated app may talk to a second bus name of the service,
`de.nexumia.atl.Permissions`; `de.nexumia.atl.Installer` stays limited
to apps with `REQUEST_INSTALL_PACKAGES`.

**Google services only with consent** (owner, 2026-11-03). An app
reaches Google servers (FCM push, microG-style services) only after the
user accepted it for that app: at the app's first request ATL shows a
warning, "<app> wants to use Google services. This connects to Google
servers.", with **Deny** and **Accept**; the default is denied. The
answer is the permission `atl.google-services` (own dialog, stored like
the others, changeable in App info). Code that talks to Google (the
push client) checks it with `Context.checkSelfPermission("atl.google-services")`
and asks with `Activity.requestPermissions`, or, without an activity,
through the same service call the activity uses. Play Integrity and
SafetyNet tokens are never faked.

**GrapheneOS as the reference** (owner, 2026-11-03; grapheneos.org
features and usage pages):

| GrapheneOS feature | Here | Why |
|---|---|---|
| Network permission, asked | adopt | install page question, Flatpak override (above) |
| Sensors permission (accelerometer, compass, ... off unless granted) | adapt | sensors come from iio-sensor-proxy on the system bus; apps get its bus name only after "yes" in an own dialog, next start |
| Storage Scopes | adapt | apps have no host files at all; what they open goes through the file chooser and documents portals, which is the scoped model by construction |
| Contact Scopes | adapt | ATL has no contacts provider; once it has one, the app sees only contacts picked through a chooser, never the whole address book |
| Sandboxed Google Play, never privileged | adapt | no Play services; Google contact only with `atl.google-services` (above), in the app's own sandbox |
| Notification when something is denied ("X tried to use the network") | adapt | ATL inside the sandbox posts one notification per app and permission when a denied permission blocks a call (network: a failed connect without network access); tapping it opens App info |
| Auto-revoke permissions of unused apps | adapt, later | the service knows the last start; after 90 days unused it resets own answers and the network override to "ask"; portal answers stay with GNOME Settings |
| Per-app exploit protection toggles (hardened malloc, dynamic code loading, memory tagging) | skip for now | Flatpak gives seccomp and namespaces per app but no per-app hardened malloc or MTE switch; ART needs dynamic code (JIT, dex2oat), so a "no dynamic code" switch would break every app |
| Hardened kernel, verified boot, OS-level hardening | skip | outside ATL (postmarketOS) |

**Microphone, enforced in PipeWire** (owner, 2026-11-03). The audio
socket stays (without it apps are silent), and recording is blocked
below the app:

- Apps get only the socket of a second pipewire-pulse,
  `atl-pulse-norecord`, a user service that refuses every record stream
  (microphone and sink monitors) of every client. An app answered "yes"
  for `RECORD_AUDIO` gets the normal socket as a Flatpak user override,
  from its next start. ATL's own `AudioRecord` check stays as the first
  layer.
- Rejected after a security review (2026-11-04): WirePlumber and
  pipewire-pulse rules matching `pipewire.access.portal.app_id`. Pulse
  clients can overwrite their properties (`PULSE_PROP`,
  `UPDATE_CLIENT_PROPLIST`), so a denied app could pose as an allowed
  one. Native PipeWire with a WirePlumber rule would be sound
  (`pipewire.*` keys come from the server), but ATL's ALSA default and
  WebKit's GStreamer output use pulse; switching both is larger. Trade-off
  of the chosen way: one more user service, and a change applies only at
  the app's next start.
- Packaging: `make-app-flatpak` installs the unit and the drop-in into the
  user's own configuration; nothing needs root.

**Owner decisions 2026-11-03 (second set):**

- **Background location**: Phosh's location portal dialog already asks;
  use while the app has a window or a running foreground service is
  "while in use" and needs no second question. `requestPermissions` and
  `checkSelfPermission` report `ACCESS_BACKGROUND_LOCATION` granted
  without a dialog unless the user said no or the portal has no grant;
  the extra question comes only at real background use (no window, no
  foreground service), default no
  (`ATLPermissions.locationAllowedNow`, #75).
- **Legacy permission review** for apps targeting SDK < 23 (Android 10+
  and GrapheneOS): such apps never ask at runtime (androidx answers from
  `checkSelfPermission`; CoMaps at SDK 21 waits on its splash). On their
  first start ATL shows one review dialog listing the app's dangerous
  permissions, each allow/deny, default deny; the answers are stored like
  the others and reported by `checkSelfPermission`. Access itself still
  goes through the portals and ATL's checks.
- **Autostart after reboot**: apps that request `RECEIVE_BOOT_COMPLETED`
  get a question (default no), stored like the others and changeable in
  App info; only apps answered "yes" get a boot autostart entry.
- **Push server**: the "Android apps" page gets a "Push server" field
  (default `https://ntfy.sh`, per user); the push work writes the
  distributor's configuration from it (interface in the push handoff).

**Facts behind it** (2026-11-03, on the phone):

- phosh-mobile-settings 0.58 loads GIO modules from the fixed
  `/usr/lib/phosh-mobile-settings/plugins` (root only) for the
  extension point `ms-device-panel`; a plugin must also say which
  devices it supports (`ms_plugin_check_device_support`; the only one is
  the Librem 5's). The panel classes (`MsPanel`, `MsPluginPanel`) are
  exported by the executable but have no headers in
  `phosh-mobile-settings-dev`, whose `libpms` refuses to compile without
  `LIBPMS_USE_UNSTABLE_API`. Not a public, versioned API: a plugin built
  outside the Phosh tree would break silently on any update.
- Portal backends: the location and camera portals ask through
  `impl.portal.Access`, which Phosh's shell implements; notifications go
  to Phosh's backend; `Background` is `none` (no background portal), and
  there is no microphone portal.

### Mapping

| Android permission | Enforced by | Asked | Shown and changed in |
|---|---|---|---|
| `INTERNET` | `--share=network` as user override | install page (no default) | App info; next start |
| `ACCESS_FINE/COARSE_LOCATION` | location portal | portal dialog at first use | GNOME Settings, at once |
| `ACCESS_BACKGROUND_LOCATION` | own answer, needed only without a window or foreground service | own dialog at the first real background use (below) | App info |
| `CAMERA` | camera portal (PipeWire remote with cameras only)[^portal-camera] | portal dialog | GNOME Settings, at once |
| `RECORD_AUDIO` | ATL's capture check, then the socket: only a pulse server without recording unless the answer is yes (below); playback unaffected | own dialog | App info; next start |
| Audio playback (no permission) | audio socket by default | - | - |
| `POST_NOTIFICATIONS` (API 33+) | notification portal | not asked: allowed, as the portal does | GNOME Settings (switch per app) |
| `READ_CONTACTS`, `READ_CALENDAR`, `READ/SEND_SMS`, `READ_CALL_LOG`, `READ_PHONE_STATE` | not implemented by ATL: data stays empty | own dialog, so the app sees a real answer | App info |
| `BIND_NOTIFICATION_LISTENER_SERVICE` (special access) | own answer | App info only, like Android's special access | App info |
| `BLUETOOTH_CONNECT/SCAN` (API 31+), `BLUETOOTH`, `BLUETOOTH_ADMIN` | `--system-talk-name=org.bluez` as user override (`--system-no-talk-name` otherwise; apps without a Bluetooth permission never get it) | install page "Allow Bluetooth?" (no default, like internet: a static Flatpak permission, and Android grants it at install before API 31); the service's dialog for an API 31+ request also opens or closes it | App info (one Bluetooth switch); next start |
| `BODY_SENSORS`, `ACTIVITY_RECOGNITION`; plain sensors (no permission) | iio-sensor-proxy (`--system-talk-name=net.hadess.SensorProxy`) for apps that use sensors | own dialog for the dangerous ones | App info; next start |
| `READ/WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_*`, `MANAGE_EXTERNAL_STORAGE` | none; file chooser and documents portals (Storage Access Framework) | granted inside the app's own storage | - |
| `atl.google-services` (ATL's own) | own answer; the push client and other Google clients check it | own dialog with a warning, default denied | App info |
| `REQUEST_INSTALL_PACKAGES` | `de.nexumia.atl.Installer` talk name | every install confirmed on the host | Android apps page |
| `RECEIVE_BOOT_COMPLETED` | own answer; a boot autostart entry only after "yes" | own question, default no | App info |
| Other background work | no background portal on Phosh: not available | - | - |
| Normal permissions (`VIBRATE`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, ...) | granted, like Android | - | - |

Location is split between two deciders, so one request never shows two
dialogs for the same thing: the location portal alone decides
`ACCESS_FINE/COARSE_LOCATION` (ATL reports them granted in a Flatpak
until the portal refused once, and asks nothing itself;
`requestPermissions` answers them without a dialog), and the permission
service alone decides `ACCESS_BACKGROUND_LOCATION`. As on Android 11+,
background location should only be asked in a request of its own after
foreground location works; asked together with FINE/COARSE it is
answered "no" without a dialog. Coarse-only apps ask the portal for
neighbourhood accuracy. Which permissions an app requests is the list
`make-app-flatpak` stores in `/app/share/atl/permissions`.

In ATL:

- `checkSelfPermission` answers from `/.flatpak-info` (static ones),
  the permission store (portal and own answers, read through the
  service) and the table above.
- `requestPermissions` asks the portal or the service, then calls
  `onRequestPermissionsResult` with the real answers; "no" twice means
  "don't ask again", as on Android 11+.
- Static permissions changed in App info apply at the next start.

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
