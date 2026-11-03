---
type: Concept
title: Google Play services compatibility
description: Which Google Play services, Firebase, Play Core and billing APIs the tested apps ship, which of them could work locally and which need Google's servers, why a local GMS needs Google's certificate, how ATL reports GMS per app (ATL_GMS_STUB), and the plan for local fused location and Maps.
resource: ../src/api-impl/android/atl/ATLToggles.java
tags: [gms, google-play-services, firebase, location, maps, compatibility]
generated: { by: claude-code/2.1.312, at: 2026-11-01T23:30:00Z }
sources:
  - id: microg-sigspoof
    resource: https://github.com/microg/GmsCore/wiki/Signature-Spoofing
    title: microG wiki, Signature Spoofing
  - id: connection-result
    resource: https://developers.google.com/android/reference/com/google/android/gms/common/ConnectionResult
    title: Google APIs for Android, ConnectionResult error codes
  - id: fcm-no-gms
    resource: https://firebase.google.com/docs/cloud-messaging/android/client
    title: Firebase, set up an FCM client app on Android (needs Google Play services)
  - id: mlkit-bundled
    resource: https://developers.google.com/ml-kit/vision/barcode-scanning/android
    title: ML Kit barcode scanning on Android, bundled and unbundled models
---

# Scope

[Decision](decisions/gms-local-only.md) of 2026-11-01: GMS APIs that work
on the phone alone are in scope, anything that needs Google's servers is
out, ATL never contacts Google's servers and never fakes attestation.
Amended 2026-11-02: ATL may report Google's certificate for its own
local GMS, as microG does.

# How apps see GMS

Apps do not talk to Google Play services directly. They ship client
libraries (`com.google.android.gms.*`, `com.google.firebase.*`) that
look up the package `com.google.android.gms` and bind to its services.
Before that, `GooglePlayServicesUtilLight.isGooglePlayServicesAvailable`
reads the package's certificate and compares it with Google's
(`GoogleSignatureVerifier`). Disassembly of Duolingo's copy shows the
log text "requires Google Play services, but their signature is
invalid." and the result `SERVICE_INVALID` (9)[^connection-result]. A
missing package gives `SERVICE_MISSING` (1). Every GMS API client,
Dynamite module (Maps, Mobile Vision, unbundled ML Kit) and Firebase
Cloud Messaging goes through this check. microG needs signature
spoofing for the same reason[^microg-sigspoof].

Consequence: a local implementation behind the GMS interfaces (the
way microG works) is only reachable when ATL reports Google's
certificate for it, which the amended decision allows.

## ATL_GMS_STUB

Upstream ATL reports `com.google.android.gms`, `com.google.android.gsf`
and `com.android.vending` as installed, backed by `gstub.jar` (a no-op
`ProviderInstaller`, a Maps creator that returns no map), and adds
Google's certificate to them and to an installed microG
(`ATLSigHelper`). Apps then believe GMS is present, but nearly all of
its services are missing.

This fork makes it a per-app choice: the toggle `ATL_GMS_STUB`
(`android.atl.ATLToggles`, environment variable, default off) turns
upstream's behaviour on, and `--install` keeps it in the app's
launcher. Per app and not global, because no app may get worse than
without GMS: an app gets it once it was compared with and without.
With it off, apps see a phone
without GMS: `NameNotFoundException` for the three
packages, and no Google certificate anywhere. An installed microG is
then also reported with its own certificate, which the client
libraries reject.

Apps that need it by default are listed in `GMS_STUB_APPS`
(`ATLToggles.gmsStub()`); `ATL_GMS_STUB=0` still turns it off for them.
DB Navigator is the first (2026-11-03): without the stub, the client
library's failed location and messaging connections ended in Google's
error "DB Navigator won't run without Google Play services, which are
not supported by your device" (`SERVICE_INVALID`) on the phone, while
with it (upstream's behaviour, as on 2026-10-28..30) the app ran.

`ghax.jar` stays: it replaces `AdvertisingIdClient` in the app with one
that returns a zero advertising ID and "limit ad tracking", locally.

# Gap list

Static scan of the APKs in `~/work/apks` on 2026-11-01 (classes the app
ships and methods it calls, by package; scanner in the agent's scratch
directory, `gms-scan.py`). Apps without any GMS, Firebase or Play
library: CoMaps, Gadgetbridge, Super Productivity, FluffyChat, Aurora
Store (Super Productivity names the GMS photo picker intent, androidx
falls back to the document picker; Aurora only offers microG for
download).

| API | Apps | Local or server | Plan |
|---|---|---|---|
| Availability check (`GoogleApiAvailability`, `GooglePlayServicesUtil`) | DB, DHL, Duolingo, WhatsApp | local | ATL reports no GMS; the apps log "requires the Google Play Store, but it is missing" and get `SERVICE_INVALID` (9) or `SERVICE_MISSING` (1), as on a phone without GMS |
| Tasks (`com.google.android.gms.tasks`) | DB, DHL, Duolingo, WhatsApp | local, in the app | runs inside the app's own copy, nothing to do |
| Fused location, settings, geofencing (`gms.location`) | DB, DHL, Duolingo, WhatsApp | local in principle | local service in `gstub.jar` on `LocationManager`, see [plan](#plan-fused-location-and-maps); without it apps use `LocationManager`: DB and WhatsApp request updates, DHL and Duolingo only read the last known location. ATL's `LocationManager` is the work of the CoMaps agent |
| Maps (`gms.maps`) | DB, DHL, WhatsApp | local in principle (MapLibre and OpenStreetMap, as in microG) | later, see [plan](#plan-fused-location-and-maps); today the app shows its "no Google Play services" placeholder |
| ML Kit barcode, bundled model (`mlkit.vision.barcode.bundled`, `libbarhopper_v3.so`) | DB | local, in the app | runs without GMS[^mlkit-bundled]; not tested yet |
| ML Kit unbundled (barcode, document scanner, GenAI) | WhatsApp | model from GMS | not reachable; out |
| Mobile Vision (`gms.vision`: barcode, face, text) | DHL, WhatsApp | local in principle | Dynamite module, not reachable; DHL's barcode scan needs another path |
| Security provider (`gms.security.ProviderInstaller`) | DB, DHL, Duolingo | local | `GooglePlayServicesNotAvailableException`, apps keep the system TLS provider |
| Firebase core, installations, analytics, Crashlytics, Remote Config, sessions, data transport | DB, DHL, Duolingo, WhatsApp | app talks to Firebase's servers itself, not through GMS | not a GMS question; runs as in the app |
| Firebase Cloud Messaging, Instance ID, `gms.cloudmessaging` | DB, DHL, Duolingo, WhatsApp | server only[^fcm-no-gms] | fails as without GMS (`SERVICE_NOT_AVAILABLE` / `MISSING_INSTANCEID_SERVICE`); no push |
| Firebase Auth, Realtime Database, App Check | DHL | server (Firebase) | app's own business; App Check with Play Integrity fails |
| Google sign-in, Credential Manager, One Tap, Block Store, FIDO / passkeys (`gms.auth`, `gms.identity`, `gms.fido`, `androidx.credentials.playservices`) | DB, DHL, Duolingo, WhatsApp | server (Google account) | out; fails as without GMS |
| SMS Retriever (`auth.api.phone`) | Duolingo, WhatsApp | local in principle, needs GMS | not reachable; apps fall back to typing the code |
| Play Integrity (`play.core.integrity`) | Duolingo, WhatsApp | server only | out; no tokens |
| Play billing (`com.android.billingclient`) | Duolingo, WhatsApp | server (Play Store) | out; billing client reports Play Store missing |
| Play In-App Review, In-App Update, Age Signals (`play.core.review`, `play.appupdate`, `play.agesignals`) | DB, DHL, Duolingo, WhatsApp | Play Store | out; fails as without Play Store |
| Ads, measurement, App Set ID, Advertising ID (`gms.ads`, `gms.measurement`, `gms.appset`, `ads.identifier`) | Duolingo (ads), DB (ID) | server; the advertising ID is local | `ghax` answers the advertising ID with zeros; the rest out |
| Places (`location.places`) | Duolingo | server | out |
| Wallet (`gms.wallet`) | DHL | server | out |
| Wearable, Nearby (`gms.wearable`, `gms.nearby`) | DB, WhatsApp | local in principle, needs GMS | not reachable; no watch pairing |
| Cronet from GMS (`gms.cronet`) | Duolingo | local | not reachable; the app uses its own HTTP stack |
| Phenotype, flags, Clearcut logging, module install | DB, DHL, Duolingo | server | out |

# Measurements

Headless runs on 2026-11-02, 150 s after the start, own data
directories, once with `ATL_GMS_STUB=1` (upstream) and once without
(this fork):

| App | With the stub | Without (default) |
|---|---|---|
| DB Navigator | cookie dialog | same screen; log: "requires the Google Play Store, but it is missing" |
| Post & DHL | tracking start screen | same screen, fewer GMS lines in the log |
| Duolingo | welcome screen; `GmsClient` binds to Block Store and other GMS services that ATL does not have ("no matching service found") | same screen; client libraries stop early with `SERVICE_INVALID` / `NameNotFoundException` instead of binding |

No app was worse without the stub on its first screen. Deeper screens
that use GMS (DHL's Standorte map, DB Navigator's location search) were
not reached headless and are still to be compared.

# Services ATL lacks

With `ATL_GMS_STUB=1` the apps bind to GMS services that `gstub.jar`
does not have (Duolingo: Block Store and others). ATL's `bindService`
finds no matching service and returns `false` at once; the client
library logs "unable to connect to service" and fails the call with a
connection error, the same path as a GMS without that API. No hang was
seen (Duolingo's welcome screen is the same in both modes). Every new
service in `gstub.jar` must keep this: answer or refuse, never leave a
call without an answer.

# Plan: fused location and Maps

All four apps reach fused location the same way: bind
`com.google.android.location.internal.GoogleLocationManagerService.START`
in `com.google.android.gms`, get the service from `IGmsServiceBroker`,
then call `IGoogleLocationManagerService` (strings present in all four
APKs; DB and DHL ship the newer client with `ILocationStatusCallback`).
One service therefore serves all four, independent of R8 renaming.

Chosen path: **port microG's broker and location service code into
`gstub.jar`** (Apache-2.0, attributed in the files), on top of ATL's
`LocationManager`. Running microG's whole GmsCore inside ATL was
rejected: it is a large Kotlin app with its own network location
backends, accounts and many system APIs ATL lacks, while the apps need
only one binder service.

Steps, each compared with and without `ATL_GMS_STUB` before an app
gets it:

1. `gstub` package declares the `GoogleLocationManagerService` action;
   ATL binds it in-process to a Java `IGmsServiceBroker` that answers
   `getService` for location and refuses every other service id at
   once (`API_UNAVAILABLE`).
2. `IGoogleLocationManagerService`: last location, location updates
   for both client generations (`LocationRequestUpdateData` and the
   newer `registerLocationUpdates`), location availability, and
   `SettingsClient.checkLocationSettings` answered "satisfied";
   geofencing refused. Parcels follow microG's `SafeParcelable`
   readers and writers, hand-written like `CreatorImpl` (no AIDL
   compiler in the build).
3. Fixes come from ATL's `LocationManager` (GPS through GeoClue, being
   extended by the CoMaps work); no network location, nothing sent to
   any server.
4. Maps only after that: microG's MapLibre backend needs the MapLibre
   Android SDK with native libraries and an OpenStreetMap tile source
   with its usage terms, a separate, larger step. Until then the Maps
   creator keeps returning no map.
