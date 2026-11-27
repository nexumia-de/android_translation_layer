package android.security.keystore;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/* An AES or HMAC key of the AndroidKeyStore. As on Android getEncoded() is null, so only the
 * keystore's own Cipher and Mac take it, and they check its KeyAuthorizations first. */
public class AndroidKeyStoreSecretKey implements SecretKey {
	private final String alias;
	private final String algorithm;
	private final byte[] material;
	final KeyAuthorizations auth;

	AndroidKeyStoreSecretKey(String alias, byte[] material, String algorithm, KeyAuthorizations auth) {
		this.alias = alias;
		this.material = material.clone();
		this.algorithm = algorithm;
		this.auth = auth;
	}

	String alias() {
		return alias;
	}

	/* for the keystore's own operations only */
	SecretKeySpec plain() {
		return new SecretKeySpec(material, algorithm);
	}

	int sizeBits() {
		return material.length * 8;
	}

	@Override
	public String getAlgorithm() {
		return algorithm;
	}

	@Override
	public String getFormat() {
		return null;
	}

	@Override
	public byte[] getEncoded() {
		return null;
	}
}
