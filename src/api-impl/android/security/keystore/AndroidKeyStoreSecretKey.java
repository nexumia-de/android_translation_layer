package android.security.keystore;

import javax.crypto.spec.SecretKeySpec;

/* marks keys from the AndroidKeyStore, so its own Cipher is picked for them */
public class AndroidKeyStoreSecretKey extends SecretKeySpec {

	public AndroidKeyStoreSecretKey(byte[] key, String algorithm) {
		super(key, algorithm);
	}
}
