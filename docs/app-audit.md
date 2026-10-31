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
that is what was installed. It is not complete:

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
| Chooser | `com.uravgcode.chooser` | 0 + 1 | 314 + 171 | 65 |
| Blokish | `org.scoutant.blokish` | 3 + 15 | 1305 + 476 | 73 |
| F-Droid | `org.fdroid.fdroid` | 47 + 19 | 788 + 383 | 87 |
| Aurora Store | `com.aurora.store` | 1 + 2 | 944 + 441 | 72 |
| DB Navigator | `de.hafas.android.db` | 0 + 0 | 1206 + 577 | 204 |
| Post & DHL | `de.dhl.paket` | 6 + 13 | 1281 + 529 | 149 |
| Duolingo | `com.duolingo` | 77 + 152 | 1655 + 650 | 79 |

Most "in total" entries come from libraries and hardware paths never reached.
DB Navigator's own code is entirely obfuscated, so its first column says
nothing; use its `obfuscated` column.

# Problems across apps

Ordered by how much they break. All were found in this audit.

| # | Problem | Evidence | Affects | Size |
|---|---|---|---|---|
| 1 | **Password fields show the password.** `EditText` is a plain `GtkText`; `setInputType` and `setTransformationMethod` are empty, so masking, number keyboards, multi-line and `setFilters` limits do nothing. Compose and WebView fields are not affected. | Code; Duolingo's dex uses `PasswordTransformationMethod`, and its `StarterInputUnderlinedView` calls `setInputType` | Duolingo sign-in, any classic form | small to medium |
| 2 | **A permission request is never answered.** `Activity.requestPermissions` only logs, so `onRequestPermissionsResult` never comes. `checkPermission` denies everything unlisted; location and microphone need `ATL_UGLY_ENABLE_LOCATION` and `ATL_UGLY_ENABLE_MICROPHONE`. | Code. No walk reached a request | Location, microphone, camera and notification prompts in all apps | medium, belongs to the [hardening roadmap](hardening-roadmap.md) |
| 3 | **Missing methods above `--sdk-int` crash.** Apps with minSdk 32 call API 27 to 32 without a version check, and `--sdk-int 26` does not protect them. | `Vibrator.hasAmplitudeControl` crashed a run and was on Duolingo's static list, called from obfuscated code | Duolingo, Post & DHL, DB Navigator | small per method |
| 4 | **Drawable states are ignored.** `View.onCreateDrawableState`, `setSelected`/`isSelected` and `isPressed` are empty, so `selected`, `checked` and `pressed` variants never show. | Stub log; code | Post & DHL tabs, Duolingo buttons, ripple and press feedback | small to medium |
| 5 | **View transforms do nothing.** `setScaleX/Y`, `setRotation`, `setPivotX/Y` are empty, as are `setClipToOutline` and `setForeground`. | Stub log: `setForeground` in 5 apps, `setScaleX` in DHL; Duolingo's own code calls them often | Animations, rounded corners, overlays | medium |
| 6 | **Restarting an app closes it.** ATL runs everything in one process, so ProcessPhoenix's helper activity kills the app and nothing starts it again. | Aurora headless, log `the app called killProcess on itself` | Aurora ends after onboarding and must be reopened | small |
| 7 | **`MediaPlayer` is mostly stubs.** The prepared, error and buffering listeners are never stored, and `setLooping`, `setVolume`, `stop` and `release` do nothing, and `isPlaying` is always false. An app waiting for `onPrepared` would hang. | Code; Duolingo's own code calls them | Audio in apps using `MediaPlayer`; SoundPool has gaps too | medium |
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

# Duolingo

`com.duolingo` 6.103.6.

| Feature | State | Evidence |
|---|---|---|
| Welcome screen | `wrong` | Buttons flat, text clipped, wrong colours. Auto-size text and problems 4 and 5 are the candidates |
| GET STARTED | `missing` | **Crashes** in both walks: `NoSuchMethodError: Vibrator.hasAmplitudeControl` aborts through JNI (problem 3) |
| Sign-in screen | `headless` | Opens, 2026-10-31 |
| Sign-in | `wrong` | **Password visible** (problem 1). Whether Play Integrity is required is still open |
| Lessons | `untested` | Depends on sign-in or GET STARTED |
| Sounds | `stub` | Problem 7 |
| Speaking exercises | `missing` | `SpeechRecognizer` absent (18 own calls), `RECORD_AUDIO` off |
| Video | `missing` | `VideoView` absent (13 own calls) |
| Picture in picture | `missing` | `PictureInPictureParams$Builder` |
| Calendar reminders | `missing` | `CalendarContract`, `ContentProviderOperation` |
| Dialogs with lists | `missing` | `AlertDialog.Builder.setSingleChoiceItems` (11 own calls), `setMultiChoiceItems`, `setAdapter` |
| Screens with a number picker | `missing` | `NumberPicker` (12 own calls) |
| Purchases | `out of scope` | Google Play billing |

Result: not usable yet. GET STARTED is a one-line fix; it may not be the last.
