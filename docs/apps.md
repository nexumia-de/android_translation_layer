---
type: Reference
title: App status
description: Every Android app tried with ATL on the phone, its state, the cause of failures and the fixes.
tags: [apps, testing, compatibility]
generated: { by: claude-code/2.1.308, at: 2026-10-28T09:20:00Z }
---

# Apps

States: `works`, `partly` (starts, some features fail), `crashes`,
`out of scope` (needs Google Play Services or similar). What each app's
features do, and the evidence, is in the [app audit](app-audit.md). New apps
follow [bringing up an app](app-bring-up.md).

| App | Version | ATL commit | State | Cause / notes | Fix |
|---|---|---|---|---|---|
| [Chooser](https://f-droid.org/packages/com.uravgcode.chooser/) (`com.uravgcode.chooser`, F-Droid) | 1.5.0 (26) | `5071ca6b` | `partly` | Owner's app. Jetpack Compose. In the headless display at 360×720 with `tools/wl-pointer`: the five onboarding pages ([first](screenshots/chooser-2026-10-28.png)) with their animations, the check mark, the main screen with the mode and number buttons ([screenshot](screenshots/chooser-main-2026-10-28.png)), and one held finger with its animated circle ([screenshot](screenshots/chooser-hold-2026-10-28.png)) work. On the owner's phone with several fingers (2026-10-28) it aborted once it picked a finger, see fix 4; with fix 4 the owner confirmed choosing works on the phone (`~/.local` build). Not tested: the mode and number buttons and the settings. No upstream report. | 1. Crashed at startup with `NoClassDefFoundError: android.view.animation.BounceInterpolator`: `f01f23bb api-impl: add BounceInterpolator`. 2. The onboarding text began with `<ContentHandlerReplacementTag />`, as `Html.fromHtml` did not parse HTML: `7d31932e api-impl: Html: parse tags and entities in fromHtml`. 3. The finger's ring circled about two radii away from the finger, as arcs of 360° and more were drawn on the wrong ellipse: `5071ca6b Path: draw arcs of 360 degrees and more as a full ellipse`. 4. Aborted after picking a finger with `NoSuchMethodError: No virtual method vibrate(Landroid/os/VibrationEffect;)V in class Landroid/os/Vibrator` (owner's log; headless the vibrator cannot be opened and one finger never picks): `c42b09f6 api-impl: add vibrate(VibrationEffect), VibratorManager and CombinedVibration`. All upstream candidates. |
| [F-Droid](https://f-droid.org/) (`org.fdroid.fdroid`, f-droid.org) | 2.0.2 (2000052) | branch `claude/fdroid-install` | `partly` | App store for the other apps; see [installing apps](installing-apps.md). Headless at 360×720 with `--sdk-int 26 -w 360 -h 720` (2026-10-28): index update, details page, install of Chooser through the portal dialog, shown as installed, uninstall. Needs `--sdk-int 26` (otherwise apps above minSdk 24 count as incompatible) and `-w 360` (otherwise its tablet layout). Crashes on the phone's Alpine `art_standalone` with `IllegalStateException: Object has been freed` in wolfssljni, fixed only in the `~/.local` build. **Open** starts the installed app. Not tested: updates, the owner's phone. No upstream report. | 1. Installing did nothing, `PackageInstaller` and `IntentSender` were empty stubs: `api-impl: implement PackageInstaller sessions on top of --install`. 2. The portal never answered for apps with a literal `android:label`: `api-impl: Application: fall back to the literal label`. 3. `--sdk-int` was lost in the launcher: `main-executable: keep --sdk-int in the launcher`. 4. Index updates failed at SDK 26 with `NoSuchMethodError: startForegroundService`: `api-impl: add Context.startForegroundService`. 5. **Open** failed with `ClassNotFoundException`, ATL started the other app's activity in F-Droid's process: `api-impl: start activities of other installed apps through their launcher`. 6. Launchers made by `--install` never started (D-Bus activation: `Failed to execute program`, bare `env`): `main-executable: start launchers with an absolute /usr/bin/env`. 7. wolfssljni: backport needed in art_standalone (see [installing apps](installing-apps.md)). 8. On the phone, typing into the search field aborted it (`NoSuchMethodError: InputDevice.getKeyboardType`, Compose's text field asks on every key): `api-impl: InputDevice: add getKeyboardType`. 9. It aborted at start with `NoSuchFieldError: FeatureInfo.version` once `getSystemAvailableFeatures` reported features (2026-11-02, found while checking the upstream sync): `api-impl: FeatureInfo: add the version field`. 1–6, 8 and 9 upstream candidates. |
| [Aurora Store](https://f-droid.org/packages/com.aurora.store/) (`com.aurora.store`, F-Droid) | 4.8.4 (76) | branch `claude/aurora-store` | `partly` | Google Play client; see [installing apps](installing-apps.md#aurora-store). Headless with `--sdk-int 26 -w 360 -h 720` (2026-10-28): anonymous login, details page through `--uri market://details?id=<package>`, download of base APK plus splits, install through the portal dialog, shown as installed with **Open** ([screenshot](screenshots/aurora-db-navigator-installed-2026-10-28.png)). Needs a device profile (Settings → Spoof manager, e.g. Google Pixel 9a): ATL's own profile (SDK 26, no features, no touch screen) makes Google Play answer "App not supported". The search button needs a second tap; the deep link confirmation sheet does not show (turned off with `PREFERENCE_CONFIRM_EXTERNAL_DEEPLINK=false`). Not tested: updates, the owner's phone. No upstream report. | 1. Downloads stopped right after starting: `api-impl: ConnectivityManager: report the network as validated`. 2. `NoSuchMethodError: StorageManager.getAllocatableBytes`: `api-impl: StorageManager: add getAllocatableBytes and allocateBytes`. 3. Refused to install without the permission: `api-impl: PackageManager: let apps that request package installs install`. 4. `split APKs are not supported`: `Install split APKs`. 5. Installed apps lost `--sdk-int`: `api-impl: PackageInstaller: pass the installer's --sdk-int on`. 6. On the phone its menu (gear icon, with the Spoof manager) was empty and every dialog had a title bar and the accent colour around it: Compose clips its sheet to a path outline, which RenderNode clipped to an empty rectangle; dialogs are now layers in the app window instead of windows phoc maximizes (`dialogs as layers, outline clipping` commits). 7. It exited (status 1) when a dialog closed, e.g. after onboarding: ViewTreeObserver had no frame clock for a dialog and treated that as fatal. 8. Settings → Installer: `NoSuchMethodError: DevicePolicyManager.isDeviceOwnerApp`. 9. On the phone after reinstalling it by opening its APK file (no `--sdk-int`, so its minSdk 21) Install showed "Permission required → Installer permission": `api-impl: Settings: report install_non_market_apps as allowed`; the install page now also keeps the launcher's `--sdk-int` on updates. 10. On the phone (2026-10-30) no app icons: Coil's disk cache is `java.io.tmpdir/coil3_disk_cache`, `/tmp` in ATL, and that directory belonged to another user: `api-impl: set java.io.tmpdir to the app's cache directory`. All upstream candidates. |
| DB Navigator (`de.hafas.android.db`, Google Play through Aurora Store) | 26.20.0 (240000860) | branch `claude/aurora-store`, art_standalone and bionic_translation patches from nexumia-de/aports | `partly` | Deutsche Bahn's journey planner; base APK plus `config.arm64_v8a` and `config.xxhdpi`. Headless with `--sdk-int 26 -w 360 -h 720` (2026-10-28), installed from Aurora Store and started from its launcher: cookie consent, onboarding ([screenshot](screenshots/db-navigator-welcome-2026-10-28.png)), a "rooted device" notice (Continue), main screen with the journey search form ([screenshot](screenshots/db-navigator-main-2026-10-28.png)), station picker. Typing into the station field did not arrive headless (`wlrctl keyboard`), so no search was run yet: **owner test on the phone needed**. Search works on the phone (owner, 2026-10-29). Its launcher also registers `x-scheme-handler/mailto` (the app declares it). Without `--sdk-int` it gets its minSdk and aborts with `NoClassDefFoundError: android.graphics.fonts.Font$Builder` (API 29 fonts, not in ATL; about a day's work). Uses Firebase and Akamai Bot Manager; push messages (FCM) will not work. No upstream report. | 1. `BootstrapMethodError ... Bootstrap method returned null` in `java.time.format.DateTimeFormatterBuilder.<clinit>`: libcore's own lambdas never worked in art_standalone; `lambda-metafactory.patch` in nexumia-de/aports `art_standalone/`. 2. Hermes (React Native) and Akamai failed to load: `__register_atfork` and `__strncat_chk` missing; `register-atfork-strncat-chk.patch` in nexumia-de/aports `bionic_translation/`. 3. Settings were lost (Tink: `IV has unexpected length`): `api-impl: keystore: AES/GCM cipher with 12 byte IVs for keystore keys`. 4. `NoSuchMethodError: Context.getAttributionSource` while installing: `api-impl: add Context.getAttributionSource`. 5. On the phone (2026-10-29) **Log in** did nothing: its login is the implicit action `de.hafas.android.db.START_LOGIN`, which `startActivityForResult` did not resolve to the app's own activity: `api-impl: Activity: start the app's own activity for an implicit intent with a result`; headless it now opens DB's login page in the browser (the return through `dbnav://` needs an account, owner test). 6. In dark mode white text on light backgrounds ("No trips exist", Log in): AppCompat's configuration context with `uiMode` -1 switched the AssetManager all Resources share to neither day nor night, while Compose drew dark colours: `api-impl: ATLLoadedApp: own AssetManager for Resources with another configuration`; now dark throughout ([screenshot](screenshots/db-navigator-dark-2026-10-29.png)). 7. On the phone (2026-10-29) the login came back from the browser but the app stayed logged out: the redirect `dbnav://dbnavigator.bahn.de/login?code=…` started a new `MainActivity` instead of reaching the open `LoginOverlayActivity` (singleTask): `api-impl: IntentFilter: match the host and path of data URIs` and `api-impl: open a URI in the best matching activity, reuse an open singleTask one`; headless a redirect sent through D-Bus `Open` now reaches `LoginOverlayActivity.onNewIntent` (a real login needs the owner's account). 8. The "Please wait" box during login was a few letters wide: Dialog ignored its theme (`DBFullScreenDialog`, not floating) and wrapped its content: `api-impl: Dialog: use the dialog theme, non-floating dialogs fill the window`. 9. On the phone (2026-10-30) the login came back to the open `LoginOverlayActivity`, then hung at "The process is taking longer than usual": ATL delivered the redirect with `onNewIntent` only, without the `onPause`/`onResume` around it that Android guarantees, and the app reads the redirect in `onResume`: `Activity: pause and resume an open activity around onNewIntent` (checked headless with F-Droid's `market://` link into its open main screen; the real login is an owner test). 9. Tickets and BahnCards were an empty bar (owner, 2026-10-30): the app shows them as HTML (`ticketHtml`, `bildSichtHtml`) in a `WebView` through `loadData(..., "base64")` with JavaScript off, and ATL stubbed WebView unless `ATL_UGLY_ENABLE_WEBVIEW` was set: `api-impl: WebView: start WebKit with the first page, wrap_content is the page height`. Tested with a small activity doing the same (base64 UTF-8 HTML, graphic, height, scripts not run); a real ticket needs the owner's account. 10. The cookie screen stayed open after the click and was gone after a restart (owner, 2026-10-30): the consent was saved, then `Intent.makeRestartActivityTask` threw `NoSuchMethodError` and the app swallowed it: `api-impl: Intent: add makeMainActivity and makeRestartActivityTask`; now the welcome screen follows. 11. The Copy popup of Share trip was green under phoc, it was the last GtkAlertDialog window: `api-impl: share through a dialog layer`. All upstream candidates. Still open: behind the login box the screen is black, ATL shows only the top activity, also when it is translucent (`TranslucentTheme`); the box's text is left aligned instead of centred; 12. Text of `TextView`s was 1.33 times too big (pango size in points, Android gives pixels), `Button`s ignored text size, `drawableStart`, gravity and `minHeight`, `TextView` ignored gravity, hints and `maxLines`, disabled views reported state_enabled, and a closed `Spinner` used the popup's row and background: fixed in the look pull request, compared with the owner's Android screenshots of 2026-10-30. 13. The app's own fonts were ignored because `Typeface`, `FontFamily` and `Paint.setTypeface` were stubs: `api-impl: Typeface: load app fonts through fontconfig`. 14. Rows like Date stacked label and value: ATL never set `smallestScreenWidthDp`, so `layout-sw350dp` was not chosen: `api-impl: set smallestScreenWidthDp`. Still open: the closed spinner still wraps "Single journey" and clips it, cause not found; the text is not narrower than the room it gets. 15. On the phone (2026-11-03) Google's error "won't run without Google Play services": since the GMS stub became opt-in the client library's location and messaging connections failed with `SERVICE_INVALID`; DB is now in `GMS_STUB_APPS` (`api-impl: ATLToggles: fake GMS for DB Navigator by default`). |
| Post & DHL (`de.dhl.paket`, Google Play through Aurora Store) | 10.1.0.44 (921502244) | branch `claude/dhl-ui` | `partly` | Needs `--sdk-int 26` and Aurora's Pixel 9a profile (minSdk 32). Starts; tracking and Frankieren (WebView) look like Android; app font, yellow bottom navigation and centred Compose text since 2026-10-30. Open: selected tab not red and its icon not the selected one, Compose cards on Packstation only 200 px wide, Mehr rows centred without their arrow and an empty white login button, Standorte list empty, onboarding pictures too small, memory ran full once on the phone. | Fonts from temporary files, text appearances and font resources; gradient fills; stretched backgrounds; text alignment |
| Duolingo (`com.duolingo`, Google Play through Aurora Store) | 6.103.6 (2488), minSdk 32 | branch `claude/duolingo-sign-in-compose` | `partly` | Base APK plus `config.arm64_v8a` and `config.xxhdpi`, run with `--sdk-int 26`. Installs from Aurora Store on the phone since `--install` no longer runs app code (2026-10-31); Google Play answers "App not supported" to the agent's test Aurora, so the APK came from the owner's download. Headless (2026-10-31) it starts to its welcome screen with the owl, "Learn for free. Forever.", GET STARTED and I ALREADY HAVE AN ACCOUNT ([screenshot](screenshots/duolingo-welcome-2026-10-31.png)). The two buttons are too flat, their text is clipped and has the wrong colours. I ALREADY HAVE AN ACCOUNT opens the sign-in screen ([screenshot](screenshots/duolingo-sign-in-2026-10-31.png)); a real sign-in needs the owner's account and shows whether Play Integrity is required. Taps need a first tap on the window to focus it. Play Integrity fails (no Play Store); if the app needs it, Duolingo is out of scope. Firebase Installations answers 403 and Google Play services' Blockstore is missing; all are caught. No upstream report. | 1. Aurora stayed at "Installing", no launcher: `--install` ran the app's Application, providers and main activity: `main: do not run app code in --install`. 2. `NoSuchMethodError: Context.bindService(Intent, int, Executor, ServiceConnection)`: `api-impl: add Context.bindService with an Executor`. 3. `NotificationManager.getNotificationDelegate`, `JobInfo.Builder.setImportantWhileForeground`, `JobParameters.getNetwork`, `UsageStatsManager.getAppStandbyBucket`, `NetworkCapabilities.getLinkDownstreamBandwidthKbps`, `PreferenceManager.getDefaultSharedPreferencesName`: `api-impl: add methods Duolingo calls at start`. 4. `ArrayIndexOutOfBoundsException` in Mixpanel: `api-impl: DisplayManager.getDisplays: return the default display`. 5. "You need to use a Theme.AppCompat theme": the app's language context has its own AssetManager and `Theme.setTo` between two AssetManagers kept only the framework's entries: `api-impl: Resources.Theme.setTo: copy themes between AssetManagers`. 6. `LayoutInflater.createView` and `onCreateView` with a context (API 29): `api-impl: LayoutInflater: add createView and onCreateView with a context`. 7. WorkManager ignored all work "in non-main process": `ApplicationInfo.processName` stayed null without `android:process`: `api-impl: PackageParser: default the process name to the package name`. 8. Nothing that tracks activities saw any, `registerActivityLifecycleCallbacks` was a stub, so androidx's `ProcessLifecycleOwner` never put the app in the foreground and Duolingo's foreground startup tasks never ran: `api-impl: Application: call ActivityLifecycleCallbacks`; below API 29 `ProcessLifecycleOwner` also needs its `ReportFragment` by tag (Aurora crashed without it): `api-impl: FragmentManager: find fragments by tag`. 9. Network callbacks got no `onAvailable` at registration, default network callbacks none at all, and `NetworkCallback`/`NetworkRequest.Builder` were inner classes apps could not create: `api-impl: ConnectivityManager: report the network when a callback is registered`. 10. Then `NetworkRequest.Builder.removeCapability`, `Context.registerReceiver` with flags, `ConnectivityManager.getRestrictBackgroundStatus`, `AppOpsManager.checkOpNoThrow`, `ShortcutManager.getPinnedShortcuts`, `Context.getOpPackageName`, `StorageStatsManager` and `StorageManager.getStorageVolumes`: `api-impl: add more methods Duolingo calls at start`. 11. It still stayed on its empty splash screen with all threads idle: from API 29 androidx drives every activity's `Lifecycle` through `Activity.registerActivityLifecycleCallbacks` and its pre and post events, which was a stub, so the launch screen never got STARTED and never collected its route (found with ART method tracing, see [development environment](dev-environment.md)): `api-impl: Activity: call its own lifecycle callbacks, with the API 29 pre and post events`. 12. Then `Window.addOnFrameMetricsAvailableListener`: `api-impl: Window: accept frame metrics listeners`. 13. Its text views build fonts with the API 29 `android.graphics.fonts` classes (`Font$Builder` was missing): `api-impl: add android.graphics.fonts.Font, FontFamily and Typeface.CustomFallbackBuilder`. 14. The sign-in screen crashed on `TypedValue.isColorType`, `View.setAutofillHints` and `WindowInsets.inset`. Fixed together with the other framework methods Duolingo's own code calls, from a static list of missing references: `api-impl: WindowInsets: add the remaining inset getters and inset()`, `api-impl: add methods Duolingo's own code calls`. 15. GET STARTED aborted on `Vibrator.hasAmplitudeControl`, then `MediaPlayer.setDataSource(AssetFileDescriptor)` and `AudioManager.isHapticPlaybackSupported`; it now opens the coach screen ([screenshot](screenshots/duolingo-coach-2026-10-31.png)): `api-impl: Vibrator: add capability queries and VibrationEffect.Composition`, `api-impl: MediaPlayer: pause, stop, seek, loop, volume, release, prepared callback`. 16. The password field showed the text: `api-impl: EditText: honour inputType and the password transformation`. 17. On the phone (2026-10-31) SIGN IN, FACEBOOK and GOOGLE aborted with `NoSuchMethodError: Paint.setColor(long)`, followed by `getContentCaptureSession` and `getUniqueDrawingId`: the toast of the sign-in screen is the first Compose view, and R8 removed Compose's SDK checks because of minSdk 32, so the API 29 paths run under any `--sdk-int`: `api-impl: add the API 29 graphics and view methods Compose calls`. 18. SIGN IN then said "We're having trouble connecting to Duolingo" without sending a request: the app counts a network without `NET_CAPABILITY_NOT_SUSPENDED` as offline: `api-impl: ConnectivityManager: report the capabilities of an ordinary network`. Random data now gets the server's "Wrong username or password" ([screenshot](screenshots/duolingo-wrong-password-2026-10-31.png)), checked headless with `tools/atl-fill-text.c`. A correct login is an owner test. `RenderEffect` (blur) does nothing yet. All upstream candidates. |
| [Gadgetbridge](https://f-droid.org/packages/nodomain.freeyourgadget.gadgetbridge/) (`nodomain.freeyourgadget.gadgetbridge`, F-Droid) | 0.95.1 (255) | branch `claude/gadgetbridge-notifications` | `partly` | Companion app for watches and bands, no Google services. ATL had no Bluetooth; [Bluetooth on BlueZ](bluetooth.md) is new. Headless (2026-11-02): welcome pages and device discovery, which found the owner's Amazfit Balance as a supported, already bonded watch ([screenshot](screenshots/gadgetbridge-discovery-2026-11-02.png)). On the owner's first phone test (2026-11-02) the pairing page hung: an activity started from `onCreate` was shown below its caller, and a page closed with back never sent `onActivityResult`; both fixed. Headless with a dummy auth key, Pair now connects to the real watch over GATT and runs Zepp OS authentication, which fails only on the dummy key. Syncing with the real key is an owner test (below). Phone notifications reach its listener when ATL runs with `ATL_ENABLE_NOTIFICATION_LISTENER=1` (headless with a test notification, 2026-11-02). F-Droid marked it incompatible because `getSystemAvailableFeatures` was empty, and its install page called it unsigned because the v3 signer (API 24+) was checked against the app's emulated API 23; both fixed. Missing: GATT server and RFCOMM for other device families ([audit](app-audit.md#gadgetbridge)) | `XmlBlock.nativeGetText` (aborted the first screen), `NotificationManager` do-not-disturb API, Bluetooth permissions granted when BlueZ has an adapter, `Intent.getShortExtra` |
| [CoMaps](https://f-droid.org/packages/app.comaps.fdroid/) (`app.comaps.fdroid`, F-Droid) | 2026.10.05-19 (26100519) | PR #73, #70 (voice) | `partly` | Offline OSM maps and navigation, native C++ renderer on OpenGL ES 3 (`liborganicmaps.so`). Headless (2026-11-03): downloads the world overview map; the map renders, zooms with the buttons and pans ([screenshot](screenshots/comaps-map-2026-11-03.png)). Fixed for that: `getSurfaceFrame` stub (400x400), offscreen EGL buffers not following resizes, `eglGetCurrentSurface` returning no surface for the drape upload thread's pbuffer context. Without location permission it stays on the splash screen: at its SDK_INT 21 androidx answers the request from `checkSelfPermission` without calling ATL (on Android 5 declared permissions are granted at install); decision open, see #79. Location through the location portal (#61) with `ATL_UGLY_ENABLE_LOCATION=1`. Voice guidance through speech-dispatcher (`spd-say`), not installed on the phone. Compass: the magnetometer (qmc6308) is readable but uncalibrated (about 370 µT instead of about 50), so no rotation vector is offered; needs hard-iron calibration, about 1-2 days. | `LocationManager` per-listener registrations, providers, last fix; `TextToSpeech` through speech-dispatcher; `FLAG_KEEP_SCREEN_ON`; asset `mark`/`reset` (its root certificate, then all downloads failed); `SurfaceView` 4-argument constructor (`getHolder()` was null); `SurfaceHolder.getSurfaceFrame`; offscreen EGL follows resizes; methods from its static list |
| [HeliBoard](https://f-droid.org/packages/helium314.keyboard/) (`helium314.keyboard`, F-Droid) | 4.2 (4201) | branch `claude/heliboard` | `partly` | Keyboard app, goal: the keyboard for all of Phosh ([feasibility](android-keyboard.md), size M). Headless with `--sdk-int 26 -w 360 -h 720` (2026-11-03): settings (welcome wizard skipped, main list, Languages & Layouts; [screenshot](screenshots/heliboard-settings-2026-11-03.png)). As OSK with `-l android/atl/ATLKeyboardViewer -e kb_class=helium314.keyboard.latin.LatinIME` under headless phoc: the English dictionary loads (native `libjni_latinime`) and the input view shows as a layer-shell window over a text field, but full screen, on the emoji page and about twice too big ([screenshot](screenshots/heliboard-osk-first-2026-11-03.png)); typing not reached yet. Open in settings: the top-bar back arrow does nothing. Static gap list: [helium314.keyboard.gaps.tsv](app-audit/helium314.keyboard.gaps.tsv). No upstream report. | 1. Without `--sdk-int` ATL reports minSdk 21, the app reads the pre-API-24 `method.xml` without `languageTag` and aborts; use `--sdk-int 26`. 2. `InputMethodSubtype` and `InputMethodInfo` were empty stubs: `api-impl: InputMethodSubtype, InputMethodInfo and the app's input methods in InputMethodManager`. 3. `api-impl: add android.text.InputType`. 4. `api-impl: KeyguardManager: add isDeviceLocked`. 5. The wizard waited for HeliBoard to be chosen: `api-impl: Settings.Secure: report the app's own input method as default and enabled`. 6. The OSK host (upstream `ATLKeyboardViewer`) loaded the IME with the wrong class loader, without a context and without a default `InputMethodImpl`; HeliBoard's `<rotate>` icons were not inflated; the fork's in-window dialogs broke the keyboard's own window; surrounding text and UTF-8 byte counts for deletions: WIP commits on the branch. 1–5 upstream candidates. |
| [GPSLogger](https://f-droid.org/packages/com.mendhak.gpslogger/) (`com.mendhak.gpslogger`, F-Droid) | 138 (138) | branch `claude/location-api` + `claude/battery-changed` | `partly` | Location tracker for the owner's Dawarich server (goal: measurements for the native Vestigia plan). Headless with `ATL_UGLY_ENABLE_LOCATION=1` and a fake location portal replaying a synthetic GPX: aborted at the first fix (`Location.setExtras` missing). After the `LocationManager` rewrite it logs every 10 s for 3 min (18 of 18 fixes, GPX file and its built-in Dawarich sender, Overland batches to a local test receiver). Open: background with the window closed, suspend (it re-arms `AlarmManager` `ELAPSED_REALTIME_WAKEUP` between fixes), Flatpak and portal permission. | `location: implement LocationManager registrations on the location portal`; Dawarich upload needs `os: sticky ACTION_BATTERY_CHANGED` (`getBatteryInfo` NPE) |
| [Blokish](https://f-droid.org/packages/org.scoutant.blokish/) (`org.scoutant.blokish`, F-Droid) | 3.6 (24) | `01084c1c` | `partly` | Smoke test of the phone setup (2026-10-28), not a target app. Starts with the title bar and the four player colour bars at the bottom; the board (black area) is not drawn: GTK warns `Trying to measure WrapperWidget ... for width of 0, but it needs at least 252`. Not investigated further. No upstream report. [Screenshot](screenshots/blokish-2026-10-28.png). Started with `tools/headless-shot.sh -d 40 -- android-translation-layer org.scoutant.blokish_24.apk`. | Crashed at startup with `NoClassDefFoundError: android.view.animation.RotateAnimation`; fixed by `01084c1c api-impl: add RotateAnimation stub` (upstream candidate). |

# Needs owner

System packages to install and decisions only the owner can make. Each
entry: date, what, why. Remove an entry once it is done.

- 2026-11-03: CoMaps after PR #73 (and #61 for location) is in a
  phone build: as `user`, `android-translation-layer
  ~/work/apks/app.comaps.fdroid/app.comaps.fdroid_26100519.apk
  > /tmp/comaps.log 2>&1`, allow the world map download, then report
  whether the map fills the screen without a black box and whether
  pinch/drag move it. Voice guidance (#70) needs
  `sudo apk add speech-dispatcher espeak-ng`, then turn on voice in a
  route.

- 2026-11-03: Window size from the monitor (no `-w`/`-h`). After the
  fix is in the aports package: start F-Droid from a launcher without
  `-w`/`-h` (or run `android-translation-layer --sdk-int 26
  <fdroid.apk>` as `user`); it should show the one-column phone layout
  across the full width, also after rotating.
- 2026-11-03: DB Navigator showed "won't run without Google Play
  services" (GMS stub off by default since #50). After the fix is in
  the aports package: start DB Navigator from the app grid, check that
  the error is gone and the journey search and tickets still work.
- 2026-11-03: [Permissions decision record](hardening-roadmap.md#decision-record-where-permissions-are-shown-and-changed):
  microphone decided (hard, through a pulse server without recording,
  ATL's check first). Phone test once #68 is in the package: as `user`,
  `systemctl --user status atl-pulse-norecord` (active) and, while an
  ATL app plays sound, `pactl -s /run/user/$(id -u)/atl-pulse-norecord/native list short clients`.

- 2026-11-03: Google Play services' error text was bare text in DB
  Navigator's top left corner. After the fix (#95) is in the aports
  package: if a "trouble with Google Play services" message shows
  again, it is a centred dialog.
- 2026-10-28: Finish the apk repository in
  [nexumia-de/aports](https://github.com/nexumia-de/aports) (signing
  secret in the environment `apk-signing`, merge its pull request #1;
  steps in its `docs/repository.md`), install
  `android-translation-layer@nexumia` with `sudo`, and try Chooser with
  several fingers on the real touch screen; headless tests cannot do
  that. The owner runs every root command themselves.
- 2026-10-29: [Hardening roadmap](hardening-roadmap.md), phase 0:
  confirm the threat model and the proposed
  [one Flatpak per app](decisions/app-sandbox.md), and send the output
  of, as `user`: `flatpak --version; flatpak remotes --user;
  flatpak list --runtime; apk info -v | grep portal`. Done by the agent
  on the phone (see [dev-environment.md](dev-environment.md#app-flatpaks));
  still open: the sign-off, `sudo apk add flatpak-builder` for the engine
  build (phase 1), and the phone on the charger while it builds.

- 2026-10-28: For DB Navigator from Aurora Store on the phone: merge
  nexumia-de/aports pull request #6 (ATL with the Aurora fixes; #5 with
  `art_standalone` r101 and the new `bionic_translation` is merged and
  published), let it publish, then
  `sudo apk add art_standalone@nexumia libandroidfw@nexumia bionic_translation@nexumia`
  and `sudo apk upgrade`; then try a journey search in DB Navigator
  (typing could not be tested headless).

- 2026-10-29: To open APK files with ATL instead of Waydroid: after
  the ATL package with `--install-ask` is in the apk repository (its
  aports pull request adds `libadwaita-dev` to `makedepends`),
  `sudo apk upgrade`, then as `user`
  `xdg-mime default com.gitlab.android_translation_layer.install.desktop application/vnd.android.package-archive`,
  and open an APK from the file manager
  ([installing apps](installing-apps.md#opening-an-apk-file)).

- 2026-11-02: Gadgetbridge with the Amazfit Balance, after the aports
  pin that includes ATL pull request #54 is published: `sudo apk upgrade`,
  then as `user` (close any other app that talks to the watch first; in
  Phosh's Bluetooth settings, "Forget" the watch if it is listed):

  ```sh
  ATL_ENABLE_NOTIFICATION_LISTENER=1 android-translation-layer \
    /home/claude/work/apks/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk \
    2>&1 | tee /tmp/gadgetbridge.log
  ```

  If the watch is already in Gadgetbridge's device list, remove it there
  first. Then "+", Start discovery, long-press "Amazfit Balance", enter
  the auth key under its settings (Gadgetbridge's wiki page "Huami Server
  pairing"), tap it, go back from "About you", tap Pair, and in ATL's
  "Bluetooth pairing request" dialog check that the watch shows the same
  code and tap Pair. Report whether it shows "Connected" and the battery,
  whether the sync button (circular arrow) fetches steps, and whether it
  reconnects by itself after the watch was out of range or Bluetooth was
  off and on, and after the phone was suspended. Then let another app
  notify (`notify-send Test Hello`): it should appear on the watch. The
  log stays in `/tmp/gadgetbridge.log`.

- 2026-11-03: Gadgetbridge notification forwarding (after the aports pin
  includes the `claude/gb-notifications` pull request), as `user` with
  the command above and the watch connected: lock the phone, then send a
  Chatty message to the phone and run `notify-send Test Hello`. Both
  should appear on the watch with the sending app's name (Chats,
  notify-send), Gadgetbridge's own "Connected" notification should not.
  Dismiss the Chatty notification on the phone: report whether it also
  disappears on the watch. The log stays in `/tmp/gadgetbridge.log`.

- 2026-11-03: Location tracking for Dawarich (GPSLogger; pull requests
  #61 location, #62 battery, #63 foreground services, wake locks next).
  Three decisions, each with the agent's recommendation:
  1. Background location (`ACCESS_BACKGROUND_LOCATION`): an app gets
     locations while it has a window or a foreground service (shown by
     its ongoing notification), as on Android 10+; without either only
     if you allow it per app (default no). Recommended: yes, as proposed
     in #58.
  2. Suspend while tracking: decided 2026-11-03, see
     [system wake windows](decisions/wake-windows.md). Steps for the owner:
     - Find what keeps the phone from suspending (it had not suspended in
       2 days; gnome-session holds a sleep inhibitor for an app), as
       `user`: `gnome-session-inhibit --list` and send the output.
     - Once ATL's package ships `atl-wake.timer`:
       `sudo systemctl enable --now atl-wake.timer`. Interval until the
       settings page has it (example 5 minutes):
       `sudo systemctl edit atl-wake.timer`, add
       `[Timer]` / `OnCalendar=` / `OnCalendar=*:0/5`.
  3. Autostart after reboot (`RECEIVE_BOOT_COMPLETED`): recommended no for
     now; start the tracker by hand after a reboot.

  Check on the phone, as `user` (prints no coordinates):
  `/usr/libexec/geoclue-2.0/demos/where-am-i -t 60 -a 8 | grep -i -E 'accuracy|error|fail'`
  and say whether a GNSS fix comes (accuracy under 50 m) outdoors.
  `/etc/geoclue/geoclue.conf` has no `[xdg-desktop-portal]` section and no
  Wi-Fi `url`, so the location portal depends on Phosh's geoclue agent and
  on the modem GNSS (`[modem-gps]`); as `claude` geoclue timed out.
- 2026-11-03: [Back navigation](navigation.md) (pull request from
  `claude/navigation`): decide the default of `ATL_NAVIGATION`
  (`buttons`, `gesture` or `both`, proposed `both`) and whether the
  choice later lives per app or globally in the settings app
  ([decision](decisions/navigation.md)). Then, once the aports pin
  includes it, as `user` on the phone with the packaged ATL:
  `android-translation-layer <app>.apk 2>&1 | tee /tmp/nav.log` with
  Gadgetbridge or F-Droid, open a second page, swipe from the left edge
  about a third of the screen inwards: the app goes back; a short swipe,
  a tap at the edge and a vertical scroll at the edge must not go back.
  Repeat from the right edge, and with a keyboard (Escape, Alt+Left).
  `ATL_NAVIGATION=gesture android-translation-layer ...` hides the header
  back button, `ATL_NAVIGATION=buttons` turns the swipe off. Report what
  went wrong; `/tmp/nav.log` shows `onBackPressed() called`.

- 2026-11-03: HeliBoard as Phosh keyboard ([feasibility](android-keyboard.md)):
  nothing to test on the phone yet, the keyboard does not type headless.
  Decision needed later: the keyboard replaces Stevia only for a live
  test the owner can undo (it would also be the lock screen keyboard);
  the agent will give exact commands to start it in place of Stevia and
  to get Stevia back (`phosh-osk-stevia --allow-replacement`).

- 2026-11-03: Push messages ([push](push.md)). Decided: FCM through
  Google only per app after consent (`atl.google-services`); push goes
  through KUnifiedPush's default server, KDE's
  `https://unifiedpush.kde.org/`, each user can choose another. Nothing
  to do on the phone: KUnifiedPush is installed and starts at the next
  login. Only to use another server, as `user`, with an ATL build that
  has `atl-push-server`: `atl-push-server set https://your-ntfy.example`
  (back with `atl-push-server reset`).

# Notes

APKs come from F-Droid with their `.asc` signature, checked against
F-Droid's key `37D2C98789D8311948394E3E41E7044E1DBA2E89`
(`keys.openpgp.org`). For each app, first search upstream's issue tracker
(`https://gitlab.com/android_translation_layer/android_translation_layer/-/issues`)
and link known reports in the notes.
