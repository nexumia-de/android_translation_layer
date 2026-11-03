---
type: Concept
title: Android keyboards as the Phosh keyboard
description: Feasibility of running HeliBoard (an Android InputMethodService) through ATL as Phosh's on-screen keyboard - what Phosh expects, what ATL's IME host already does, the gaps, size and risks.
tags: [keyboard, ime, osk, phosh, input-method-v2, heliboard]
resource: ../src/api-impl/android/inputmethodservice/InputMethodService.java
generated: { by: claude-code/opus-5.5, at: 2026-11-03T00:00:00Z }
sources:
  - id: osk0
    resource: https://world.pages.gitlab.gnome.org/Phosh/phosh/phosh-dbus-sm.puri.OSK0.html
    title: Phosh, sm.puri.OSK0 D-Bus interface
  - id: im-v2
    resource: https://gitlab.freedesktop.org/wayland/wayland-protocols/-/blob/main/unstable/input-method/input-method-unstable-v2.xml
    title: input-method-unstable-v2 (as in wlroots/phoc)
  - id: heliboard
    resource: https://f-droid.org/packages/helium314.keyboard/
    title: HeliBoard on F-Droid
---

# Android keyboards as the Phosh keyboard

Goal: HeliBoard (`helium314.keyboard` 4.2, versionCode 4201, from
F-Droid, signature checked) as the keyboard for every app on Phosh, not
only ATL apps.

## What Phosh expects (phone, 2026-11-03)

- Phosh 0.58 runs `phosh-osk-stevia --allow-replacement` (78 MB RSS).
  gnome-session starts it from `/usr/share/applications/sm.puri.OSK0.desktop`
  (a `RequiredComponents` entry of `phosh.session`, `AutoRestart=true`).
- Phosh talks to the keyboard over D-Bus: name and object `sm.puri.OSK0`,
  method `SetVisible`, property `Visible`[^osk0]. `--allow-replacement` lets
  another process take the name.
- Text goes through Wayland: `zwp_input_method_v2` (surrounding text,
  content type, `commit_string`, preedit, `delete_surrounding_text`)[^im-v2],
  plus `zwp_virtual_keyboard_v1` for keys, and a layer-shell surface.
  The compositor gives one input method per seat, so the second keyboard
  only gets `unavailable` while Stevia runs.

## What HeliBoard needs

`helium314.keyboard.latin.LatinIME` (an `InputMethodService`), a Compose
`SettingsActivity`, the native dictionary `libjni_latinime.so` (arm64
present), minSdk 21, target 37; permissions `READ_CONTACTS`, `VIBRATE`,
user dictionary.

## What ATL already has

Upstream ATL contains an IME host: `android.atl.ATLKeyboardViewer`
(`-l android/atl/ATLKeyboardViewer -e kb_class=<IME class>`) creates the
`InputMethodService`, calls its lifecycle and puts its views into
`ATLKeyboardDialog`, a gtk4-layer-shell window at the bottom with an
exclusive zone that owns `sm.puri.OSK0` (with replace) and answers
`SetVisible`. `ATLInputConnection` maps `commitText`, `setComposingText`,
`finishComposingText` and `deleteSurroundingText` to input-method-v2. It is
built when `gtk4-layer-shell-0` is found (1.3.0 on the phone).

## Gaps

1. `getTextBeforeCursor`/`getTextAfterCursor` do not see the surrounding
   text, so suggestions and auto-capitalisation have no context.
2. `deleteSurroundingText` passes Android characters where the protocol
   counts UTF-8 bytes: wrong for umlauts and emoji.
3. `sendKeyEvent` handles only Delete; Enter, arrows and Tab need the
   virtual keyboard with a keymap.
4. The content type (password, number, email, URL) is ignored: the IME
   always gets an empty `EditorInfo`.
5. Activate and deactivate only show and hide the window; the IME never
   gets `onStartInput`/`onFinishInput` per field.
6. `setSelection` does nothing.

## Status (2026-11-03)

- Settings activity: runs headless ([app status](apps.md)).
- Keyboard: under headless phoc HeliBoard's input view shows as the
  layer-shell window over a text field, with its dictionary loaded, but
  full screen, on the emoji page and twice too big; no text typed yet.
  Gaps 1 and 2 above are fixed on the branch, 3 to 6 are open.

## Size and risks

Size **M**: the host exists, the work is the gaps above plus whatever
HeliBoard's own code reaches in ATL. The settings activity (task 1) is
small, like other Compose apps. HeliBoard inside ATL apps without Wayland
(task 3) would be L and is optional.

Risks:

- **Lock screen**: the PIN is typed on the OSK. A keyboard that crashes or
  does not show can lock the owner out. It is never set as default before a
  live test that can be undone (see Needs owner in [apps](apps.md)).
- **Memory**: an ART process stays resident (estimate 150 to 300 MB,
  Stevia 78 MB) on a phone shared by several apps.
- **Start time**: ART needs seconds to start, so the keyboard has to stay
  running and only show and hide.
- A crash leaves no keyboard until gnome-session restarts the component.
- Popups (long press, emoji) as extra layer-shell windows are untested.
