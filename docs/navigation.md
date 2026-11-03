---
type: Reference
title: Back navigation in ATL
description: How ATL turns the header bar button, an edge swipe and the keys Escape, Alt+Left and XF86Back into Android's back, the ATL_NAVIGATION setting and how to test it headless.
tags: [navigation, gestures, keyboard, back, testing]
generated: { by: claude-code/2.1.312, at: 2026-11-03T12:00:00Z }
---

# Sources of back

| Source | Code | What the app sees |
|---|---|---|
| Header bar button | `src/main-executable/back_button.c` | `Dialog.onBackPressed` or `Activity.onBackPressed` |
| Escape, Alt+Left, XF86Back | `src/main-executable/navigation.c` | `KEYCODE_BACK` down and up |
| Swipe from the left or right edge | `src/main-executable/navigation.c` | `ACTION_CANCEL` for the touch, then `KEYCODE_BACK` down and up |

`KEYCODE_BACK` goes to the topmost dialog (`Dialog.dispatchKeyEvent`: its
`OnKeyListener`, then `onKeyUp` -> `onBackPressed`), else to the current
activity's `dispatchKeyEvent`, whose default (like Android) ends in
`onKeyDown` (starts tracking) and `onKeyUp` -> `onBackPressed`
(`current_activity_back_key()` in `android_app_Activity.c`). Keys and the
swipe do nothing where the header button is insensitive: on the first
activity when it handles neither back nor keys
(`current_activity_back_available()`).

Keys reach a focused view that handles keys first (`WrapperWidget.c`);
only unhandled ones go back. Escape is Android's own fallback for BACK
(`Generic.kcm`), Alt+Left the GNOME and browser shortcut.

# Edge swipe

A `GtkGestureDrag` in the capture phase of the window content (activities
and dialogs, below the header bar):

- starts only in the 20 px zone at the left or right edge, else it steps
  aside at once (ViewPager, drawers and lists keep their touches);
- until the finger has moved 16 px inwards and more sideways than up or
  down, the app sees the touch, so taps and vertical scrolls at the edge
  work;
- then it takes the touch: the app's views get `ACTION_CANCEL`
  (`view_cancel_touch_sequence()`) and GTK cancels their click and
  long-press gestures; an arrow hint follows the finger;
- from then on the rest of that touch sequence is the swipe's alone (a
  capture-phase controller swallows it; other fingers still reach the
  app), and only that finger's release ends the swipe;
- releasing after 72 px inwards goes back; the hint turns to the accent
  colour at that point. If the press already dismissed a dialog (a tap
  on its scrim), the swipe does not go back a second time.

The cancel goes once through the outermost view with its own
`dispatchTouchEvent` (the DecorView), which passes it on in Java; the
GTK path to the inner views is used only without one, or where its Java
dispatch could not reach them.

# Setting

`ATL_NAVIGATION=buttons|gesture|both` (default `both`): `buttons` keeps
only the header bar button, `gesture` only the swipe. Keys always work.
`atl_navigation_mode()` in `navigation.c` reads it; the settings app can
back it with GSettings later. Decision: [navigation](decisions/navigation.md).

# Testing headless

```sh
# keys: wl-pointer's key command (wlrctl's keys never reach GTK apps)
tools/headless-shot.sh -d 40 -x 'wl-pointer key Escape' -x 'wl-pointer key alt+Left' -- android-translation-layer app.apk
# swipe from the left edge
tools/headless-shot.sh -d 40 -x 'swipe 2 400 200 400 600' -- android-translation-layer app.apk
```

ATL prints `onBackPressed() called` when an activity's default back runs.
