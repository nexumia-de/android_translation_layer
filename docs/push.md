---
type: Concept
title: Push messages
description: How the tested apps receive push messages (Firebase Cloud Messaging, UnifiedPush, their own connection), why FCM needs Google's servers, how ATL bridges Android UnifiedPush to a Linux distributor over D-Bus, and which Linux distributors fit the phone.
resource: ../src/api-impl-jni/content/android_content_Context.c
tags: [push, unifiedpush, ntfy, fcm, gms, notifications]
sources:
  - id: kup-1.0
    resource: https://blogs.kde.org/2024/10/19/kunifiedpush-1.0.0-is-out/
    title: KDE blog, KUnifiedPush 1.0.0 is out
  - id: up-kup
    resource: https://unifiedpush.org/users/distributors/kunifiedpush/
    title: UnifiedPush, KUnifiedPush distributor
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
  distributor's immediate `NewEndpoint` is not lost. When D-Bus starts
  the app for a push, the connector exists before the name is owned and
  holds calls until `Application.onCreate` has run (at most 20 s, then
  it answers with an error).
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

An app's c2dm intents and services of `com.google.android.gms` that ATL
does not run in the app go over D-Bus to an external Google Play
services (upstream's GCM service, which talks to Google). Without the
app's `atl.google-services`, or with no such service on the session bus,
ATL drops them; a `REGISTER` or `UNREGISTER` gets `REGISTRATION` with
`error=SERVICE_NOT_AVAILABLE` at once, as Google Play services answers
when it cannot reach Google. Firebase then retries with backoff and the
app carries on without push. Without `ATL_GMS_STUB` the client libraries
already stop before that (`MISSING_INSTANCEID_SERVICE`).

## Linux distributors

On Alpine edge (2026-11-03): `kunifiedpush` (KDE's distributor daemon,
the only Linux D-Bus distributor listed by unifiedpush.org; servers:
ntfy, Gotify, NextPush, Mozilla autopush; needs Qt 6 and KDE Frameworks
6), `ntfy` (server and client, no D-Bus distributor) and `gotify`
(server). ntfy fits as the server: one HTTP service, one WebSocket from
the phone for all apps, and the same server serves Android phones
through the ntfy app. While the phone is suspended no push arrives on
Phosh unless the modem or Wi-Fi wakes it.

### KUnifiedPush on the phone

Installed by the owner on 2026-11-03 (26.08.2). It starts at login
(`/etc/xdg/autostart/org.kde.kunifiedpush-distributor.desktop`), owns
`org.unifiedpush.Distributor.kde` and reads its settings with QSettings:
`~/.config/KDE/kunifiedpush-distributor.conf` over
`/etc/xdg/KDE/kunifiedpush-distributor.conf` (`[PushProvider] Type=Ntfy`,
`[Ntfy] Url=`; the system file points to KDE's ntfy server,
`https://unifiedpush.kde.org/`). It stores its clients in the same file.
That server is the default for ATL too (owner decision of 2026-11-03,
replacing ntfy.sh): KDE names it KUnifiedPush's default provider[^kup-1.0],
and no terms of use or usage limits are published for it (checked
2026-11-03: the KDE announcement, unifiedpush.org's KUnifiedPush
page[^up-kup] and the server itself, a plain ntfy without accounts or
login, `config.js`); nothing restricts it to KDE apps. Its settings
page is a Plasma KCM, so on Phosh a user who wants another server sets
it with `atl-push-server` (installed with ATL):

    atl-push-server get | set URL | reset

A running distributor is changed over D-Bus,
`org.kde.kunifiedpush.Management.setPushProvider("Ntfy", {"Url": <url>})`
on `/org/unifiedpush/Distributor`, which saves the setting and moves the
registered apps to the new server; otherwise the per-user file is
written. `reset` moves a running distributor back to the system default
and removes the per-user `Url`, so the system setting applies again.
The settings page for Android apps will call the same helper.
Test: `src/push/atl-push-server-test.sh` (private bus, own config).

Headless on 2026-11-03, private session bus, local ntfy 2.33
(`apk fetch`, no root) and the real distributor: a test APK registered
through ATL's bridge, got the ntfy endpoint, and an HTTP POST to it
arrived as `MESSAGE`. After `UNREGISTER` KUnifiedPush called
`Unregistered` with an empty token.

Cost, measured on the phone after 60 s idle with one connection open:

| Process | RSS | of it anonymous | PSS | CPU idle |
|---|---|---|---|---|
| `kunifiedpush-distributor` | 109 MB | 6 MB | 50 MB | 1 tick (10 ms) per minute |
| `ntfy serve` (only for a local server) | 34 MB | | | |

Most of its RSS are the Qt 6 and KDE Frameworks libraries, shared with
any other Qt app; with none running, about 50 MB of RAM (PSS) is what
push costs on the phone.

[^kup-1.0]: KDE, "KUnifiedPush 1.0.0 is out", 2024-10-19: "The default provider of KUnifiedPush is Ntfy with unifiedpush.kde.org", <https://blogs.kde.org/2024/10/19/kunifiedpush-1.0.0-is-out/>
[^up-kup]: UnifiedPush, KUnifiedPush distributor page, <https://unifiedpush.org/users/distributors/kunifiedpush/>
