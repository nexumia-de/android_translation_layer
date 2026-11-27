---
type: Concept
title: Keystore and confirming the owner
description: How ATL keeps app keys (AndroidKeyStore), enforces what apps ask for (purposes, user authentication, unlocked device, invalidation by a new fingerprint), confirms the owner with the fingerprint (fingerprintd) or the password (polkit) through the permission service, and what it deliberately does not do (attestation, StrongBox, Play Integrity).
tags: [security, keystore, biometrics, fingerprint, polkit, flatpak]
status: draft
generated: { by: claude-code, at: 2026-11-27T17:30:00+01:00 }
sources:
  - id: android-keystore
    resource: https://developer.android.com/privacy-and-security/keystore
    title: Android developers, Android Keystore system (key use authorizations)
  - id: android-biometric
    resource: https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt
    title: Android developers, BiometricPrompt
  - id: fprintd-dbus
    resource: https://fprint.freedesktop.org/fprintd-dev/Device.html
    title: fprintd D-Bus API, net.reactivated.Fprint.Device
  - id: polkit-authority
    resource: https://www.freedesktop.org/software/polkit/docs/latest/eggdbus-interface-org.freedesktop.PolicyKit1.Authority.html
    title: polkit, org.freedesktop.PolicyKit1.Authority (CheckAuthorization)
  - id: portal-secret
    resource: https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Secret.html
    title: xdg-desktop-portal, Secret portal
---

# Goal

Apps that protect data with the Android keystore (password managers,
banking and TAN apps, messengers) get what they ask for, as far as the
phone can honestly give it: keys that only the app can use, that need the
owner's fingerprint or password when the app says so, and truthful
answers where ATL cannot give more. Owner's request (2026-11-27): "as
secure as it gets", without faking Play Integrity.

# What it protects against

Someone holding the **unlocked** phone who opens an app: keys bound to
user authentication need the fingerprint or the password again. Other apps
and host programs: the keys of an app are in its own Flatpak, encrypted
with that app's portal secret.

Not covered: the app itself. Keys and checks run in the app's process (the
keys are software keys), so the app's own code could read them; Android's
keystore runs in a separate process or in secure hardware. A banking
server that demands hardware attestation still refuses ATL, rightly.

# Pieces

| Piece | Where | What |
|---|---|---|
| Storage | `src/api-impl/android/security/keystore/AndroidKeyStore.java`, `src/api-impl-jni/security/` | libsecret; inside the app's Flatpak a keyring file in the app's data, encrypted with the app's own secret from the Secret portal.[^portal-secret] Each entry carries its `KeyAuthorizations` (format marked `ATLK`); entries from before have none: every purpose, no authentication, as before. |
| Keys | `AndroidKeyStoreSecretKey`, `AndroidKeyStorePrivateKey` | `getEncoded()` is null, private keys are only `ECKey`/`RSAKey`, as on Android. So only the keystore's own operations take them. |
| Operations | `AndroidKeyStoreCipher` (AES GCM, CBC, CTR, ECB), `AndroidKeyStoreRSACipher`, `AndroidKeyStoreSignature`, `AndroidKeyStoreMac`, `AndroidKeyStoreKeyAgreement` (ECDH) | Check the key's authorizations, then hand the work to Bouncy Castle (`Software`). Registered in `AndroidKeyStoreProvider`, first in the provider list, with `SupportedKeyClasses`, so other keys still go to the other providers. |
| Rules | `KeyAuthorizations` | Purposes; user authentication per use or within a time; `setUnlockedDeviceRequired`; `setInvalidatedByBiometricEnrollment`; `setRandomizedEncryptionRequired` (caller IVs and ECB refused for encryption).[^android-keystore] |
| Facts | `KeyInfo` (through `SecretKeyFactory`/`KeyFactory` of "AndroidKeyStore") | `isInsideSecureHardware()` false, security level software, authentication not enforced by secure hardware. |
| Confirming | `BiometricPrompt`, `BiometricManager`, `KeyguardManager`, `android.atl.ATLAuth` | The app shows a dialog ("Touch the fingerprint sensor", "Use password" when the device credential is allowed); the host checks.[^android-biometric] |
| Host side | `src/installer-service/auth.c` in the permission service (`de.nexumia.atl.Permissions`, version 4) | Fingerprint: one verify attempt per call on fingerprintd's `net.reactivated.Fprint` device.[^fprintd-dbus] Password: polkit action `de.nexumia.atl.confirm-credential` (`auth_self`, never kept), so Phosh's system prompt asks and PAM checks.[^polkit-authority] Lock state: `org.gnome.ScreenSaver.GetActive`. Only callers in an ATL app sandbox. |
| Policy file | `res/polkit/de.nexumia.atl.policy` | Installed by the host package (`meson install`). Without it the password is "unavailable" and only the fingerprint can confirm. |

## Confirmations and keys

- A success records the time per type (fingerprint, password). Keys with a
  validity (`setUserAuthenticationParameters(t, type)` with `t > 0`, or the
  old `setUserAuthenticationValidityDurationSeconds(t)`) work for `t`
  seconds after a confirmation of an allowed type; otherwise `init`
  throws `UserNotAuthenticatedException`.
- Keys that need a confirmation for every use (`t = 0`, or `-1` in the
  old call) start their operation, which then waits: `doFinal`/`sign`
  throw until a `BiometricPrompt` with a `CryptoObject` succeeds. The
  success unlocks the operations of that app waiting at that moment (ATL
  cannot see which `Cipher` object the CryptoObject wraps without
  reflection into libcore); every new `init` needs a new confirmation.
- Keys bound to the fingerprint store a hash of the enrolled fingers'
  names (from the service); a finger added or removed makes them throw
  `KeyPermanentlyInvalidatedException`. Re-enrolling a finger under the
  same name is not seen.
- Five wrong fingerprints in a row lock the fingerprint for 30 seconds
  for every app (the service counts), like Android.

# Deliberately not done

- **Attestation**: `setAttestationChallenge` gives a chain without the
  attestation extension. A real one would need Google-rooted hardware keys;
  even the phone's own TEE would report an unlocked bootloader.
- **StrongBox**: `setIsStrongBoxBacked(true)` throws
  `StrongBoxUnavailableException`, as on phones without one.
- **Play Integrity, SafetyNet**: never faked
  ([GMS decision](decisions/gms-local-only.md)).
- **Apps that refuse ATL's process** (VR SecureGo plus, Revolut): their
  protection libraries reject the environment before the keystore is
  used; disguising the process is out ([app status](apps.md)).

# Open

- `KeyguardManager.createConfirmDeviceCredentialIntent` still returns
  null (no confirmation activity); apps then fall back to `BiometricPrompt`
  or report that no lock screen is set.
- `FingerprintManager` (apps with `SDK_INT` below 28) still reports no
  hardware.
- Keys in the TEE (`/dev/tee0`, `qcomtee`) would keep keys out of the app's
  process. Not started: research, and attestation would still say
  "unlocked".
- A real self-signed certificate for key pairs (`AndroidKeyStoreCertificate`
  holds only the public key).

# Testing

- `tools/keystore-test.sh`: the keystore classes on a desktop JDK 17 with
  the service and libsecret replaced; 43 checks of the rules above,
  including entries in the format from before.
- On the phone (2026-11-27, engine of #219 through `flatpak run
  --app-path=<checked-out CI bundle> --command=android-translation-layer
  de.nexumia.atl.BaseApp`, headless, a test activity in an extra dex
  started with `-l` next to an APK): keystore keys through the Secret
  portal keyring, KeyInfo, EC signature, aliases, StrongBox refused, a
  per-use key refused before the fingerprint; the owner's finger on the
  sensor gave `MATCH` in fingerprintd, `match` in the service,
  `onAuthenticationSucceeded` (biometric) and the key worked. Aegis 82
  (F-Droid) offers "Biometric unlock" with this engine; its first-run
  intro does not get past ATL's activity order and ViewPager yet.
  fingerprintd counts only a firm, settled press.
- The service from a sandbox: run the built `atl-permission-service` while
  the name is free and call it with `flatpak run --command=gdbus <ATL app> call --session -d de.nexumia.atl.Permissions -o /de/nexumia/atl/Permissions -m de.nexumia.atl.Permissions.Authenticators`.
  `VerifyFingerprint` needs a finger on the sensor, `ConfirmCredential`
  the policy file and the owner's password.

[^android-keystore]: Android developers, Android Keystore system.
[^android-biometric]: Android developers, BiometricPrompt.
[^fprintd-dbus]: fprintd D-Bus API.
[^polkit-authority]: polkit Authority D-Bus interface.
[^portal-secret]: xdg-desktop-portal, Secret portal.
