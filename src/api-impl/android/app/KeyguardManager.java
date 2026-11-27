package android.app;

public class KeyguardManager {
	public boolean inKeyguardRestrictedInputMode() {
		return false;
	}

	public boolean isKeyguardLocked() {
		return false;
	}

	/* the phone's lock screen belongs to Phosh; an app only runs while it is unlocked */
	public boolean isDeviceLocked() {
		return false;
	}

	public boolean isKeyguardSecure() {
		return true;
	}

	/* null, as on Android without a secure lock screen the app could confirm */
	public android.content.Intent createConfirmDeviceCredentialIntent(CharSequence title, CharSequence description) {
		return null;
	}

	public KeyguardLock newKeyguardLock(String tag) {
		return new KeyguardLock();
	}

	public boolean isDeviceSecure() {
		return false;
	}

	public class KeyguardLock {
		public void disableKeyguard() {}
		public void reenableKeyguard() {}
	}
}
