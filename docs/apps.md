---
type: Reference
title: App status
description: Every Android app tried with ATL on the phone, its state, the cause of failures and the fixes.
tags: [apps, testing, compatibility]
generated: { by: claude-code/2.1.308, at: 2026-10-28T09:20:00Z }
---

# Apps

States: `works`, `partly` (starts, some features fail), `crashes`,
`out of scope` (needs Google Play Services or similar).

| App | Version | ATL commit | State | Cause / notes | Fix |
|---|---|---|---|---|---|
| [Chooser](https://f-droid.org/packages/com.uravgcode.chooser/) (`com.uravgcode.chooser`, F-Droid) | 1.5.0 (26) | `5071ca6b` | `partly` | Owner's app. Jetpack Compose. In the headless display at 360×720 with `tools/wl-pointer`: the five onboarding pages ([first](screenshots/chooser-2026-10-28.png)) with their animations, the check mark, the main screen with the mode and number buttons ([screenshot](screenshots/chooser-main-2026-10-28.png)), and one held finger with its animated circle ([screenshot](screenshots/chooser-hold-2026-10-28.png)) work. On the owner's phone with several fingers (2026-10-28) it aborted once it picked a finger, see fix 4; with fix 4 the owner confirmed choosing works on the phone (`~/.local` build). Not tested: the mode and number buttons and the settings. No upstream report. | 1. Crashed at startup with `NoClassDefFoundError: android.view.animation.BounceInterpolator`: `f01f23bb api-impl: add BounceInterpolator`. 2. The onboarding text began with `<ContentHandlerReplacementTag />`, as `Html.fromHtml` did not parse HTML: `7d31932e api-impl: Html: parse tags and entities in fromHtml`. 3. The finger's ring circled about two radii away from the finger, as arcs of 360° and more were drawn on the wrong ellipse: `5071ca6b Path: draw arcs of 360 degrees and more as a full ellipse`. 4. Aborted after picking a finger with `NoSuchMethodError: No virtual method vibrate(Landroid/os/VibrationEffect;)V in class Landroid/os/Vibrator` (owner's log; headless the vibrator cannot be opened and one finger never picks): `c42b09f6 api-impl: add vibrate(VibrationEffect), VibratorManager and CombinedVibration`. All upstream candidates. |
| [F-Droid](https://f-droid.org/) (`org.fdroid.fdroid`, f-droid.org) | 2.0.2 (2000052) | branch `claude/fdroid-install` | `partly` | App store for the other apps; see [installing apps](installing-apps.md). Headless at 360×720 with `--sdk-int 26 -w 360 -h 720` (2026-10-28): index update, details page, install of Chooser through the portal dialog, shown as installed, uninstall. Needs `--sdk-int 26` (otherwise apps above minSdk 24 count as incompatible) and `-w 360` (otherwise its tablet layout). Crashes on the phone's Alpine `art_standalone` with `IllegalStateException: Object has been freed` in wolfssljni, fixed only in the `~/.local` build. **Open** starts the installed app. Not tested: updates, the owner's phone. No upstream report. | 1. Installing did nothing, `PackageInstaller` and `IntentSender` were empty stubs: `api-impl: implement PackageInstaller sessions on top of --install`. 2. The portal never answered for apps with a literal `android:label`: `api-impl: Application: fall back to the literal label`. 3. `--sdk-int` was lost in the launcher: `main-executable: keep --sdk-int in the launcher`. 4. Index updates failed at SDK 26 with `NoSuchMethodError: startForegroundService`: `api-impl: add Context.startForegroundService`. 5. **Open** failed with `ClassNotFoundException`, ATL started the other app's activity in F-Droid's process: `api-impl: start activities of other installed apps through their launcher`. 6. Launchers made by `--install` never started (D-Bus activation: `Failed to execute program`, bare `env`): `main-executable: start launchers with an absolute /usr/bin/env`. 7. wolfssljni: backport needed in art_standalone (see [installing apps](installing-apps.md)). 8. On the phone, typing into the search field aborted it (`NoSuchMethodError: InputDevice.getKeyboardType`, Compose's text field asks on every key): `api-impl: InputDevice: add getKeyboardType`. 1–6 and 8 upstream candidates. |
| [Aurora Store](https://f-droid.org/packages/com.aurora.store/) (`com.aurora.store`, F-Droid) | 4.8.4 (76) | branch `claude/aurora-store` | `partly` | Google Play client; see [installing apps](installing-apps.md#aurora-store). Headless with `--sdk-int 26 -w 360 -h 720` (2026-10-28): anonymous login, details page through `--uri market://details?id=<package>`, download of base APK plus splits, install through the portal dialog, shown as installed with **Open** ([screenshot](screenshots/aurora-db-navigator-installed-2026-10-28.png)). Needs a device profile (Settings → Spoof manager, e.g. Google Pixel 9a): ATL's own profile (SDK 26, no features, no touch screen) makes Google Play answer "App not supported". The search button needs a second tap; the deep link confirmation sheet does not show (turned off with `PREFERENCE_CONFIRM_EXTERNAL_DEEPLINK=false`). Not tested: updates, the owner's phone. No upstream report. | 1. Downloads stopped right after starting: `api-impl: ConnectivityManager: report the network as validated`. 2. `NoSuchMethodError: StorageManager.getAllocatableBytes`: `api-impl: StorageManager: add getAllocatableBytes and allocateBytes`. 3. Refused to install without the permission: `api-impl: PackageManager: let apps that request package installs install`. 4. `split APKs are not supported`: `Install split APKs`. 5. Installed apps lost `--sdk-int`: `api-impl: PackageInstaller: pass the installer's --sdk-int on`. All upstream candidates. |
| DB Navigator (`de.hafas.android.db`, Google Play through Aurora Store) | 26.20.0 (240000860) | branch `claude/aurora-store`, art_standalone and bionic_translation patches from nexumia-de/aports | `partly` | Deutsche Bahn's journey planner; base APK plus `config.arm64_v8a` and `config.xxhdpi`. Headless with `--sdk-int 26 -w 360 -h 720` (2026-10-28), installed from Aurora Store and started from its launcher: cookie consent, onboarding ([screenshot](screenshots/db-navigator-welcome-2026-10-28.png)), a "rooted device" notice (Continue), main screen with the journey search form ([screenshot](screenshots/db-navigator-main-2026-10-28.png)), station picker. Typing into the station field did not arrive headless (`wlrctl keyboard`), so no search was run yet: **owner test on the phone needed**. Cosmetic: the From/To hints are missing and "Single journey" is clipped. Without `--sdk-int` it gets its minSdk and aborts with `NoClassDefFoundError: android.graphics.fonts.Font$Builder` (API 29 fonts, not in ATL; about a day's work). Uses Firebase and Akamai Bot Manager; push messages (FCM) will not work. No upstream report. | 1. `BootstrapMethodError ... Bootstrap method returned null` in `java.time.format.DateTimeFormatterBuilder.<clinit>`: libcore's own lambdas never worked in art_standalone; `lambda-metafactory.patch` in nexumia-de/aports `art_standalone/`. 2. Hermes (React Native) and Akamai failed to load: `__register_atfork` and `__strncat_chk` missing; `register-atfork-strncat-chk.patch` in nexumia-de/aports `bionic_translation/`. 3. Settings were lost (Tink: `IV has unexpected length`): `api-impl: keystore: AES/GCM cipher with 12 byte IVs for keystore keys`. 4. `NoSuchMethodError: Context.getAttributionSource` while installing: `api-impl: add Context.getAttributionSource`. All upstream candidates. |
| [Blokish](https://f-droid.org/packages/org.scoutant.blokish/) (`org.scoutant.blokish`, F-Droid) | 3.6 (24) | `01084c1c` | `partly` | Smoke test of the phone setup (2026-10-28), not a target app. Starts with the title bar and the four player colour bars at the bottom; the board (black area) is not drawn: GTK warns `Trying to measure WrapperWidget ... for width of 0, but it needs at least 252`. Not investigated further. No upstream report. [Screenshot](screenshots/blokish-2026-10-28.png). Started with `tools/headless-shot.sh -d 40 -- android-translation-layer org.scoutant.blokish_24.apk`. | Crashed at startup with `NoClassDefFoundError: android.view.animation.RotateAnimation`; fixed by `01084c1c api-impl: add RotateAnimation stub` (upstream candidate). |

# Needs owner

System packages to install and decisions only the owner can make. Each
entry: date, what, why. Remove an entry once it is done.

- 2026-10-28: Finish the apk repository in
  [nexumia-de/aports](https://github.com/nexumia-de/aports) (signing
  secret in the environment `apk-signing`, merge its pull request #1;
  steps in its `docs/repository.md`), install
  `android-translation-layer@nexumia` with `sudo`, and try Chooser with
  several fingers on the real touch screen; headless tests cannot do
  that. The owner runs every root command themselves.

- 2026-10-28: For DB Navigator from Aurora Store on the phone: merge
  nexumia-de/aports pull request #5 (`art_standalone` r101 and the new
  `bionic_translation`), let it publish, then
  `sudo apk add art_standalone@nexumia libandroidfw@nexumia bionic_translation@nexumia`
  and `sudo apk upgrade`; then try a journey search in DB Navigator
  (typing could not be tested headless).

# Notes

APKs come from F-Droid with their `.asc` signature, checked against
F-Droid's key `37D2C98789D8311948394E3E41E7044E1DBA2E89`
(`keys.openpgp.org`). For each app, first search upstream's issue tracker
(`https://gitlab.com/android_translation_layer/android_translation_layer/-/issues`)
and link known reports in the notes.
