---
type: Concept
title: Installing apps with F-Droid
description: How ATL installs, lists and uninstalls APKs for an app store like F-Droid, how to set F-Droid up on the phone, and what is still missing.
resource: ../src/api-impl/android/content/pm/PackageInstaller.java
tags: [fdroid, install, packageinstaller, launcher, portal]
generated: { by: claude-code/2.1.308, at: 2026-10-28T15:50:00Z }
sources:
  - id: fdroid-session
    resource: https://gitlab.com/fdroid/fdroidclient/-/blob/08f444713cefc97a8fa3cd3161a4484db6db54c8/app/src/main/kotlin/org/fdroid/install/SessionInstallManager.kt
    title: F-Droid client 2.0.2, SessionInstallManager (session installs)
  - id: fdroid-compat
    resource: https://gitlab.com/fdroid/fdroidclient/-/blob/08f444713cefc97a8fa3cd3161a4484db6db54c8/libs/index/src/androidMain/kotlin/org/fdroid/CompatibilityChecker.kt
    title: F-Droid client 2.0.2, CompatibilityChecker (SDK, ABI and feature checks)
  - id: wolfssljni-fix
    resource: https://github.com/wolfSSL/wolfssljni/blob/master/src/java/com/wolfssl/provider/jsse/WolfSSLImplementSSLSession.java
    title: wolfssljni master, WolfSSLImplementSSLSession.setNativeTimeout (catches a freed WOLFSSL)
---

# What works

The official F-Droid client 2.0.2 (`org.fdroid.fdroid`, from
`https://f-droid.org/F-Droid.apk`, signature checked) runs in ATL and
installs, lists and uninstalls apps. Tested headless on 2026-10-28 with
Chooser: install from the details page, the portal's "Create
Application" dialog, F-Droid then shows it as installed with **Open**
([screenshot](screenshots/fdroid-chooser-installed-2026-10-28.png)),
and **Uninstall** in the ⋮ menu asks first
([screenshot](screenshots/fdroid-uninstall-2026-10-28.png)) and removes
it.

# How it works

An app installed into ATL is an APK in
`$ANDROID_APP_DATA_DIR/_installed_apks_/` plus a launcher from the
desktop portal's `DynamicLauncher` (`android-translation-layer
--install`). Other apps find a package there as `<package>.apk`.

- **Install**: F-Droid 2.0 uses `PackageInstaller` sessions[^fdroid-session]:
  `createSession`, `openWrite`, `commit(IntentSender)`. The session is a
  directory in `_installer_sessions_/`. `commit` checks the APK (one
  APK, package name as announced, same signer as an installed version),
  names it `<package>.apk` and runs `android-translation-layer --install
  -w <width> -h <height> <apk>` with the installer's window size. The
  exit status becomes the session status: 0 success, 2 the user
  cancelled the portal dialog (`STATUS_FAILURE_ABORTED`), anything else
  a failure. Then `ACTION_PACKAGE_ADDED` is broadcast and the status
  goes back through the `IntentSender`.
- **List**: `getInstalledPackages` and `getInstalledApplications` return
  the calling app plus every `<package>.apk` it may see, which with
  `QUERY_ALL_PACKAGES` (F-Droid declares it) is all of them.
- **Uninstall**: `startActivityForResult` with `ACTION_DELETE` or
  `ACTION_UNINSTALL_PACKAGE` and a `package:` URI shows an "Uninstall
  …?" dialog. Confirming removes the APK, the app's data directory, its
  D-Bus service file and its launcher (portal
  `DynamicLauncher.Uninstall`), and broadcasts `ACTION_PACKAGE_REMOVED`.
  `PackageInstaller.uninstall` does the same without a dialog.

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

# Gaps

- **wolfSSL**: F-Droid's hostname verifier sets the TLS session timeout,
  which walks all cached sessions; art_standalone's vendored wolfssljni
  throws `IllegalStateException: Object has been freed` for one closed
  earlier, and F-Droid crashes (seen after a mirror fallback). Upstream
  wolfssljni catches it[^wolfssljni-fix]. The one-line backport in
  `WolfSSLImplementSSLSession.setNativeTimeout` is in the `~/.local`
  build (rebuild just that jar with `make wolfssljni-hostdex` in
  art_standalone, 12 s); Alpine's `art_standalone-0_git20261018-r0` on
  the phone does not have it.
- **Open** in F-Droid (launch another installed app) is not tested.
- Installed apps show a generic icon in F-Droid.
- Split APKs are refused (`STATUS_FAILURE_INCOMPATIBLE`); F-Droid only
  ships single APKs.
- An APK installed by hand with `--install` keeps its file name, e.g.
  `com.uravgcode.chooser_26.apk`, so F-Droid does not see it as
  installed. Install such APKs from F-Droid instead, or name the file
  `<package>.apk` first.
- Install pre-approval (Android 14) and user-action confirmations are
  not offered; the portal dialog is the confirmation.

# Testing headless

`tools/headless-shot.sh` starts its own session bus, so the portal and
its "Create Application" dialog run inside the test display. Point
`XDG_DATA_HOME` and `ANDROID_APP_DATA_DIR` at a scratch directory so no
real launcher is touched. The details page opens directly with `--uri
https://f-droid.org/packages/<package>/` once the index exists. At
360×720: **Install** is at about `tap 180 446`, the portal's **Create**
at `tap 312 23`, the ⋮ menu at `tap 331 78`, its **Uninstall** at `tap
96 348`, and the dialog's **Uninstall** at `tap 190 388`. The download
and the portal dialog take about a minute (`-w 60`).
