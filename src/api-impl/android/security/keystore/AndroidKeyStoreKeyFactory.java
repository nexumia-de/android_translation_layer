package android.security.keystore;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyFactorySpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

/* KeyFactory.getInstance("EC"/"RSA", "AndroidKeyStore"): KeyInfo for keystore private keys.
 * The provider comes first (Context), so lookups without a provider name end up here too;
 * everything else goes to the software KeyFactory. */
public abstract class AndroidKeyStoreKeyFactory extends KeyFactorySpi {
	private final KeyFactory software;

	protected AndroidKeyStoreKeyFactory(String algorithm) {
		try {
			software = Software.keyFactory(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new ProviderException(e);
		}
	}

	public static class EC extends AndroidKeyStoreKeyFactory { public EC() { super("EC"); } }
	public static class RSA extends AndroidKeyStoreKeyFactory { public RSA() { super("RSA"); } }

	@Override
	protected PublicKey engineGeneratePublic(KeySpec keySpec) throws InvalidKeySpecException {
		return software.generatePublic(keySpec);
	}

	@Override
	protected PrivateKey engineGeneratePrivate(KeySpec keySpec) throws InvalidKeySpecException {
		return software.generatePrivate(keySpec);
	}

	@Override
	@SuppressWarnings("unchecked")
	protected <T extends KeySpec> T engineGetKeySpec(Key key, Class<T> keySpec) throws InvalidKeySpecException {
		if (key instanceof AndroidKeyStorePrivateKey) {
			if (keySpec == null || !keySpec.isAssignableFrom(KeyInfo.class))
				throw new InvalidKeySpecException("Key material is not exportable; only KeyInfo is available");
			AndroidKeyStorePrivateKey k = (AndroidKeyStorePrivateKey)key;
			return (T)new KeyInfo(k.alias(), k.sizeBits(), k.auth);
		}
		return software.getKeySpec(key, keySpec);
	}

	@Override
	protected Key engineTranslateKey(Key key) throws InvalidKeyException {
		if (key instanceof AndroidKeyStorePrivateKey)
			return key;
		return software.translateKey(key);
	}
}
