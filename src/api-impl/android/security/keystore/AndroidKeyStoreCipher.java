package android.security.keystore;

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidParameterSpecException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;

/* AES with AndroidKeyStore keys. Like Android's keystore: the key's purposes and user
 * authentication are checked; for encryption the IV is always generated (12 bytes for GCM, which
 * apps such as Tink check, while Bouncy Castle on its own makes 16), and with
 * randomizedEncryptionRequired an IV from the caller or ECB is refused. */
public abstract class AndroidKeyStoreCipher extends CipherSpi {
	private static final int GCM_TAG_BITS = 128;

	private final String mode;
	private final Cipher cipher;
	private AndroidKeyStoreSecretKey key;

	protected AndroidKeyStoreCipher(String mode, String padding) {
		this.mode = mode;
		try {
			cipher = Software.cipher("AES/" + mode + "/" + padding);
		} catch (NoSuchAlgorithmException e) {
			throw new ProviderException(e);
		}
	}

	public static class AesGcmNoPadding extends AndroidKeyStoreCipher {
		public AesGcmNoPadding() { super("GCM", "NoPadding"); }
	}

	public static class AesCbcNoPadding extends AndroidKeyStoreCipher {
		public AesCbcNoPadding() { super("CBC", "NoPadding"); }
	}

	public static class AesCbcPkcs7Padding extends AndroidKeyStoreCipher {
		public AesCbcPkcs7Padding() { super("CBC", "PKCS7Padding"); }
	}

	public static class AesCtrNoPadding extends AndroidKeyStoreCipher {
		public AesCtrNoPadding() { super("CTR", "NoPadding"); }
	}

	public static class AesEcbNoPadding extends AndroidKeyStoreCipher {
		public AesEcbNoPadding() { super("ECB", "NoPadding"); }
	}

	public static class AesEcbPkcs7Padding extends AndroidKeyStoreCipher {
		public AesEcbPkcs7Padding() { super("ECB", "PKCS7Padding"); }
	}

	private static boolean encrypting(int opmode) {
		return opmode == Cipher.ENCRYPT_MODE || opmode == Cipher.WRAP_MODE;
	}

	private AndroidKeyStoreSecretKey start(int opmode, Key key) throws InvalidKeyException {
		if (!(key instanceof AndroidKeyStoreSecretKey))
			throw new InvalidKeyException("Unsupported key: " + (key != null ? key.getClass().getName() : "null"));
		AndroidKeyStoreSecretKey k = (AndroidKeyStoreSecretKey)key;
		if (encrypting(opmode) && k.auth.randomizedEncryptionRequired && mode.equals("ECB"))
			throw new InvalidKeyException("Randomized encryption (IND-CPA) required but may be violated by block mode: ECB");
		k.auth.checkInit(encrypting(opmode) ? KeyProperties.PURPOSE_ENCRYPT : KeyProperties.PURPOSE_DECRYPT, this);
		this.key = k;
		return k;
	}

	@Override
	protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
		AndroidKeyStoreSecretKey k = start(opmode, key);
		try {
			if (mode.equals("ECB")) {
				cipher.init(opmode, k.plain(), random);
				return;
			}
			if (!encrypting(opmode))
				throw new InvalidKeyException("IV required when decrypting. Use IvParameterSpec or AlgorithmParameters to provide it.");
			byte[] iv = new byte[mode.equals("GCM") ? 12 : 16];
			(random != null ? random : new SecureRandom()).nextBytes(iv);
			AlgorithmParameterSpec spec = mode.equals("GCM") ? new GCMParameterSpec(GCM_TAG_BITS, iv) : new IvParameterSpec(iv);
			cipher.init(opmode, k.plain(), spec, random);
		} catch (InvalidAlgorithmParameterException e) {
			throw new InvalidKeyException(e);
		}
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
	    throws InvalidKeyException, InvalidAlgorithmParameterException {
		if (params == null) {
			engineInit(opmode, key, random);
			return;
		}
		AndroidKeyStoreSecretKey k = start(opmode, key);
		if (encrypting(opmode) && k.auth.randomizedEncryptionRequired)
			throw new InvalidAlgorithmParameterException("Caller-provided IV not permitted");
		cipher.init(opmode, k.plain(), params, random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
	    throws InvalidKeyException, InvalidAlgorithmParameterException {
		if (params == null) {
			engineInit(opmode, key, random);
			return;
		}
		try {
			Class<? extends AlgorithmParameterSpec> type = mode.equals("GCM") ? GCMParameterSpec.class : IvParameterSpec.class;
			engineInit(opmode, key, params.getParameterSpec(type), random);
		} catch (InvalidParameterSpecException e) {
			throw new InvalidAlgorithmParameterException(e);
		}
	}

	private void checkAllowed() throws IllegalBlockSizeException {
		if (key == null)
			throw new IllegalStateException("not initialized");
		if (!key.auth.operationAllowed(this))
			throw new IllegalBlockSizeException("Key user not authenticated");
	}

	private void checkAllowedUnchecked() {
		try {
			checkAllowed();
		} catch (IllegalBlockSizeException e) {
			throw new ProviderException("Keystore operation failed", new KeyStoreException("Key user not authenticated"));
		}
	}

	@Override
	protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
		if (!this.mode.equalsIgnoreCase(mode))
			throw new NoSuchAlgorithmException("mode is fixed: " + mode);
	}

	@Override
	protected void engineSetPadding(String padding) throws NoSuchPaddingException {
		throw new NoSuchPaddingException("padding is fixed: " + padding);
	}

	@Override
	protected int engineGetBlockSize() {
		return cipher.getBlockSize();
	}

	@Override
	protected int engineGetOutputSize(int inputLen) {
		return cipher.getOutputSize(inputLen);
	}

	@Override
	protected byte[] engineGetIV() {
		return cipher.getIV();
	}

	@Override
	protected AlgorithmParameters engineGetParameters() {
		return cipher.getParameters();
	}

	@Override
	protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
		checkAllowedUnchecked();
		return cipher.update(input, inputOffset, inputLen);
	}

	@Override
	protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
	    throws ShortBufferException {
		checkAllowedUnchecked();
		return cipher.update(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected void engineUpdateAAD(byte[] src, int offset, int len) {
		checkAllowedUnchecked();
		cipher.updateAAD(src, offset, len);
	}

	@Override
	protected void engineUpdateAAD(ByteBuffer src) {
		checkAllowedUnchecked();
		cipher.updateAAD(src);
	}

	@Override
	protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
	    throws IllegalBlockSizeException, BadPaddingException {
		checkAllowed();
		return cipher.doFinal(input, inputOffset, inputLen);
	}

	@Override
	protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
	    throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		checkAllowed();
		return cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected byte[] engineWrap(Key keyToWrap) throws IllegalBlockSizeException, InvalidKeyException {
		checkAllowed();
		byte[] encoded = keyToWrap.getEncoded();
		if (encoded == null)
			throw new InvalidKeyException("key cannot be encoded");
		try {
			return cipher.doFinal(encoded);
		} catch (BadPaddingException e) {
			throw new IllegalBlockSizeException(e.toString());
		}
	}

	@Override
	protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType)
	    throws InvalidKeyException, NoSuchAlgorithmException {
		try {
			checkAllowed();
			byte[] encoded = cipher.doFinal(wrappedKey);
			switch (wrappedKeyType) {
				case Cipher.SECRET_KEY:
					return new javax.crypto.spec.SecretKeySpec(encoded, wrappedKeyAlgorithm);
				case Cipher.PRIVATE_KEY:
					return Software.keyFactory(wrappedKeyAlgorithm).generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(encoded));
				case Cipher.PUBLIC_KEY:
					return Software.keyFactory(wrappedKeyAlgorithm).generatePublic(new java.security.spec.X509EncodedKeySpec(encoded));
				default:
					throw new InvalidKeyException("unknown key type " + wrappedKeyType);
			}
		} catch (GeneralSecurityException e) {
			if (e instanceof InvalidKeyException)
				throw (InvalidKeyException)e;
			throw new InvalidKeyException(e);
		}
	}
}
