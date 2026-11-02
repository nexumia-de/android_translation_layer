---
type: Decision Record
title: Google Play services APIs only where they work locally
description: Accepted - Google Play services APIs that can work on the phone without Google's servers are in scope; anything that needs Google's servers stays out; ATL never reports Google's signing certificate and never fakes attestation.
tags: [decision, gms, google-play-services, firebase, privacy, scope]
status: accepted
generated: { by: claude-code/2.1.312, at: 2026-11-01T12:00:00Z }
sources:
  - id: microg-sigspoof
    resource: https://github.com/microg/GmsCore/wiki/Signature-Spoofing
    title: microG wiki, Signature Spoofing (why microG needs the system to report Google's certificate)
  - id: gms-availability
    resource: https://developers.google.com/android/reference/com/google/android/gms/common/ConnectionResult
    title: Google APIs for Android, ConnectionResult (SERVICE_MISSING = 1, SERVICE_INVALID = 9)
---

# Status

Accepted by the owner on 2026-11-01. Replaces the line "Apps that need
Google Play Services (Play Integrity, Google sign-in, FCM push) are out
of scope" in `AGENTS.md`.

# Context

Many apps from Google Play ship Google Play services (GMS) and Firebase
client libraries. Some of the APIs behind them could work on the phone
alone (location, maps from OpenStreetMap, barcode scanning, the Tasks
helpers), others exist only as a front end to Google's servers (push
messages, sign-in, Play Integrity, billing).

Upstream ATL ships a fake GMS (`gstub.jar`) and reports it, and any
installed microG, as signed with Google's certificate
(`ATLSigHelper`). The client libraries inside the apps check that
certificate before they use any GMS API; microG needs the same
"signature spoofing" from the system for this reason[^microg-sigspoof].

# Decision

- GMS APIs that can work locally are in scope: an app may use them
  through ATL as long as nothing is sent to Google's servers.
- APIs that need Google's servers (FCM push, SafetyNet and Play
  Integrity, Google sign-in, Play billing, ads) stay out of scope. They
  fail the way they fail on a phone without GMS, so apps take their own
  fallback.
- ATL never reports Google's signing certificate for a package and
  never produces attestation or integrity tokens.
- An app must never be worse than on a phone without GMS. If claiming
  GMS for an app breaks it, ATL does not claim GMS for that app.

# Consequences

- Without Google's certificate the client libraries in the apps answer
  `SERVICE_INVALID` (9) for any GMS package, and do not bind to its
  services[^gms-availability]. A local GMS behind the GMS interfaces,
  the way microG does it, is therefore not reachable for the apps; see
  [GMS compatibility](../gms-compat.md) for the measurement and the
  apps it affects.
- This fork turns upstream's fake GMS and its certificate off by
  default (`ATL_GMS_STUB`, see [GMS compatibility](../gms-compat.md));
  apps see a phone without GMS (`SERVICE_MISSING` or `SERVICE_INVALID`).
- Whether a local GMS with Google's certificate should be allowed for
  single apps is an open owner decision, listed in
  [app status](../apps.md) under "Needs owner".
