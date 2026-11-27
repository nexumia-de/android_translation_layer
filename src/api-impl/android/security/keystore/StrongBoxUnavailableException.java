package android.security.keystore;

import java.security.ProviderException;

/* there is no StrongBox (separate secure chip) for keys here */
public class StrongBoxUnavailableException extends ProviderException {
	public StrongBoxUnavailableException() {
		super();
	}

	public StrongBoxUnavailableException(String message) {
		super(message);
	}

	public StrongBoxUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
