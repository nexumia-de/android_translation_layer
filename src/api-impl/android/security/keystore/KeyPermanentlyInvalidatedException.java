package android.security.keystore;

import java.security.InvalidKeyException;

/* the key can never be used again, e.g. a fingerprint was added after it was made */
public class KeyPermanentlyInvalidatedException extends InvalidKeyException {
	public KeyPermanentlyInvalidatedException() {
		super("Key permanently invalidated");
	}

	public KeyPermanentlyInvalidatedException(String message) {
		super(message);
	}

	public KeyPermanentlyInvalidatedException(String message, Throwable cause) {
		super(message, cause);
	}
}
