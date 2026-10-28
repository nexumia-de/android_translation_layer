---
type: Reference
title: App status
description: Every Android app tried with ATL on the phone, its state, the cause of failures and the fixes.
tags: [apps, testing, compatibility]
generated: { by: claude-code/2.1.308, at: 2026-10-28T09:20:00Z }
---

# Apps

States: `works`, `partly` (starts, some features fail), `crashes`,
`out of scope` (needs Google Play Services or similar).

| App | Version | ATL commit | State | Cause / notes | Fix |
|---|---|---|---|---|---|
| [Blokish](https://f-droid.org/packages/org.scoutant.blokish/) (`org.scoutant.blokish`, F-Droid) | 3.6 (24) | `01084c1c` | `partly` | Smoke test of the phone setup (2026-10-28), not a target app. Starts with the title bar and the four player colour bars at the bottom; the board (black area) is not drawn: GTK warns `Trying to measure WrapperWidget ... for width of 0, but it needs at least 252`. Not investigated further. No upstream report. Started with `tools/headless-shot.sh -d 40 -- android-translation-layer org.scoutant.blokish_24.apk`. | Crashed at startup with `NoClassDefFoundError: android.view.animation.RotateAnimation`; fixed by `01084c1c api-impl: add RotateAnimation stub` (upstream candidate). |

# Needs owner

System packages to install and decisions only the owner can make. Each
entry: date, what, why. Remove an entry once it is done.

None.

# Notes

APKs come from F-Droid with their `.asc` signature, checked against
F-Droid's key `37D2C98789D8311948394E3E41E7044E1DBA2E89`
(`keys.openpgp.org`). For each app, first search upstream's issue tracker
(`https://gitlab.com/android_translation_layer/android_translation_layer/-/issues`)
and link known reports in the notes.
