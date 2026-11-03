---
okf_version: "0.2"
---

# Android Translation Layer (Phosh fork) knowledge bundle

[Open Knowledge Format](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md)
(OKF v0.2) bundle of this fork. Upstream's own documentation is in
[../doc/](../doc/). Shared Phosh knowledge lives in
[../../phosh-toolkit/docs/index.md](../../phosh-toolkit/docs/index.md).
Change history is in [log.md](log.md).

# Development

* [Fork workflow](fork-workflow.md) - Branches, updating from upstream, how fixes go upstream as GitLab merge requests, and the fixes not yet upstream.
* [Development environment](dev-environment.md) - The owner's phone as test device: separate user, build into `~/.local`, headless test display, required hosts.
* [Back navigation](navigation.md) - Header bar button, edge swipe and Escape/Alt+Left as Android back (KEYCODE_BACK), the ATL_NAVIGATION setting, headless tests with wl-pointer key.

# Apps

* [Bringing up and auditing an app](app-bring-up.md) - The order of work for every app: manifest, static gap and stub list, run-time stub log, comparison with real Android, per-feature audit.
* [App status](apps.md) - Every Android app tried with ATL, its state, the cause of failures and the fixes.
* [App audit](app-audit.md) - Per app and feature: what works, what is stubbed or missing, what is wrong or untested, with the evidence.
* [Google Play services compatibility](gms-compat.md) - GMS, Firebase and Play APIs in the tested apps, local or server-only, why a local GMS needs Google's certificate, and `ATL_GMS_STUB`.
* [Push messages](push.md) - FCM, UnifiedPush or own connection per app, why FCM cannot be routed to ntfy, the UnifiedPush D-Bus bridge, Linux distributors.
* [Bluetooth on BlueZ](bluetooth.md) - How android.bluetooth maps onto BlueZ over D-Bus: adapter, discovery, bonding, GATT client, notifications; what is not implemented and how to test.
* [Android keyboards as the Phosh keyboard](android-keyboard.md) - Feasibility of HeliBoard as Phosh OSK through ATL's IME host: sm.puri.OSK0, input-method-v2, existing host, gaps, size M, risks.
* [Installing apps with F-Droid and Aurora Store](installing-apps.md) - How ATL installs, lists and uninstalls APKs, also split APKs from Google Play, how to set up F-Droid and Aurora Store, and the gaps.

# Security

* [Hardening roadmap](hardening-roadmap.md) - Threat model, current state and phased plan to run every Android app as its own Flatpak generated at install time: ATL engine for Flatpak, host installer and broker, permission mapping, portals, APK signatures.

# Decisions

* [Google Play services APIs only where they work locally](decisions/gms-local-only.md) - Accepted: local GMS APIs in scope, server-only ones out, never Google's certificate or fake attestation.
* [Back navigation by button, edge swipe and keys](decisions/navigation.md) - Proposed: header button, edge swipe and Escape/Alt+Left go back via KEYCODE_BACK; ATL_NAVIGATION=buttons|gesture|both until the settings app.
* [Every Android app as its own Flatpak](decisions/app-sandbox.md) - Proposed: one Flatpak per app, built on the phone from the APK, on an ATL engine for Flatpak, with a small host broker.
* [System wake windows for Android apps](decisions/wake-windows.md) - Accepted direction: apps never keep the phone awake; a system timer wakes it at a user-set interval and ATL apps run their due alarms in that window.
