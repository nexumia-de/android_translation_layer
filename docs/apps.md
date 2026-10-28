---
type: Reference
title: App status
description: Every Android app tried with ATL on the phone, its state, the cause of failures and the fixes.
tags: [apps, testing, compatibility]
generated: { by: claude-code/2.1.312, at: 2026-10-28T08:15:00Z }
---

# Apps

States: `works`, `partly` (starts, some features fail), `crashes`,
`out of scope` (needs Google Play Services or similar).

| App | Version | ATL commit | State | Cause / notes | Fix |
|---|---|---|---|---|---|

# Needs owner

System packages to install and decisions only the owner can make. Each
entry: date, what, why. Remove an entry once it is done.

- 2026-10-28: `apk add openjdk17-jdk`. ATL's `tools/jni_upcall_gen`
  uses Java 12 APIs (`Path.of`, `Files.mismatch`) and fails to compile
  with JDK 8; upstream's nightlies build ATL with `openjdk17-jdk`.
  Blocks the ATL build and every app test.

# Notes

No app tried yet. For each app, first search upstream's issue tracker
(`https://gitlab.com/android_translation_layer/android_translation_layer/-/issues`)
and link known reports in the notes.
