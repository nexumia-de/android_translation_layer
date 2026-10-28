---
type: Playbook
title: Fork workflow
description: Branches of this fork, updating from upstream on GitLab, and how fixes go upstream as merge requests.
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

The owner pushes that branch to their GitLab fork of ATL and opens the
merge request there. Once upstream merged it, the next update from
upstream brings it back into `phosh`.

# Open questions

- Do the ATL maintainers accept AI-written merge requests? Not asked
  yet; ask before the first one and record the answer here.
