package android.app;

import android.atl.ATLAuth;

/* The phone's lock screen belongs to Phosh; the host's permission service reports whether it is up
 * and whether the owner can be confirmed (docs/keystore-auth.md). */
public class KeyguardManager {
	public boolean inKeyguardRestrictedInputMode() {
		return isKeyguardLocked();
	}

	public boolean isKeyguardLocked() {
		return ATLAuth.deviceLocked();
	}

	public boolean isDeviceLocked() {
		return ATLAuth.deviceLocked();
	}

	/* a fingerprint or the password can confirm the owner */
	public boolean isKeyguardSecure() {
		return ATLAuth.available() != 0;
	}

	public boolean isDeviceSecure() {
		return ATLAuth.available() != 0;
	}

	/* null, as on Android without a secure lock screen: ATL has no confirmation activity yet;
	 * BiometricPrompt with DEVICE_CREDENTIAL asks for the password */
	public android.content.Intent createConfirmDeviceCredentialIntent(CharSequence title, CharSequence description) {
		return null;
	}

	public KeyguardLock newKeyguardLock(String tag) {
		return new KeyguardLock();
	}

	public class KeyguardLock {
		public void disableKeyguard() {}
		public void reenableKeyguard() {}
	}
}
