---
type: Decision Record
title: Sandbox ATL apps with bubblewrap, like Flatpak
description: Proposed - every Android app runs in its own bubblewrap sandbox that ATL starts itself, with a filtered D-Bus proxy, portals and one permission store per app; ATL's Java permission answers follow the same store.
tags: [decision, security, sandbox, bubblewrap, permissions]
status: draft
generated: { by: claude-code/2.1.308, at: 2026-10-29T16:45:00Z }
sources:
  - id: bwrap
    resource: https://github.com/containers/bubblewrap
    title: bubblewrap, unprivileged sandboxing tool used by Flatpak
  - id: flatpak-under-the-hood
    resource: https://docs.flatpak.org/en/latest/under-the-hood.html
    title: Flatpak documentation, Under the hood (bubblewrap, seccomp, D-Bus proxy)
---

# Status

Proposed (2026-10-29). Accepted once the owner agrees and phase 0 of the
[hardening roadmap](../hardening-roadmap.md) confirms that the phone's
kernel allows unprivileged user namespaces.

# Context

ATL runs Android apps as the owner's user with full access to their
files, the session bus and devices. Android permissions are answered by a
fixed list in Java, which native code and trackers can ignore. The owner
wants Android-like permission management that actually protects data on
their daily driver phone.

Options considered:

- **Java-only permission checks**: easy, but not enforcement.
- **One shared Flatpak for ATL**: all apps share one sandbox and one set
  of permissions; nested sandboxes are blocked inside Flatpak.
- **A container with full Android (Waydroid)**: what ATL is meant to
  replace.
- **Per-app bubblewrap started by ATL**: the mechanism Flatpak itself
  uses,[^flatpak-under-the-hood] available unprivileged on Alpine.[^bwrap]

# Decision

- Each app runs in its own `bwrap` sandbox. ATL re-executes itself into
  it early in `main()`, so launchers, D-Bus activation and store installs
  are covered without changing their command lines.
- Access is derived from one policy file per app, kept by ATL outside the
  app's writable area; `checkPermission`, the permission dialogs, the
  settings page (launcher action **Permissions**) and the `bwrap` and
  `xdg-dbus-proxy` arguments all read it.
- Host changes (install, uninstall, launchers) go through a confirming
  host-side ATL component; user files and devices through portals.
- The work happens in this fork, in ATL itself, not in a separate
  project; upstream is asked about direction in one issue before the
  sandbox lands, per [the upstream rules](../fork-workflow.md).

# Consequences

- Real isolation of files, secrets and session; permissions can be
  revoked.
- ATL needs `bubblewrap` and `xdg-dbus-proxy` at runtime (aports
  dependencies) and user namespaces in the kernel.
- Some apps will break under the sandbox; they get a per-app opt-out and
  an entry in [apps.md](../apps.md).
- WebKit's own nested sandbox and PipeWire microphone isolation need
  research before they can be enforced.
- If upstream builds a sandbox of its own, the fork follows upstream's
  design where it differs.

[^bwrap]: bubblewrap
[^flatpak-under-the-hood]: Flatpak documentation, Under the hood
