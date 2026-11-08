---
type: Reference
title: App audit
description: Per app and feature - what works, what is stubbed or missing, what is wrong or untested in ATL, with the evidence. Complete lists of gaps and stub hits are generated snapshots in app-audit/.
resource: app-audit/
tags: [apps, testing, compatibility, stubs, audit]
generated: { by: claude-code/2.1.312, at: 2026-10-31T13:30:00Z }
---

# How to read this

Done with the method in [bringing up an app](app-bring-up.md). States are
defined there. Fix history stays in [app status](apps.md).

Audit of 2026-10-31 on ATL `phosh` plus branch `claude/duolingo-signin`, as
that is what was installed; the fixes marked below followed the same day, and
`*.gaps.tsv` include them while `*.hits.tsv` are from before them. It is not
complete:

- **Headless walks reached little.** One or two short walks per app under
  phoc, without an account, typing or real location. A feature not reached
  is `untested`, or keeps the state of an earlier test named in its row.
- A `stub` or `missing` state from the lists means the code path exists in
  the app. Unless the row says a run hit it, it was not seen failing.
- Only an owner test can confirm rows that need an account, touch or sensors.
- C code in `src/api-impl-jni` is not covered by either list.

Every element is in the snapshots, one pair per app:
[`app-audit/`](app-audit/), `<package>.gaps.tsv` and `<package>.hits.tsv`.

| App | Package | Missing or stub, own code | Missing or stub, in total | Stubs hit |
|---|---|---|---|---|
| Chooser | `com.uravgcode.chooser` | 0 + 1 | 313 + 171 | 65 |
| Blokish | `org.scoutant.blokish` | 3 + 15 | 1304 + 472 | 73 |
| F-Droid | `org.fdroid.fdroid` | 47 + 19 | 787 + 374 | 87 |
| Aurora Store | `com.aurora.store` | 1 + 2 | 943 + 437 | 72 |
| DB Navigator | `de.hafas.android.db` | 0 + 0 | 1205 + 573 | 204 |
| Post & DHL | `de.dhl.paket` | 6 + 13 | 1280 + 524 | 149 |
| Duolingo | `com.duolingo` | 72 + 146 | 1630 + 643 | 79 |
| komoot | `de.komoot.android` | 38 + 78 | 976 + 578 | 111 |

Most "in total" entries come from libraries and hardware paths never reached.
DB Navigator's own code is entirely obfuscated, so its first column says
nothing; use its `obfuscated` column.

# Problems across apps

Ordered by how much they broke. All were found in this audit; fixed rows say so.

| # | Problem | Evidence | Affects | Size |
|---|---|---|---|---|
| 1 | **Fixed.** `EditText` honours `inputType` and `setTransformationMethod`: masking and the input purpose for the on-screen keyboard. Still empty: multi-line and `setFilters` limits. Compose and WebView fields never had the problem. | Duolingo password field shows dots (test text set by a debug build, typing does not reach fields headless) | | |
| 2 | **Partly fixed.** `requestPermissions` now answers, with the current state and no dialog, so apps no longer wait. Normal permissions and `POST_NOTIFICATIONS` count as granted. Location, microphone and camera stay denied unless `ATL_UGLY_ENABLE_LOCATION` or `ATL_UGLY_ENABLE_MICROPHONE` is set; a real dialog is open. | Code. No walk reached a request, so the answer is unseen in an app | Location, microphone and camera prompts in all apps | medium, belongs to the [hardening roadmap](hardening-roadmap.md) |
| 3 | **Missing methods above `--sdk-int` crash.** Apps with minSdk 32 call API 27 to 32 without a version check, and `--sdk-int 26` does not protect them. | `Vibrator.hasAmplitudeControl` crashed a run and was on Duolingo's static list, called from obfuscated code | Duolingo, Post & DHL, DB Navigator | small per method |
| 4 | **Drawable states are ignored.** `View.onCreateDrawableState`, `setSelected`/`isSelected` and `isPressed` are empty, so `selected`, `checked` and `pressed` variants never show. | Stub log; code | Post & DHL tabs, Duolingo buttons, ripple and press feedback | small to medium |
| 5 | **View transforms do nothing.** `setScaleX/Y`, `setRotation`, `setPivotX/Y` are empty, as are `setClipToOutline` and `setForeground`. | Stub log: `setForeground` in 5 apps, `setScaleX` in DHL; Duolingo's own code calls them often | Animations, rounded corners, overlays | medium |
| 6 | **Restarting an app closes it.** ATL runs everything in one process, so ProcessPhoenix's helper activity kills the app and nothing starts it again. | Aurora headless, log `the app called killProcess on itself` | Aurora ends after onboarding and must be reopened | small |
| 7 | **Mostly fixed.** `MediaPlayer` has pause, stop, seek, loop, volume, release, `isPlaying`, the prepared callback and descriptors as sources; `SoundPool` reports loads. Error, buffering and info listeners stay empty, `PlaybackParams` and video surfaces are missing. | Duolingo runs through this code; **sound itself is unheard headless, owner test** | Audio in apps using `MediaPlayer` | small |
| 8 | **No camera.** `CameraManager.getCameraIdList` is empty, the `camera2` classes are absent and `Camera` is a stub. | Lists, code | QR and barcode scanning | large: weeks, needs a libcamera or portal backend |
| 9 | **`TextView.setEllipsize` is empty**, so long text is cut instead of ending in "...". | Stub log: DB Navigator, Post & DHL | Toolbars, lists | small |
| 10 | **WebView has gaps.** `loadUrl` with headers is absent, `CookieManager.setCookie` is a stub, and all `WebSettings` setters but JavaScript do nothing, as do `stopLoading`, `clearHistory`, `clearCache` and `destroy`. | Lists; `WebSettings` and clear calls in DB Navigator's stub log | Hybrid pages in Post & DHL and DB Navigator; a login that sets cookies may fail | medium |

Not problems, but the lists are full of them: accessibility, autofill, status
bar colours and `WindowInsets` zeros are stubs on purpose today. NFC and
Bluetooth adapters are `null`, which correct apps handle as "no such hardware".

# Chooser

`com.uravgcode.chooser` 1.5.0. No Google services. Compose.

| Feature | State | Evidence |
|---|---|---|
| Onboarding | `headless` | Walk: main screen after 5 pages |
| Choose with several fingers | `phone` | Owner, 2026-10-28 |
| Vibration | `headless` | feedbackd checked with a test harness, 2026-10-30; the owner confirmed choosing, not the vibration itself |
| Mode and number buttons | `untested` | Buttons show; not tapped |
| Settings | `untested` | |

Result: complete apart from the two untested rows. Nothing it needs is missing.

# Blokish

`org.scoutant.blokish` 3.6. Smoke test, not a target app.

| Feature | State | Evidence |
|---|---|---|
| Start, pieces shown | `headless` | Walk |
| Board | `wrong` | Black, pieces run off the right edge; `Measure WrapperWidget` warning, known since 2026-10-28 |
| Moving a piece | `untested` | `View.bringToFront` and `getAnimation` are stubs and it uses them |
| Animations | `stub` | `Animation.setDuration`, `setInterpolator`, `setStartOffset`, `setRepeatCount` |
| Settings | `missing` | `PreferenceFragment` absent (3 own call sites), opening it would crash |

Result: not usable as a game.

# F-Droid

`org.fdroid.fdroid` 2.0.2.

| Feature | State | Evidence |
|---|---|---|
| Index update | `headless` | 2026-10-28. Still downloading at the end of both walks of this audit, so not compared |
| Details | `headless` | 2026-10-28 |
| Search | `untested` | Crash on the first key fixed; no test after the fix on record |
| Install, update, uninstall | `headless` | Install and uninstall; updates `untested` |
| Open an installed app | `headless` | |
| Overflow menu, tabs | `headless` | Walk: All apps, Nearby, About |
| Nearby (swap) | `missing` | `BluetoothDevice`, `WifiConfiguration`, `MulticastLock`, `UsbDevice`, `WifiInfo` methods; called from its own code |
| QR scanning | `missing` | Camera |
| Auto-updates | `untested` | `UserManager.hasUserRestriction` missing (8 own calls), `PackageInstaller.Session.requestUserPreapproval` missing, `SessionParams` setters stubs |
| Files picked by the user | `untested` | A `ContentResolver.openOutputStream` variant and `takePersistableUriPermission` are missing, called from its own code |
| Internet state in a sandbox | `wrong` | "No internet" in its Flatpak ([hardening roadmap](hardening-roadmap.md)) |

Result: the store works. Nearby is out of reach. Add the small `UserManager`
and `ContentResolver` methods before they crash settings.

# Aurora Store

`com.aurora.store` 4.8.4.

| Feature | State | Evidence |
|---|---|---|
| Onboarding | `wrong` | Problem 6: the app closes at its end |
| Anonymous login, details, download, install | `headless` | 2026-10-28, needs a device profile |
| Search | `untested` | Typing was not testable headless; no owner result on record |
| Updates | `untested` | |
| Biometric lock | `missing` | `androidx.biometric` paths, `BiometricPrompt` absent |
| Device admin installer | `missing` | `DeviceAdminReceiver` absent; other installers work |

Result: the store works. Its own code has only a handful of entries.

# DB Navigator

`de.hafas.android.db` 26.20.0.

| Feature | State | Evidence |
|---|---|---|
| Cookie consent, onboarding | `headless` | Walk |
| Rooted device notice | `wrong` | Black behind the box (translucent activities, [app status](apps.md)) |
| Journey search | `phone` | Owner, 2026-10-29. This walk stopped at "The process is taking longer than usual" and did not reach the form, not compared |
| Login | `untested` | Fixed 2026-10-30, a real login not confirmed on record |
| Tickets | `phone` | Visible, owner, 2026-10-30 |
| Date and time picker | `untested` | `NumberPicker` absent (63 obfuscated call sites). Would crash if the app's picker uses it. **Test first** |
| Nearby stations, map | `untested` | Problem 2; location off by default |
| Whatever needs the camera | `missing` | Manifest asks for it; what uses it was not traced |
| Whatever needs NFC | `missing` | Adapter `null`; same |
| Push messages | `out of scope` | FCM |
| Dark mode | `headless` | 2026-10-29 |
| Share, copy | `headless` | 2026-10-30 |

Result: the core works on the phone. The date picker is the biggest unknown.

# Post & DHL

`de.dhl.paket` 10.1.0.44.

| Feature | State | Evidence |
|---|---|---|
| Start, tabs | `headless` | Walk tapped all five tabs, no crash in the log |
| Tab highlight | `wrong` | Problem 4 is the likely cause of "selected tab not red"; not yet proven |
| Tracking without login | `headless` | WebView page looks like Android; a real number `untested` |
| Barcode scan of a parcel | `missing` | Camera |
| Frankieren | `headless` | WebView |
| Standorte | `wrong` | List empty; location is off (problem 2), likely the cause, not proven |
| Packstation | `wrong` | Compose cards 200 px wide |
| Mehr, login button | `wrong` | No arrows, white button without text |
| Login | `untested` | `WebView.loadUrl` with headers missing (3 own calls), cookie stubs (problem 10) |
| Identity check (AusweisApp) | `missing` | NFC |
| Device check | `untested` | RootBeer's effect still open |
| Whatever needs Bluetooth LE | `missing` | `bluetooth.le` absent; what uses it was not traced |
| Push messages | `out of scope` | FCM |

Result: usable for reading, not yet finished. Most open rows have a known
cause.

# komoot

`de.komoot.android` 2026.44.2, minSdk 32.

| Feature | State | Evidence |
|---|---|---|
| Start, privacy consent | `headless` | Consent page with Accept, Refuse and Customize ([screenshot](screenshots/komoot-privacy-2026-11-07.png)), Refuse leads on, 2026-11-07 |
| Start page | `headless` | Google, Facebook and email buttons, 2026-11-07 |
| Sign-up and sign-in with email | `headless` | The email page ([screenshot](screenshots/komoot-email-2026-11-07.png)) sends the address; for a test address the server answered with its sign-up page ([screenshot](screenshots/komoot-sign-up-2026-11-07.png)). No account was made; a real sign-in is an owner test |
| Text fields | `wrong` | The hint "username@email.com" is drawn as dark as typed text; Next and Sign Up keep their disabled look after text was filled in, but react |
| Check boxes | `wrong` | GTK's blue check box instead of komoot's, and its text is cut at the right edge |
| Sign-in with Google | `out of scope` | Google sign-in |
| Sign-in with Facebook | `untested` | |
| Map | `untested` | MapLibre (OpenGL ES); reached only after sign-in |
| Location | `untested` | Needs the owner's phone and sign-in |
| Route planning, navigation, voice | `untested` | Speech: `TextToSpeech` gaps in the list |
| Offline maps | `untested` | `MapDownloadService` |
| Watches and sensors (Bluetooth LE) | `untested` | `BluetoothLeAdvertiser` and others missing |
| Number pickers | `missing` | `NumberPicker` (51 own calls) |
| Drag and drop | `missing` | `DragEvent` |
| Web pages in the app | `missing` | `WebResourceError`, `WebViewClient.onReceivedError` and more (23 own calls) |
| Premium purchases | `out of scope` | Google Play billing |

Result: starts and reaches sign-in; the map and everything after sign-in need the owner's account on the phone.

# Duolingo

`com.duolingo` 6.103.6.

| Feature | State | Evidence |
|---|---|---|
| Welcome screen | `wrong` | Buttons flat, text clipped, wrong colours. Auto-size text and problems 4 and 5 are the candidates |
| GET STARTED | `headless` | Opens the coach screen ([screenshot](screenshots/duolingo-coach-2026-10-31.png)). Its speech bubble is too narrow and CONTINUE was not followed further |
| Sign-in screen | `headless` | Opens, 2026-10-31 |
| Sign-in | `headless` | With random data the server answers "Wrong username or password" ([screenshot](screenshots/duolingo-wrong-password-2026-10-31.png)), so the request goes out. That text is also the server's answer to a failed Play Integrity check (`401`), and the test ran with upstream's GMS stub on, so it does not show whether a correct password would have passed. With the stub off (default since 2026-11-02) the login should work; fallback is the password reset inside the app ([Duolingo login](duolingo-login.md)). A correct login is an owner test |
| Sign-in with Google | `missing` | No abort any more; the app says "Unable to sign in with Google", there are no Google Play services |
| Sign-in with Facebook | `wrong` | No abort any more, but a black screen follows. Cause not looked at |
| Lessons | `untested` | Depends on sign-in or GET STARTED |
| Sounds | `untested` | No crash after the `MediaPlayer` work, unheard headless |
| Speaking exercises | `missing` | `SpeechRecognizer` absent (18 own calls), `RECORD_AUDIO` off |
| Video | `missing` | `VideoView` absent (13 own calls) |
| Picture in picture | `missing` | `PictureInPictureParams$Builder` |
| Calendar reminders | `missing` | `CalendarContract`, `ContentProviderOperation` |
| Dialogs with lists | `missing` | `AlertDialog.Builder.setSingleChoiceItems` (11 own calls), `setMultiChoiceItems`, `setAdapter` |
| Screens with a number picker | `missing` | `NumberPicker` (12 own calls) |
| Purchases | `out of scope` | Google Play billing |

Result: not usable yet, but sign-in and the first steps are open to testing. The look of buttons and text is the next visible problem.

# Gadgetbridge

`nodomain.freeyourgadget.gadgetbridge` 0.95.1. ATL reports `SDK_INT` 23 (its
minSdk), so Gadgetbridge uses the pre-Android-8 Bluetooth paths and no
companion device manager. Gap list: `app-audit/nodomain.freeyourgadget.gadgetbridge.gaps.tsv`.
No stub log yet.

| Feature | State | Evidence |
|---|---|---|
| Install from F-Droid or the APK file | `headless` | Install page accepts the APK since the v3 signer fix (2026-11-02); F-Droid's compatibility check uses the now filled `getSystemAvailableFeatures` |
| Start, welcome pages | `headless` | All five pages, 2026-11-02; the changelog dialog on first start is empty |
| Permissions page | `wrong` | Lists calendar, contacts, location, do-not-disturb, notification access and more as not granted; requesting them does nothing useful |
| Device discovery | `headless` | BLE scan and classic discovery through BlueZ found the owner's Amazfit Balance as supported and bonded ([screenshot](screenshots/gadgetbridge-discovery-2026-11-02.png)); BlueZ's discovery stops afterwards |
| Pairing, connecting (BLE GATT) | `headless` | With the real auth key and the real watch (2026-11-02): pairing through ATL's pairing agent and code dialog, Zepp OS authentication, one GATT connection, automatic reconnect after `bluetoothctl disconnect`. Suspend and out of range are an owner test ([app status](apps.md#needs-owner)) |
| Sync | `headless` | Sync button: activity, sleep and temperature fetched in 11 rounds as on Android (2026-11-02) |
| Devices needing a GATT server on the phone | `missing` | `BluetoothManager.openGattServer` returns null |
| Classic Bluetooth devices (RFCOMM) | `missing` | `BluetoothSocket.connect` throws |
| Forwarding phone notifications | `headless` | With `ATL_ENABLE_NOTIFICATION_LISTENER=1` and the watch connected (2026-11-03): a Chatty notification reached Gadgetbridge's listener under its desktop entry id with the app name as label, was not filtered (no "Not forwarding" or "Ignoring" line) and was closed again; whether the watch shows it is the owner's test. Gadgetbridge's own status notifications now carry its package name and are no longer forwarded. By default Gadgetbridge forwards only while the screen is off, which ATL reads from Phosh |
| Calls on the watch | `missing` | No telephony state on the phone side |
| Music control | `missing` | `MediaController$TransportControls`, `AudioManager.dispatchMediaKeyEvent` |
| Do not disturb | `missing` | Reported as not granted, the desktop owns it |
| Settings with number pickers | `missing` | `NumberPicker` (170 own calls) |
| Quick settings tiles | `out of scope` | No Android quick settings on Phosh |
| Background sync, alarms | `untested` | Needs the watch |

Result: the app runs and finds the watch; whether it connects and syncs is
the owner's test.

## Gadgetbridge: permissions and data sources

How ATL answers each permission or special access Gadgetbridge checks
(manifest of 0.95.1, `PackageManager.checkPermission`, 2026-11-03). Today
`requestPermissions` answers at once with these values, without a dialog
(phase 4 of the [hardening roadmap](hardening-roadmap.md) replaces this).

| Gadgetbridge use | Permission or check | ATL today | Decision |
|---|---|---|---|
| Bluetooth scan, connect | `BLUETOOTH*` | granted when BlueZ has an adapter | keep; ask once per app with phase 4 |
| Notification forwarding | notification listener access (`enabled_notification_listeners`) | only with `ATL_ENABLE_NOTIFICATION_LISTENER=1` | special access as on Android: a per-app switch, stored, env var stays as test override (phase 4) |
| Own notifications | `POST_NOTIFICATIONS` | granted | keep |
| Notification source names | `PackageManager.getApplicationInfo` for desktop apps | label of desktop apps that sent a notification | done (desktop entry id as package name) |
| Do not disturb sync | `ACCESS_NOTIFICATION_POLICY` | not granted | honest "not available"; Phosh's DND is a GSettings key, mapping later (S) |
| Weather, sunrise | `ACCESS_*_LOCATION` | only with `ATL_UGLY_ENABLE_LOCATION` | portal-backed location with a dialog (phase 4); `LocationManager` updates are stubs (M) |
| Caller names | `READ_CONTACTS`, `ContactsContract$PhoneLookup` | denied, class missing | later: EDS address book behind the dialog (M) |
| Calls on the watch | `READ_PHONE_STATE`, `READ_CALL_LOG`, `ANSWER_PHONE_CALLS`, `CALL_PHONE` | denied, `getCallState` stub | later: ModemManager or GNOME Calls over D-Bus (L) |
| SMS on the watch | `RECEIVE_SMS`, `SEND_SMS`, `SmsManager` | denied, class missing | later: ModemManager messaging (L) |
| Calendar events | `READ_CALENDAR`, `CalendarContract` | denied, classes missing | later: EDS calendars (M) |
| Media control | `MEDIA_CONTENT_CONTROL` | denied | later: MPRIS (M) |
| Exact alarms, overlays, battery | `SCHEDULE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`, battery optimisation | answered as allowed | keep (no such limits on the desktop) |
| QR code pairing for some devices | `CAMERA` | denied (not handled) | portal camera with phase 4 |

Sizes: S under a day, M a few days, L a week or more. Denied permissions do
not crash Gadgetbridge; its permissions page lists them as not granted.
