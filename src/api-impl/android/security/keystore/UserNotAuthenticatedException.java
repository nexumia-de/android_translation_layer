package android.security.keystore;

import java.security.InvalidKeyException;

/* the key needs a confirmation of the owner (BiometricPrompt) that has not happened, or is too old */
public class UserNotAuthenticatedException extends InvalidKeyException {
	public UserNotAuthenticatedException() {
		super("User not authenticated");
	}

	public UserNotAuthenticatedException(String message) {
		super(message);
	}

	public UserNotAuthenticatedException(String message, Throwable cause) {
		super(message, cause);
	}
}
