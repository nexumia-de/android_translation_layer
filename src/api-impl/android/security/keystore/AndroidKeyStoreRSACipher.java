package android.security.keystore;

import java.security.AlgorithmParameters;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

/* RSA for keystore key pairs, in software: the private key's purposes and user authentication
 * are checked, public key operations need nothing. Also serves the provider names apps ask for on
 * Android (AndroidKeyStoreBCWorkaround, AndroidOpenSSL). */
public abstract class AndroidKeyStoreRSACipher extends CipherSpi {

	private final Cipher cipher;
	private AndroidKeyStorePrivateKey key; // null for public key operations

	protected AndroidKeyStoreRSACipher(String transformation) {
		try {
			cipher = Software.cipher(transformation);
		} catch (NoSuchAlgorithmException e) {
			throw new ProviderException(e);
		}
	}

	public static class PKCS1Padding extends AndroidKeyStoreRSACipher {
		public PKCS1Padding() { super("RSA/ECB/PKCS1Padding"); }
	}

	public static class NoPadding extends AndroidKeyStoreRSACipher {
		public NoPadding() { super("RSA/ECB/NoPadding"); }
	}

	public static class OAEPPadding extends AndroidKeyStoreRSACipher {
		public OAEPPadding() { super("RSA/ECB/OAEPPadding"); }
	}

	public static class OAEPWithSHA256AndMGF1Padding extends AndroidKeyStoreRSACipher {
		public OAEPWithSHA256AndMGF1Padding() { super("RSA/ECB/OAEPWithSHA-256AndMGF1Padding"); }
	}

	private Key plain(int opmode, Key key) throws InvalidKeyException {
		this.key = null;
		if (!(key instanceof AndroidKeyStorePrivateKey))
			return key;
		AndroidKeyStorePrivateKey k = (AndroidKeyStorePrivateKey)key;
		boolean decrypting = opmode == Cipher.DECRYPT_MODE || opmode == Cipher.UNWRAP_MODE;
		k.auth.checkInit(decrypting ? KeyProperties.PURPOSE_DECRYPT : KeyProperties.PURPOSE_SIGN, this);
		this.key = k;
		return k.plain();
	}

	private void checkAllowed() throws IllegalBlockSizeException {
		if (key != null && !key.auth.operationAllowed(this))
			throw new IllegalBlockSizeException("Key user not authenticated");
	}

	@Override
	protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
		if (!"ECB".equalsIgnoreCase(mode) && !"NONE".equalsIgnoreCase(mode))
			throw new NoSuchAlgorithmException(mode);
	}

	@Override
	protected void engineSetPadding(String padding) throws NoSuchPaddingException {
		throw new NoSuchPaddingException(padding);
	}

	@Override
	protected int engineGetBlockSize() { return cipher.getBlockSize(); }

	@Override
	protected int engineGetOutputSize(int inputLen) { return cipher.getOutputSize(inputLen); }

	@Override
	protected byte[] engineGetIV() { return cipher.getIV(); }

	@Override
	protected AlgorithmParameters engineGetParameters() { return cipher.getParameters(); }

	@Override
	protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
		cipher.init(opmode, plain(opmode, key), random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		cipher.init(opmode, plain(opmode, key), params, random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		cipher.init(opmode, plain(opmode, key), params, random);
	}

	@Override
	protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
		try {
			checkAllowed();
		} catch (IllegalBlockSizeException e) {
			throw new ProviderException("Keystore operation failed", new KeyStoreException(e.getMessage()));
		}
		return cipher.update(input, inputOffset, inputLen);
	}

	@Override
	protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
		try {
			checkAllowed();
		} catch (IllegalBlockSizeException e) {
			throw new ProviderException("Keystore operation failed", new KeyStoreException(e.getMessage()));
		}
		return cipher.update(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) throws IllegalBlockSizeException, BadPaddingException {
		checkAllowed();
		return cipher.doFinal(input, inputOffset, inputLen);
	}

	@Override
	protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		checkAllowed();
		return cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected byte[] engineWrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
		return cipher.wrap(key);
	}

	@Override
	protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType) throws InvalidKeyException, NoSuchAlgorithmException {
		try {
			checkAllowed();
		} catch (IllegalBlockSizeException e) {
			throw new InvalidKeyException(e.getMessage());
		}
		return cipher.unwrap(wrappedKey, wrappedKeyAlgorithm, wrappedKeyType);
	}
}
