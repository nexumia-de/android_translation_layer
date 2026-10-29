---
type: Concept
title: Hardening roadmap
description: Threat model, current state and phased plan for permissions and sandboxing of Android apps run by ATL - permission store and dialogs, brokers, bubblewrap, D-Bus proxy, portals, seccomp, Landlock and APK signature checks.
tags: [security, sandbox, permissions, bubblewrap, portal, dbus, seccomp, landlock, roadmap]
status: draft
generated: { by: claude-code/2.1.308, at: 2026-10-29T16:45:00Z }
sources:
  - id: android-permissions
    resource: https://developer.android.com/guide/topics/permissions/overview
    title: Android developers, Permissions on Android (install-time, runtime and special permissions)
  - id: bwrap
    resource: https://github.com/containers/bubblewrap
    title: bubblewrap, unprivileged sandboxing tool used by Flatpak
  - id: dbus-proxy
    resource: https://github.com/flatpak/xdg-dbus-proxy
    title: xdg-dbus-proxy, filtering D-Bus proxy used by Flatpak
  - id: flatpak-perms
    resource: https://docs.flatpak.org/en/latest/sandbox-permissions.html
    title: Flatpak documentation, Sandbox permissions
  - id: flatpak-under-the-hood
    resource: https://docs.flatpak.org/en/latest/under-the-hood.html
    title: Flatpak documentation, Under the hood (bubblewrap, seccomp, D-Bus proxy)
  - id: portal-registry
    resource: https://github.com/flatpak/xdg-desktop-portal/blob/main/data/org.freedesktop.host.portal.Registry.xml
    title: xdg-desktop-portal, org.freedesktop.host.portal.Registry (app ids for unsandboxed apps)
  - id: portal-camera
    resource: https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Camera.html
    title: xdg-desktop-portal, Camera portal
  - id: portal-secret
    resource: https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Secret.html
    title: xdg-desktop-portal, Secret portal
  - id: landlock
    resource: https://docs.kernel.org/userspace-api/landlock.html
    title: Linux kernel documentation, Landlock unprivileged access control
  - id: seccomp
    resource: https://docs.kernel.org/userspace-api/seccomp_filter.html
    title: Linux kernel documentation, Seccomp BPF
  - id: apk-signing
    resource: https://source.android.com/docs/security/features/apksigning
    title: Android Open Source Project, Application signing (APK signature schemes v1-v4)
---

# Goal

An Android app run by ATL can reach only what the user allowed: its own
data, the devices and services it was granted, and files the user picked.
Granting works like on Android (dialog on first use, settings per app,
revocable), and the enforcement happens in the kernel, not in ATL's Java
code. This page is the plan; the architecture choice is proposed in
[decisions/app-sandbox.md](decisions/app-sandbox.md).

# Threat model

| Asset | Example |
|---|---|
| The owner's files | `~/Documents`, `~/.ssh`, photos, other apps' data |
| Secrets | GNOME keyring (Secret Service): Wi-Fi and account passwords |
| Session integrity | autostart files, `~/.local/share/applications`, `~/.profile`, D-Bus services that start programs |
| Privacy | location, microphone, camera, sensors, contacts, network identity |
| Other ATL apps | their data directories and keystore entries |

Adversaries, most likely first:

1. **Tracking and analytics SDKs in normal apps** (the realistic case):
   they read what they may, phone home, fingerprint the device.
2. **An app exploited through content** (WebView, media parsers, native
   libraries).
3. **A malicious APK** from a store or opened by hand.

Out of scope: kernel exploits, root on the phone, physical access,
Google Play Services. ATL itself (ART, `bionic_translation`, the
API implementation) runs **inside** the sandbox with the app, so bugs in
ATL do not widen access.

# Current state (2026-10-29, `phosh` at `9ac9cc78`)

ATL runs every app as the owner's user with full access. What exists:

| Area | Today | File |
|---|---|---|
| `checkPermission` | fixed list: storage, network granted; location and microphone only with `ATL_UGLY_ENABLE_LOCATION` / `ATL_UGLY_ENABLE_MICROPHONE`; everything else denied | `src/api-impl/android/content/pm/PackageManager.java` |
| `requestPermissions` | logs "not handled", never calls `onRequestPermissionsResult` | `src/api-impl/android/app/Activity.java` |
| Install permission | granted when the manifest requests `REQUEST_INSTALL_PACKAGES` | `PackageManager.canRequestPackageInstalls()` |
| Files | app data in `~/.local/share/android_translation_layer/<apk>_/`; `/storage/emulated/0/` is rewritten there by a libc path override (cooperative, native code can ignore it) | `src/main-executable/libc_bio_path_overrides.c` |
| Launchers, D-Bus services, installs | written by the app's own process into `~/.local/share/applications`, `~/.local/share/dbus-1/services`, `_installed_apks_`; `update-desktop-database` via `system()` | `src/main-executable/main.c` |
| Location | `libportal` location portal, gated by the env variable | `src/api-impl-jni/location/` |
| Audio | ALSA `default` device (PipeWire on the phone), playback and capture | `src/api-impl-jni/audio/` |
| Vibration | writes the feedbackd vibra `/dev/input/event*` device found via udev | `src/api-impl-jni/android_os_Vibrator.c` |
| Sensors | reads `/sys/bus/iio/devices` directly | `src/api-impl-jni/sensors/` |
| Keystore | Secret Service via libsecret, schema named after the app id | `src/api-impl-jni/security/` |
| Notifications, MPRIS | own the app's bus name on the session bus | `src/api-impl-jni/app/`, `media/` |
| WebView | WebKitGTK 6, which starts its own bubblewrap sandbox for web processes | `src/api-impl-jni/widgets/android_webkit_WebView.c` |
| APK signatures | not verified on install or update | `PackageInstaller.java` |

Consequence: any app, or any tracker inside it, can read `~/.ssh`, query
every keyring secret over D-Bus, write an autostart or launcher file that
runs code outside ATL, and record audio through ALSA. Permission answers
in Java are only a promise to cooperative apps.

# Target architecture

```
 launcher / D-Bus activation / store install
            │
            ▼
   atl (host side, trusted, small)          ── policy store per app
     reads policy, asks the user (install,       (grants, network, files)
     uninstall, first-use dialogs)
            │ execs
            ▼
   bwrap  ── namespaces, read-only /usr, app dir only, no $HOME,
            │  --unshare-net if no network, seccomp, no_new_privs
            ├── xdg-dbus-proxy (session bus filtered per app)
            ▼
   atl --sandboxed  (ART + app + ATL APIs, untrusted)
     ── portals: location, camera, file chooser, documents, open URI,
        notifications, secret ── decisions per app id
     ── broker requests to host side: install, uninstall, launchers
```

Principles, taken from Flatpak and Android:[^flatpak-under-the-hood][^android-permissions]

- **One source of truth**: a per-app policy file kept by ATL outside the
  app's writable area. Java `checkPermission`, the dialogs, the settings
  page and the generated `bwrap` arguments all read it.
- **Deny by default**: what is not in the policy is not mounted, not
  proxied, not granted.
- **Ask at the moment of use** for dangerous permissions (location,
  microphone, camera, contacts, notifications on API 33+), grant normal
  permissions (internet, vibrate) silently, show special permissions
  (install apps, all files) as separate switches.[^android-permissions]
- **Broker, don't widen**: anything that must touch the host (installs,
  launchers, opening URIs, picking files) goes through a confirming host
  component or a portal, never through a bind mount of `$HOME`.
- **Self-sandboxing**: ATL re-executes itself inside `bwrap` early in
  `main()`, so desktop launchers, D-Bus activation and installs from
  stores are all covered without changing every `Exec=` line.
- **Kernel enforcement in layers**: namespaces and mounts
  (bubblewrap),[^bwrap] syscall filter (seccomp),[^seccomp] optional
  Landlock[^landlock] as a second filesystem fence.

# Roadmap

Sizes: S = a day, M = a few days, L = a week or more of agent work plus
phone tests. Every phase ends with a phone test by the owner
([owner tests](dev-environment.md)) and an entry in [log.md](log.md).

## Phase 0 - Facts and threat model sign-off (S)

- Confirm this threat model with the owner.
- Check the phone (read-only, as user): unprivileged user namespaces
  (`unshare -U true`), active LSMs (`cat /sys/kernel/security/lsm`, look
  for `landlock`), seccomp (`grep Seccomp /proc/self/status`), versions of
  `xdg-desktop-portal`, PipeWire and WirePlumber.
- System packages the owner installs: `bubblewrap`, `xdg-dbus-proxy`
  (both in Alpine), `libseccomp-dev` for building.
- Resource inventory per app in [apps.md](apps.md): which paths, devices
  and bus names each tested app touches (from ATL's own logs and
  `strace -f -e trace=%file,connect`).

Done when: the kernel facts are in [dev-environment.md](dev-environment.md)
and the decision record is accepted or changed.

## Phase 1 - Permission model without sandbox (M)

The visible part, useful before any sandbox.

- Policy store per app (key file under
  `~/.local/share/android_translation_layer/_policy_/<package>.ini`),
  kept on update, removed on uninstall. Classification of permissions into
  normal, dangerous (with Android's permission groups) and special.
- `checkPermission` reads the store; `ATL_UGLY_*` stay as developer
  override.
- `requestPermissions` shows a libadwaita dialog ("Allow while using",
  "Only this time", "Don't allow"), stores the answer, calls
  `onRequestPermissionsResult`; `shouldShowRequestPermissionRationale`
  answers from the stored history.
- `REQUEST_INSTALL_PACKAGES` becomes a switch, asked the first time a
  store installs.
- Launcher action **Permissions** (next to **Uninstall**) opens
  `--app-settings <package>`: one switch per requested dangerous or
  special permission, later network, storage, "clear data".
- `--install-ask` page lists the requested permissions, read-only.

Done when: an app asking for location gets the dialog, works after
"Allow", gets a denial after "Don't allow", and the switch in the
settings page changes the answer on the next start.

## Phase 2 - Brokers: take host writes out of the app process (M-L)

Prerequisite for the sandbox, and already a fix for persistence.

- Install, update, uninstall: the app process sends the staged APK to the
  host side (`atl` outside the sandbox, started with a dedicated option
  and a D-Bus name the proxy lets the app call); the host side shows the
  existing install page and writes the APK, launcher, icons and D-Bus
  service file. The app never writes `~/.local/share/applications`.
- `update-desktop-database` runs only on the host side.
- Opening URIs and `ACTION_VIEW` of other apps: OpenURI portal only, no
  `xdg-open`.
- Starting another installed ATL app (`startActivity` with an explicit or
  resolved component): D-Bus activation of that app's name, allowed by the
  proxy only for ATL app names.
- Vibration through feedbackd's D-Bus API instead of writing the input
  device.
- Keystore: Secret portal[^portal-secret] when available (per-app secret,
  no access to other keyring items); fallback a key file in the app's
  data directory.

Done when: Aurora and F-Droid install, update and uninstall apps with no
write outside the app's data directory from the app process
(checked with `strace`), and all apps in [apps.md](apps.md) are not worse.

## Phase 3 - Filesystem and process sandbox (L)

- Self re-exec into `bwrap`: `--unshare-all` (user, pid, ipc, uts,
  cgroup; network see phase 5), `--die-with-parent`, `--new-session`,
  `--cap-drop ALL`, `--clearenv` with an allowlist.[^bwrap]
- Mounts: `/usr`, `/lib`, the ATL and ART installation and the needed
  parts of `/etc` (fonts, ssl, resolv.conf, localtime) read-only; the
  app's data directory read-write at the same path; private `/tmp`;
  `/proc`, minimal `/dev`, `/dev/dri` for the GPU; the Wayland socket;
  the PipeWire socket (see phase 5); fontconfig cache read-only. No
  `$HOME`.
- The APK and its split APKs read-only; `_installed_apks_` of other apps
  not visible except the metadata `PackageManager` needs (served by the
  host side or a read-only index).
- WebView: WebKit's own sandbox nests a second `bwrap`. Test nesting
  (needs user namespaces inside); fallback is WebKit's in-process mode,
  recorded per app.
- Opt-in first (`--sandbox`, an env variable for the aports package),
  per-app opt-out in the settings page for apps that break; default on
  only together with phase 4.

Done when: a test APK (see phase 8) cannot read `~/.ssh` or another app's
data, cannot write `~/.config/autostart`, and the apps in
[apps.md](apps.md) still pass.

## Phase 4 - D-Bus filtering and app identity (L)

Without this, phase 3 is easy to escape: the session bus reaches the
keyring and services that start programs.

- `xdg-dbus-proxy` per app with `--filter`:[^dbus-proxy] own
  `<package>` (GApplication, notifications, MPRIS); talk to
  `org.freedesktop.portal.*`, `org.freedesktop.Notifications`,
  feedbackd, the ATL host broker and other ATL app names; nothing else.
  No system bus, or only what a granted feature needs.
- App identity towards portals, so each Android app has its own portal
  decisions. Two options to prototype, recorded in the decision record:
  - **A, like Flatpak**: run the proxy inside a small `bwrap` with a
    generated read-only `/.flatpak-info` naming the app id; portals and
    PipeWire then treat the app as sandboxed.[^flatpak-under-the-hood]
    Risk: portals expect Flatpak conventions (document portal paths,
    launcher id prefixes).
  - **B, host app registry**: register the app id with
    `org.freedesktop.host.portal.Registry`.[^portal-registry] Simpler,
    but the app counts as unsandboxed, the call must happen before any
    portal call on that connection, and a sandboxed app must not be able
    to register a foreign id.
- Location, notifications and background activity then get their portal
  prompts and their entries in the permission store per app.

Done when: from inside the sandbox `org.freedesktop.secrets` and other
non-allowed names are unreachable, portals show the Android app's name,
and phase 3 is switched on by default.

## Phase 5 - Enforced device permissions and shared files (M-L)

Each Android permission maps to a kernel or portal mechanism:

| Permission | Enforcement |
|---|---|
| `INTERNET` (normal on Android, a switch here) | `--unshare-net` when off; per-host filtering is out of scope |
| Location | portal only, ATL dialog plus portal decision |
| Camera | Camera portal: PipeWire remote limited to camera nodes[^portal-camera] |
| Microphone | PipeWire access control for the sandboxed client; research how WirePlumber restricts capture for sandboxed clients, otherwise no PipeWire socket for capture |
| Sensors | bind `/sys/bus/iio` read-only only when granted, or iio-sensor-proxy |
| Storage / "all files" | no host paths; Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`) through the FileChooser and Documents portals; optional per-app bind of a folder the user picks in the settings page |
| Contacts, calendar | not implemented by ATL; stay denied |

Done when: switching a permission off in the settings page removes the
access at the next start, and the denial is visible in the app as on
Android.

## Phase 6 - Defence in depth (M)

- seccomp filter like Flatpak's (deny `ptrace`, `perf_event_open`,
  `keyctl`, `add_key`, `kexec_*`, module loading, `TIOCSTI`, old socket
  families, personality changes), generated with libseccomp and passed to
  `bwrap --seccomp`.[^seccomp][^flatpak-under-the-hood] ART's JIT needs
  executable mappings, so W^X rules are not applied.
- `no_new_privs` (bwrap sets it), strict umask, data directories `0700`.
- Landlock ruleset as a second fence for the file paths, if the phone's
  kernel has it enabled (phase 0).[^landlock]
- Resource limits with `prlimit` (no systemd scopes on Alpine/OpenRC).

## Phase 7 - Install integrity (M)

- Verify APK signatures (schemes v1-v3, v4 optional) before install;
  refuse an update signed by a different certificate; warn on downgrade,
  require confirmation.[^apk-signing]
- Remember the installing store per app (`getInstallerPackageName`), and
  allow updates only from it or from an explicit user action.
- Show signer and permission changes on the update page.

## Phase 8 - Tests and upkeep (ongoing)

- A small test APK in the fork that tries each escape (read `~/.ssh`,
  write autostart, call `org.freedesktop.secrets`, open a socket while
  `INTERNET` is off, record audio without the grant) and reports results;
  run headless after every sandbox change and before each aports pin.
- Sandbox profile and known breakages per app in [apps.md](apps.md).
- Keep `bubblewrap`, `xdg-dbus-proxy`, WebKitGTK and ATL's dependencies
  current through the aports pins.
- Upstream: ATL's maintainers plan bubblewrap themselves (TODO in
  `PackageManager.checkPermission`). Per [the upstream
  rules](fork-workflow.md), ask in one issue what direction they want
  before phase 3; nothing is sent without the owner.

# Order and dependencies

```
0 ──► 1 ──► 2 ──► 3 ─┬─► 4 ──► (sandbox on by default)
                     │         ├──► 5
                     │         └──► 6
                     └─► 7 (independent, can start after 1)
8 runs alongside from phase 3 on
```

Phase 1 gives the visible Android behaviour; phases 2 to 4 give real
isolation and belong together; 5 to 7 tighten it.

# Open questions

- Does the phone's kernel allow unprivileged user namespaces and nested
  ones (for WebKit)? Without them, `bwrap` needs setuid, which Alpine does
  not ship; Landlock plus seccomp would be the fallback.
- Portal identity: option A or B in phase 4.
- Microphone isolation with PipeWire for non-Flatpak sandboxes.
- ATL inside Flatpak: nested `bwrap` is blocked there; the Flatpak
  portal's `Spawn` with sandbox flags would be the backend. Only relevant
  if the Flatpak build is used again.

[^android-permissions]: Android developers, Permissions on Android
[^bwrap]: bubblewrap
[^dbus-proxy]: xdg-dbus-proxy
[^flatpak-perms]: Flatpak documentation, Sandbox permissions
[^flatpak-under-the-hood]: Flatpak documentation, Under the hood
[^portal-registry]: xdg-desktop-portal, host app registry
[^portal-camera]: xdg-desktop-portal, Camera portal
[^portal-secret]: xdg-desktop-portal, Secret portal
[^landlock]: Linux kernel documentation, Landlock
[^seccomp]: Linux kernel documentation, Seccomp BPF
[^apk-signing]: AOSP, Application signing
