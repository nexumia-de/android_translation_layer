---
type: Concept
title: Installing apps with F-Droid and Aurora Store
description: How ATL installs, lists and uninstalls APKs (also split APKs) for app stores like F-Droid and Aurora Store, how to set them up on the phone, and what is still missing.
resource: ../src/api-impl/android/content/pm/PackageInstaller.java
tags: [fdroid, aurora-store, google-play, split-apk, install, packageinstaller, launcher, portal]
generated: { by: claude-code/2.1.308, at: 2026-10-29T11:45:00Z }
sources:
  - id: fdroid-session
    resource: https://gitlab.com/fdroid/fdroidclient/-/blob/08f444713cefc97a8fa3cd3161a4484db6db54c8/app/src/main/kotlin/org/fdroid/install/SessionInstallManager.kt
    title: F-Droid client 2.0.2, SessionInstallManager (session installs)
  - id: fdroid-compat
    resource: https://gitlab.com/fdroid/fdroidclient/-/blob/08f444713cefc97a8fa3cd3161a4484db6db54c8/libs/index/src/androidMain/kotlin/org/fdroid/CompatibilityChecker.kt
    title: F-Droid client 2.0.2, CompatibilityChecker (SDK, ABI and feature checks)
  - id: aurora-session
    resource: https://gitlab.com/AuroraOSS/AuroraStore/-/blob/4.8.4/app/src/main/java/com/aurora/store/data/installer/SessionInstaller.kt
    title: Aurora Store 4.8.4, SessionInstaller (writes base APK and splits into one session)
  - id: aurora-spoof
    resource: https://gitlab.com/AuroraOSS/AuroraStore/-/blob/4.8.4/app/src/main/java/com/aurora/store/data/providers/SpoofProvider.kt
    title: Aurora Store 4.8.4, SpoofProvider (device profile sent to Google Play)
  - id: wolfssljni-fix
    resource: https://github.com/wolfSSL/wolfssljni/blob/master/src/java/com/wolfssl/provider/jsse/WolfSSLImplementSSLSession.java
    title: wolfssljni master, WolfSSLImplementSSLSession.setNativeTimeout (catches a freed WOLFSSL)
---

# What works

The official F-Droid client 2.0.2 (`org.fdroid.fdroid`, from
`https://f-droid.org/F-Droid.apk`, signature checked) runs in ATL and
installs, lists and uninstalls apps. Tested headless on 2026-10-28 with
Chooser: install from the details page, F-Droid then shows it as installed with **Open**
([screenshot](screenshots/fdroid-chooser-installed-2026-10-28.png)),
**Open** starts Chooser through its launcher, and **Uninstall** in the ⋮ menu asks first
([screenshot](screenshots/fdroid-uninstall-2026-10-28.png)) and removes
it.

Aurora Store 4.8.4 (`com.aurora.store`, from F-Droid) installs apps from
Google Play the same way, including split APKs; tested headless on
2026-10-28 with DB Navigator ([screenshot](screenshots/aurora-db-navigator-installed-2026-10-28.png)).

# How it works

An app installed into ATL is an APK in
`$ANDROID_APP_DATA_DIR/_installed_apks_/` plus a launcher
(`android-translation-layer --install`). Other apps find a package there
as `<package>.apk`.

- **Install**: F-Droid 2.0 uses `PackageInstaller` sessions[^fdroid-session]:
  `createSession`, `openWrite`, `commit(IntentSender)`. The session is a
  directory in `_installer_sessions_/`. `commit` finds the base APK
  (the one whose manifest has no `split` attribute), checks it (package
  name as announced, same signer as an installed version), names it
  `<package>.apk` and runs `android-translation-layer --install -w
  <width> -h <height> [--sdk-int <n>] <apk> [<split>...]` with the
  installer's window size and, if the installer was started with one,
  its `--sdk-int`. The
  exit status becomes the session status: 0 success, 2 the user
  cancelled the portal dialog in a Flatpak (`STATUS_FAILURE_ABORTED`),
  anything else a failure. Then `ACTION_PACKAGE_ADDED` is broadcast and the status
  goes back through the `IntentSender`.
- **Split APKs**: Google Play delivers most apps as a base APK plus
  configuration splits (`config.arm64_v8a.apk` with the native
  libraries, `config.xxhdpi.apk`, languages); Aurora Store writes all of
  them into one session[^aurora-session]. ATL runs an app from several
  APK files given on the command line; `--install` copies the files
  after the first to `_installed_apks_/<package>.apk.splits/` and puts
  them into the launcher's command line. Uninstalling removes them too.
- **List**: `getInstalledPackages` and `getInstalledApplications` return
  the calling app plus every `<package>.apk` it may see, which with
  `QUERY_ALL_PACKAGES` (F-Droid declares it) is all of them.
- **Uninstall**: `startActivityForResult` with `ACTION_DELETE` or
  `ACTION_UNINSTALL_PACKAGE` and a `package:` URI shows an "Uninstall
  …?" dialog. Confirming removes the APK, the app's data and cache, its
  D-Bus service file and its launcher with its icon (also one made
  through the portal), and broadcasts `ACTION_PACKAGE_REMOVED`.
  `PackageInstaller.uninstall` does the same without a dialog.
  `startActivity` with the same intent (Aurora Store) runs the
  launcher's **Uninstall** action (see below) instead of handing the
  `package:` URI to `xdg-open`, which opened GNOME Software.
- **Changes from other processes**: once an app looks at other packages
  (`getPackageInfo`, `getInstalledPackages`), `PackageInstaller` watches
  `_installed_apks_` (`java.nio.file.WatchService`, inotify). When an
  APK appears, changes or disappears and the app did not do it itself
  (the launcher's Uninstall action, an opened APK file, another store),
  ATL drops its cached package and broadcasts `ACTION_PACKAGE_ADDED` or
  `ACTION_PACKAGE_REMOVED`. Before, Aurora kept showing an app
  uninstalled from the app grid as installed until it was restarted.
  Tested headless on 2026-10-29: Aurora's **Uninstall** on DB Navigator
  showed the uninstall page, and after confirming Aurora switched to
  **Install** at once.
- **Open**: `startActivity` with `ACTION_MAIN` for a component of another
  installed package runs `gtk-launch <package>`, which starts the app
  through its launcher in its own ATL process, as Phosh does.
- **Launchers**: the launcher is `DBusActivatable`, so Phosh starts the
  app through the D-Bus service file `--install` writes. dbus-daemon does
  not search `PATH`, so its `Exec` starts with `/usr/bin/env`; launchers
  created by older ATL builds start with a bare `env` and fail to start
  (`Failed to execute program <package>`) until the app is installed
  again.
- **Launcher file**: outside Flatpak, `--install` writes
  `~/.local/share/applications/<package>.desktop` itself, with the icon in
  `$ANDROID_APP_DATA_DIR/_launcher_icons_/`, and replaces a launcher an
  earlier install made through the portal. The portal's
  `DynamicLauncher` always showed its GTK "Create Application" dialog
  (Phosh's portal does not implement it), although the user had already
  chosen to install, and its `Install()` rejects a desktop entry with
  more than one group, so no actions. In a Flatpak ATL still uses the
  portal, with its dialog.
- **dbus-broker**: postmarketOS' session bus is dbus-broker, which
  (unlike dbus-daemon) reads service files only when asked. Without
  that a newly installed app does not start from its launcher (`The name
  is not activatable`) and a changed service file keeps its old `Exec`,
  so ATL calls `org.freedesktop.DBus.ReloadConfig` after writing or
  removing one. `tools/headless-shot.sh` runs dbus-daemon; to test
  against dbus-broker, point `DBUS_SESSION_BUS_ADDRESS` inside the test
  at `unix:path=/run/user/$(id -u)/bus` and give the activated services
  the test display with `dbus-update-activation-environment --systemd
  WAYLAND_DISPLAY="$XDG_RUNTIME_DIR/$WAYLAND_DISPLAY"` (reset it after).
- **Uninstall from the launcher**: the launcher has a desktop action
  **Uninstall**, which Phosh shows on a long press in the app grid. It
  asks "Uninstall <app>?" and removes the same as above. Because the
  launcher is `DBusActivatable`, Phosh runs the action through D-Bus
  (`org.freedesktop.Application.ActivateAction`), which first starts
  the app's D-Bus service. So the service file passes
  `--dbus-activated`: ATL then takes the bus name before the app starts
  and waits up to 0.5 s for the request; for `uninstall` it only shows
  the question and never starts the app, so a broken app can still be
  removed. Without D-Bus the action's `Exec` runs `--uninstall`, which
  only removes an APK in `_installed_apks_`. Launchers from older
  builds get the action when the app is installed again.

# Opening an APK file

Opening an `.apk` in a file manager or browser asks to install it, like
Android's package installer. `res/desktop/com.gitlab.android_translation_layer.install.desktop`
(installed to `share/applications`, hidden from the app grid) handles
`application/vnd.android.package-archive` with
`android-translation-layer --install-ask -w 360 -h 720 %f`.

`--install-ask` loads the APK like `--install`, without starting it or
extracting its native libraries, and shows a page in its own window:
icon, name, package, version and what installing does
(`Application.get_install_state`):

| State | Message | Buttons |
|---|---|---|
| not installed | Do you want to install this app? | Cancel, Install |
| newer version | Do you want to update this app? Its data stays. (version old → new) | Cancel, Update |
| same version | This version is already installed… | Cancel, Reinstall |
| older version | A newer version is already installed… | Close |
| other signer | …signed by someone else. Uninstall it first… | Close |

Like `PackageInstaller`, it copies the APK as `<package>.apk` into
`_installer_sessions_/ask-*/` and runs `--install` with its own window
size and `--sdk-int`, so the app gets the same launcher, D-Bus service
and data directory as one installed from F-Droid. An update or reinstall
without `--sdk-int` keeps the one in the installed app's launcher, e.g.
the `--sdk-int 26` F-Droid and Aurora Store need; a new app gets its own
minSdk. Afterwards the page
offers **Open** (the launcher, through D-Bus activation) and **Done**.
The data directory made for the opened file name is removed again,
unless it existed before. Exit status: 0 done, 2 cancelled, 1 failed.

PNG icons are taken for xxxhdpi (640 dpi), the largest an APK has, for
the page and the launcher; the one for ATL's own density is 48 px and
looked blurry at 128 px. Launchers made earlier keep their icon until
the app is installed again.

The page is libadwaita (`src/main-executable/prompt_page.c`):
`AdwStatusPage` with the buttons at the bottom, in reach of the thumb,
following the system's light or dark style. It is its own
`AdwApplicationWindow`, which phoc maximizes on the phone; a
`GtkAlertDialog` without a parent window, as the uninstall question
was, sat small in the top left corner. The launcher's **Uninstall**
action uses the same page. libadwaita's style sheet is only loaded when
no app runs in the process, since it would restyle the app's GTK
widgets.

To make ATL the default for APK files (instead of e.g. Waydroid), as
the user:

```sh
xdg-mime default com.gitlab.android_translation_layer.install.desktop application/vnd.android.package-archive
```

Tested headless under phoc on 2026-10-29 with Blokish: `gio open` →
page → Install → Open starts Blokish; the launcher's uninstall action →
page → Uninstall removes it
([page](screenshots/install-ask-2026-10-29.png), [F-Droid's icon](screenshots/install-ask-fdroid-2026-10-29.png),
[uninstall](screenshots/uninstall-page-2026-10-29.png)).

# Installing an APK as its own Flatpak

For the [hardening roadmap](hardening-roadmap.md) (phase 2) an APK can
become its own sandboxed Flatpak `de.nexumia.atl.<package>` instead:

```sh
android-translation-layer --install-flatpak [-w 360 -h 720] [--sdk-int N] [-l ACTIVITY] app.apk [splits...]
```

- ATL on the host reads the package name, label, launcher icon (the
  APK's file, or the adaptive or vector icon rendered to SVG), URL
  schemes and `<uses-permission>` list. It creates a plain
  `android.app.Application` for that and never loads the app's own
  `Application` class, content providers or activities, so no app
  code runs outside the sandbox. A data directory it had to create for
  reading the APK is removed again.
- It then runs `libexec/android-translation-layer/make-app-flatpak`
  (`flatpak/make-app.sh`; `ATL_MAKE_FLATPAK` overrides the path), which
  copies the installed engine `de.nexumia.atl.BaseApp`, adds the APKs,
  launcher (with `MimeType` and `--uri %u` for URL schemes) and icon,
  exports into the local repository and installs or updates the app
  with `flatpak --user`. No root.
- Permissions: every app gets Wayland, the GPU, IPC, audio playback and
  feedbackd; `android.permission.INTERNET` adds `--share=network`.
  Everything else waits for portals and the broker (phases 3 and 4).
- The package name is checked against Android's rules and the label is
  kept to one line, since both come from the APK and end up in the
  launcher.

Tried headless on 2026-10-29 with Chooser (name "Chooser", SVG icon, no
network), F-Droid (network, `market`/`fdroidrepos` schemes) and Blokish
(PNG icon, no network; starts and draws its board in the sandbox). The
escape test finds no escape; audio playback is now granted, as
planned. Not yet used by `--install-ask` or by app stores (`--install`
from `PackageInstaller`); they switch once the apps run fully inside
the sandbox (phase 3).

# App stores in their own Flatpak

A sandboxed app cannot run anything on the host, and Flatpak's way out
(`flatpak-spawn --host`, the `org.freedesktop.Flatpak` bus name) would
give it the whole user account back. Like Android's package installer,
a store therefore hands the APK to a service of the system, and the
system asks the user:

- **`atl-installer-service`** (`src/installer-service/`, installed to
  `libexec/android-translation-layer/`, D-Bus activated as
  `de.nexumia.atl.Installer` on the session bus, exits when idle).
  Methods: `Install(ah apks, a{sv} options) → s error`,
  `Uninstall(s package) → b`, `List() → a(sh)` (package and a read-only
  fd of its APK), `Launch(s package, s uri)`.
- **Who may call it**: `make-app-flatpak` adds
  `--talk-name=de.nexumia.atl.Installer` only for APKs that request
  `android.permission.REQUEST_INSTALL_PACKAGES` (F-Droid, Aurora); for
  other apps the D-Bus proxy hides the service. The caller's name on the
  page comes from its sandbox (`/proc/<pid>/root/.flatpak-info` and its
  launcher), never from a parameter.
- **Install**: the store passes open files, not paths, so it needs no
  host file access. The service copies them to
  `~/.cache/android_translation_layer/installer/` and runs
  `android-translation-layer --install-ask --install-flatpak
  --installer-name <store>`: the page says "<store> wants to install
  this app", every install and update is confirmed (no separate
  per-store grant, which would add nothing on top), and the store hears
  "installed" as soon as it is, since it shows its own Open button. Exit
  status 2 means the user said no, 3 refused.
- **Uninstall**: `--uninstall-flatpak <package>` asks on the host
  ("<store> wants to uninstall this app") and runs `flatpak uninstall
  --delete-data`. Inside the sandbox ATL no longer shows its own
  uninstall dialog, which an app could fake.
- **Inside the store** (ATL in a Flatpak, `/.flatpak-info` present):
  `PackageInstaller` sessions, `INSTALL_PACKAGE` intents, uninstalls,
  `getInstalledPackages` and starting another installed app go to the
  service. Installed apps appear in `_installed_apks_view_/` as links to
  the APK fds the service handed over (`/proc/self/fd/<n>`), refreshed
  at most every two seconds and after every change, so the rest of ATL
  reads them as outside a sandbox.

## Signer check

`android.atl.ApkSigners` verifies an APK like Android's package manager
before an install, with AOSP's verifier (`android.util.apk`, Android
11): APK Signature Scheme v3 (with key rotation: an update may come from
a key the app rotated to), else v2, else the v1 JAR signature of every
entry outside `META-INF/`. `PackageParser.collectCertificates` only
read the v1 signature of `AndroidManifest.xml`, so a changed
`classes.dex` passed and APKs signed with v2 or v3 only had no signer at
all. The install page, `--install-flatpak` itself (exit status 3) and
`PackageInstaller` refuse unsigned or changed APKs, updates from another
signer and downgrades.

Tried headless on 2026-10-30 with Blokish (signed v1 to v3) through
`--install-flatpak`: a file changed after signing, the signature removed
and a re-signing with another key were refused, the original installed.
Through the service (test client outside a sandbox): the page
compared against the installed Flatpak, Cancel answered `cancelled`,
Reinstall and a new install answered success, Uninstall asked and
removed the Flatpak with its data, List returned every installed app.

# Setting F-Droid up on the phone

```sh
android-translation-layer --install --sdk-int 26 -w 360 -h 720 org.fdroid.fdroid_2.0.2.apk
```

- `--sdk-int 26`: ATL gives every app its own `minSdkVersion` as
  `Build.VERSION.SDK_INT`, so F-Droid (minSdk 24) would believe the
  device is Android 7 and mark every app needing more as incompatible[^fdroid-compat].
  Apps installed from it still get their own minSdk. Coverage of the
  apps in F-Droid's index by their lowest minSdk (2026-10-28): 24 → 68 %,
  26 → 87 %, 29 → 94 %. Higher values crash F-Droid in ATL: at 29
  `Paint.setColor(long)` is missing, at 33
  `Activity.getOnBackInvokedDispatcher()`. At 26 it needed
  `Context.startForegroundService` (added). `--install` keeps
  `--sdk-int` in the launcher.
- `-w 360 -h 720`: ATL reports the launch size as the display size and
  never updates it, and the default is 960×540, so F-Droid shows its
  two-pane tablet layout squeezed into 360 px. Apps installed from
  F-Droid inherit the size.
- F-Droid builds its compatibility flags when it updates the index; after
  changing `--sdk-int` it needs a fresh index (or new data directory).

# Aurora Store

Aurora Store 4.8.4 from F-Droid (`com.aurora.store_76.apk`, signature
checked), installed like F-Droid with `--install --sdk-int 26 -w 360 -h
720`; apps installed from it get the same `--sdk-int`.

- **Account**: the anonymous login works.
- **Device profile**: Aurora sends Google Play a device profile built
  from what the device reports[^aurora-spoof]. ATL reports SDK 26 (with
  `--sdk-int 26`), no features, no GL extensions and no touch screen, so
  Google Play answers "App not supported" (`AppNotSupported(code=2)`)
  for apps like DB Navigator. Choose a profile in Aurora's settings
  (Spoof manager → device, e.g. **Google Pixel 9a**, arm64 only, SDK 35)
  and log in again. Headless the profile was written to the preferences
  instead (`DEVICE_SPOOF_ENABLED`, `DEVICE_SPOOF_PROPERTIES` = the
  profile's `.properties` from Aurora's APK as a JSON object; the APK's
  `res/*.properties` are the built-in profiles).
- **Deep links**: `--uri market://details?id=<package>` opens the details
  page, but the confirmation sheet for external links does not show in
  ATL; headless it was turned off with
  `PREFERENCE_CONFIRM_EXTERNAL_DEEPLINK=false`.
- **Download**: Aurora's download worker (WorkManager) needs a validated
  network and `StorageManager.getAllocatableBytes`; both are in ATL now.
  Files go to `~/.cache/android_translation_layer/com.aurora.store/Downloads/` (the app cache, per user since ATL used a shared `/tmp/atl_cache/` that only its first user could write).
- **Install**: needs `canRequestPackageInstalls()`, which ATL reports
  for apps declaring `REQUEST_INSTALL_PACKAGES`; the store's own
  install button is the confirmation.

# Gaps

- **wolfSSL**: F-Droid's hostname verifier sets the TLS session timeout,
  which walks all cached sessions; art_standalone's vendored wolfssljni
  throws `IllegalStateException: Object has been freed` for one closed
  earlier, and F-Droid crashes (seen after a mirror fallback). Upstream
  wolfssljni catches it[^wolfssljni-fix]. The one-line backport in
  `WolfSSLImplementSSLSession.setNativeTimeout` is in the `~/.local`
  build (rebuild just that jar with `make wolfssljni-hostdex` in
  art_standalone, 12 s) and in `art_standalone@nexumia` from
  nexumia-de/aports; Alpine's own `art_standalone` does not have it.
- Installed apps show a generic icon in F-Droid.
- An APK installed by hand with `--install` keeps its file name, e.g.
  `com.uravgcode.chooser_26.apk`, so F-Droid does not see it as
  installed. Open the file instead (`--install-ask`, see above), which
  names it `<package>.apk`.
- Each install compiles the APK from its staging path, so
  `~/.cache/art/` keeps an unused oat file per install (also for
  F-Droid's sessions and for the opened file).
- Install pre-approval (Android 14) and user-action confirmations are
  not offered; the store's own install button is the confirmation.

- **SDK level of installed apps**: an app installed from a store
  started with `--sdk-int` gets the same value. DB Navigator needs it:
  at its own minSdk it needs `android.graphics.fonts` (API 29), which
  ATL lacks.

# Testing headless

`tools/headless-shot.sh` starts its own session bus, so the portal and
the apps' D-Bus services run inside the test. Point
`XDG_DATA_HOME` and `ANDROID_APP_DATA_DIR` at a scratch directory so no
real launcher is touched. The details page opens directly with `--uri
https://f-droid.org/packages/<package>/` once the index exists. At
360×720: **Install** is at about `tap 180 446`, the ⋮ menu at `tap 331 78`, its **Uninstall** at `tap
96 348`, and the dialog's **Uninstall** at `tap 190 388`. The download
takes about a minute (`-w 60`). The launcher's **Uninstall** is what
`gdbus call --session --dest <package> --object-path /<package with / for .>
--method org.freedesktop.Application.ActivateAction uninstall '[]' '{}'`
does; its question's **Uninstall** is at about `tap 240 110`.

Aurora Store at 360×720 with `--uri 'market://details?id=<package>'`:
**Install** at about `tap 267 243`; DB Navigator's three APKs (47 MB) took about 90 s to download.
Aurora keeps a failed install as "Installing" in its database
(`databases/aurora_database`, table `download`); delete the row to try
again.
