package android.hardware.biometrics;

import android.content.Context;
import android.content.DialogInterface;
import android.os.CancellationSignal;
import java.security.Signature;
import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import javax.crypto.Mac;

/* there is no biometric hardware: authenticate reports BIOMETRIC_ERROR_HW_NOT_PRESENT */
public class BiometricPrompt {
	public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
	public static final int BIOMETRIC_ERROR_CANCELED = 5;
	public static final int BIOMETRIC_ERROR_USER_CANCELED = 10;
	public static final int BIOMETRIC_ERROR_NO_BIOMETRICS = 11;
	public static final int BIOMETRIC_ERROR_HW_NOT_PRESENT = 12;
	public static final int BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL = 14;
	public static final int AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL = 1;
	public static final int AUTHENTICATION_RESULT_TYPE_BIOMETRIC = 2;

	private BiometricPrompt() {}

	public void authenticate(CancellationSignal cancel, Executor executor, final AuthenticationCallback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				callback.onAuthenticationError(BIOMETRIC_ERROR_HW_NOT_PRESENT, "No biometric hardware");
			}
		});
	}

	public void authenticate(CryptoObject crypto, CancellationSignal cancel, Executor executor, AuthenticationCallback callback) {
		authenticate(cancel, executor, callback);
	}

	public static class CryptoObject {
		private final Object crypto;

		public CryptoObject(Signature signature) { crypto = signature; }
		public CryptoObject(Cipher cipher) { crypto = cipher; }
		public CryptoObject(Mac mac) { crypto = mac; }

		public Signature getSignature() { return crypto instanceof Signature ? (Signature)crypto : null; }
		public Cipher getCipher() { return crypto instanceof Cipher ? (Cipher)crypto : null; }
		public Mac getMac() { return crypto instanceof Mac ? (Mac)crypto : null; }
	}

	public static class AuthenticationResult {
		private final CryptoObject crypto;
		private final int type;

		public AuthenticationResult(CryptoObject crypto, int type) {
			this.crypto = crypto;
			this.type = type;
		}

		public CryptoObject getCryptoObject() { return crypto; }
		public int getAuthenticationType() { return type; }
	}

	public static abstract class AuthenticationCallback {
		public void onAuthenticationError(int errorCode, CharSequence errString) {}
		public void onAuthenticationHelp(int helpCode, CharSequence helpString) {}
		public void onAuthenticationSucceeded(AuthenticationResult result) {}
		public void onAuthenticationFailed() {}
	}

	public static class Builder {
		public Builder(Context context) {}
		public Builder setTitle(CharSequence title) { return this; }
		public Builder setSubtitle(CharSequence subtitle) { return this; }
		public Builder setDescription(CharSequence description) { return this; }
		public Builder setNegativeButton(CharSequence text, Executor executor, DialogInterface.OnClickListener listener) { return this; }
		public Builder setConfirmationRequired(boolean required) { return this; }
		public Builder setDeviceCredentialAllowed(boolean allowed) { return this; }
		public Builder setAllowedAuthenticators(int authenticators) { return this; }
		public BiometricPrompt build() { return new BiometricPrompt(); }
	}
}
