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

# Apps

* [Bringing up and auditing an app](app-bring-up.md) - The order of work for every app: manifest, static gap and stub list, run-time stub log, comparison with real Android, per-feature audit.
* [App status](apps.md) - Every Android app tried with ATL, its state, the cause of failures and the fixes.
* [App audit](app-audit.md) - Per app and feature: what works, what is stubbed or missing, what is wrong or untested, with the evidence.
* [Installing apps with F-Droid and Aurora Store](installing-apps.md) - How ATL installs, lists and uninstalls APKs, also split APKs from Google Play, how to set up F-Droid and Aurora Store, and the gaps.

# Security

* [Hardening roadmap](hardening-roadmap.md) - Threat model, current state and phased plan to run every Android app as its own Flatpak generated at install time: ATL engine for Flatpak, host installer and broker, permission mapping, portals, APK signatures.

# Decisions

* [Every Android app as its own Flatpak](decisions/app-sandbox.md) - Proposed: one Flatpak per app, built on the phone from the APK, on an ATL engine for Flatpak, with a small host broker.
