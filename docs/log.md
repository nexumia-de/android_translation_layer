# Directory Update Log

## 2026-10-28
* **Creation**: Fork of ATL on GitHub (`nexumia-de/android_translation_layer`, `master` = upstream, default branch `phosh`). [Fork workflow](fork-workflow.md), [development environment](dev-environment.md) for the owner's phone as test device, and an empty [app status](apps.md) list. `AGENTS.md` names which shared phosh-toolkit rules apply to this fork. On the owner's request the agent works autonomously and merges its own pull requests into `phosh` (merge commits, never `master`); [app status](apps.md) has a "Needs owner" list for system packages and decisions.
* **Phone setup**: First session on the owner's Fairphone 6 (Nura edge). [Development environment](dev-environment.md) records the device, the system packages (including the `libunwind-dev` workaround for the `llvm-libunwind` conflict and JDK 17 for `tools/jni_upcall_gen`), the build into `~/.local` with `tools/phone-setup.sh`, the run environment, the headless cage display with `tools/headless-shot.sh`, GitHub access and the hosts tested from the phone. [App status](apps.md): Blokish from F-Droid as smoke test (`partly`), after the fix `api-impl: add RotateAnimation stub`. The owner then removed Alpine's ATL packages; the smoke test is unchanged without them.
* **Chooser**: First app of the owner's, `com.uravgcode.chooser` 1.5.0 from F-Droid: starts and shows its onboarding after `api-impl: add BounceInterpolator` and `api-impl: Html: parse tags and entities in fromHtml` ([app status](apps.md)). `wlrctl` requested for input in the headless display ([development environment](dev-environment.md)).
* **Input in the headless display**: `tools/wl-pointer` taps and swipes through one virtual pointer (`wlrctl` drops clicks), used by `tools/headless-shot.sh -x`. Chooser now works through onboarding, main screen and a held finger after `Path: draw arcs of 360 degrees and more as a full ellipse` ([app status](apps.md), [development environment](dev-environment.md)).
* **Package for the owner**: ATL from `phosh` is packaged in the new central repository [nexumia-de/aports](https://github.com/nexumia-de/aports) (`apk.nexumia.de`, tag `@nexumia`) instead of an `alpine/` directory here ([development environment](dev-environment.md)).
* **Chooser on the phone**: The owner installed `android-translation-layer@nexumia` and tried Chooser with several fingers; it aborted after picking one, because `Vibrator.vibrate(VibrationEffect)` was missing. Fixed by `api-impl: add vibrate(VibrationEffect), VibratorManager and CombinedVibration` ; the owner confirmed it on the phone ([app status](apps.md)). [Development environment](dev-environment.md) now describes tests with the owner on the real screen.
* **Installing apps with F-Droid**: F-Droid 2.0.2 installs, lists and uninstalls apps in ATL (headless, with Chooser): `PackageInstaller` sessions run `--install`, `getInstalledPackages` lists `_installed_apks_`, `ACTION_DELETE` asks and uninstalls. F-Droid needs `--sdk-int 26` and `-w 360 -h 720`, which `--install` now keeps in the launcher; art_standalone's wolfssljni needs a backport against a crash ([installing apps](installing-apps.md), [app status](apps.md)).
* **Launchers and Open**: Launchers made by `--install` could not start through D-Bus activation (bare `env` in the service file); they now use `/usr/bin/env`, older launchers need the app installed again. F-Droid's **Open** starts the other app with `gtk-launch` ([installing apps](installing-apps.md)).
* **Upstream candidates**: [Fork workflow](fork-workflow.md) lists the fix commits not yet upstream and their status, and how merge requests are written. The owner does not want to flood the maintainers: they decide what is sent, one merge request at a time.
* **How upstream is supported**: `AGENTS.md` records the owner's rules for working with the ATL maintainers: nothing sent on the agent's initiative, bug reports preferred, merge requests only small, understood, tested and disclosed, one at a time ([fork workflow](fork-workflow.md)).
* **Aurora Store and DB Navigator**: Aurora Store 4.8.4 installs DB Navigator 26.20.0 from Google Play into ATL (headless): split APK installs, the store's `--sdk-int` passed on, a validated network for WorkManager, `StorageManager.getAllocatableBytes`, the install permission, 12 byte GCM IVs for keystore keys (Tink) and `Context.getAttributionSource`. DB Navigator also needed two runtime fixes outside ATL, now patches in nexumia-de/aports: lambdas inside libcore (`java.time`) in art_standalone, and `__register_atfork`/`__strncat_chk` in bionic_translation. It runs to its journey search form; a search on the phone is still to be tried ([installing apps](installing-apps.md), [app status](apps.md), [development environment](dev-environment.md)).
* **DB Navigator phone test**: the "Needs owner" entry points at nexumia-de/aports pull request #6, which brings the Aurora fixes to the phone; the gaps no longer list split APKs or the wolfssljni fix as missing from the phone's packages ([app status](apps.md), [installing apps](installing-apps.md)).
* **Typing in Compose text fields**: F-Droid aborted on the phone at the first key typed into its search field: Jetpack Compose text fields call `InputDevice.getKeyboardType()` for every key event, which ATL lacked. It now reports an alphabetic keyboard, since GTK delivers typed text, also from the on-screen keyboard, as keys ([app status](apps.md)).
* **App cache per user**: `Context.getCacheDir()` was `/tmp/atl_cache/<package>` for every user, so when a second user ran the same app it could not write its cache (F-Droid: `IOException` in `File.createTempFile`, no app list). It is now `$XDG_CACHE_HOME/android_translation_layer/<package>` (default `~/.cache`) ([installing apps](installing-apps.md)).
* **Launchers without a dialog, uninstall from the app grid**: outside Flatpak `--install` writes the launcher and its icon itself instead of going through the portal, whose GTK "Create Application" dialog asked again after the user had chosen to install in the store. The launcher gets an **Uninstall** action, shown on a long press in Phosh's app grid: it asks first and removes the APK, data, cache, D-Bus service and launcher without starting the app ([installing apps](installing-apps.md)).
* **Launchers on dbus-broker**: on the phone a newly installed app did not start from its launcher and the Uninstall action did nothing: dbus-broker reads D-Bus service files only on `ReloadConfig`, which ATL now calls. `--install` also no longer crashes on exit (SIGSEGV in the JIT thread from `exit()` cleanup), which app stores would have taken as a failed install ([installing apps](installing-apps.md)).
* **Dialogs as layers, outline clipping**: Android dialogs are layers over the activity in the app window (a `GtkOverlay` with a dimmed scrim) instead of their own windows, which got a title bar and, under phoc on the phone, the accent colour around them. RenderNode clips to path outlines (Compose shapes with unequal corners; the empty menu of Aurora Store) and to rounded rectangles; `Path.isConvex()` says true. ViewTreeObserver no longer exits when a dialog has no frame clock yet (Aurora closed after onboarding). `DevicePolicyManager` answers that no app is device or profile owner. Headless tests can run under phoc ([development environment](dev-environment.md), [app status](apps.md)).

## 2026-10-29
* **DB Navigator login and dark mode**: Log in did nothing, as `startActivityForResult` ignored the app's own implicit action `START_LOGIN`; it now opens the login page. In dark mode the app showed white text on light backgrounds: AppCompat's configuration context (`uiMode` -1) switched the AssetManager shared by all Resources; Resources with another configuration get their own now ([app status](apps.md)).
* **Aurora Store's installer permission**: reinstalled by opening its APK file, Aurora lost `--sdk-int 26`, ran at its minSdk 21 and asked `Settings.Secure.install_non_market_apps`, unknown to ATL, so it showed "Permission required" on the phone. ATL answers 1 now, and an update or reinstall through the install page keeps the installed launcher's `--sdk-int` ([installing apps](installing-apps.md#aurora-store)).
* **Sharp app icons**: the install page and launchers used the PNG icon for ATL's own density (48 px) and scaled it up blurry; `get_app_icon_path()` now takes the xxxhdpi one ([installing apps](installing-apps.md#opening-an-apk-file)). Launchers made before keep their old icon until the app is installed again.
* **Opening an APK file asks to install it**: a hidden launcher handles `application/vnd.android.package-archive` with the new `--install-ask`, a libadwaita page (icon, name, version, install/update/reinstall, refusing an older version or another signer) in its own window, then Open or Done. The launcher's uninstall question uses the same page instead of a `GtkAlertDialog` that sat in the top left corner. ATL now builds with `libadwaita-dev` ([installing apps](installing-apps.md), [development environment](dev-environment.md)).
* **Upstream sync and dependency forks**: `.github/workflows/sync-upstream.yml` mirrors upstream `master` daily and opens a pull request into `phosh`. `art_standalone` and `bionic_translation` are forked the same way; their fixes (wolfssljni, lambdas, `__register_atfork`, `__strncat_chk`) are commits there instead of patches in aports ([fork workflow](fork-workflow.md)).
* **Uninstalling from Aurora Store, and Aurora seeing uninstalls**: Aurora's **Uninstall** (`startActivity` with `ACTION_UNINSTALL_PACKAGE`) opened GNOME Software through `xdg-open`; it now runs the launcher's Uninstall action, the same page as in the app grid. An app uninstalled from the app grid stayed "installed" in a running Aurora until its restart; ATL now watches `_installed_apks_` and broadcasts `ACTION_PACKAGE_ADDED`/`REMOVED` for changes made by other processes ([installing apps](installing-apps.md)).
* **Hardening roadmap**: [hardening roadmap](hardening-roadmap.md) with threat model, the current access of apps (no sandbox, `requestPermissions` unhandled, host writes from the app process, keyring and ALSA reachable) and eight phases from a permission store with Android-like dialogs to bubblewrap, a filtered D-Bus proxy, portals, seccomp, Landlock and APK signature checks. Proposed decision: [per-app bubblewrap sandbox started by ATL](decisions/app-sandbox.md).
* **Hardening plan switched to Flatpak**: on the owner's choice the [hardening roadmap](hardening-roadmap.md) now runs every Android app as its own Flatpak (`de.nexumia.atl.<package>`), generated on the phone by a host-side installer and broker, on an ATL engine built for Flatpak; ATL's own bubblewrap sandbox is dropped. New: status table per phase, escape test APK from phase 0 on, prototype as go/no-go. The [decision record](decisions/app-sandbox.md) is rewritten accordingly (still proposed).
* **DB Navigator login redirect and progress box**: the browser's redirect `dbnav://dbnavigator.bahn.de/login?code=…` opened a new main activity, so the login never finished; intent filters now match host and path, and a URI opened in the running app goes to the best matching activity, an open singleTask one through `onNewIntent`. Dialogs use their theme; non-floating ones fill the window, so the "Please wait" box is no longer a few letters wide. Still open: the black screen behind translucent activities and the app's own fonts ([app status](apps.md)).
* **App Flatpaks, first steps**: phase 0 facts of the [hardening roadmap](hardening-roadmap.md) (user namespaces, Flatpak 1.18.4, GNOME 50, portals 1.22.1, no Landlock) in [dev-environment.md](dev-environment.md#app-flatpaks). Prototype: Chooser generated as its own Flatpak from upstream's Flathub base app runs sandboxed up to `onResume`, then hits an asset bug fixed only in the fork. New tools in `flatpak/`: the engine manifest built from this checkout and the dependency forks, `make-app.sh` (APK to installed Flatpak) and `escape-test.sh` (18 escape attempts, all blocked).
* **First app in its own Flatpak on the fork's engine**: `flatpak/de.nexumia.atl.BaseApp.yml` builds on the phone (libportal and the `jni_upcall_gen` Java libraries added, build state off the `/tmp` RAM disk); Chooser generated from it with `flatpak/make-app.sh` runs sandboxed through its onboarding, and `flatpak/escape-test.sh` blocks all 18 escape attempts ([hardening roadmap](hardening-roadmap.md), phases 0, T and 1).
* **Chooser as Flatpak on the real screen**: the owner installed the generated Chooser from a bundle and confirmed it runs and chooses with several fingers; the escape test passes as their user. No vibration (the sandbox hides `/dev/input`, ATL must use feedbackd) and no sound (no audio socket; audio playback needs no permission on Android, so the [hardening roadmap](hardening-roadmap.md) now grants it by default).
* **Engine in CI**: `.github/workflows/engine-flatpak.yml` builds the engine Flatpak for aarch64 on GitHub's arm runner and publishes it through the pre-release `engine` and nexumia-de/phosh-apps to `flatpak.nexumia.de` ([hardening roadmap](hardening-roadmap.md), [development environment](dev-environment.md#app-flatpaks)).
* **Installer for app Flatpaks**: `--install-flatpak` turns an APK into its own Flatpak with the name, icon, URL schemes and permissions from the APK (`INTERNET` maps to network), without running any app code on the host; `flatpak/make-app.sh` is installed as `libexec/android-translation-layer/make-app-flatpak` for it and now grants audio playback by default ([installing apps](installing-apps.md#installing-an-apk-as-its-own-flatpak), [hardening roadmap](hardening-roadmap.md)).

## 2026-10-30
* **Vibration through feedbackd**: `Vibrator` calls feedbackd's Haptic D-Bus interface when it is there, so apps vibrate inside their Flatpak, where the input devices are hidden, and follow the feedback profile; the device file stays the fallback. Checked headless against feedbackd with a JNI harness and from inside an app sandbox ([hardening roadmap](hardening-roadmap.md), [fork workflow](fork-workflow.md)). The engine built in CI runs Chooser on the phone.
* **Installer service and signer check**: app stores in their own Flatpak install, uninstall, list and start apps through the new host service `de.nexumia.atl.Installer`, which only APKs requesting `REQUEST_INSTALL_PACKAGES` can reach and which asks the user on the host for every install and uninstall. Signers are now verified over the whole APK with AOSP's v2/v3 verifier; unsigned, changed and foreign-signed APKs are refused ([installing apps](installing-apps.md#app-stores-in-their-own-flatpak), [hardening roadmap](hardening-roadmap.md)).
* **Store installs end to end, links**: sandboxed F-Droid installed, opened and uninstalled Chooser through the installer service, asked on the host each time; apps without the install permission cannot reach the service. Generated apps are now D-Bus activatable, so a login redirect reaches the running app; `make-app-flatpak` no longer skips updates because of a crash in `flatpak info` ([installing apps](installing-apps.md#links-into-and-out-of-the-sandbox)).
* **Installer service interface versioned**: the interface is one XML file, the service reports `Version` (1), and ATL in the sandbox checks it before its first call with clear messages, since the host package and the engine are updated separately; it stays in this repository ([installing apps](installing-apps.md#interface-and-versions)).
* **Apps installed from a file get --sdk-int 26**: F-Droid opened from the owner's Downloads folder installed but did not start, since without `--sdk-int` ATL reported its minSdk 24; the install page and `--install-flatpak` now use 26 (or a higher minSdk), like app stores ([installing apps](installing-apps.md#opening-an-apk-file)).
* **DB Navigator login after the redirect**: on the owner's phone the login hung after the browser's redirect; ATL now calls `onPause`, `onNewIntent`, `onResume` for an open activity like Android ([app status](apps.md), [fork workflow](fork-workflow.md)).

## 2026-10-30
* **WebView by default**: ATL starts WebKit when an app loads the first page into a WebView, `ATL_UGLY_DISABLE_WEBVIEW` turns it off; wrap_content takes the height of the page, and `setJavaScriptEnabled` is honoured (Android's default is off). `setBlockNetworkLoads` is still ignored. DB Navigator's tickets and BahnCards need it. [Apps](apps.md)
* **Share dialog as a layer**: the Copy/Email dialog is an `AlertDialog` in the app window instead of a window of its own, which phoc showed over the accent colour.
* **Restart intents**: `Intent.makeRestartActivityTask` closes DB Navigator's cookie screen.
* **Text and buttons match Android**: `TextView` sizes are pixels (they were points, 1.33 times too big); gravity, hints, `maxLines`, `singleLine`, compound drawables from XML, `minWidth` and `minHeight` work; `Button`s honour text size and centre their label; `AlertDialog` has one row of buttons; a closed `Spinner` uses `getView` without GTK's arrow and frame. `spinnerStyle` is the default style of `Spinner`.
* **App fonts**: `Typeface` loads an app's fonts (assets, font resources, buffers) through fontconfig; files are kept in `_fonts_` of the data directory. `Paint` and `TextView` use the family. Variable axes and exact weights are not read: a Typeface has a family and bold/italic.
* **smallestScreenWidthDp**: set from the monitor, so `layout-sw350dp` and similar resources are chosen.
* **Post & DHL fonts and gradients**: AndroidX copies a font resource to a temporary file, loads it with `Typeface.createFromFile` and deletes it; fontconfig read the file only later, so text was boxes or the default font. `createFromFile` now keeps a copy in `_fonts_`. `LinearGradient` and `RadialGradient` were empty, so vector drawables with gradient fills were black; paths are now filled and stroked with GSK gradients in `GskCanvas.drawPath`. Other drawing calls still ignore shaders ([app status](apps.md)).
* **Post & DHL bottom navigation and text**: a view's background drawable is stretched to the view like on Android (GtkPicture kept its intrinsic aspect ratio, so the yellow navigation bar was a square in the middle). `StaticLayout` honours its alignment, so centred Compose text is centred. `TextView.setTextAppearance` applies colour, size, style and `fontFamily`, and `Resources.getFont` loads font files and font family XML; AppCompat's fast font path works with the API 28 `createFromFamiliesWithDefault`. Aurora Store and Chooser checked headless afterwards ([app status](apps.md)).
* **Aurora Store icons**: on the phone Aurora showed no app icons. Coil, its image loader, keeps its disk cache in `java.io.tmpdir`, which ATL left at `/tmp`, so `/tmp/coil3_disk_cache` belonged to whichever user ran Aurora first (here a headless test as `claude`) and other users could not write it. Like Android, ATL now sets `java.io.tmpdir` to the app's cache directory ([app status](apps.md)).
* **No app code while installing**: `--install` created the app's own `Application`, ran `onCreate` and its content providers and built its main activity, so the app's code ran inside the install. On the phone Duolingo's install from Aurora Store ran Duolingo code, ended without a launcher and Aurora stayed at "Installing" (not yet confirmed with the fix: the APK is still needed). `--install` now loads the APK with a plain `Application`, like `--install-ask` and `--install-flatpak` ([installing apps](installing-apps.md)).

## 2026-10-31
* **Duolingo**: installs from Aurora Store and no longer crashes at start, after `Context.bindService` with an Executor, `DisplayManager.getDisplays` returning the default display, `LayoutInflater` methods with a context, a few API 28-29 methods, and `Theme.setTo` between two AssetManagers: since contexts with another configuration get their own AssetManager, a theme copied from one lost the app's entries (Duolingo's splash theme, "You need to use a Theme.AppCompat theme"). It still stays on an empty splash screen ([app status](apps.md)).
* **Duolingo splash, part 1**: The splash hang was not the splash condition: all threads were idle, the app waited for events ATL never sent. Fixed on the way: the application's process name was null (WorkManager ignored all work), `ActivityLifecycleCallbacks` were never called (androidx's `ProcessLifecycleOwner` never reached the foreground), `FragmentManager.findFragmentByTag` always returned null (needed by `ProcessLifecycleOwner` below API 29; Aurora crashed without it), network callbacks got no initial `onAvailable`, and eight missing methods. Duolingo now runs its startup tasks and loads its remote configuration, but still shows the empty splash ([app status](apps.md)). Checked headless: Aurora, DB Navigator, Post & DHL and F-Droid look as before (F-Droid's extra right-hand pane is the same on the old build). [Development environment](dev-environment.md): the keyring daemon reads `XDG_DATA_HOME` of the test session, not of the app.
* **Duolingo welcome screen**: The splash hang was androidx's activity `Lifecycle`: from API 29 it is driven by `Activity.registerActivityLifecycleCallbacks` with pre and post events, a stub in ATL, so no activity of an app for API 29 and newer ever got STARTED. ATL now runs the lifecycle through `perform*` methods with these callbacks, accepts frame metrics listeners and has the API 29 `android.graphics.fonts` classes; Duolingo shows its welcome screen ([app status](apps.md)). [Development environment](dev-environment.md): ART method tracing shows which app methods ran. Aurora, DB Navigator, Post & DHL and F-Droid look as before.
* **Method for every app**: Apps now go through a fixed order instead of crash after crash: manifest, static list of missing and stubbed elements, stub log at run time, fixes, comparison with real Android, per-feature audit ([bringing up an app](app-bring-up.md), rule in `AGENTS.md`). `tools/atl-gaps.py` and `tools/atl-stublog-rewrite.py` moved into the repository, `tools/atl-stub-hits.py` is new. The gap tool no longer counts classes an app ships itself (`android.support`) and now lists references into empty ATL methods, also on screens no test reached. Generated snapshots in `app-audit/` record every element, so a commit's diff shows what closed.
* **First audit**: All seven apps audited per feature ([app audit](app-audit.md)). Found: password fields show the text, permission requests are never answered, Duolingo's GET STARTED crashes on `Vibrator.hasAmplitudeControl`, drawable states and view transforms are empty, Aurora closes after onboarding because ProcessPhoenix cannot restart a single process. Nothing was fixed in this change.
* **Duolingo sign-in screen**: A static list of framework references ATL lacks (classes, methods and fields, split by caller: the app's own code, obfuscated code, libraries) and a local stub log showed what the sign-in screen needed: `TypedValue.isColorType`, `View.setAutofillHints`, `WindowInsets.inset` and other methods Duolingo's own code calls. It now opens; a real sign-in is an owner test ([app status](apps.md)). Aurora, DB Navigator, Post & DHL and F-Droid are pixel-identical to before.
* **Most urgent audit findings**: Password fields are masked and tell the input method their purpose, `requestPermissions` answers with the current state instead of never, `MediaPlayer` and `SoundPool` got their missing controls and callbacks, and `Vibrator` got its capability queries. Duolingo's GET STARTED opens the coach screen. Sound and a real sign-in are owner tests ([app audit](app-audit.md)).
* **Duolingo sign-in reaches the server**: SIGN IN, FACEBOOK and GOOGLE aborted in the `AndroidComposeView` constructor. An app with minSdk 32 has no Compose SDK checks left, so ATL now has the API 29 `android.graphics.RenderNode` on top of `android.view.RenderNode`, `RecordingCanvas`, colours as `long` and the `View` methods Compose calls. SIGN IN then claimed there was no connection, because ATL's network lacked `NOT_SUSPENDED`. Random data now gets "Wrong username or password" from the server ([app status](apps.md)). `tools/atl-fill-text.c` fills text fields in headless tests ([development environment](dev-environment.md)).

## 2026-11-02
* **Google Play services**: Owner decision of 2026-11-01 recorded ([decision](decisions/gms-local-only.md), `AGENTS.md`): local GMS APIs in scope, server-only ones out, no Google certificate, no fake attestation. [GMS compatibility](gms-compat.md) lists the GMS, Firebase and Play APIs in DB Navigator, Post & DHL, Duolingo and WhatsApp (CoMaps, Gadgetbridge, Super Productivity, FluffyChat and Aurora Store ship none) and shows that the client libraries check Google's certificate before any GMS API, so a local GMS cannot be reached without it. Upstream's fake GMS with Google's certificate is now behind `ATL_GMS_STUB` (off): apps see a phone without GMS; the first screens of the three apps are unchanged. [Development environment](dev-environment.md): an ATL in its own prefix runs the Java code of libart's prefix unless started with `RUN_FROM_BUILDDIR`.
* **GMS amended**: The owner allows Google's certificate for ATL's local GMS, like microG; no Google servers, no fake attestation ([decision](decisions/gms-local-only.md), `AGENTS.md`). `ATL_GMS_STUB` is per app and kept by `--install`. [GMS compatibility](gms-compat.md) has the plan for fused location (microG's broker and location service ported into `gstub.jar`) and Maps.
* **Bluetooth and Gadgetbridge**: ATL had no Bluetooth. `android.bluetooth` now runs on BlueZ over the system bus: adapter, devices, bonding, classic discovery, LE scanning and the GATT client with notifications ([Bluetooth on BlueZ](bluetooth.md)). Gadgetbridge 0.95.1 starts, after `XmlBlock.nativeGetText` and a few smaller gaps, and its discovery finds the owner's Amazfit Balance headless. Connecting to the watch is an owner test ([app status](apps.md)). GATT server, RFCOMM and notification forwarding are missing ([app audit](app-audit.md#gadgetbridge)).
* **Gadgetbridge: install and notifications**: F-Droid marked Gadgetbridge incompatible because ATL's `getSystemAvailableFeatures` was empty while the app requires `android.hardware.bluetooth`. Installing the APK file said unsigned because the v3 signature verifier picked signers for the app's emulated API level (23) instead of the platform's; it now verifies as API 32. `NotificationListenerService` gets the desktop's notifications through a session bus monitor when `ATL_ENABLE_NOTIFICATION_LISTENER` is set ([Bluetooth on BlueZ](bluetooth.md), [app audit](app-audit.md#gadgetbridge)).
* **Gadgetbridge pairing hung**: An activity started from another one's `onCreate` was shown below it, and a page closed with back never reported to `onActivityResult`, so the pairing page waited forever. `finish()` now delivers the result (`RESULT_CANCELED` unless set), as on Android. BlueZ discovery that answered `InProgress` is now stopped. Pairing reaches the watch's authentication headless.
* **Gadgetbridge Bluetooth connection**: Reconnects failed with `le-connection-abort-by-local` because BlueZ aborts an LE connect while discovery runs; ATL now pauses discovery around Connect and Pair. ATL has its own BlueZ pairing agent (Android's `ACTION_PAIRING_REQUEST`, with a code dialog when the app does not answer), `createBond` reports its own result, and a receiver registered several times gets each broadcast once (Gadgetbridge had opened five parallel connections). Headless with the watch: pairing with the code dialog, one connection, sync of 11 rounds, and automatic reconnect after `bluetoothctl disconnect`. `ViewGroup.removeViews` was missing and crashed the device list on disconnect.
* **Gadgetbridge sync and charts**: After pairing, Gadgetbridge aborted on `ImageView.setImageLevel` (Drawable had no levels), every sync and Find device failed with `CloneNotSupportedException` (`Intent` not cloneable), the sync button's progress bar aborted because `AnimationUtils.loadInterpolator` returned null, and the HRV page aborted because androidx GridLayout needs `android.util.LogPrinter`. All four fixed (commits in [fork-workflow](fork-workflow.md)); tested with the owner's Amazfit Balance: pairing, sync and charts work.
* **Handoff rules**: parallel agents write findings after every step, commit WIP, use their own prefix and the build lock, and keep secrets out ([dev-environment](dev-environment.md#parallel-agents-and-handoff)); AGENTS.md only links there.
* **GitLab mirror**: `phosh` and `master` are pushed to the owner's GitLab fork on every push ([fork-workflow](fork-workflow.md#mirror-on-gitlab)); needs a deploy key set up by the owner.

## 2026-11-04: permission service security fixes

Review findings fixed: `de.nexumia.atl.Permissions` runs as its own
process (an app allowed to talk to it could reach the installer's
methods through the shared connection's unique name), and the installer
checks every caller's sandbox policy; callers are identified through the
broker's process fd; concurrent requests share one dialog and the
"not after the second no" count is re-read when storing; `AudioRecord`
checks per object. `flatpak/permissions-test.sh`: 16 checks, including
the unique-name call and two concurrent requests.

## 2026-11-03: runtime permissions through the installer service

`de.nexumia.atl.Permissions` asks with the desktop's access dialog and
stores answers in the permission store (table `de.nexumia.atl`); ATL's
`checkSelfPermission`, `requestPermissions` and `AudioRecord` use it in
an app's Flatpak, including `atl.google-services` (warning, default
denied). Headless with GTK's dialog: not requested → no, contacts
allowed, Google denied, microphone allowed and written into the
PipeWire rules. Not yet tried inside a real app Flatpak.
## 2026-11-04: microphone: separate pulse server instead of property rules

Security review: pulse clients can overwrite
`pipewire.access.portal.app_id`, so the 2026-11-03 rules were
bypassable (`PULSE_PROP=...`). Now apps without the microphone get only
the socket of a second pipewire-pulse that refuses all record streams;
matching every client with and without `application.name`. Headless:
denied app plays, cannot record microphone or monitor, also with forged
properties, and cannot reach the normal socket; allowed app records.
Native PipeWire with the WirePlumber rule was the alternative: its
`pipewire.*` keys come from the server, but ATL's ALSA default and
WebKit's GStreamer use pulse, so it would need changes in both.

## 2026-11-03: microphone blocked in PipeWire, headless

WirePlumber 0.5.18 `access.rules` + `access.permission-managers` and
pipewire-pulse `pulse.rules` (`block-record-stream`) on
`pipewire.access.portal.app_id`, in the user's own configuration:
a fake ATL Flatpak client (bwrap with `/.flatpak-info`) plays but cannot
record the microphone or a sink monitor; an allowed one can
(`flatpak/microphone/test.sh`). The WirePlumber rule alone left sink
monitors recordable through pipewire-pulse.

## 2026-11-03

* **Back navigation**: Besides the header bar button, a swipe from the left or right edge and the keys Escape, Alt+Left and XF86Back go back; they send `KEYCODE_BACK` like Android, so dialogs (`OnKeyListener`, cancelable) and `Activity.onKeyDown`/`onKeyUp` see it before `onBackPressed`. `ATL_NAVIGATION=buttons|gesture|both` picks the on-screen means ([back navigation](navigation.md), [decision](decisions/navigation.md)). `wl-pointer key` types keys headless.
* **HeliBoard**: [Feasibility](android-keyboard.md) of HeliBoard as the Phosh keyboard through ATL's existing IME host (size M; risks: lock screen, memory, start time). Its settings run headless; as on-screen keyboard its input view appears under headless phoc but is not usable yet ([app status](apps.md)).
* **GPSLogger and location**: `LocationManager` only handled one `requestLocationUpdates` variant, never stopped the portal session and its `Location` lacked most fields, so GPSLogger 138 aborted at its first fix. Registrations now follow Android (Looper, Executor, `PendingIntent`, `LocationRequest`, minimum time and distance, `removeUpdates` ends the portal session) and the fix time is right (the portal's second timestamp field is microseconds, not milliseconds). Headless with a fake location portal GPSLogger logs and uploads through its Dawarich sender ([app status](apps.md)).
* **HeliBoard types**: Under headless phoc HeliBoard now types into a GTK entry and takes over from Stevia when it stops; a reversible phone test is under Needs owner ([keyboard](android-keyboard.md), [app status](apps.md)).

## 2026-11-03: internet asked on the install page

`--install-ask --install-flatpak` asks "Allow internet?" for apps with
`INTERNET` (no default, Install disabled until answered); the answer is a
Flatpak user override, not metadata. Headless with GPSLogger: "No
internet" → override `shared=!network`, metadata without network;
uninstall removed the override.

## 2026-11-04: App info page

`--app-info <package>` shows an installed app Flatpak's version,
Internet, permission answers, More Permissions, Clear Data and
Uninstall. Headless (`flatpak/app-info-test.sh`, own Flatpak directory
and permission store): Internet and microphone switched on → override
`shared=network`, `sockets=pulseaudio`, store answer yes.

* **Push messages**: Scan of the apps: DB Navigator, Post & DHL and Duolingo use only FCM, WhatsApp FCM and its own connection, FluffyChat UnifiedPush. FCM cannot be routed to ntfy; Google's servers only per app with consent (`atl.google-services`, owner decision 2026-11-03). UnifiedPush maps to the host distributor over D-Bus ([push](push.md)).
* **UnifiedPush bridge and FCM without consent**: The connector found no distributor (null package name); ATL now reports any `org.unifiedpush.Distributor.*` on the session bus as `org.unifiedpush.distributor.dbus`, owns the app's name before registering, maps UNREGISTER, failures, message ids and Unregistered. c2dm registrations get `SERVICE_NOT_AVAILABLE` at once without `atl.google-services` ([push](push.md)).
* **UnifiedPush on D-Bus activation**: the connector is exported before the app owns its name and takes calls in a worker thread; calls wait until `Application.onCreate` has run, so the push that started an app reaches it (checked headless). Without `atl.google-services` no c2dm intent and no external Google Play services start leaves the app ([push](push.md)).
* **Wake windows**: the owner decided that Android apps never keep the phone awake; a system timer wakes it at a user-set interval and ATL apps run their due alarms in a short window ([decision](decisions/wake-windows.md)). Measured: the phone had not suspended in 2 days (a session inhibitor), unprivileged alarm timers get `EPERM`.
* **Push server setting**: KUnifiedPush, now on the phone, is configured on Phosh with `atl-push-server get|set|reset` (default KUnifiedPush's own, KDE's `https://unifiedpush.kde.org/`; no published usage terms), over its D-Bus Management interface when it runs, else in the per-user file. Tested headless with a local ntfy: ATL bridge to KUnifiedPush to ntfy and back. It costs about 50 MB PSS and no measurable CPU at idle ([push](push.md)).

## 2026-11-04: "Android apps" page

`--android-apps`: navigation, push server, wake interval (read-only),
signature checks, installer rights per store and the installed apps;
reached from App info. Headless (`flatpak/android-apps-test.sh`, fake
apps, stand-in `atl-push-server`): store switched off → override
`Installer=none`, push server set, navigation "gesture" saved and given
to every app.
## 2026-11-04: legacy permission review

Apps with target SDK or SDK_INT below 23 get one review dialog of their
dangerous permissions before their code runs (service method `Review`,
GTK/Phosh access dialog with check boxes, default off, shown once).
Headless (`flatpak/legacy-review-test.sh`, 7 checks): camera ticked →
yes, others no, internet not listed, second call without a dialog.
## 2026-11-04: App info from the launcher

Generated launchers get the action "App Info"; ATL in the sandbox
answers it without starting the app and calls the permission service's
new `OpenAppInfo` (interface version 2), which opens `--app-info` for
the caller on the host. Headless: the service part (`permissions-test.sh`,
24 checks); the in-sandbox part needs an engine build.

## 2026-11-04: updates without asking

App stores in their own Flatpak may update the apps they installed
without the install page, as on Android 12+, once **Update Apps Without
Asking** is on in the store's App info, and only for updates that ask
for nothing new ([installing apps](installing-apps.md#updates-without-asking)).
The installer of record is kept in each app Flatpak,
`setRequireUserAction` reaches the host, `getInstallerPackageName`
answers from the record (installer interface version 2), a notification
tells about each such update. The main program's option struct is
zeroed before parsing (a new field made `--install-flatpak` crash).
Headless (`flatpak/auto-update-test.sh`, 19 checks, all ok).
## 2026-11-04: F-Droid updates apps without asking

With **Update Apps Without Asking** on, the switch is the consent:
stores run at SDK 26, below the level where they ask for no user action
themselves ([installing apps](installing-apps.md#updates-without-asking)).
`PackageInfo.signatures` comes from the v3/v2 signing block too, so
F-Droid lists updates for apps signed with v2/v3 only; `JobScheduler`
waits for a job's network (Wi-Fi-only updates). End to end headless:
F-Droid in its own Flatpak updated Chooser 1.4.11 to 1.5.0 by its own
update job, without a page. Updates while the store is closed are not
done yet.
## 2026-11-04: atl-local gets appstream branches

`flatpak update --user` failed on the owner's phone: "No such ref
'appstream2/aarch64' in remote atl-local". `make-app-flatpak` exported
apps without appstream data; it now runs `flatpak build-update-repo`
after each export ([development environment](dev-environment.md#app-flatpaks)).
## 2026-11-04: Bluetooth asked on first use, not at install

The install page asked "Allow Bluetooth?" for every app with a Bluetooth
permission, F-Droid included, which uses it only to swap apps nearby. It
is now asked when an app first uses Bluetooth; "Allow" opens BlueZ in
the sandbox and restarts the app once ([installing apps](installing-apps.md)).
The permission service freed each request's key twice and crashed after
every answer (D-Bus started it again for the next question). Headless:
`flatpak/bluetooth-test.sh` (10 checks), `flatpak/permissions-test.sh`
(24 checks).

## 2026-11-04: HeliBoard colours (colour filters and tints)

On the phone in dark style HeliBoard's key area was a white surface
with white labels. HeliBoard colours its views after inflation with
`BlendModeColorFilter` (MODULATE) and multiplying tints; ATL dropped
nearly all of it. Colour filters of every kind now become a colour
matrix (Paint, a native filter paintable for bitmaps and nine-patches,
the filtered colour of a `ColorDrawable` background); `DrawableContainer`
passes filter, tint, state and level on; drawables from the resource
cache are separate objects; `ImageView` applies its colour filter and
image tint. A long press no longer aborts when it ends. Light and dark
tested headless with `--sdk-int` 26 and 30; F-Droid, Gadgetbridge and
Chooser compared with `phosh` ([app status](apps.md),
[keyboard](android-keyboard.md)).

## 2026-11-06: DB Navigator pages on the first tap, shadows

* **First tap on Compose screens**: on a newly shown Compose screen (Legal notice, Licences) the first tap did nothing. ATL sent hover events (from GTK crossings and the headless mouse) straight into a press, without Android's `ACTION_HOVER_EXIT`, and with raw = local coordinates and time 0. Hovering now ends before a press, there is no hover during a press or from touch screens, and hover events get window coordinates and a real time. Ruled out on the way: `isInTouchMode()` (still false) and hidden views in the touch hit test ([app status](apps.md)).
* **Gradient rectangles**: `GskCanvas.drawRect` and `drawRoundRect` ignored shaders, so DB Navigator's drop shadows were black bars; they now go through `drawPath` with GSK gradients ([app status](apps.md)).

## 2026-11-07: komoot

* **komoot**: komoot 2026.44.2 from Google Play (Aurora Store, Pixel 9a profile) starts and reaches sign-in headless. Missing APIs on its start path were filled in groups from the gap list, and `View.isShowingLayoutBounds()` returns `false` instead of `true`, which had covered Compose screens in red debug bounds. Bar colours come from the theme instead of a fixed red and grey ([app status](apps.md), [app audit](app-audit.md)).

## 2026-11-07: DB Navigator location

* **Fused location in gstub**: the fake GMS package answers `GoogleLocationManagerService.START` with a broker and a location service on ATL's `LocationManager` (location portal): `getLastLocation`, `updateLocationRequest`, location availability and `checkLocationSettings`. DB Navigator's Nearby lists the stops around a fake position at Berlin Hbf instead of waiting forever; without location it shows its normal hint. `WorkSource` is `Parcelable` now; the client's `LocationRequest` aborted on it ([app status](apps.md), [GMS compatibility](gms-compat.md)).
* **Merged with the komoot work**: `Activity.addContentView` came with both branches; the one from `phosh` stays. komoot's row notes that it needs `GMS_STUB_APPS` for location.

## 2026-11-08: Owner decisions

* **Sandbox accepted**: the owner confirmed the threat model and [one Flatpak per app](decisions/app-sandbox.md), which is now Accepted; phase 0 of the [hardening roadmap](hardening-roadmap.md) only lacks a WebView app and the start time.
* **Back navigation accepted**: default `ATL_NAVIGATION=both` ([decision](decisions/navigation.md)); per app or global later is still open.
* **Background location and boot autostart**: both per app, only for apps the owner allows; the boot autostart entry is not built yet ([hardening roadmap](hardening-roadmap.md)).
* **Phone**: speech-dispatcher, espeak-ng and flatpak-builder installed; the owner allows tests on the phone's own hardware within what `claude` reaches ([development environment](dev-environment.md)).
