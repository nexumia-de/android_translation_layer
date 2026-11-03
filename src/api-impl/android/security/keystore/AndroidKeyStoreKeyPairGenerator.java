package android.security.keystore;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/* Generates an RSA key pair in software and keeps it under the spec's alias
 * in the Secret Service, like the AES keys of KeyGenerator. */
public class AndroidKeyStoreKeyPairGenerator extends KeyPairGeneratorSpi {

	private KeyGenParameterSpec spec;
	private SecureRandom random;

	public static class RSA extends AndroidKeyStoreKeyPairGenerator {}

	@Override
	public void initialize(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
		if (!(params instanceof KeyGenParameterSpec))
			throw new InvalidAlgorithmParameterException("KeyGenParameterSpec required");
		this.spec = (KeyGenParameterSpec)params;
		this.random = random;
	}

	@Override
	public void initialize(int keysize, SecureRandom random) {
		throw new IllegalArgumentException("AndroidKeyStore needs a KeyGenParameterSpec with an alias");
	}

	@Override
	public KeyPair generateKeyPair() {
		if (spec == null)
			throw new IllegalStateException("not initialized");
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "BC");
			int keySize = spec.getKeySize() > 0 ? spec.getKeySize() : 2048;
			if (random != null)
				generator.initialize(keySize, random);
			else
				generator.initialize(keySize);
			KeyPair pair = generator.generateKeyPair();
			AndroidKeyStore.setKeyPair(spec.getKeystoreAlias(), pair);
			return pair;
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException(e);
		}
	}
}
