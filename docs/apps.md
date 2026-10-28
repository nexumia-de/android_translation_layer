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
| [Chooser](https://f-droid.org/packages/com.uravgcode.chooser/) (`com.uravgcode.chooser`, F-Droid) | 1.5.0 (26) | `7d31932e` | `partly` | Owner's app. Jetpack Compose. Starts and shows the first onboarding page correctly at 360×720 ([screenshot](screenshots/chooser-2026-10-28.png)). Interaction not tested yet: the headless display has no input (see "Needs owner"); the main feature needs several fingers at once, which cannot be simulated there and needs a test on the real touch screen. No upstream report. | Crashed at startup with `NoClassDefFoundError: android.view.animation.BounceInterpolator`: fixed by `f01f23bb api-impl: add BounceInterpolator`. The onboarding text began with `<ContentHandlerReplacementTag />`: `Html.fromHtml` did not parse HTML; fixed by `7d31932e api-impl: Html: parse tags and entities in fromHtml`. Both upstream candidates. |
| [Blokish](https://f-droid.org/packages/org.scoutant.blokish/) (`org.scoutant.blokish`, F-Droid) | 3.6 (24) | `01084c1c` | `partly` | Smoke test of the phone setup (2026-10-28), not a target app. Starts with the title bar and the four player colour bars at the bottom; the board (black area) is not drawn: GTK warns `Trying to measure WrapperWidget ... for width of 0, but it needs at least 252`. Not investigated further. No upstream report. [Screenshot](screenshots/blokish-2026-10-28.png). Started with `tools/headless-shot.sh -d 40 -- android-translation-layer org.scoutant.blokish_24.apk`. | Crashed at startup with `NoClassDefFoundError: android.view.animation.RotateAnimation`; fixed by `01084c1c api-impl: add RotateAnimation stub` (upstream candidate). |

# Needs owner

System packages to install and decisions only the owner can make. Each
entry: date, what, why. Remove an entry once it is done.

- 2026-10-28: `apk add wlrctl`. Clicks and key presses in the headless
  cage (virtual pointer and keyboard protocols, which cage supports), to
  test apps beyond their first screen, e.g. Chooser's onboarding.

# Notes

APKs come from F-Droid with their `.asc` signature, checked against
F-Droid's key `37D2C98789D8311948394E3E41E7044E1DBA2E89`
(`keys.openpgp.org`). For each app, first search upstream's issue tracker
(`https://gitlab.com/android_translation_layer/android_translation_layer/-/issues`)
and link known reports in the notes.
