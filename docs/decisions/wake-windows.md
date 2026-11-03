---
type: Decision Record
title: System wake windows for Android apps
description: Accepted direction - Android apps never keep the phone awake on their own; a system timer wakes the phone at a fixed interval set by the user, every ATL app runs its due alarms in that short window, then the phone suspends again.
tags: [decision, power, suspend, alarms, background]
status: accepted
generated: { by: claude-code/2.1.308, at: 2026-11-03T11:30:00Z }
sources:
  - id: systemd-timer
    resource: https://www.freedesktop.org/software/systemd/man/latest/systemd.timer.html
    title: systemd.timer, WakeSystem=
  - id: timerfd
    resource: https://man7.org/linux/man-pages/man2/timerfd_create.2.html
    title: timerfd_create(2), CLOCK_REALTIME_ALARM and CLOCK_BOOTTIME_ALARM need CAP_WAKE_ALARM
  - id: android-doze
    resource: https://developer.android.com/training/monitoring-device-state/doze-standby
    title: Android, Doze and maintenance windows
---

# Status

Accepted direction by the owner on 2026-11-03: "apps must not decide to
keep the phone awake; the system wakes the phone briefly at intervals so
all apps can run their due work, configured in a system part". Phase 1
below is built; phase 2 is planned.

# Context

Android trackers such as GPSLogger take no wake lock of their own. Between
fixes they sleep and re-arm `AlarmManager` `ELAPSED_REALTIME_WAKEUP`
alarms (`setExactAndAllowWhileIdle`); on Android the kernel's alarm timer
wakes the phone for them, and Doze batches such alarms into maintenance
windows[^android-doze].

On the owner's phone (Fairphone 6, postmarketOS, measured 2026-11-03):

- systemd 262 with logind; RTC `rtc0` is the PMIC's `rtc-pm8xxx` with a
  `wakealarm`; `/sys/power/mem_sleep` is `[s2idle] deep`.
- An unprivileged process gets `EPERM` for `CLOCK_REALTIME_ALARM` and
  `CLOCK_BOOTTIME_ALARM` timers (`CAP_WAKE_ALARM`)[^timerfd]. A user
  systemd timer accepts `WakeSystem=yes` but only the system manager can
  arm the RTC[^systemd-timer]. So waking needs a root part.
- `suspend_stats` showed `success=0` after 2 days 7 hours: the phone had
  not suspended since boot, and gnome-session held a `block-weak` sleep
  inhibitor ("user session inhibited") for some app in the owner's session.
- ATL's `AlarmManager` scheduled with `Handler.postDelayed`, whose clock
  (`CLOCK_MONOTONIC`) stops in suspend: alarms came late by the time slept,
  `cancel` did nothing and a new `set` did not replace the old alarm.

# Decision

1. **Apps never hold the phone awake by themselves while it is idle.**
   No suspend inhibit for foreground services, location sessions or
   pending wake-up alarms. Wake locks (`PARTIAL_WAKE_LOCK`, #71) stay
   what they are on Android: a hold while the phone is already awake,
   e.g. during an alarm's work.
2. **The system wakes the phone at a fixed interval**, the same for all
   apps: a system timer `atl-wake.timer` with `WakeSystem=true`, aligned
   to the wall clock (`OnCalendar=*:0/15`), and `atl-wake.service`, which
   holds a sleep inhibitor for a short window (30 s) after each wake.
   Packaged with ATL (aports); the owner enables it.
3. **ATL apps run their due alarms in that window.** `AlarmManager`
   schedules on the boot clock and wall clock, notices the resume
   (logind `PrepareForSleep(false)`) and delivers every due alarm at once,
   each under a partial wake lock limited to the window. Alarms that fall
   between windows are delivered in the next window, like Android's Doze.
4. **The interval is a user setting** in the "Android apps" settings page:
   off, 5, 15 (default until measured) or 30 minutes. The page changes the
   timer through a small polkit-protected helper; until it exists the
   owner sets it with a drop-in.
5. **Phase 2, a wake broker:** ATL publishes each app's next wake-up alarm
   (`$XDG_RUNTIME_DIR/atl-wake/<package>` on the host,
   `$XDG_RUNTIME_DIR/app/<flatpak id>/atl-next-wakeup` in a Flatpak); a
   root service arms the RTC only for the next window that has a due
   alarm, so the phone does not wake when nothing is due.

# Consequences

- Trackers get one fix per window when the screen is off: with a 15
  minute window, GPSLogger logs about 4 points an hour while idle. The
  battery cost per window and the gaps are measured in the phone test.
- No app can drain the battery by holding the phone awake while idle; a
  misbehaving app is limited to the window.
- Foreground location (window open, screen on) is unaffected.
- The root part is two systemd units, no own daemon in phase 1.

# Options not taken

- Suspend inhibit while an app has a foreground service with a location
  session (recommended earlier in docs/apps.md "Needs owner"): rejected by
  the owner; apps would decide.
- A user-level timer: cannot wake the phone (no `CAP_WAKE_ALARM`).
- Giving ATL `CAP_WAKE_ALARM` (file capability): every app could wake the
  phone at will, and Flatpak drops capabilities anyway.
