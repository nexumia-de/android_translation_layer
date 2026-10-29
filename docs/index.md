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

* [App status](apps.md) - Every Android app tried with ATL, its state, the cause of failures and the fixes.
* [Installing apps with F-Droid and Aurora Store](installing-apps.md) - How ATL installs, lists and uninstalls APKs, also split APKs from Google Play, how to set up F-Droid and Aurora Store, and the gaps.

# Security

* [Hardening roadmap](hardening-roadmap.md) - Threat model, current state and phased plan for permissions and sandboxing of apps: permission store and dialogs, brokers, bubblewrap, D-Bus proxy, portals, seccomp, Landlock, APK signatures.

# Decisions

* [Sandbox ATL apps with bubblewrap](decisions/app-sandbox.md) - Proposed: one bubblewrap sandbox and one permission store per app, started by ATL itself.
