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
| DB Navigator (`de.hafas.android.db`, Google Play through Aurora Store) | 26.20.0 (240000860) | branch `claude/aurora-store`, art_standalone and bionic_translation patches from nexumia-de/aports | `partly` | Deutsche Bahn's journey planner; base APK plus `config.arm64_v8a` and `config.xxhdpi`. Headless with `--sdk-int 26 -w 360 -h 720` (2026-10-28), installed from Aurora Store and started from its launcher: cookie consent, onboarding ([screenshot](screenshots/db-navigator-welcome-2026-10-28.png)), a "rooted device" notice (Continue), main screen with the journey search form ([screenshot](screenshots/db-navigator-main-2026-10-28.png)), station picker. Typing into the station field did not arrive headless (`wlrctl keyboard`), so no search was run yet: **owner test on the phone needed**. Search works on the phone (owner, 2026-10-29). Its launcher also registers `x-scheme-handler/mailto` (the app declares it). Without `--sdk-int` it gets its minSdk and aborts with `NoClassDefFoundError: android.graphics.fonts.Font$Builder` (API 29 fonts, not in ATL; about a day's work). Uses Firebase and Akamai Bot Manager; push messages (FCM) will not work. No upstream report. | 1. `BootstrapMethodError ... Bootstrap method returned null` in `java.time.format.DateTimeFormatterBuilder.<clinit>`: libcore's own lambdas never worked in art_standalone; `lambda-metafactory.patch` in nexumia-de/aports `art_standalone/`. 2. Hermes (React Native) and Akamai failed to load: `__register_atfork` and `__strncat_chk` missing; `register-atfork-strncat-chk.patch` in nexumia-de/aports `bionic_translation/`. 3. Settings were lost (Tink: `IV has unexpected length`): `api-impl: keystore: AES/GCM cipher with 12 byte IVs for keystore keys`. 4. `NoSuchMethodError: Context.getAttributionSource` while installing: `api-impl: add Context.getAttributionSource`. 5. On the phone (2026-10-29) **Log in** did nothing: its login is the implicit action `de.hafas.android.db.START_LOGIN`, which `startActivityForResult` did not resolve to the app's own activity: `api-impl: Activity: start the app's own activity for an implicit intent with a result`; headless it now opens DB's login page in the browser (the return through `dbnav://` needs an account, owner test). 6. In dark mode white text on light backgrounds ("No trips exist", Log in): AppCompat's configuration context with `uiMode` -1 switched the AssetManager all Resources share to neither day nor night, while Compose drew dark colours: `api-impl: ATLLoadedApp: own AssetManager for Resources with another configuration`; now dark throughout ([screenshot](screenshots/db-navigator-dark-2026-10-29.png)). 7. On the phone (2026-10-29) the login came back from the browser but the app stayed logged out: the redirect `dbnav://dbnavigator.bahn.de/login?code=…` started a new `MainActivity` instead of reaching the open `LoginOverlayActivity` (singleTask): `api-impl: IntentFilter: match the host and path of data URIs` and `api-impl: open a URI in the best matching activity, reuse an open singleTask one`; headless a redirect sent through D-Bus `Open` now reaches `LoginOverlayActivity.onNewIntent` (a real login needs the owner's account). 8. The "Please wait" box during login was a few letters wide: Dialog ignored its theme (`DBFullScreenDialog`, not floating) and wrapped its content: `api-impl: Dialog: use the dialog theme, non-floating dialogs fill the window`. 9. On the phone (2026-10-30) the login came back to the open `LoginOverlayActivity`, then hung at "The process is taking longer than usual": ATL delivered the redirect with `onNewIntent` only, without the `onPause`/`onResume` around it that Android guarantees, and the app reads the redirect in `onResume`: `Activity: pause and resume an open activity around onNewIntent` (checked headless with F-Droid's `market://` link into its open main screen; the real login is an owner test). 9. Tickets and BahnCards were an empty bar (owner, 2026-10-30): the app shows them as HTML (`ticketHtml`, `bildSichtHtml`) in a `WebView` through `loadData(..., "base64")` with JavaScript off, and ATL stubbed WebView unless `ATL_UGLY_ENABLE_WEBVIEW` was set: `api-impl: WebView: start WebKit with the first page, wrap_content is the page height`. Tested with a small activity doing the same (base64 UTF-8 HTML, graphic, height, scripts not run); a real ticket needs the owner's account. 10. The cookie screen stayed open after the click and was gone after a restart (owner, 2026-10-30): the consent was saved, then `Intent.makeRestartActivityTask` threw `NoSuchMethodError` and the app swallowed it: `api-impl: Intent: add makeMainActivity and makeRestartActivityTask`; now the welcome screen follows. 11. The Copy popup of Share trip was green under phoc, it was the last GtkAlertDialog window: `api-impl: share through a dialog layer`. All upstream candidates. Still open: behind the login box the screen is black, ATL shows only the top activity, also when it is translucent (`TranslucentTheme`); the box's text is left aligned instead of centred; 12. Text of `TextView`s was 1.33 times too big (pango size in points, Android gives pixels), `Button`s ignored text size, `drawableStart`, gravity and `minHeight`, `TextView` ignored gravity, hints and `maxLines`, disabled views reported state_enabled, and a closed `Spinner` used the popup's row and background: fixed in the look pull request, compared with the owner's Android screenshots of 2026-10-30. 13. The app's own fonts were ignored because `Typeface`, `FontFamily` and `Paint.setTypeface` were stubs: `api-impl: Typeface: load app fonts through fontconfig`. 14. Rows like Date stacked label and value: ATL never set `smallestScreenWidthDp`, so `layout-sw350dp` was not chosen: `api-impl: set smallestScreenWidthDp`. Still open: the closed spinner still wraps "Single journey" and clips it, cause not found; the text is not narrower than the room it gets. 15. On the phone (2026-11-03) Google's error "won't run without Google Play services": since the GMS stub became opt-in the client library's location and messaging connections failed with `SERVICE_INVALID`; DB is now in `GMS_STUB_APPS` (`api-impl: ATLToggles: fake GMS for DB Navigator by default`). 16. Pages built with Jetpack Compose did not open on the first tap (owner, 2026-11-06: "some pages cannot be opened"; headless: Profile > Legal notice > Licences needed two taps): ATL went from hover straight into `ACTION_DOWN` without Android's `ACTION_HOVER_EXIT`, and its hover events had raw = local coordinates and time 0, so Compose dropped the first gesture on a new screen: `api-impl: end hovering before a press, like Android`. 17. Black bars between sections (Settings, cookie screen, Passengers & discounts): the app's `drop_shadow_*` gradients were drawn with `drawRect`, which ignored the shader: `api-impl: GskCanvas: draw rects with a gradient shader as paths`; now soft shadows. Help, Privacy, Cancel contract and Terms of Use are web links and open in the browser. 18. Nearby waited forever (owner, 2026-11-07: "crashes after accepting the map"; the crash itself was not reproduced headless, the owner's log is needed): the fake GMS package had no services, so the client library's fused location never connected. `gstub: fused location service on LocationManager` answers `GoogleLocationManagerService.START` with a broker and a location binder (`getLastLocation`, `updateLocationRequest`, availability, `checkLocationSettings` in `gstub: answer checkLocationSettings`), on ATL's location portal; once connected, the client's `LocationRequest` aborted with `IncompatibleClassChangeError` because `WorkSource` was not `Parcelable`: `api-impl: WorkSource: Parcelable, with uids and names` (also a likely cause of the owner's crash, if a microG on the phone was bound instead of gstub). Headless with a fake location portal at Berlin Hbf, Nearby lists the stations around it and offers VBB tickets ([screenshot](screenshots/db-navigator-nearby-2026-11-07.png)); without location it shows "Public transit stops cannot be displayed" instead of a spinner. Still open: the Google map itself (gstub's Maps creator returns no map, the app shows "having trouble with Google Play services" over the search field; MapLibre backend, several days); Journeys while logged in (owner: the browser opened again and again, then a crash after login) needs the owner's log. |
| Post & DHL (`de.dhl.paket`, Google Play through Aurora Store) | 10.1.0.44 (921502244) | branch `claude/dhl-ui` | `partly` | Needs `--sdk-int 26` and Aurora's Pixel 9a profile (minSdk 32). Starts; tracking and Frankieren (WebView) look like Android; app font, yellow bottom navigation and centred Compose text since 2026-10-30. Open: selected tab not red and its icon not the selected one, Compose cards on Packstation only 200 px wide, Mehr rows centred without their arrow and an empty white login button, Standorte list empty, onboarding pictures too small, memory ran full once on the phone. | Fonts from temporary files, text appearances and font resources; gradient fills; stretched backgrounds; text alignment |
| Duolingo (`com.duolingo`, Google Play through Aurora Store) | 6.103.6 (2488), minSdk 32 | branch `claude/duolingo-sign-in-compose` | `partly` | Base APK plus `config.arm64_v8a` and `config.xxhdpi`, run with `--sdk-int 26`. Installs from Aurora Store on the phone since `--install` no longer runs app code (2026-10-31); Google Play answers "App not supported" to the agent's test Aurora, so the APK came from the owner's download. Headless (2026-10-31) it starts to its welcome screen with the owl, "Learn for free. Forever.", GET STARTED and I ALREADY HAVE AN ACCOUNT ([screenshot](screenshots/duolingo-welcome-2026-10-31.png)). The two buttons are too flat, their text is clipped and has the wrong colours. I ALREADY HAVE AN ACCOUNT opens the sign-in screen ([screenshot](screenshots/duolingo-sign-in-2026-10-31.png)); a real sign-in needs the owner's account and shows whether Play Integrity is required. Taps need a first tap on the window to focus it. Play Integrity fails (no Play Store); if the app needs it, Duolingo is out of scope. Firebase Installations answers 403 and Google Play services' Blockstore is missing; all are caught. No upstream report. | 1. Aurora stayed at "Installing", no launcher: `--install` ran the app's Application, providers and main activity: `main: do not run app code in --install`. 2. `NoSuchMethodError: Context.bindService(Intent, int, Executor, ServiceConnection)`: `api-impl: add Context.bindService with an Executor`. 3. `NotificationManager.getNotificationDelegate`, `JobInfo.Builder.setImportantWhileForeground`, `JobParameters.getNetwork`, `UsageStatsManager.getAppStandbyBucket`, `NetworkCapabilities.getLinkDownstreamBandwidthKbps`, `PreferenceManager.getDefaultSharedPreferencesName`: `api-impl: add methods Duolingo calls at start`. 4. `ArrayIndexOutOfBoundsException` in Mixpanel: `api-impl: DisplayManager.getDisplays: return the default display`. 5. "You need to use a Theme.AppCompat theme": the app's language context has its own AssetManager and `Theme.setTo` between two AssetManagers kept only the framework's entries: `api-impl: Resources.Theme.setTo: copy themes between AssetManagers`. 6. `LayoutInflater.createView` and `onCreateView` with a context (API 29): `api-impl: LayoutInflater: add createView and onCreateView with a context`. 7. WorkManager ignored all work "in non-main process": `ApplicationInfo.processName` stayed null without `android:process`: `api-impl: PackageParser: default the process name to the package name`. 8. Nothing that tracks activities saw any, `registerActivityLifecycleCallbacks` was a stub, so androidx's `ProcessLifecycleOwner` never put the app in the foreground and Duolingo's foreground startup tasks never ran: `api-impl: Application: call ActivityLifecycleCallbacks`; below API 29 `ProcessLifecycleOwner` also needs its `ReportFragment` by tag (Aurora crashed without it): `api-impl: FragmentManager: find fragments by tag`. 9. Network callbacks got no `onAvailable` at registration, default network callbacks none at all, and `NetworkCallback`/`NetworkRequest.Builder` were inner classes apps could not create: `api-impl: ConnectivityManager: report the network when a callback is registered`. 10. Then `NetworkRequest.Builder.removeCapability`, `Context.registerReceiver` with flags, `ConnectivityManager.getRestrictBackgroundStatus`, `AppOpsManager.checkOpNoThrow`, `ShortcutManager.getPinnedShortcuts`, `Context.getOpPackageName`, `StorageStatsManager` and `StorageManager.getStorageVolumes`: `api-impl: add more methods Duolingo calls at start`. 11. It still stayed on its empty splash screen with all threads idle: from API 29 androidx drives every activity's `Lifecycle` through `Activity.registerActivityLifecycleCallbacks` and its pre and post events, which was a stub, so the launch screen never got STARTED and never collected its route (found with ART method tracing, see [development environment](dev-environment.md)): `api-impl: Activity: call its own lifecycle callbacks, with the API 29 pre and post events`. 12. Then `Window.addOnFrameMetricsAvailableListener`: `api-impl: Window: accept frame metrics listeners`. 13. Its text views build fonts with the API 29 `android.graphics.fonts` classes (`Font$Builder` was missing): `api-impl: add android.graphics.fonts.Font, FontFamily and Typeface.CustomFallbackBuilder`. 14. The sign-in screen crashed on `TypedValue.isColorType`, `View.setAutofillHints` and `WindowInsets.inset`. Fixed together with the other framework methods Duolingo's own code calls, from a static list of missing references: `api-impl: WindowInsets: add the remaining inset getters and inset()`, `api-impl: add methods Duolingo's own code calls`. 15. GET STARTED aborted on `Vibrator.hasAmplitudeControl`, then `MediaPlayer.setDataSource(AssetFileDescriptor)` and `AudioManager.isHapticPlaybackSupported`; it now opens the coach screen ([screenshot](screenshots/duolingo-coach-2026-10-31.png)): `api-impl: Vibrator: add capability queries and VibrationEffect.Composition`, `api-impl: MediaPlayer: pause, stop, seek, loop, volume, release, prepared callback`. 16. The password field showed the text: `api-impl: EditText: honour inputType and the password transformation`. 17. On the phone (2026-10-31) SIGN IN, FACEBOOK and GOOGLE aborted with `NoSuchMethodError: Paint.setColor(long)`, followed by `getContentCaptureSession` and `getUniqueDrawingId`: the toast of the sign-in screen is the first Compose view, and R8 removed Compose's SDK checks because of minSdk 32, so the API 29 paths run under any `--sdk-int`: `api-impl: add the API 29 graphics and view methods Compose calls`. 18. SIGN IN then said "We're having trouble connecting to Duolingo" without sending a request: the app counts a network without `NET_CAPABILITY_NOT_SUSPENDED` as offline: `api-impl: ConnectivityManager: report the capabilities of an ordinary network`. Random data now gets the server's "Wrong username or password" ([screenshot](screenshots/duolingo-wrong-password-2026-10-31.png)), checked headless with `tools/atl-fill-text.c`. A correct login is an owner test. `RenderEffect` (blur) does nothing yet. All upstream candidates. |
| [Gadgetbridge](https://f-droid.org/packages/nodomain.freeyourgadget.gadgetbridge/) (`nodomain.freeyourgadget.gadgetbridge`, F-Droid) | 0.95.1 (255) | branch `claude/gadgetbridge-notifications` | `partly` | Companion app for watches and bands, no Google services. ATL had no Bluetooth; [Bluetooth on BlueZ](bluetooth.md) is new. Headless (2026-11-02): welcome pages and device discovery, which found the owner's Amazfit Balance as a supported, already bonded watch ([screenshot](screenshots/gadgetbridge-discovery-2026-11-02.png)). On the owner's first phone test (2026-11-02) the pairing page hung: an activity started from `onCreate` was shown below its caller, and a page closed with back never sent `onActivityResult`; both fixed. Headless with a dummy auth key, Pair now connects to the real watch over GATT and runs Zepp OS authentication, which fails only on the dummy key. Syncing with the real key is an owner test (below). Phone notifications reach its listener when ATL runs with `ATL_ENABLE_NOTIFICATION_LISTENER=1` (headless with a test notification, 2026-11-02). Installed through F-Droid it runs with `--sdk-int 26` (the store passes its level on), and there the onboarding stopped on page 3 (owner, 2026-11-04): the permissions page calls `CompanionDeviceManager.getAssociations()` from API 26, and the missing `android.companion` classes threw `NoClassDefFoundError` while the page was built; started from the APK file (API 23) it never got there. Fixed with `android.companion` stubs (no associations, `associate` fails like a device without the feature, so Gadgetbridge falls back to plain Bluetooth pairing). F-Droid marked it incompatible because `getSystemAvailableFeatures` was empty, and its install page called it unsigned because the v3 signer (API 24+) was checked against the app's emulated API 23; both fixed. Missing: GATT server and RFCOMM for other device families ([audit](app-audit.md#gadgetbridge)) | `XmlBlock.nativeGetText` (aborted the first screen), `NotificationManager` do-not-disturb API, Bluetooth permissions granted when BlueZ has an adapter, `Intent.getShortExtra`, `android.companion.CompanionDeviceManager` (onboarding stopped on page 3 under `--sdk-int 26`) |
| [CoMaps](https://f-droid.org/packages/app.comaps.fdroid/) (`app.comaps.fdroid`, F-Droid) | 2026.10.05-19 (26100519) | PR #73, #70 (voice) | `partly` | Offline OSM maps and navigation, native C++ renderer on OpenGL ES 3 (`liborganicmaps.so`). Headless (2026-11-03): downloads the world overview map; the map renders, zooms with the buttons and pans ([screenshot](screenshots/comaps-map-2026-11-03.png)). Fixed for that: `getSurfaceFrame` stub (400x400), offscreen EGL buffers not following resizes, `eglGetCurrentSurface` returning no surface for the drape upload thread's pbuffer context. Without location permission it stays on the splash screen: at its SDK_INT 21 androidx answers the request from `checkSelfPermission` without calling ATL (on Android 5 declared permissions are granted at install); decision open, see #79. Location through the location portal (#61) with `ATL_UGLY_ENABLE_LOCATION=1`. Voice guidance through speech-dispatcher (`spd-say`), not installed on the phone. Compass: the magnetometer (qmc6308) is readable but uncalibrated (about 370 µT instead of about 50), so no rotation vector is offered; needs hard-iron calibration, about 1-2 days. | `LocationManager` per-listener registrations, providers, last fix; `TextToSpeech` through speech-dispatcher; `FLAG_KEEP_SCREEN_ON`; asset `mark`/`reset` (its root certificate, then all downloads failed); `SurfaceView` 4-argument constructor (`getHolder()` was null); `SurfaceHolder.getSurfaceFrame`; offscreen EGL follows resizes; methods from its static list |
| [HeliBoard](https://f-droid.org/packages/helium314.keyboard/) (`helium314.keyboard`, F-Droid) | 4.2 (4201) | branch `claude/heliboard` | `partly` | Keyboard app, goal: the keyboard for all of Phosh ([feasibility](android-keyboard.md), size M). Headless with `--sdk-int 26 -w 360 -h 720` (2026-11-03): settings (welcome wizard skipped, main list, Languages & Layouts; [screenshot](screenshots/heliboard-settings-2026-11-03.png)). As OSK with `-l android/atl/ATLKeyboardViewer -e kb_class=helium314.keyboard.latin.LatinIME` under headless phoc (branch `claude/heliboard-osk`): letter keyboard with suggestions at the bottom, typing `h`, `i`, Enter into a GTK entry (zenity) gives "hi" ([screenshot](screenshots/heliboard-osk-typing-2026-11-03.png)); it waits while Stevia holds the keyboard and takes over when Stevia's service is stopped. Colours (branch `claude/drawable-tint`, 2026-11-04): on the phone in dark style the key area was plain white with white labels; now the keyboard has HeliBoard's own background in light and dark, a held key gets its pressed background, and a long press opens the popup keys and types the chosen one ([dark, Backspace held](screenshots/heliboard-osk-dark-2026-11-04.png), [light](screenshots/heliboard-osk-light-2026-11-04.png)). HeliBoard's default theme follows the system's dark style only from API 29 on: with `--sdk-int 26` it stays light in dark style, with `--sdk-int 30` it follows (both tested, no other difference seen). Open: key previews stay drawn after release; not tested on the phone (see Needs owner). Open in settings: the top-bar back arrow does nothing. Static gap list: [helium314.keyboard.gaps.tsv](app-audit/helium314.keyboard.gaps.tsv). No upstream report. | 1. Without `--sdk-int` ATL reports minSdk 21, the app reads the pre-API-24 `method.xml` without `languageTag` and aborts; use `--sdk-int 26`. 2. `InputMethodSubtype` and `InputMethodInfo` were empty stubs: `api-impl: InputMethodSubtype, InputMethodInfo and the app's input methods in InputMethodManager`. 3. `api-impl: add android.text.InputType`. 4. `api-impl: KeyguardManager: add isDeviceLocked`. 5. The wizard waited for HeliBoard to be chosen: `api-impl: Settings.Secure: report the app's own input method as default and enabled`. 6. The OSK host (upstream `ATLKeyboardViewer`) loaded the IME with the wrong class loader, without a context and without a default `InputMethodImpl`; HeliBoard's `<rotate>` icons were not inflated; the fork's in-window dialogs broke the keyboard's own window; surrounding text and UTF-8 byte counts for deletions: pull request #65. 7. It opened on the emoji page: `api-impl: View.isShown: visible only when the view and all its ancestors are`. 8. A key press threw in the key preview: `api-impl: Paint.getTextWidths: fill the advances and return the char count`. 9. No field lifecycle, content type or keys: `api-impl: keyboard host: input lifecycle per field, content type, keys`; Enter acted before the preedit was committed (fixed there too). 10. With Stevia running it stayed "unavailable": `api-impl: keyboard host: ask for the input method again while another keyboard has it`. 11. White key area in dark style: HeliBoard colours its views after inflation with colour filters (`BlendModeColorFilter`, MODULATE) and multiplying tints; ATL dropped them. `api-impl: apply color filters of every kind, not only PorterDuff SRC_IN` (Paint ignored the mode and every non-PorterDuff filter), `api-impl: color filters and tints for bitmaps, nine-patches and color drawables` (they were no-ops; a `ColorDrawable` view background ignored its filter; drawables from the resource cache were one shared object, so `mutate()` could not separate them), `api-impl: DrawableContainer: pass color filter, tint, state and level on to the current child`, `api-impl: ImageView and LayerDrawable: apply color filters and tints`. 12. Releasing a long press aborted ART (`NullPointerException` in `Canvas.getMatrix` on a canvas without bitmap): `api-impl: Canvas: matrix calls on a canvas without bitmap`. 1–5, 7–12 upstream candidates. |
| [GPSLogger](https://f-droid.org/packages/com.mendhak.gpslogger/) (`com.mendhak.gpslogger`, F-Droid) | 138 (138) | branch `claude/location-api` + `claude/battery-changed` | `partly` | Location tracker for the owner's Dawarich server (goal: measurements for the native Vestigia plan). Headless with `ATL_UGLY_ENABLE_LOCATION=1` and a fake location portal replaying a synthetic GPX: aborted at the first fix (`Location.setExtras` missing). After the `LocationManager` rewrite it logs every 10 s for 3 min (18 of 18 fixes, GPX file and its built-in Dawarich sender, Overland batches to a local test receiver). Open: background with the window closed, suspend (it re-arms `AlarmManager` `ELAPSED_REALTIME_WAKEUP` between fixes), Flatpak and portal permission. | `location: implement LocationManager registrations on the location portal`; Dawarich upload needs `os: sticky ACTION_BATTERY_CHANGED` (`getBatteryInfo` NPE) |
| [WhatsApp](https://www.whatsapp.com/android/) (`com.whatsapp`, direct download from whatsapp.com) | 2.26.43.72 (264307238) | branch `claude/whatsapp-2` | `partly` | Single APK (all ABIs, native code merged into `libs.so`), signer cert SHA-256 `3987d043…ce5d44` (WhatsApp Inc.), record in `~/work/apks/com.whatsapp/SOURCE.txt`. Works without Google Play Services (own push connection; Firebase Messaging fails with TIMEOUT and is caught). Static list: 2847 missing or stubbed references. Headless (2026-11-02) it starts to "Welcome to WhatsApp" with **Agree and continue** ([screenshot](screenshots/whatsapp-welcome-2026-11-02.png)). After it (2026-11-03, with #91 and #96) "Enter your phone number" ([screenshot](screenshots/whatsapp-phone-entry-2026-11-03.png)); without a modem (#91) WhatsApp offers only "Link as companion device" (no QR code drawn yet). Registration needs the owner's number: owner test. Upstream issues #64, #90, #92–#107 are WhatsApp reports by others. | 1. Aborted at start with `NoSuchMethodError: Drawable.isAutoMirrored`: `api-impl: Drawable: add the base methods subclasses and callers expect`. 2. Providers failed to load with `NoClassDefFoundError: ContentProvider$PipeDataWriter`: `api-impl: ContentProvider: PipeDataWriter, call, batches and defaults`. 3. `LayerDrawable` layer sizes and gravity were stubs: `api-impl: LayerDrawable: layer size, gravity and relative insets`. 4. Dialogs had no owner activity, onStart, onStop: `api-impl: Dialog: owner activity, onStart and onStop`. 5. Companion mode instead of phone registration, `hasSystemFeature(android.hardware.telephony)` was always false: #91 (ModemManager). 6. Phone number entry blank, `ScrollView.fillViewport` was a no-op: #96. All upstream candidates. |
| [FluffyChat](https://f-droid.org/packages/chat.fluffy.fluffychat/) (`chat.fluffy.fluffychat`, F-Droid) | 3574 | pull request #81, bionic_translation pull requests #3 and #4 | `partly` | Flutter Matrix client; push through UnifiedPush (see the push pull request #64). Headless at 360×720 (2026-11-03) it starts to its welcome screen with Create new account, Sign in and Login with Matrix-ID ([screenshot](screenshots/fluffychat-welcome-2026-11-03.png)); the text is in a monospace fallback font. **Sign in** opens the homeserver list (fetched over TLS). Typing into the homeserver field with `wlrctl keyboard type` did not arrive (the tap did not show a cursor either; Flutter text input in ATL not investigated yet), and **Continue** with matrix.org starts its OIDC login in a browser tab, which ATL did not open (`startActivityForResult` with a `VIEW` intent for https: "intent was not handled"). Not tested yet: login against the local conduit server, chats, UnifiedPush registration, the owner's phone. No upstream report. | 1. Aborted at start on Flutter's engine lookups and embedding classes: `android.media.Image`, `HardwareBuffer`, `Bitmap.copyPixelsFromBuffer`, `Context.getCodeCacheDir`, the `android.view.textservice` classes, `AccessibilityEvent`/`AccessibilityRecord` and the `AccessibilityManager` listener callbacks, `View.requestUnbufferedDispatch` and `InputDevice.getMotionRange(int)` (first tap). 2. Stayed on the launch screen with all threads idle: Flutter always runs Dart on the platform thread now (the manifest switch `DisableMergedPlatformUIThread` is refused), and its task runner needs `ALooper_addFd` on the main thread, which `libandroid` has only since upstream `45a80eb4` (came with the upstream sync); the installed builds of 2026-11-02 lacked it. 3. `getSystemService("restrictions")` returned null: `RestrictionsManager` with empty restrictions, also `textservices`. 4. Every connection failed with "Failed to find root cert cache": dart:io loads `/system/etc/security/cacerts`, now mapped to `/etc/ssl/certs`; bionic_translation applied path overrides to `open` but not to `stat` (its #3). 5. "Couldn't resolve native function 'calloc'": Dart FFI's `dlsym(RTLD_DEFAULT)` did not reach the host libc (bionic_translation #4). 6. Client initialization failed with "no such algorithm: RSA for provider AndroidKeyStore" (flutter_secure_storage 10): `api-impl: keystore: RSA key pairs`. All upstream candidates. |
| [Blokish](https://f-droid.org/packages/org.scoutant.blokish/) (`org.scoutant.blokish`, F-Droid) | 3.6 (24) | `01084c1c` | `partly` | Smoke test of the phone setup (2026-10-28), not a target app. Starts with the title bar and the four player colour bars at the bottom; the board (black area) is not drawn: GTK warns `Trying to measure WrapperWidget ... for width of 0, but it needs at least 252`. Not investigated further. No upstream report. [Screenshot](screenshots/blokish-2026-10-28.png). Started with `tools/headless-shot.sh -d 40 -- android-translation-layer org.scoutant.blokish_24.apk`. | Crashed at startup with `NoClassDefFoundError: android.view.animation.RotateAnimation`; fixed by `01084c1c api-impl: add RotateAnimation stub` (upstream candidate). |
| [komoot](https://www.komoot.com/) (`de.komoot.android`, Google Play via Aurora Store) | 2026.44.2 (264028003) | `claude/komoot` | `partly` | Hiking and cycling planner, minSdk 32, Compose and classic views, MapLibre map. Headless up to the sign-up page: privacy consent ([screenshot](screenshots/komoot-privacy-2026-11-07.png)), the start page with Google, Facebook and email, the email page ([screenshot](screenshots/komoot-email-2026-11-07.png)), and with a test address komoot's server answered with its sign-up page ([screenshot](screenshots/komoot-sign-up-2026-11-07.png)). Location: komoot takes positions from GMS fused location like DB Navigator, so the gstub location service (pull request #114) serves it once komoot runs with the GMS stub; it is not in `GMS_STUB_APPS` yet (without it the client sees no `com.google.android.gms`). Not tested: sign-in with a real account, map, location, route planning, navigation, offline maps. Sign-in with Google needs Google's servers (out of scope). No upstream report. | 1. Start aborted on missing APIs, one after another: `android.window.SplashScreenView`, `android.util.CloseGuard`, `Trace.beginAsyncSection`, `JobScheduler.getPendingJob`, `Display.getRoundedCorner`, `Activity.addContentView`, `Window.setNavigationBarDividerColor`, `View.setImportantForContentCapture`, `JobParameters.getJobId`, `Context.startActivities`, `SingleLineTransformationMethod`. Fixed with the gap list in groups (job and service, os, window and view, text and widget, app framework). 2. Every screen was red: `View.isShowingLayoutBounds()` returned `true`, so Compose drew its layout-bounds debug paint (red) over everything: `api-impl: View: isShowingLayoutBounds returns false` (upstream candidate, the `true` came from upstream). 3. The first tap aborted in `AutofillManager.notifyViewVisibilityChanged`: autofill values and a manager without a service. |

# Needs owner

System packages to install and decisions only the owner can make. Each
entry: date, what, why. Remove an entry once it is done.

- 2026-11-08: The agent for issue #118 runs in `tmux` session `perf118`
  inside the laptop's SSH login (session `c22`, started 19:44). logind
  kills that login with everything in it once the connection times out
  (`KillUserProcesses=yes`, linger does not prevent it), so the agent
  and any job it started there can stop overnight. The agent was not
  allowed to restart itself as a user unit (no self-persistence). If it
  stopped, start it again outside any SSH login, for example:

  ```sh
  systemd-run --user --unit=claude-perf118 --collect -p Type=forking tmux new -d -s perf118 -x 200 -y 50 claude --permission-mode auto --model opus "Lies zuerst ~/work/handoff/perf-118.md und arbeite autonom weiter."
  ```

  Measurement jobs the agent starts itself use `systemd-run --user` and
  are not affected.

- 2026-11-08: Measurements for issue #118 ([performance](performance.md))
  need tools `claude` cannot install or enable. `perf` (Alpine package
  `perf`, 7.2.x, works with the 7.3 kernel) would show which code wakes
  the main thread and the JIT thread while an app idles; without it the
  measurements only count context switches per thread:

  ```sh
  sudo apk add perf strace sysstat
  ```

  With `kernel.perf_event_paranoid = 2` (the current value) `perf` sees
  only the `claude` user's own processes and no kernel samples, which is
  enough for `perf stat`/`perf record` of one ATL app. Scheduler
  tracepoints (`perf sched`, `perf trace`, who wakes whom) need `-1`,
  also after a reboot:

  ```sh
  echo 'kernel.perf_event_paranoid = -1' | sudo tee /etc/sysctl.d/90-perf.conf
  ```

  ```sh
  sudo sysctl -w kernel.perf_event_paranoid=-1
  ```

  The kernel is built without `CONFIG_SCHEDSTATS` and
  `CONFIG_ANON_VMA_NAME` (no per-thread run statistics, no names on
  anonymous memory) and `/proc/timer_list` is root-only; nothing to do
  there short of another kernel. Timer and wake-up tracing through
  `/sys/kernel/tracing` is root-only as well; making it readable for the
  `claude` user would need a group on the mount
  (`mount -o remount,gid=1000,mode=750 /sys/kernel/tracing`), only if
  the perf route is not enough.

- 2026-11-08: Gadgetbridge connected to the Amazfit Balance, 30 minutes
  (issue #118): the headless measurement covers the idle app without a
  watch; the connected case runs on the phone as `user` with the
  packaged ATL, since the watch is bonded to the owner's session.
  Start it from a terminal and leave it connected for 30 minutes with
  the screen off, then copy the three result files where `claude` can
  read them. The scripts are in `claude`'s checkout
  (`/home/claude/work/android_translation_layer/tools`, world-readable):

  ```sh
  android-translation-layer -w 360 -h 720 --sdk-int 26 /home/claude/work/apks/nodomain.freeyourgadget.gadgetbridge/nodomain.freeyourgadget.gadgetbridge_255.apk 2>&1 | tee /tmp/gb-connected.log
  ```

  In a second terminal, once the watch shows connected (PID from
  `pgrep -f gadgetbridge_255`):

  ```sh
  T=/home/claude/work/android_translation_layer/tools; P=$(pgrep -f gadgetbridge_255 | head -1); python3 $T/perf-smaps.py $P start > /tmp/gb-connected-smaps-start.tsv; $T/perf-threads.sh $P > /tmp/gb-connected-threads-start.tsv; $T/perf-idle.sh $P 1800 60 > /tmp/gb-connected-idle.tsv; python3 $T/perf-smaps.py $P end > /tmp/gb-connected-smaps-end.tsv; $T/perf-threads.sh $P > /tmp/gb-connected-threads-end.tsv; chmod 644 /tmp/gb-connected-*
  ```

  Why: Gadgetbridge's cost with a watch (GATT notifications, sync) is
  what made the owner drop it; the idle number is the floor, not the
  real case.

- 2026-11-07: DB Navigator: for the crash after
  accepting the map and in Journeys after the login, a log is needed:
  start DB Navigator from a terminal as in its launcher
  (`grep -h ^Exec ~/.local/share/applications/*hafas*.desktop`) with
  `2>&1 | tee /tmp/db.log`, reproduce, then `chmod 644 /tmp/db.log`.

- 2026-11-07: komoot on the phone, once the `claude/komoot` pull request
  is merged and its aports pin published: `sudo apk upgrade`, install
  komoot with Aurora Store (it needs the Pixel 9a device profile in
  Aurora, as komoot needs Android 12L), sign in with email (not Google),
  and look at the map, the location dot and planning a route. Say what
  differs from Android; a log helps (`... 2>&1 | tee /tmp/komoot.log`).

- 2026-11-06: DB Navigator first-tap and black-bar fixes (pull request
  #112, merged). Once in the aports package: open
  Profile > Legal notice > Licences with one tap, and check that
  Settings shows no black bars.

- 2026-11-04: Updates without asking for F-Droid (pull requests #105
  and #106). They work for apps in their own Flatpak only: F-Droid on
  the phone was installed without a sandbox, so first install it again
  by opening its APK (it then becomes its own Flatpak). Apps installed
  before without a sandbox are not updated this way until the new
  F-Droid installs them again (roadmap phase 6, migration). Then long-press F-Droid in the app grid,
  **App Info**, turn on **Update Apps Without Asking**. F-Droid installs
  its updates when its daily update job is due while F-Droid is open,
  on Wi-Fi by default (F-Droid's settings, automatic updates), and a
  notification says which app was updated.

- 2026-11-03: FluffyChat on the phone, once ATL pull request #81
  and bionic_translation pull requests #3 and #4 are
  merged and their aports pins published: `sudo apk upgrade`, then as
  `user` start FluffyChat
  (`android-translation-layer ~/Downloads/chat.fluffy.fluffychat_3574.apk`
  or its launcher) and check that the welcome screen with **Sign in**
  appears; Secret Service (GNOME Keyring) must be running, FluffyChat keeps
  its key there. Send `~/fluffychat.log` from
  `android-translation-layer chat.fluffy.fluffychat_3574.apk > ~/fluffychat.log 2>&1`
  if it does not. No real account is needed for this step.

- 2026-11-03: CoMaps after PR #73 (and #61 for location) is in a
  phone build: as `user`, `android-translation-layer
  ~/work/apks/app.comaps.fdroid/app.comaps.fdroid_26100519.apk
  > /tmp/comaps.log 2>&1`, allow the world map download, then report
  whether the map fills the screen without a black box and whether
  pinch/drag move it. Voice guidance (#70): speech-dispatcher and
  espeak-ng are installed (2026-11-08); turn on voice in a route.

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
- 2026-11-03: DB Navigator covered the whole screen (edge to edge made
  the window fullscreen). After the fix (#94) is in the aports package:
  start DB Navigator from the app grid; Phosh's bar and ATL's title bar
  stay and the app starts below them.
- 2026-10-28: Finish the apk repository in
  [nexumia-de/aports](https://github.com/nexumia-de/aports) (signing
  secret in the environment `apk-signing`, merge its pull request #1;
  steps in its `docs/repository.md`), install
  `android-translation-layer@nexumia` with `sudo`, and try Chooser with
  several fingers on the real touch screen; headless tests cannot do
  that. The owner runs every root command themselves.
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
  Background location and autostart after reboot are decided (owner,
  2026-11-08, as in the [hardening roadmap](hardening-roadmap.md)): both
  only for apps the owner allows, asked per app, default no. Still open:
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

  Check on the phone, as `user` (prints no coordinates):
  `/usr/libexec/geoclue-2.0/demos/where-am-i -t 60 -a 8 | grep -i -E 'accuracy|error|fail'`
  and say whether a GNSS fix comes (accuracy under 50 m) outdoors.
  `/etc/geoclue/geoclue.conf` has no `[xdg-desktop-portal]` section and no
  Wi-Fi `url`, so the location portal depends on Phosh's geoclue agent and
  on the modem GNSS (`[modem-gps]`); as `claude` geoclue timed out.
- 2026-11-03: [Back navigation](navigation.md) (pull request from
  `claude/navigation`): default `both` decided (owner, 2026-11-08);
  still to decide: whether the choice later lives per app or globally in
  the settings app ([decision](decisions/navigation.md)). Then, once the aports pin
  includes it, as `user` on the phone with the packaged ATL:
  `android-translation-layer <app>.apk 2>&1 | tee /tmp/nav.log` with
  Gadgetbridge or F-Droid, open a second page, swipe from the left edge
  about a third of the screen inwards: the app goes back; a short swipe,
  a tap at the edge and a vertical scroll at the edge must not go back.
  Repeat from the right edge, and with a keyboard (Escape, Alt+Left).
  `ATL_NAVIGATION=gesture android-translation-layer ...` hides the header
  back button, `ATL_NAVIGATION=buttons` turns the swipe off. Report what
  went wrong; `/tmp/nav.log` shows `onBackPressed() called`.

- 2026-11-03: HeliBoard as Phosh keyboard, a live test that undoes
  itself after 15 minutes. Needs the packaged ATL with pull requests #92
  and #110 (colours in dark style; after their aports pin). How the keyboard is started, checked on the
  phone: under systemd, Stevia is the user service
  `mobi.phosh.OSK.service` (package `stevia-systemd`, `Type=dbus`,
  `Restart=on-failure`), pulled in with `Wants=` by Phosh's session, so
  `systemctl --user stop` stops it cleanly (no restart, the session goes
  on) and `start` brings it back. Never kill Stevia with `pkill`: it is a
  required session component, and repeated crashes can end the session.
  **Risk**: while HeliBoard is the keyboard, a crash leaves no on-screen
  keyboard until the automatic return in step 3; a password that is not
  only digits could then not be typed on the lock screen, hence step 1.
  As `user`, phone on the charger, in a terminal:
  1. Note the lock setting and turn locking off for the test:
     `gsettings get org.gnome.desktop.screensaver lock-enabled` (note the
     answer), then `gsettings set org.gnome.desktop.screensaver lock-enabled false`.
  2. Get HeliBoard and check it:
     `cd ~ && curl -LO https://f-droid.org/repo/helium314.keyboard_4201.apk`
     and `echo "69b2b2fb349934371b1ed2d3569541cc0bda987f8aaf5eb5f5b157961f76de86  helium314.keyboard_4201.apk" | sha256sum -c`
     must say `OK`.
  3. Automatic way back first, it needs no keyboard: in 15 minutes it
     stops HeliBoard and starts Stevia again:
     `systemd-run --user --unit=heliboard-revert --on-active=15min /bin/sh -c 'systemctl --user stop heliboard-osk; systemctl --user start mobi.phosh.OSK.service'`
  4. Switch: `systemctl --user stop mobi.phosh.OSK.service && systemd-run --user --unit=heliboard-osk android-translation-layer --sdk-int 30 -l android/atl/ATLKeyboardViewer -e kb_class=helium314.keyboard.latin.LatinIME $HOME/helium314.keyboard_4201.apk`
     (until HeliBoard is up after some seconds there is no keyboard;
     `--sdk-int 30` so that HeliBoard follows the dark style).
  5. Try: type in a text field (Chatty, the browser, a terminal),
     suggestions, backspace, Enter, an umlaut by holding `a`, a password
     field, light and dark style (keys and labels readable). Save the log: `journalctl --user -u heliboard-osk > /tmp/heliboard.log`.
  6. Back by hand: `systemctl --user stop heliboard-osk; systemctl --user start mobi.phosh.OSK.service; systemctl --user stop heliboard-revert.timer`,
     then `gsettings set org.gnome.desktop.screensaver lock-enabled X`
     with the value noted in step 1. Without a keyboard: wait for step 3,
     or run the same commands over SSH. A reboot also brings Stevia back,
     nothing of the test is kept.
  The agent reads `/tmp/heliboard.log`. HeliBoard at every login (a user
  drop-in for `mobi.phosh.OSK.service`, undone with
  `systemctl --user revert mobi.phosh.OSK.service`) is a later decision,
  only after this test and a lock screen check.

- 2026-11-03: Push messages ([push](push.md)). Decided: FCM through
  Google only per app after consent (`atl.google-services`); push goes
  through KUnifiedPush's default server, KDE's
  `https://unifiedpush.kde.org/`, each user can choose another. Nothing
  to do on the phone: KUnifiedPush is installed and starts at the next
  login. Only to use another server, as `user`, with an ATL build that
  has `atl-push-server`: `atl-push-server set https://your-ntfy.example`
  (back with `atl-push-server reset`).

- 2026-11-03: WhatsApp registration (after #89, #91, #96 are merged and
  packaged): on the phone as `user`, start WhatsApp, tap **Agree and
  continue**, enter your own number and check that the SMS or call code
  arrives and the chat list opens. Only the owner can do this (real
  number). Note whether messages arrive while WhatsApp is closed
  (its own keep-alive service, no Google push without consent).

# Notes

APKs come from F-Droid with their `.asc` signature, checked against
F-Droid's key `37D2C98789D8311948394E3E41E7044E1DBA2E89`
(`keys.openpgp.org`). For each app, first search upstream's issue tracker
(`https://gitlab.com/android_translation_layer/android_translation_layer/-/issues`)
and link known reports in the notes.
