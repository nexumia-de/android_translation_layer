@../phosh-toolkit/AGENTS.md

# Android Translation Layer (Phosh fork)

Fork of [Android Translation Layer](https://gitlab.com/android_translation_layer/android_translation_layer)
(ATL, GPL-3.0). Goal: get specific Android apps running natively on the
owner's Phosh phone with ATL instead of Waydroid, and send every fix
upstream. Knowledge of this fork: [docs/index.md](docs/index.md).
Upstream's own documentation is in `doc/`.

## Branches

- `master` is identical to upstream `master`. Never commit to it; it is
  only updated from upstream (see [docs/fork-workflow.md](docs/fork-workflow.md)).
- `phosh` is the default branch: upstream plus this fork's files
  (`AGENTS.md`, `CLAUDE.md`, `docs/`) and fixes not yet merged upstream.
- Upstream does not know this fork's files. A fix meant for upstream is
  a commit that touches only upstream files, so it can be cherry-picked
  onto a branch from `master` for a GitLab merge request.

## Which shared rules apply

ATL is a third-party runtime, not a Phosh app. Of the shared rules in
../phosh-toolkit/AGENTS.md these do **not** apply here: app id and
metadata, metainfo and desktop file checks, Flatpak workflow, changelog
fragments and `release.toml`, REUSE compliance, the toolkit's UI and
design guidelines for upstream code, and the GitHub milestone layout.
Everything else applies: English, knowledge in `docs/`, network rules,
pull requests and CI.

Upstream code follows upstream's rules instead: `doc/CodeStyle.md`, the
clang-format check of their CI, and their commit message style
(`<area>: <what>`).

## Working on the phone

The owner's phone is their **daily driver**. The agent runs there as
the separate user `claude` without sudo or doas.

- Never try to get root, and never read or change other users' files or
  system settings. If a system package is missing, give the owner the
  exact install command.
- Build and install ATL and its dependencies into `~/.local`.
- Test apps in an own headless Wayland compositor, never in the owner's
  Phosh session; judge results from logs and screenshots.
- Builds are heavy: say before a long build so the owner can plug the
  phone in.
- Setup and test steps: [docs/dev-environment.md](docs/dev-environment.md).

## Apps

- Apps that need Google Play Services (Play Integrity, Google sign-in,
  FCM push) are out of scope.
- Status of every app tried, with the cause of each failure and the fix:
  [docs/apps.md](docs/apps.md). Check upstream's issue tracker for known
  reports before debugging an app.

## Upstream contributions

- Before the first AI-written merge request, ask the ATL maintainers
  whether they accept such contributions, and record the answer in
  [docs/fork-workflow.md](docs/fork-workflow.md).
- Merge requests go to GitLab from the owner's GitLab fork; the owner
  opens them.

## Autonomy and merging

This overrides the shared rule "Do not merge; merging stays with the
owner" for this repository only. Every other repository keeps it.

- Work autonomously: do not ask the owner between steps and do not wait
  for confirmation. The owner follows progress in
  [docs/apps.md](docs/apps.md); keep it current after every fix.
- Merge your own pull requests into `phosh` yourself, as soon as ATL
  builds and the apps in [docs/apps.md](docs/apps.md) are not worse than
  before. Use a merge commit, never squash or rebase, so fix commits
  stay cherry-pickable for upstream.
- Never push to or merge into `master`.
- When something large blocks an app, record it with a size estimate
  in [docs/apps.md](docs/apps.md) and continue with other work. Things
  only the owner can do (system packages, decisions) go under "Needs
  owner" there; stop only when nothing is left without them.
