---
type: Playbook
title: Bringing up and auditing an app
description: The fixed order of work for every app in ATL - manifest, static gap and stub list, run-time stub log, comparison with real Android, per-feature audit - instead of fixing one crash at a time.
resource: ../tools/atl-gaps.py
tags: [apps, testing, compatibility, stubs, debugging, method]
generated: { by: claude-code/2.1.312, at: 2026-10-31T12:30:00Z }
sources:
  - id: wine-debug
    resource: https://codeweavers.com/blog/aeikum/2019/1/15/working-on-wine-part-4-debugging-wine
    title: Working on Wine part 4, debugging Wine (FIXME is printed by default and marks missing features)
  - id: wine-channels
    resource: https://www.winehq.org/docs/wineusr-guide/x275
    title: Wine User's Guide, debug channels and message classes
  - id: wine-testing
    resource: https://goma.googlesource.com/wine/+/refs/tags/wine-20021007/documentation/testing.sgml
    title: Wine documentation, conformance tests that must pass on Windows
  - id: darling-stubs
    resource: https://lwn.net/Articles/794871
    title: LWN on Darling (stubs let a program load and mostly do nothing)
---

# Why this order

Compatibility layers work their way along real apps. Crash-by-crash work
is the slow way to do that: Duolingo took eight rounds of "start, read the
crash, add one method", and its worst problem was not a crash at all. It
hung on an empty splash screen, because `registerActivityLifecycleCallbacks`
existed and did nothing.

So every app goes through four checks, each covering what the one before
cannot see:

| Check | Finds | Blind to |
|---|---|---|
| [Static list](#2-static-list): missing | classes, methods and fields ATL lacks, before the app runs | whether the code is ever reached |
| [Static list](#2-static-list): stubs | calls into ATL methods with an empty body, also on screens no test reached | whether the code is reached; methods that do something, but not enough |
| [Stub log](#3-stub-log) | stubs that really ran, with the app's stack | screens that were not opened |
| [Comparison](#5-comparison-with-real-android) | wrong behaviour of finished code | nothing, but it needs a reference and a person's eye |

Wine does the same with other names: stub functions log a `FIXME`, shown by
default, marking a missing feature[^wine-debug][^wine-channels], and
conformance tests check that Wine does what Windows does and must pass on
real Windows[^wine-testing]. Stubs exist to make a program load and in most
cases do nothing, which gives no guarantee that it runs correctly; Darling's
developers say the same[^darling-stubs]. ATL's bar is lower than Wine's: no
test suite, a phone and a few apps. The comparison is therefore done on
screens, not with unit tests.

# The steps

## 1. Manifest

Read the APK before running it: `android.hardware.*` features, permissions,
number of services, receivers and providers. Upstream's issue tracker comes
next (AGENTS.md). The manifest names the features an app can have, which
are the rows of the [audit](#6-audit). Anything needing Google Play Services
is out of scope; note that as soon as it is known.

## 2. Static list

```sh
python3 -I tools/atl-gaps.py ~/work/apks/<app dir or .apk> --app-prefix de.hafas,de.bahn
```

The tool needs art_standalone's `dexdump2` and reads the build in `~/.local`
(so run it on the branch being worked on) and writes `gaps.md` and `gaps.tsv`
to `~/scratch-gaps/<name>`. Dumps of a big app take about 1.3 GB and the run
about 10 minutes. The dump cache is per framework build: delete `OUT/dump`
after changing ATL.

Each entry is `class`, `method` or `field` (ATL lacks it) or `stub` (ATL has
an empty body). `gaps.md` starts with a table by feature. A class in a
framework package that only the app ships, such as `android.support`, is not
a gap, as the app's copy is the one that loads.

The list is noisy, and call counts do not predict blockers:
`TypedValue.isColorType`, which crashed Duolingo's sign-in screen, ranked 972
with one call site. Work through it like this:

1. Entries called from the app's own package (column `app`). Pass all the
   app's packages to `--app-prefix`.
2. Entries called from obfuscated code (column `obfuscated`). R8 moves app
   and library code there alike, so disassemble the caller before judging.
3. Entries called only from named libraries (ads, Play Services, analytics).
   Leave these until a stub log or a crash shows they are reached.

Within a group, fix what belongs together ahead of the crashes, and filter by
the screen being worked on. References behind `SDK_INT` checks are listed too.

The stub entries need judgement. A base method a framework subclass implements
is left out, but a hook or a default that is also empty on Android
(`Drawable.getIntrinsicHeight`) is not, and a `class` entry for hardware the
app only touches behind a check (camera, Bluetooth) may never be hit.

## 3. Stub log

ATL has no switch for this yet. `tools/atl-stublog-rewrite.py` adds a call to
`android.atl.StubLog.hit` to every method that is empty or only returns a
default value; each logs once with six stack frames as `ATL-STUB`. The
rewritten sources are for a local build only and are **never committed**.
Whether a stub log belongs in ATL itself is a question for upstream
([fork workflow](fork-workflow.md)).

```sh
git worktree add --detach ~/scratch-stublog HEAD && cd ~/scratch-stublog
python3 -I tools/atl-stublog-rewrite.py .
meson setup builddir --prefix=$HOME/.local --libdir=lib --buildtype=debugoptimized -Ddownload_tool_deps=true
ninja -C builddir install        # installs over ~/.local
# run the app headless, see development environment
grep -A6 ATL-STUB app.log
```

Afterwards install the normal build again with `ninja -C <checkout>/builddir
install`. A run takes one fresh data directory per app. ATL's own lines
containing `STUB` or `not implemented` are in the same log.

Read the log before tracing by hand:

- `tools/atl-stub-hits.py app.log` prints each hit with the first frame
  outside the framework. With `-` there, ATL called itself: mostly noise.
- For "hangs silently", look at the last stubs before the app went quiet,
  above all registration and callback methods (`register*`, `add*Listener`,
  `set*Callback`): the app waits for an event a stub never sends.
- Compare the hits of two runs when a tap does nothing.

A stub on the log is not a bug in itself; it is one when the app depends on
what the real method does. Some hits are harmless on purpose (hooks, status
bar colours, accessibility while no screen reader runs), and a base class
default such as `Drawable.getIntrinsicHeight` returning -1 is also what Android
does. [ART method tracing](dev-environment.md#which-app-methods-ran)
stays the last resort, for when neither list explains a hang.

## 4. Fixing

- Implement it if the caller uses the result or waits for a callback. An
  empty stub is enough only when the real method's effect does not matter
  here, and then the commit says why.
- Fix groups of one screen together, in one commit per subject.
- Keep commits small and upstream-ready, with the app, the symptom and the
  cause recorded in [app status](apps.md).

## 5. Comparison with real Android

Passing both lists says nothing about whether the result is right. For each
screen compare ATL's screenshot under
[headless phoc](dev-environment.md#headless-phoc) with one from real Android,
such as the owner's (kept per app, for example `~/work/dhl-app/reference`),
at the same logical size. Touch, sensors and accounts are
[owner tests](dev-environment.md#tests-with-the-owner-on-the-real-screen).

After a change that touches shared code, check that Aurora Store, DB
Navigator, Post & DHL and F-Droid look as before.

## 6. Audit

"Works" means little without saying what was checked. Each app has a row per
feature in the [app audit](app-audit.md), with one of these states and the
evidence for it:

| State | Meaning |
|---|---|
| `phone` | The owner confirmed it on the phone |
| `headless` | Seen working in a headless run |
| `stub` | The app reaches an ATL stub here, so it cannot work fully |
| `missing` | ATL lacks the API, so it fails or is unavailable when used |
| `wrong` | It runs, but differs from Android |
| `untested` | Nothing known; needs a test or the owner |
| `out of scope` | Needs Google Play Services or similar |

Do not call a feature working because no list mentions it. Redo the audit
for an app when its version changes or a fix touches one of its features.

## 7. What is recorded

Every element is recorded, not only the gaps that were closed, but only the
feature table is written by hand:

| Where | What | How |
|---|---|---|
| `docs/app-audit/<package>.gaps.tsv` | every missing and stubbed element the app references | generated by `tools/atl-gaps.py`, never edited |
| `docs/app-audit/<package>.hits.tsv` | every stub a run reached, with the first app frame | generated from the stub log, never edited |
| [app audit](app-audit.md) | one row per feature with state and evidence | by hand |
| [app status](apps.md) | cause and fix of each failure | by hand |

Commit the regenerated snapshots together with a fix: the diff is the record
of what closed and what appeared, so nobody has to keep that list by hand. An
element can leave a snapshot only because the build changed, or the app.
Snapshots show what ATL has; only the audit says whether it is right.

```sh
python3 -I tools/atl-gaps.py <apk> --app-prefix <prefixes>
cp ~/scratch-gaps/<name>/gaps.tsv docs/app-audit/<package>.gaps.tsv
python3 -I tools/atl-stub-hits.py app.log > docs/app-audit/<package>.hits.tsv
```
