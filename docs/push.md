---
type: Concept
title: Push messages
description: How the tested apps receive push messages (Firebase Cloud Messaging, UnifiedPush, their own connection), why FCM needs Google's servers, how ATL bridges Android UnifiedPush to a Linux distributor over D-Bus, and which Linux distributors fit the phone.
resource: ../src/api-impl-jni/content/android_content_Context.c
tags: [push, unifiedpush, ntfy, fcm, gms, notifications]
generated: { by: claude-code, at: 2026-11-03T12:00:00Z }
---

# Push messages

## Which app uses what

Scan of the APKs in `~/work/apks` on 2026-11-03 (strings in the dex
files: `FirebaseMessagingService`, `com.google.android.c2dm.intent.RECEIVE`,
`org.unifiedpush.android.connector`):

| App | Push path | Without Google |
|---|---|---|
| DB Navigator, Post & DHL, Duolingo | FCM only | no push; the apps work, messages only when opened |
| WhatsApp | FCM, and its own `MessageService` with keep-alive alarms | its own connection, as on phones without Google Play services (only while ATL keeps the app running) |
| FluffyChat | UnifiedPush connector 3, plus `foss_embedded_fcm_distributor` | UnifiedPush through a distributor; the embedded FCM distributor talks to Google and is dropped by the connector when GMS is absent |
| Chooser, F-Droid, Aurora Store, Blokish, GPSLogger, OwnTracks, CoMaps, Gadgetbridge, Super Productivity, HeliBoard | none (OwnTracks has its own MQTT or HTTP connection) | nothing to do |

## Why FCM cannot be redirected

The app's server sends to `fcm.googleapis.com` with the FCM token the
phone got from Google. Only Google's servers can deliver it, over the
connection Google Play services (or microG's GCM) keep open to
`mtalk.google.com`. A local layer cannot receive these messages without
that connection, and "routing Firebase to ntfy" only works where the
sender can target another endpoint, which is UnifiedPush. The decision
of 2026-11-01 ([GMS local only](decisions/gms-local-only.md)) excluded
Google's servers. Amended 2026-11-03: an app may use Google's servers
(FCM) only after the owner allows it for that app in a warning dialog
(Accept or Deny, denied by default, changeable in the app's info page),
like GrapheneOS's sandboxed Google Play. The permission is called
`atl.google-services`; until the settings dialog exists, ATL treats it
as denied unless `ATL_GOOGLE_SERVICES=1` is set. Play Integrity stays
out.

## UnifiedPush in ATL

UnifiedPush has an Android spec (broadcasts) and a Linux spec (D-Bus:
`org.unifiedpush.Distributor1` on `org.unifiedpush.Distributor.*`,
`org.unifiedpush.Connector1` at `/org/unifiedpush/Connector` on the
app's bus name). ATL maps one onto the other, so Android apps and
native Linux apps share the one distributor of the host:

- `PackageManager.queryBroadcastReceivers(REGISTER)` reports one
  distributor, `org.unifiedpush.distributor.dbus`, when a D-Bus
  distributor is on the session bus or activatable.
- `REGISTER` and `UNREGISTER` broadcasts to it become D-Bus `Register`
  and `Unregister` calls, with the app's package name as its service
  name; a failed `Register` becomes `REGISTRATION_FAILED`.
- The app owns its package name on the session bus (ATL does this for
  every app) and exports `Connector1`; `NewEndpoint`, `Message` and
  `Unregistered` become the connector broadcasts. The launcher's D-Bus
  service file lets the distributor start an app that is not running.
  ATL owns the name and exports `Connector1` before it registers, so the
  distributor's immediate `NewEndpoint` is not lost.
- In a Flatpak the app's bus name is its app id (`$FLATPAK_ID`), the
  only name it may own; `flatpak/make-app.sh` lets apps with `INTERNET`
  talk to `org.unifiedpush.Distributor.*`. Not tested in a Flatpak yet.
- `ATL_UNIFIEDPUSH_DISTRIBUTOR` picks a distributor name when several
  are installed.

Tested headless on 2026-11-03 with a test APK that sends the same
broadcasts as connector 3, a small D-Bus distributor and push server
(scratch script of the push agent): distributor found, `Register`,
`NEW_ENDPOINT` with the endpoint, an HTTP push arrives as `MESSAGE` with its id, `UNREGISTER`
gives `UNREGISTERED`. FluffyChat itself does not start in ATL yet, so
the end-to-end test with a Matrix server is still open.

## Cloud Messaging without consent

With `ATL_GMS_STUB` an app's c2dm `REGISTER` goes over D-Bus to an
external `com.google.android.gms` (upstream's GCM service, which talks
to Google). ATL now answers it at once with `REGISTRATION` and
`error=SERVICE_NOT_AVAILABLE`, as Google Play services does when it
cannot reach Google, when the app lacks `atl.google-services` or no such
service is on the session bus. Firebase then retries with backoff and the
app carries on without push. Without `ATL_GMS_STUB` the client libraries
already stop before that (`MISSING_INSTANCEID_SERVICE`).

## Linux distributors

On Alpine edge (2026-11-03): `kunifiedpush` (KDE's distributor daemon,
the only Linux D-Bus distributor listed by unifiedpush.org; servers:
ntfy, Gotify, NextPush, Mozilla autopush; needs Qt 6 and KDE Frameworks
6, not on the phone yet), `ntfy` (server and client, no D-Bus
distributor) and `gotify` (server). ntfy fits as the server: one
self-hosted HTTP service, one WebSocket from the phone for all apps,
and the same server serves Android phones through the ntfy app. Each
open connection costs battery only while the phone is awake; while
suspended no push arrives on Phosh unless the modem or Wi-Fi wakes it.
