package android.security.keystore;

import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyPairGeneratorSpi;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;

/* Generates an RSA or EC key pair in software and keeps it under the spec's alias with its
 * KeyAuthorizations, like the keys of KeyGenerator. The app gets the public key and an
 * AndroidKeyStorePrivateKey. */
public class AndroidKeyStoreKeyPairGenerator extends KeyPairGeneratorSpi {

	private final String algorithm;
	private KeyGenParameterSpec spec;
	private SecureRandom random;

	protected AndroidKeyStoreKeyPairGenerator(String algorithm) {
		this.algorithm = algorithm;
	}

	public static class RSA extends AndroidKeyStoreKeyPairGenerator {
		public RSA() {
			super("RSA");
		}
	}

	public static class EC extends AndroidKeyStoreKeyPairGenerator {
		public EC() {
			super("EC");
		}
	}

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

	/* Android's defaults: RSA with 2048 bit, EC on P-256; an EC key size
	 * picks the NIST curve of that size */
	private AlgorithmParameterSpec params() {
		AlgorithmParameterSpec params = spec.getAlgorithmParameterSpec();
		if (params != null || !"EC".equals(algorithm))
			return params;
		switch (spec.getKeySize()) {
			case 224:
				return new ECGenParameterSpec("secp224r1");
			case 384:
				return new ECGenParameterSpec("secp384r1");
			case 521:
				return new ECGenParameterSpec("secp521r1");
			default:
				return new ECGenParameterSpec("secp256r1");
		}
	}

	@Override
	public KeyPair generateKeyPair() {
		if (spec == null)
			throw new IllegalStateException("not initialized");
		if (spec.isStrongBoxBacked())
			throw new StrongBoxUnavailableException("No StrongBox available");
		KeyAuthorizations auth = KeyAuthorizations.of(spec);
		auth.checkCreate();
		try {
			KeyPairGenerator generator = Software.keyPairGenerator(algorithm);
			AlgorithmParameterSpec params = params();
			if (params != null && random != null)
				generator.initialize(params, random);
			else if (params != null)
				generator.initialize(params);
			else if (random != null)
				generator.initialize(spec.getKeySize() > 0 ? spec.getKeySize() : 2048, random);
			else
				generator.initialize(spec.getKeySize() > 0 ? spec.getKeySize() : 2048);
			KeyPair pair = generator.generateKeyPair();
			String alias = spec.getKeystoreAlias();
			AndroidKeyStore.setKeyPair(alias, algorithm, pair.getPrivate(), pair.getPublic(), auth);
			/* the private key stays in the keystore; the public key is the app's */
			return new KeyPair(pair.getPublic(), AndroidKeyStorePrivateKey.wrap(alias, pair.getPrivate(), auth));
		} catch (GeneralSecurityException e) {
			throw new ProviderException(e);
		}
	}
}
