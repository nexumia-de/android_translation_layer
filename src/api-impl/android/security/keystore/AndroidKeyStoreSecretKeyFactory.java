package android.security.keystore;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKeyFactorySpi;

/* SecretKeyFactory.getInstance(<algorithm>, "AndroidKeyStore"): KeyInfo for keystore keys, as on
 * Android. The provider comes first (Context), so lookups without a provider name end up here
 * too; other keys and specs go to the software SecretKeyFactory, if there is one. */
public abstract class AndroidKeyStoreSecretKeyFactory extends SecretKeyFactorySpi {
	private final String algorithm;

	protected AndroidKeyStoreSecretKeyFactory(String algorithm) {
		this.algorithm = algorithm;
	}

	public static class AES extends AndroidKeyStoreSecretKeyFactory { public AES() { super("AES"); } }
	public static class HmacSHA1 extends AndroidKeyStoreSecretKeyFactory { public HmacSHA1() { super("HmacSHA1"); } }
	public static class HmacSHA224 extends AndroidKeyStoreSecretKeyFactory { public HmacSHA224() { super("HmacSHA224"); } }
	public static class HmacSHA256 extends AndroidKeyStoreSecretKeyFactory { public HmacSHA256() { super("HmacSHA256"); } }
	public static class HmacSHA384 extends AndroidKeyStoreSecretKeyFactory { public HmacSHA384() { super("HmacSHA384"); } }
	public static class HmacSHA512 extends AndroidKeyStoreSecretKeyFactory { public HmacSHA512() { super("HmacSHA512"); } }

	static final String[] ALGORITHMS = {"AES", "HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512"};

	private SecretKeyFactory software() throws InvalidKeySpecException {
		try {
			return Software.secretKeyFactory(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new InvalidKeySpecException("To generate secret key in Android Keystore, use KeyGenerator initialized with KeyGenParameterSpec");
		}
	}

	@Override
	protected SecretKey engineGenerateSecret(KeySpec keySpec) throws InvalidKeySpecException {
		return software().generateSecret(keySpec);
	}

	@Override
	protected KeySpec engineGetKeySpec(SecretKey key, Class<?> keySpec) throws InvalidKeySpecException {
		if (!(key instanceof AndroidKeyStoreSecretKey))
			return software().getKeySpec(key, keySpec);
		if (keySpec == null || !keySpec.isAssignableFrom(KeyInfo.class))
			throw new InvalidKeySpecException("Key material is not exportable; only KeyInfo is available");
		AndroidKeyStoreSecretKey k = (AndroidKeyStoreSecretKey)key;
		return new KeyInfo(k.alias(), k.sizeBits(), k.auth);
	}

	@Override
	protected SecretKey engineTranslateKey(SecretKey key) throws InvalidKeyException {
		if (key instanceof AndroidKeyStoreSecretKey)
			return key;
		try {
			return software().translateKey(key);
		} catch (InvalidKeySpecException e) {
			throw new InvalidKeyException(e.getMessage());
		}
	}
}
