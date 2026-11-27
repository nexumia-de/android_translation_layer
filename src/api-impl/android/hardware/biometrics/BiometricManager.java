package android.hardware.biometrics;

import android.atl.ATLAuth;
import android.atl.ATLPaths;

/* What can confirm the phone's owner, from the host's permission service (docs/keystore-auth.md):
 * the fingerprint through fingerprintd (BIOMETRIC_STRONG) and the password through polkit
 * (DEVICE_CREDENTIAL). */
public class BiometricManager {
	public static final int BIOMETRIC_SUCCESS = 0;
	public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
	public static final int BIOMETRIC_ERROR_NONE_ENROLLED = 11;
	public static final int BIOMETRIC_ERROR_NO_HARDWARE = 12;
	public static final int BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15;

	public static interface Authenticators {
		public static final int BIOMETRIC_STRONG = 0x000F;
		public static final int BIOMETRIC_WEAK = 0x00FF;
		public static final int DEVICE_CREDENTIAL = 1 << 15;
	}

	public BiometricManager() {}

	@Deprecated
	public int canAuthenticate() {
		return canAuthenticate(Authenticators.BIOMETRIC_WEAK);
	}

	public int canAuthenticate(int authenticators) {
		if (!ATLPaths.in_flatpak)
			return BIOMETRIC_ERROR_NO_HARDWARE;
		int available = ATLAuth.available();
		if ((authenticators & Authenticators.BIOMETRIC_WEAK) != 0 && (available & ATLAuth.BIOMETRIC_STRONG) != 0)
			return BIOMETRIC_SUCCESS;
		if ((authenticators & Authenticators.DEVICE_CREDENTIAL) != 0 && (available & ATLAuth.DEVICE_CREDENTIAL) != 0)
			return BIOMETRIC_SUCCESS;
		return BIOMETRIC_ERROR_NONE_ENROLLED;
	}
}
