---
type: Decision Record
title: Every Android app as its own Flatpak
description: Proposed - the host-side ATL installer turns each APK into its own Flatpak at install time, running on an ATL engine built for Flatpak; Flatpak provides sandbox, permissions and portal identity, a small host broker handles installs and intents.
tags: [decision, security, sandbox, flatpak, permissions]
status: draft
generated: { by: claude-code/2.1.308, at: 2026-10-29T17:40:00Z }
sources:
  - id: flatpak-under-the-hood
    resource: https://docs.flatpak.org/en/latest/under-the-hood.html
    title: Flatpak documentation, Under the hood (bubblewrap, seccomp, D-Bus proxy)
  - id: flatpak-commands
    resource: https://docs.flatpak.org/en/latest/flatpak-command-reference.html
    title: Flatpak command reference
---

# Status

Proposed (2026-10-29). The owner chose this option over ATL's own
bubblewrap sandbox on 2026-10-29; it becomes Accepted when the prototype
in phase 0 of the [hardening roadmap](../hardening-roadmap.md) works on
the phone.

# Context

ATL runs Android apps as the owner's user with full access to files,
the session bus and devices; its permission answers are a fixed list in
Java that native code and trackers can ignore. The owner wants
Android-like, revocable permissions that protect data on their daily
driver phone.

Options considered:

- **Java-only permission checks**: not enforcement.
- **ATL itself as one Flatpak** (upstream's existing mode): all apps
  share one sandbox, one set of permissions, one portal identity.
- **A container with full Android (Waydroid)**: what ATL replaces.
- **ATL's own per-app sandbox** with bubblewrap, xdg-dbus-proxy and
  seccomp (the first version of this record): rebuilds what Flatpak
  already does, and leaves portal identity per app, WebKit's nested
  sandbox and a settings UI as own work.
- **One Flatpak per Android app, generated at install time**: Flatpak's
  mature sandbox,[^flatpak-under-the-hood] per-app portal identity,
  permission overrides that Flatseal and GNOME Settings already edit, and
  WebKit's sandbox through the Flatpak portal. Generating an app compiles
  nothing.[^flatpak-commands]

# Decision

- Every Android app is installed as its own Flatpak
  `de.nexumia.atl.<package>` in the user installation, built on the
  phone from the APK by ATL's host-side installer and exported from a
  local repository.
- ATL (ART, `bionic_translation`, the API implementation) is built once
  as a Flatpak engine (extension or runtime, chosen in phase 0) and
  published in the owner's Flatpak repository.
- A small host-side broker, part of ATL's aports package, is the only
  trusted component: install, update, uninstall, signature checks,
  listing apps, relaying intents between apps, the per-app settings page.
  It identifies callers by their Flatpak app id.
- Android permissions map to Flatpak permissions and portals; ATL's Java
  answers follow the effective Flatpak permissions.
- The work happens in this fork, not in a separate project; upstream is
  asked about direction in one issue before ATL's code changes for it
  land, per [the upstream rules](../fork-workflow.md).

# Consequences

- Real per-app isolation with an established, audited sandbox; less own
  security code.
- New infrastructure: the engine needs a flatpak-builder build in CI and
  hosting in the owner's Flatpak repository, and runtime updates.
- Static permissions (network, audio) change only at the next start;
  microphone and playback share the audio socket for now.
- Locally generated Flatpaks are a less common use of Flatpak; the local
  repository is unsigned and trusted because only the broker writes it.
- Existing installs need a one-time migration.
- If upstream builds its own sandbox, the fork follows upstream's design
  where they conflict.

[^flatpak-under-the-hood]: Flatpak documentation, Under the hood
[^flatpak-commands]: Flatpak command reference
