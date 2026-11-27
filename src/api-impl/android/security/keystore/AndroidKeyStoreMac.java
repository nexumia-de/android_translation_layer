package android.security.keystore;

import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.Mac;
import javax.crypto.MacSpi;

/* HMAC with keystore keys, in software, after the key's purposes and user authentication are
 * checked. */
public abstract class AndroidKeyStoreMac extends MacSpi {
	private final Mac mac;
	private AndroidKeyStoreSecretKey key;

	protected AndroidKeyStoreMac(String algorithm) {
		try {
			mac = Software.mac(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new ProviderException(e);
		}
	}

	public static class HmacSHA1 extends AndroidKeyStoreMac { public HmacSHA1() { super("HmacSHA1"); } }
	public static class HmacSHA224 extends AndroidKeyStoreMac { public HmacSHA224() { super("HmacSHA224"); } }
	public static class HmacSHA256 extends AndroidKeyStoreMac { public HmacSHA256() { super("HmacSHA256"); } }
	public static class HmacSHA384 extends AndroidKeyStoreMac { public HmacSHA384() { super("HmacSHA384"); } }
	public static class HmacSHA512 extends AndroidKeyStoreMac { public HmacSHA512() { super("HmacSHA512"); } }

	static final String[] ALGORITHMS = {"HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512"};

	@Override
	protected void engineInit(Key key, AlgorithmParameterSpec params) throws InvalidKeyException, InvalidAlgorithmParameterException {
		if (!(key instanceof AndroidKeyStoreSecretKey))
			throw new InvalidKeyException("Unsupported key: " + (key != null ? key.getClass().getName() : "null"));
		AndroidKeyStoreSecretKey k = (AndroidKeyStoreSecretKey)key;
		k.auth.checkInit(KeyProperties.PURPOSE_SIGN, this);
		this.key = k;
		mac.init(k.plain(), params);
	}

	private void checkAllowed() {
		if (key == null)
			throw new IllegalStateException("not initialized");
		if (!key.auth.operationAllowed(this))
			throw new ProviderException("Keystore operation failed", new KeyStoreException("Key user not authenticated"));
	}

	@Override
	protected int engineGetMacLength() {
		return mac.getMacLength();
	}

	@Override
	protected void engineUpdate(byte input) {
		checkAllowed();
		mac.update(input);
	}

	@Override
	protected void engineUpdate(byte[] input, int offset, int len) {
		checkAllowed();
		mac.update(input, offset, len);
	}

	@Override
	protected void engineUpdate(ByteBuffer input) {
		checkAllowed();
		mac.update(input);
	}

	@Override
	protected byte[] engineDoFinal() {
		checkAllowed();
		return mac.doFinal();
	}

	@Override
	protected void engineReset() {
		mac.reset();
	}
}
