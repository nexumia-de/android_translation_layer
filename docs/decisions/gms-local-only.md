---
type: Decision Record
title: Satisfying apps' device-integrity and anti-tamper checks (owner-configurable)
description: Accepted - ATL may disguise its environment and provide a local GMS, Google's certificate and integrity/attestation responses so apps' client-side anti-tamper and device-integrity checks pass; each is owner-configurable, off by default, turned on per app by an environment variable; the responses are produced on the phone, nothing is sent to Google's servers.
tags: [decision, gms, play-integrity, safetynet, anti-tamper, rasp, disguise, scope]
status: accepted
generated: { by: claude-code, at: 2026-11-28T17:30:00Z }
sources:
  - id: microg-sigspoof
    resource: https://github.com/microg/GmsCore/wiki/Signature-Spoofing
    title: microG wiki, Signature Spoofing (why microG needs the system to report Google's certificate)
  - id: gms-availability
    resource: https://developers.google.com/android/reference/com/google/android/gms/common/ConnectionResult
    title: Google APIs for Android, ConnectionResult (SERVICE_MISSING = 1, SERVICE_INVALID = 9)
---

# Status

Accepted by the owner on 2026-11-01 (local GMS APIs), amended on 2026-11-02
(Google's certificate for the GMS package) and on 2026-11-28 (environment
disguise and integrity/attestation responses are owner-configurable
capabilities). The owner owns this fork and sets its stance; this record is
the current one.

# Context

Many apps from Google Play ship Google Play services (GMS) and Firebase
client libraries, and some ship anti-tamper / RASP libraries (DexProtector,
obfuscated protection libraries) that inspect the process before they run.
Those checks look at the process environment — `/proc/self/maps`, the loaded
libraries, system properties, the keystore — and some call Play Integrity or
SafetyNet. Under ATL the process is laid out like a glibc/GTK Linux program,
with host libraries and no `/system` or `/apex` tree, which these checks can
notice.

Upstream ATL ships a local GMS (`gstub.jar`) and reports it, and any
installed microG, as signed with Google's certificate (`ATLSigHelper`);
the client libraries inside apps check that certificate before they use a
GMS API, the same "signature spoofing" microG needs from the
system[^microg-sigspoof].

# Decision

- **Environment disguise is allowed and owner-configurable.** ATL may
  present an app with an Android-like view of its process so client-side
  anti-tamper checks pass: serve an Android-like `/proc/self/maps`
  (`ATL_FAKE_MAPS=<file>`), trace what a protection library reads
  (`ATL_TRACE_OPEN=1`), and present Android-like system properties and a
  `/system`-style library layout. Each knob is off by default and set per
  app, so the shipped engine is unchanged until the owner turns it on.
- **A local GMS and Google's certificate are allowed** (`ATL_GMS_STUB`,
  per app): GMS APIs that can work on the phone run; ATL reports Google's
  signing certificate for its own GMS package as microG's signature
  spoofing does.
- **Integrity and attestation responses are allowed and owner-configurable.**
  ATL may answer Play Integrity and SafetyNet, and key attestation, locally.
  The responses are produced on the phone; how far an app gets depends on
  whether it verifies them server-side: a client-side check passes, while a
  backend that re-verifies the token with Google may still refuse.
- The responses are produced on the phone; ATL does not contact Google's
  servers to produce them.
- An app should never be worse with a capability than without it. Turn each
  one on per app after comparing the app with and without it; see
  [GMS compatibility](../gms-compat.md) and [app status](../apps.md).

# Consequences

- The client libraries in the apps only use a GMS package signed with
  Google's certificate; without it they answer `SERVICE_INVALID` (9) and
  never bind to its services[^gms-availability]. Reporting the certificate
  for ATL's own GMS (`gstub.jar`) is what makes a local GMS reachable.
- A GMS that claims to be present but lacks a service answers at once (bind
  refused) rather than hanging, so an app can take its fallback.
- Environment disguise is staged: `ATL_FAKE_MAPS` gets a protection library
  past the `/proc/self/maps` read, after which it opens and inspects the
  libraries the map lists, so a full Android-like library view is what it
  needs to continue (see [app status](../apps.md): VR SecureGo plus).
- These are configurable capabilities, not defaults: with every knob unset
  the engine behaves exactly as before, so each app keeps its current
  result until the owner enables a capability for it.

[^microg-sigspoof]: microG wiki, Signature Spoofing.
[^gms-availability]: Google APIs for Android, `ConnectionResult`.
