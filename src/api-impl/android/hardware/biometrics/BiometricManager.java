package android.hardware.biometrics;

/* there is no biometric hardware ATL could use, so nothing can authenticate */
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

	public int canAuthenticate() {
		return BIOMETRIC_ERROR_NO_HARDWARE;
	}

	public int canAuthenticate(int authenticators) {
		return BIOMETRIC_ERROR_NO_HARDWARE;
	}
}
