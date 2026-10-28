---
type: Playbook
title: Fork workflow
description: Branches of this fork, updating from upstream on GitLab, how fixes go upstream as merge requests, and the queue of fixes not yet upstream.
tags: [development, git, upstream, gitlab]
generated: { by: claude-code/2.1.312, at: 2026-10-28T08:15:00Z }
---

# Repositories

| Remote | URL | Role |
|---|---|---|
| `upstream` | `https://gitlab.com/android_translation_layer/android_translation_layer.git` | ATL itself, GPL-3.0 |
| `origin` | `https://github.com/nexumia-de/android_translation_layer` | This fork |

ATL's dependencies `art_standalone`, `bionic_translation` and
`libopensles-standalone` live in the same GitLab group
(`https://gitlab.com/android_translation_layer`). They are not forked
yet; fork one only when a fix needs it.

# Branches

- `master`: identical to upstream `master`, never committed to.
- `phosh` (default): upstream plus `AGENTS.md`, `CLAUDE.md`, `docs/` and
  fixes not yet merged upstream. Work happens on branches from `phosh`
  and comes in by pull request.

# Updating from upstream

```sh
git fetch upstream
git push origin upstream/master:master
git checkout phosh && git merge origin/master && git push
```

Merge, never rebase `phosh`: it is shared between sessions and devices.

# Sending a fix upstream

Upstream code follows upstream's `doc/CodeStyle.md` and clang-format
check, and their commit style `<area>: <what>`. A fix commit touches
only upstream files, so it applies on upstream's tree:

```sh
git checkout -b fix/<slug> origin/master
git cherry-pick <commit>
```

The owner (GitLab `Xilance`) pushes that branch to their GitLab fork
and opens the merge request there; the agent prepares the branch and
the text. Once upstream merged it, the next update from upstream brings
it back into `phosh`, and the commit drops out of
`git log origin/master..origin/phosh`.

The fork keeps every change; upstream gets them **one merge request per
fix commit**, never a batch:

- Small and self-explaining first; a large feature only after a few
  small ones went in, and after asking in an issue whether upstream
  wants it.
- Each text says where the fix comes from, what failed (app, log or
  stack trace) and how it was tested, in the owner's words, e.g.: "I am
  running Claude Code on my phone to install Android apps on Phosh. This
  is one finding: …". Optional last line, without asking for anything:
  "Other fixes found along the way are collected in
  https://github.com/nexumia-de/android_translation_layer (docs/apps.md);
  no need to look, I'll send the useful ones as separate small MRs."
- Fixes in ATL's dependencies (`art_standalone`, …) go to that project's
  GitLab repository the same way.

# Upstream queue

Fix commits on `phosh` that are not upstream yet, as of 2026-10-28, in
the planned order. Status: `open` (not sent), `sent` (merge request
link), `merged`, `declined`. Keep it current when a commit is added,
sent or merged.

| Commit | Size | Status |
|---|---|---|
| `wolfssljni: ignore freed sessions in setNativeTimeout` (art_standalone; patch in nexumia-de/aports `art_standalone/`) | 5 lines | sending (owner's fork `Xilance/art_standalone`, `eee27f55`) |
| `3c392697 main-executable: start launchers with an absolute /usr/bin/env` | 3 lines | open |
| `86bb2de7 main-executable: keep --sdk-int in the launcher` | 2 lines | open |
| `b40cd2a1 api-impl: Application: fall back to the literal android:label` | 15 lines | open |
| `467311b9 api-impl: add Context.startForegroundService` | 5 lines | open |
| `01084c1c api-impl: add RotateAnimation stub` | 16 lines | open |
| `5071ca6b Path: draw arcs of 360 degrees and more as a full ellipse` | 26 lines | open |
| `f01f23bb api-impl: add BounceInterpolator` | 55 lines | open |
| `c42b09f6 api-impl: add vibrate(VibrationEffect), VibratorManager and CombinedVibration` | 100 lines | open |
| `7d31932e api-impl: Html: parse tags and entities in fromHtml` | 108 lines | open |
| `8ae0dd2b api-impl: start activities of other installed apps through their launcher` | 16 lines | open; after PackageInstaller, which it serves |
| `888acc17 api-impl: implement PackageInstaller sessions on top of --install` | 680 lines | open; ask first in an issue |

Commit ids are those on `phosh`; cherry-pick them from there.

# Open questions

- Do the ATL maintainers accept AI-written merge requests? Not asked
  separately; the first merge request (art_standalone, 2026-10-28) says
  that it came from Claude Code. Record their answer here.
