---
type: Concept
title: Google Play services compatibility
description: Which Google Play services, Firebase, Play Core and billing APIs the tested apps ship, which of them could work locally and which need Google's servers, why a local GMS is out of reach without Google's certificate, and how ATL reports GMS (ATL_GMS_STUB).
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
out, and ATL never reports Google's signing certificate or fakes
attestation.

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

Consequence: without Google's certificate, which the decision rules
out, no local implementation behind the GMS interfaces (the way microG
works) can be reached by the apps' client libraries. The only local
GMS features are the ones that run inside the app without GMS anyway.

## ATL_GMS_STUB

Upstream ATL reports `com.google.android.gms`, `com.google.android.gsf`
and `com.android.vending` as installed, backed by `gstub.jar` (a no-op
`ProviderInstaller`, a Maps creator that returns no map), and adds
Google's certificate to them and to an installed microG
(`ATLSigHelper`). Apps then believe GMS is present, but nearly all of
its services are missing.

This fork turns that off by default: the toggle `ATL_GMS_STUB`
(`android.atl.ATLToggles`, environment variable, default off) restores
upstream's behaviour for comparisons. With it off, apps see a phone
without GMS: `NameNotFoundException` for the three
packages, and no Google certificate anywhere. An installed microG is
then also reported with its own certificate, which the client
libraries reject.

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
| Fused location, settings, geofencing (`gms.location`) | DB, DHL, Duolingo, WhatsApp | local in principle | not reachable without Google's certificate (see above); apps use `LocationManager`: DB and WhatsApp request updates, DHL and Duolingo only read the last known location. ATL's `LocationManager` is the work of the CoMaps agent |
| Maps (`gms.maps`) | DB, DHL, WhatsApp | local in principle (MapLibre and OpenStreetMap, as in microG) | Dynamite module, not reachable without Google's certificate; the app shows its "no Google Play services" placeholder |
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

# Open owner decision

A local GMS for the apps (fused location on `LocationManager`, Maps on
MapLibre, Mobile Vision barcodes) is only reachable if ATL reports
Google's certificate for it, per app or globally, as upstream does
today. The decision forbids that; it stays listed under "Needs owner"
in [app status](apps.md). Another path, replacing the client library
classes inside the app (`ghax.jar`), breaks on every library version
and does not work for DB Navigator and WhatsApp, whose GMS classes are
renamed by R8 (method names `a`, `b`, `A00`).
