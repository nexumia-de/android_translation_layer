---
type: Playbook
title: Development environment
description: The owner's Phosh phone as build and test device - separate user, build into ~/.local, headless test display, required hosts.
tags: [development, phone, testing, network]
generated: { by: claude-code/2.1.312, at: 2026-10-28T08:15:00Z }
---

# Why the phone

ATL runs the app's ARM64 native libraries directly, so tests need an
aarch64 machine. The phone is real hardware with the real Phosh; the
cloud dev container is x86_64 and could only emulate aarch64 with
qemu-user, 5-20 times slower and without a GPU.

# Rules on the phone

The phone is the owner's daily driver.

- The agent runs as the user `claude`, which is in neither `wheel` nor
  `sudo`. The owner's home directory is `chmod 700`.
- The owner installs system packages; the agent asks with the exact
  command.
- ATL and its dependencies are built and installed into `~/.local` of
  the `claude` user.
- Apps are tested in a headless Wayland compositor started by the
  `claude` user, not in the owner's Phosh session.
- Long builds keep all cores busy: announce them so the phone is on its
  charger. Automatic suspend must be off while the agent works.

# Running the agent

```sh
sudo su - claude              # doas on postmarketOS
tmux new -s claude
cd ~/work/android_translation_layer && claude remote-control
```

Close the terminal window; the session keeps running and shows up in
the Claude Code app. Back in with `tmux attach -t claude`.

Checkouts in `~/work`: this fork and, next to it, `phosh-toolkit`
(`AGENTS.md` imports `../phosh-toolkit/AGENTS.md`):

```sh
git clone https://github.com/nexumia-de/android_translation_layer
git clone https://github.com/nexumia-de/phosh-toolkit
```

# Still to record

Filled in by the first session on the phone:

- Device, distribution and version.
- Packages the owner installed for the build (upstream's
  [Build.md](../doc/Build.md) lists the dependencies and the build order:
  `bionic_translation`, `art_standalone`, `libopensles-standalone`, then
  ATL).
- The exact build commands with `--prefix ~/.local`, and the environment
  needed to run `android-translation-layer` from there.
- Which headless compositor is used and how a screenshot is taken.
- How the `claude` user pushes to GitHub (e.g. a deploy key for this
  repository only).

# Required hosts

Tested from the cloud dev container on 2026-10-28:

| Host | Used for | State |
|---|---|---|
| `gitlab.com` | Upstream ATL and its dependencies | reachable |
| `dl-cdn.alpinelinux.org` | Alpine packages (ATL is packaged in Alpine) | blocked by the proxy (`403`) |
| `mirror.postmarketos.org` | postmarketOS packages | connection failed |

The phone itself has unrestricted network access.
