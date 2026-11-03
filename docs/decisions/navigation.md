---
type: Decision Record
title: Back navigation by button, edge swipe and keys
description: Proposed - ATL goes back by the header bar button, a swipe from the left or right edge and the keys Escape, Alt+Left and XF86Back; on-screen choice ATL_NAVIGATION=buttons|gesture|both (default both) until the settings app owns it; Home and Recents stay Phosh's job.
tags: [decision, navigation, gestures, keyboard, back, settings]
status: proposed
generated: { by: claude-code/2.1.312, at: 2026-11-03T12:00:00Z }
sources:
  - id: android-gesture-nav
    resource: https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav
    title: Android developers, Gesture navigation (edge back swipe, system gesture insets)
  - id: android-generic-kcm
    resource: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/data/keyboards/Generic.kcm
    title: AOSP Generic.kcm (key ESCAPE falls back to BACK)
  - id: android-keyevent
    resource: https://developer.android.com/reference/android/app/Activity#onKeyUp(int,%20android.view.KeyEvent)
    title: Activity.onKeyUp (KEYCODE_BACK calls onBackPressed when tracked)
---

# Status

Proposed on 2026-11-03 for the owner's request of 2026-11-02 (Android
control in ATL: swipe from the side for back, control by keys).
Implementation: pull request on branch `claude/navigation`, details in
[navigation.md](../navigation.md).

# Context

Android apps expect a system back action: the navigation bar's back
button, or since Android 10 a swipe from the left or right edge. ATL had
only the back button in its header bar. Phones running Phosh have no
navigation bar, and with a keyboard (Librem 5 keyboard case, Bluetooth,
the desktop) users expect Escape or Alt+Left like in GNOME apps and
browsers.

# Decision

- Back has three sources: the header bar button, a swipe from the left or
  right window edge, and the keys Escape (without modifiers), Alt+Left and
  XF86Back. Keys and swipe send `KEYCODE_BACK` down and up like Android's
  gesture navigation, so a showing dialog (its `OnKeyListener`, then
  `onBackPressed`), `Activity.onKeyDown`/`onKeyUp` and finally
  `onBackPressed` (androidx `OnBackPressedDispatcher`) get it in Android's
  order. A focused view that handles Escape itself gets the key first.
- Keys are always on; they take no screen space. The on-screen choice is
  `buttons`, `gesture` or `both` (default). Until the ATL settings app
  exists it is the environment variable `ATL_NAVIGATION`; the C function
  `atl_navigation_mode()` is the one place that reads it, so the settings
  app can back it with GSettings later without touching the callers.
- Home (minimise) and Recents (app switcher) are not reimplemented: Phosh
  shows its overview on a swipe up from the bottom edge, and ATL apps are
  ordinary windows there.
- Predictive back (Android 13+ `OnBackInvokedCallback` with an animation
  that previews the previous screen) is out of scope for now; the swipe
  shows an arrow hint instead.

# Consequences

- A swipe that starts in the 20 px edge zone and moves 16 px inwards is
  taken from the app (it gets `ACTION_CANCEL`); a horizontal list or
  drawer that the user drags from exactly the edge then goes back instead,
  as on Android. Touches that start elsewhere, taps and vertical scrolls at
  the edge stay with the app.
- Phosh's own edge gestures (top: quick settings, bottom: overview) are
  outside the window, so they do not collide with the side edges.
- With `gesture` the header bar has no back button; apps without a
  keyboard then rely on the swipe.
- Open for the owner: the default (`both`), and whether the choice later
  lives per app or globally in the settings app.
