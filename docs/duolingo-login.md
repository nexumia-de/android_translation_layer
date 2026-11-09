---
type: Concept
title: Duolingo login and Play Integrity
description: Why Duolingo's app rejects a correct email and password as "Wrong username or password" on devices without Google's Play Integrity, what ATL's GMS stub has to do with it, and the two ways to log in without Google.
resource: ../src/api-impl/android/atl/ATLToggles.java
tags: [apps, duolingo, gms, play-integrity, login]
generated: { by: claude-code/2.1.312, at: 2026-11-08T22:40:00Z }
sources:
  - id: eos-thread
    resource: https://community.e.foundation/t/unable-to-log-into-duolingo-app/75563
    title: /e/OS community, "Unable to log into Duolingo App" (2025-10 to 2026-01), with tcecyk's logcat
  - id: iode-thread
    resource: https://community.iode.tech/t/duolingo-can-t-connect-with-account/7405
    title: iodéOS community, "Duolingo can't connect with account", rik's steps of 2026-01-11
  - id: graphene-thread
    resource: https://discuss.grapheneos.org/d/31569-login-problem-duolingo
    title: GrapheneOS forum, "login problem duolingo" (2026-02 to 2026-10)
  - id: sailfish-thread
    resource: https://forum.sailfishos.org/t/duolingo-does-not-log-in-anymore-aas-or-microg-related-problem-at-least-one-more-app-affected/27818
    title: Sailfish OS forum, Duolingo does not log in any more (2026-02)
  - id: duolingo-help
    resource: https://www.duolingo.com/help/trouble-accessing-my-account
    title: Duolingo help, "I'm having trouble accessing my account"
---

# Duolingo login and Play Integrity

Status 2026-11-08, from public reports and ATL's code; **not yet tested
on the phone** (the phone was not reachable from this session, and the
owner's failed attempt is not recorded with date, message or ATL build).

## What the server does

Duolingo's app asks Play Core's *standard integrity* API
(`requestExpressIntegrityToken`, served by the Play Store package
`com.android.vending`) right before the login request and sends the
result along. If the verdict is bad, the login API answers `401`, and
the app shows the same "Wrong username or password" it shows for a bad
password. A logcat from /e/OS (microG, Fairphone 3) shows the sequence
`abuse_req_cls_sig_measure`, `StandardIntegrity: requestExpressIntegrityToken`,
binding to `com.android.vending/...expressintegrityservice`, then
`ErrorResponse 401`[^eos-thread]. The same credentials work in the
browser on the same phone[^eos-thread][^iode-thread].

So the text "Wrong username or password" does **not** prove a wrong
password. ATL's headless test of 2026-10-31 (random data, see
[app status](apps.md)) got this text with upstream's GMS stub still on,
which at that time made ATL report a signed `com.android.vending` to
every app; that answer may have been the integrity `401` as well.

## Which devices are affected

| Device | Play Store visible to the app | Integrity verdict | Login |
|---|---|---|---|
| Stock Android with Google | yes | good | works |
| /e/OS, iodéOS, LineageOS for microG, Sailfish AAS | yes (microG's fake store) | fails | rejected[^eos-thread][^iode-thread][^sailfish-thread] |
| GrapheneOS, sandboxed Play, integrity **blocked per app** | API blocked | none | works ("I decided to try to block play integrity in the app settings on a whim, and was surprised to see this actually worked!", 2026-03-25)[^graphene-thread] |
| GrapheneOS, sandboxed Play, integrity allowed | yes | fails | rejected, "try later"[^graphene-thread] |
| Duolingo 6.29.2 (2092) or older | any | not asked | works; the session survives an update to a current version[^graphene-thread] |

The pattern: a device that *has* a Play Store but fails the check is
rejected; a device *without* a Play Store, where the integrity client
reports the API as unavailable, is let in. Google's presence is the
problem, not its absence.

## What this means for ATL

- Upstream ATL reports `com.android.vending`, `com.google.android.gms`
  and `com.google.android.gsf` with Google's certificate to every app
  (`gstub.jar`). Duolingo then binds the integrity service, finds none,
  and is in the "fails" row above. Since 2026-11-02 this fork turns the
  stub on only for the apps in `GMS_STUB_APPS` (`ATLToggles.java`,
  currently DB Navigator), so Duolingo sees no Play Store, like the
  GrapheneOS profile with the API blocked, which is the row that works.
  Any Duolingo test from before that build, or run with
  `ATL_GMS_STUB=1`, says nothing about the current state.
- Sign-in **with Google** needs Google's account servers and is out of
  scope ([decision](decisions/gms-local-only.md)). An account that was
  created with Google sign-in has no password; Duolingo's help says to
  set one through "Forgot password" with the Google account's email
  address[^duolingo-help], after which the email login works.
- ATL's `Build` values (`MANUFACTURER` "HTC", brand "google", see
  `src/api-impl/android/os/Build.java`) go into the request as device
  data. No report shows the server minding them, but they are the next
  suspect if a login without any Play Store is still rejected.

## Ways to log in

1. **Current build, email and password.** ATL from `phosh` after
   2026-11-02, `ATL_GMS_STUB` unset. If the account was made with
   Google, set a password first (above). Expected to work; the log must
   not contain `StandardIntegrity` binding to `com.android.vending`.
2. **Password reset inside the app** (works on every microG device
   above[^iode-thread][^eos-thread]): tap "Forgot password" *in the
   app* (a reset requested in the browser carries other parameters and
   does not open the app), open the link from the email **in the app**,
   type the old or a new password and save. The reset endpoint logs the
   user in without the integrity check, and the session stays. On ATL
   the link is opened with the running app's `--uri` support:

   ```sh
   android-translation-layer --uri 'https://www.duolingo.com/reset_password?...' <duolingo apk>
   ```

   ATL resolves the activity through Duolingo's intent filters
   (`main.c`, "failed to resolve activity to handle URI" if none
   matches). Reported pitfalls: email clients that strip tracking
   parameters from the link break it, and some users could not press
   the save button, which on ATL would be a Compose input problem to
   look at.
3. **Old version for the first login**: Duolingo 6.29.2 (version code
   2092) does not ask for integrity; log in, then update, the session
   survives[^graphene-thread]. Needs that APK (Aurora Store, manual
   download by version code); the owner's download is 6.103.6.

## Open

- The owner's test with the current build (way 1), then way 2 if it
  fails. Record the log lines around the login (`DuoLog`, `PlayCore`,
  `ErrorResponse`).
- Whether Duolingo's integrity client takes ATL's missing Play Store as
  "API not available" (the GrapheneOS case) or as an error. Only the
  log on the phone shows it.
