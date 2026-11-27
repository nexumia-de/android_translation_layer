package android.security.keystore;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.KeyAgreement;
import javax.crypto.KeyAgreementSpi;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;

/* ECDH with a keystore private key (PURPOSE_AGREE_KEY), in software. */
public class AndroidKeyStoreKeyAgreement extends KeyAgreementSpi {
	private final KeyAgreement agreement;
	private AndroidKeyStorePrivateKey key;

	public AndroidKeyStoreKeyAgreement() {
		try {
			agreement = Software.keyAgreement("ECDH");
		} catch (NoSuchAlgorithmException e) {
			throw new ProviderException(e);
		}
	}

	private AndroidKeyStorePrivateKey start(Key key) throws InvalidKeyException {
		if (!(key instanceof AndroidKeyStorePrivateKey))
			throw new InvalidKeyException("Unsupported key: " + (key != null ? key.getClass().getName() : "null"));
		AndroidKeyStorePrivateKey k = (AndroidKeyStorePrivateKey)key;
		k.auth.checkInit(KeyProperties.PURPOSE_AGREE_KEY, this);
		this.key = k;
		return k;
	}

	private void checkAllowed() {
		if (key == null)
			throw new IllegalStateException("not initialized");
		if (!key.auth.operationAllowed(this))
			throw new IllegalStateException("Key user not authenticated");
	}

	@Override
	protected void engineInit(Key key, SecureRandom random) throws InvalidKeyException {
		agreement.init(start(key).plain(), random);
	}

	@Override
	protected void engineInit(Key key, AlgorithmParameterSpec params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		agreement.init(start(key).plain(), params, random);
	}

	@Override
	protected Key engineDoPhase(Key key, boolean lastPhase) throws InvalidKeyException, IllegalStateException {
		checkAllowed();
		return agreement.doPhase(key, lastPhase);
	}

	@Override
	protected byte[] engineGenerateSecret() throws IllegalStateException {
		checkAllowed();
		return agreement.generateSecret();
	}

	@Override
	protected int engineGenerateSecret(byte[] sharedSecret, int offset) throws IllegalStateException, ShortBufferException {
		checkAllowed();
		return agreement.generateSecret(sharedSecret, offset);
	}

	@Override
	protected SecretKey engineGenerateSecret(String algorithm) throws IllegalStateException, NoSuchAlgorithmException, InvalidKeyException {
		checkAllowed();
		return agreement.generateSecret(algorithm);
	}
}
