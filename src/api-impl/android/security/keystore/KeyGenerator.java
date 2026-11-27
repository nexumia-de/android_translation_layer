package android.security.keystore;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;

/* Makes an AES or HMAC key in software and keeps it under the spec's alias with its
 * KeyAuthorizations; the app gets an AndroidKeyStoreSecretKey. Sizes as on Android: AES 128 bit
 * unless the spec says 192 or 256, HMAC the digest's size. */
public abstract class KeyGenerator extends KeyGeneratorSpi {

	private final String algorithm;
	private final int defaultSize;
	private KeyGenParameterSpec spec;
	private SecureRandom random;

	protected KeyGenerator(String algorithm, int defaultSize) {
		this.algorithm = algorithm;
		this.defaultSize = defaultSize;
	}

	public static class AES extends KeyGenerator { public AES() { super("AES", 128); } }
	public static class HmacSHA1 extends KeyGenerator { public HmacSHA1() { super("HmacSHA1", 160); } }
	public static class HmacSHA224 extends KeyGenerator { public HmacSHA224() { super("HmacSHA224", 224); } }
	public static class HmacSHA256 extends KeyGenerator { public HmacSHA256() { super("HmacSHA256", 256); } }
	public static class HmacSHA384 extends KeyGenerator { public HmacSHA384() { super("HmacSHA384", 384); } }
	public static class HmacSHA512 extends KeyGenerator { public HmacSHA512() { super("HmacSHA512", 512); } }

	static final String[] ALGORITHMS = {"AES", "HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512"};

	@Override
	protected void engineInit(AlgorithmParameterSpec params, SecureRandom random) throws InvalidAlgorithmParameterException {
		if (!(params instanceof KeyGenParameterSpec))
			throw new InvalidAlgorithmParameterException("Cannot initialize without a KeyGenParameterSpec");
		KeyGenParameterSpec spec = (KeyGenParameterSpec)params;
		int size = spec.getKeySize() > 0 ? spec.getKeySize() : defaultSize;
		if (algorithm.equals("AES") && size != 128 && size != 192 && size != 256)
			throw new InvalidAlgorithmParameterException("Unsupported key size: " + size + ". Supported: 128, 192, 256.");
		if (!algorithm.equals("AES") && (size < 64 || size > 512 || size % 8 != 0))
			throw new InvalidAlgorithmParameterException("HMAC key size must be a multiple of 8 between 64 and 512: " + size);
		this.spec = spec;
		this.random = random;
	}

	@Override
	protected void engineInit(SecureRandom random) {
		throw new IllegalStateException("Cannot initialize without a KeyGenParameterSpec");
	}

	@Override
	protected void engineInit(int keysize, SecureRandom random) {
		throw new UnsupportedOperationException("Cannot initialize without a KeyGenParameterSpec");
	}

	@Override
	protected SecretKey engineGenerateKey() {
		if (spec == null)
			throw new IllegalStateException("Not initialized");
		if (spec.isStrongBoxBacked())
			throw new StrongBoxUnavailableException("No StrongBox available");
		KeyAuthorizations auth = KeyAuthorizations.of(spec);
		auth.checkCreate();
		byte[] material = new byte[(spec.getKeySize() > 0 ? spec.getKeySize() : defaultSize) / 8];
		(random != null ? random : new SecureRandom()).nextBytes(material);
		String alias = spec.getKeystoreAlias();
		AndroidKeyStore.setSecretKey(alias, material, algorithm, auth);
		return new AndroidKeyStoreSecretKey(alias, material, algorithm, auth);
	}
}
