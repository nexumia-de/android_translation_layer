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
- **No app code while installing**: `--install`, `--install-ask` and
  `--install-flatpak` read name, icon, link types and signer from the
  APK with a plain `Application`; they do not create the app's own
  `Application`, its content providers or its main activity. Until
  2026-10-30 `--install` did, so app code ran inside the install; most likely why for
  Duolingo the install process ended without a launcher and Aurora Store
  stayed at "Installing".
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
`android-translation-layer --install-ask --install-flatpak -w 360 -h 720 %f`:
the app becomes its own sandboxed Flatpak (see below), with the internet
question and runtime permissions. Until 2026-11-04 the handler installed
without `--install-flatpak`, unsandboxed; apps installed that way, and by a
store that does not run in a Flatpak, stay unsandboxed until they are
moved (roadmap phase 6).

An app installed from a file gets `--sdk-int` like the apps a store
installs: 26, or the app's own `minSdkVersion` if that is higher (an
update keeps the launcher's value if it has one). Without it ATL reports
the app's `minSdkVersion`, and F-Droid installed from the owner's
Downloads folder did not start (2026-10-30: `You need to use a
Theme.AppCompat theme`, abort). The same default applies to
`--install-flatpak`. Not the newest Android version: apps then use the
APIs and behaviour of that version, much of which ATL does not
implement; 26 is the level F-Droid, Aurora Store and DB Navigator are
tested with.

`--install-ask` loads the APK like `--install`, without
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
  feedbackd. Internet is asked, never granted by default
  ([decision](hardening-roadmap.md#decision-record-where-permissions-are-shown-and-changed)):
  for an app with `android.permission.INTERNET` the install page asks
  "Allow internet?" with no preselected answer and Install disabled
  until it is answered ([page](screenshots/install-ask-internet-2026-11-03.png));
  `--network=yes|no` passes the answer to `make-app-flatpak -N`, which
  writes it as a user override (`flatpak override --user
  --share=network` or `--unshare=network`). Updates keep the answer and
  do not ask again; a new install always asks, even if a removal outside
  `--uninstall-flatpak` left an override behind, and `--uninstall-flatpak`
  resets the app's overrides. Apps without `INTERNET` are never asked
  and get `--unshare=network`, also when an update drops `INTERNET`.
  Bluetooth is asked the same way for an app with a Bluetooth permission
  ("Allow Bluetooth?", 2026-11-04): `--bluetooth=yes|no` passes the answer
  to `make-app-flatpak -B`, which writes `--system-talk-name=org.bluez` or
  `--system-no-talk-name=org.bluez` as a user override and stores it as the
  answer to `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN`; App info has one
  Bluetooth switch for both. Without the override the sandbox has no system
  bus, so BlueZ is unreachable.
  Everything else waits for portals and the broker (phases 3 and 4).
- App info (`android-translation-layer --app-info <package>`,
  [screenshot](screenshots/app-info-2026-11-04.png)): a window on the
  host for an app installed as its own Flatpak: name, package and
  version (`make-app-flatpak -V` writes it), the Internet switch (the
  Flatpak override; if the app runs, a banner offers a restart), the
  answers of the permissions without a portal (permission store table
  `de.nexumia.atl`; the microphone also switches the pulse socket),
  **More Permissions** (`gnome-control-center applications <id>`),
  **Clear Data** (asks, stops the app, removes `~/.var/app/<id>`) and
  **Uninstall** (the existing page). The launcher's **App Info** action
  (long press) reaches the app inside its sandbox (`DBusActivatable`,
  `ActivateAction("app-info")`; a D-Bus activated start for it does not
  start the app), which asks the permission service's `OpenAppInfo`
  to open its own App info on the host. Open: the "Android apps" page
  that lists apps. Headless check:
  `flatpak/app-info-test.sh`.
- "Android apps" page (`android-translation-layer --android-apps`,
  [screenshot](screenshots/android-apps-2026-11-04.png); opened from
  every app's App info, **All Android Apps**, since there is no third
  settings app in the grid): back navigation (saved in
  `~/.config/android_translation_layer/settings.ini`, which ATL on the
  host reads, and given to every app Flatpak as `ATL_NAVIGATION` by a
  user override; `make-app-flatpak` applies it at install), the push
  server (`atl-push-server get|set|reset`, default KDE's), the wake
  interval (shown; the system timer is changed by the owner until a
  polkit helper exists, see the wake decision), signature checks (always
  on), which app stores may install apps (the `de.nexumia.atl.Installer`
  talk name as an override) and the installed apps with a link to their
  App info. Headless check: `flatpak/android-apps-test.sh`.
- Runtime permissions without a portal (contacts, calendar, SMS, call
  log, phone state, background location, microphone, Bluetooth, body
  sensors, activity recognition, and ATL's own `atl.google-services`):
  every app may call `de.nexumia.atl.Permissions` (`Check`, `Request`;
  `src/installer-service/de.nexumia.atl.Permissions.xml`), served by its
  own process, `atl-permission-service`: the D-Bus proxy lets an app
  reach every name of a connection through its unique name, so sharing
  the installer service's connection would open it to all apps. The
  installer service also checks each caller itself: only callers whose
  sandbox may talk to `de.nexumia.atl.Installer` (app stores), or callers
  outside a sandbox. Both identify the caller through the broker's
  process fd (`GetConnectionCredentials`), so a reused pid cannot pose as
  it. It asks with the desktop's access
  dialog (Phosh's shell, else GTK's;
  [contacts](screenshots/permission-dialog-2026-11-03.png),
  [Google services](screenshots/google-services-dialog-2026-11-03.png)),
  stores the answer in the permission store table `de.nexumia.atl`,
  answers "no" without asking for what the APK does not request
  (`make-app-flatpak` writes the list to `/app/share/atl/permissions`)
  and stops asking after the second "no"; requests for the same
  question while its dialog is open wait for that one answer.
  Apps made for Android before 6 (target SDK or `SDK_INT` below 23,
  e.g. CoMaps at SDK 21) never ask: before their code runs, ATL asks
  the service's `Review` once, which shows one dialog of the app's
  dangerous permissions that the service decides, each off by default,
  an earlier "yes" ticked and an earlier final "no" left out
  ([screenshot](screenshots/legacy-review-2026-11-04.png)); the answers
  are stored like the others (owner, 2026-11-03; Android 10+ and
  GrapheneOS do the same). Location and camera are not part of it:
  their portals ask at first use. The service reviews only apps whose
  Flatpak says so (`make-app-flatpak -T` writes the target SDK; else the
  `--sdk-int` it runs with), and concurrent calls share one dialog.
  Headless check: `flatpak/legacy-review-test.sh`.
  Background location (owner, 2026-11-03; split with the location
  portal work, #76): the portal decides fine and coarse location, and
  its dialog covers use while the app has a window or a foreground
  service, so `requestPermissions` for `ACCESS_BACKGROUND_LOCATION`
  shows no second dialog: it reports granted unless the user said no or
  the portal has no grant (asked together with foreground location it
  is denied, as on Android 11). The question comes only at real
  background use (no window, no foreground service):
  `ATLPermissions.locationAllowedNow`, which the location code calls. Inside the app,
  `checkSelfPermission` and `requestPermissions` use it
  (`android.atl.ATLPermissions`); `AudioRecord` records only after a
  "yes" (per `AudioRecord` object; without it the object is
  `STATE_UNINITIALIZED`, as on Android). A `RECORD_AUDIO` "yes" also
  gives the app the normal pulse socket instead of the one without
  recording (Flatpak user override, from the app's next start). Headless check:
  `flatpak/permissions-test.sh`.
- Microphone: the normal pulse socket would let every app record
  (microphone and sink monitors, i.e. other apps' sound). Apps get
  instead only the socket of a second pipewire-pulse,
  `atl-pulse-norecord` (`flatpak/microphone/`: a user systemd unit and a
  drop-in for the system's `pipewire-pulse.conf`), which refuses every
  record stream of every client and lets no client load modules
  (a loopback into a pipe sink, RTP or a tunnel would record on the
  server side). The socket directory is mounted read-only, so an app
  cannot replace the shared socket. `make-app-flatpak` sets it up in the
  user's own configuration (no root) and gives the app
  `--filesystem=xdg-run/atl-pulse-norecord` and `PULSE_SERVER`; an app
  the user allows to record gets the normal socket as a user override
  (next start). Rules on client properties are not used: pulse clients
  can rewrite them (`PULSE_PROP`, `UPDATE_CLIENT_PROPLIST`), which made
  the first version (WirePlumber and pipewire-pulse rules on
  `pipewire.access.portal.app_id`) bypassable. Headless check with the
  forged-property cases: `flatpak/microphone/test.sh`.
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
  fd of its APK), `Launch(s package, s uri)`, `GetInstaller(s package)
  → s` (version 2, see [updates without asking](#updates-without-asking)).
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
  --installer-name <store> --installer-id <store package>`: the page says
  "<store> wants to install this app", every install and update is
  confirmed (no separate per-store grant, which would add nothing on
  top) except the updates below, and the store hears
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

## Updates without asking

Android 12 and later let the app store that installed an app (the
installer of record) update it without asking, if the store says so
with `PackageInstaller.SessionParams.setRequireUserAction(USER_ACTION_NOT_REQUIRED)`;
otherwise the system shows its install dialog. F-Droid 2.0.2 sets it on
every session at SDK 31 and later
(`SessionInstallManager.getSessionParams`), Aurora Store too. ATL does
the same, with a switch the user turns on per store:

- **Installer of record**: the service passes the caller's package
  (from its sandbox, like its name) as `--installer-id`, which
  `make-app-flatpak -I` writes to `/app/share/atl/installer` in the
  app's Flatpak, on every install and update. An app installed from a
  file has none. `PackageManager.getInstallerPackageName()` answers from
  it inside a Flatpak (`GetInstaller`, interface version 2); F-Droid
  uses it to tell its own apps from others.
- **The store asks**: `setRequireUserAction` is kept in the session.
  Stores run here with `--sdk-int 26`, below the level where F-Droid
  and Aurora set `USER_ACTION_NOT_REQUIRED`, and running them at 31
  would reach about 300 API gaps behind SDK checks (F-Droid crashed at
  start on `WindowManager.getCurrentWindowMetrics`; Compose uses
  `RenderNode` and `ImageDecoder` there). So the user's switch is the
  consent: every session that does not set `USER_ACTION_REQUIRED`
  becomes the option `unattended` of `Install`, which the service
  passes on as `--unattended` only for a caller in a sandbox. An older
  service ignores the option and asks.
- **The user allows it**: App info of an app that requests
  `REQUEST_INSTALL_PACKAGES` has **App Store → Update Apps Without
  Asking**, off by default. It is the entry `atl.update-without-asking`
  of the store's app id in the permission store table `de.nexumia.atl`,
  which apps cannot reach (the permission service only answers its own
  list of permissions).
- **The host decides** (`may_update_unattended` in `main.c`): without
  the page only if all hold, else the page is shown as before and the
  log says why (`install: asking, no update without asking: ...`):
  - an update to a higher version code, with the signer checked against
    the installed APK;
  - the installed app's installer of record is the calling store;
  - the store's switch is on;
  - the new APK requests no permission the installed one did not (so
    no new Flatpak permission and no new question; internet already
    answered);
  - its target SDK is not lower than the installed one's.
- **Afterwards** a notification "<App> updated" says which store
  updated it to which version, as Android does.

**F-Droid's automatic updates.** F-Droid 2.0.2 updates apps itself in
`AppUpdateWorker`, a WorkManager job every 24 hours (flex 60 min) on
Wi-Fi by default (setting `appAutoUpdates`: `OnlyWifi`, `Always`,
`Never`), and `RepoUpdateWorker` updates the index every 4 hours. In
ATL these run while F-Droid runs: WorkManager plans each job again at
every start, and an overdue one runs right away. ATL's `JobScheduler`
waits for the network a job requires (Wi-Fi only: an unmetered network,
from the portal's `NetworkMonitor`). With the switch on, a due update
job installs every update F-Droid lists without a page, and F-Droid
and ATL each show a notification. F-Droid only offered updates for
apps whose signer it can read: `PackageInfo.signatures` now comes from
the APK Signature Scheme v3 or v2 block too, not only v1 (Chooser from
F-Droid is signed with v2/v3 only, and F-Droid never listed its update).

End to end on 2026-11-04, headless, F-Droid as its own Flatpak with
`--sdk-int 26` on the engine built from this code, Chooser 1.4.11 from
f-droid.org installed with F-Droid as installer of record, the switch
on, F-Droid's update job made due (`last_enqueue_time` moved back in
its `androidx.work.workdb`): F-Droid showed the update and installed
Chooser 1.5.0 without a page.

Not yet: updates while the store is closed. A store started without a
window does not list its updates in time: F-Droid's `updateAll` takes
the list in memory (`UpdatesManager.updates`), which in a cold start
without its UI is filled only after an index change, and returns when
it is empty. Work in progress on branch `claude/store-background-updates`
(a `--background` start, a user timer `atl-store-updates.timer`, jobs
one after another).

Headless check: `flatpak/auto-update-test.sh` (fake store in bwrap,
the service and ATL from the build directory, a fake
`make-app-flatpak`, a notification server that records). It needs two
APKs of one app with one signer and rising version codes; Blokish with
its `versionCode` patched in the binary manifest and signed v1 with a
test key (`jarsigner`) served on 2026-11-04: 19 checks, all ok.

## Interface and versions

The interface is defined once, in
`src/installer-service/de.nexumia.atl.Installer.xml`, which the service
loads from its resources. The service comes with the host package
(aports) and ATL inside an app's Flatpak with the engine
(`flatpak.nexumia.de`); the two are updated separately, so a new engine
can meet an old service and the other way round. Therefore:

- the interface only grows: new methods, options and error texts raise
  the `Version` property; existing methods keep their signature and
  meaning; a change that cannot be made that way gets a new interface
  name (`de.nexumia.atl.Installer2`) next to the old one;
- ATL in the sandbox reads `Version` once before its first call
  (`SERVICE_MIN_VERSION` in
  `src/api-impl-jni/content/android_content_pm_PackageInstaller.c`) and
  fails with a message that names the cause: service not reachable (the
  app does not request `REQUEST_INSTALL_PACKAGES`, or the host package
  is older than the engine) or too old;
- version 1 is the interface as first released (#33), which had no
  `Version` property; a service without it counts as version 1;
  version 2 added the `Install` option `unattended` and `GetInstaller`.

Checked with a C harness on a private bus (2026-10-30): with the
current service, with a service as in #33 (no `Version`) and with none.

It stays in the ATL fork, in its own directory: the service runs the
host's ATL for the page and the signer check, its other half is ATL's
`PackageInstaller`, and ATL is its only user (Android likewise keeps
its package installer service in the framework). A separate repository
would not help with the version skew above and would need two pull
requests for every interface change; it is worth it once another
project uses the service.

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

End to end on 2026-10-30, headless, with the engine built in CI from
this code and F-Droid 2.0.2 as its own Flatpak: F-Droid listed the
installed app Flatpaks, installed Chooser through the service ("F-Droid
wants to install this app", then F-Droid showed Open), Open started
Chooser in its own sandbox, and Uninstall in F-Droid's menu asked on the
host and removed Chooser, which F-Droid noticed at once. DB Navigator,
which does not request `REQUEST_INSTALL_PACKAGES`, gets
`ServiceUnknown`. Open: in the sandbox F-Droid shows "No internet"
although it fetches its index and downloads: ATL's
`ConnectivityManager` asks GLib's network monitor, which inside a
Flatpak learns the state from the portal only after startup and ATL
does not pass that change on.

## Links into and out of the sandbox

- Out: ATL opens URLs with the OpenURI portal, which every Flatpak may
  use. Checked headless: Aurora's "Source code" link in its sandbox
  reached `org.freedesktop.portal.OpenURI.OpenURI` with its URL.
- In: generated apps are D-Bus activatable like ATL's launchers outside
  a sandbox (`DBusActivatable=true` and a service file inside the
  Flatpak, which Flatpak exports), so a link such as DB Navigator's
  login redirect `dbnav://dbnavigator.bahn.de/login?code=…` reaches the
  running app instead of starting a second one. Checked headless:
  `gio open` of that link reached the running sandboxed DB Navigator
  (`opening uri over DBus`, `LoginOverlayActivity`). A real login needs
  the owner's account.
- `make-app-flatpak` decides between install and update with `flatpak
  list`: Flatpak 1.18.4 aborts in `flatpak info` for apps from the local
  repository (exit status 134), which made every update look like a new
  install that Flatpak then skipped.

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
- Window size: without `-w`/`-h` ATL starts with the monitor's size in
  logical pixels and follows the window when the compositor resizes it
  (Phosh maximizes it): `Display`, `Configuration` (`screenWidthDp`,
  `screenHeightDp`, `smallestScreenWidthDp`, orientation), every
  `Resources`' `DisplayMetrics`, and `onConfigurationChanged` for the
  application, the activities and their views (2026-11-03). Before,
  the default was 960×540 and never updated, so F-Droid showed its
  two-pane tablet layout on the phone. With `-w`/`-h` the size is
  fixed at launch as before, and only then do apps installed from a
  store get `-w`/`-h` in their launcher.
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
- `JobScheduler` checks only the network of a job; idle, charging,
  battery and storage constraints are not checked, and a job runs only
  while its app runs.
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
