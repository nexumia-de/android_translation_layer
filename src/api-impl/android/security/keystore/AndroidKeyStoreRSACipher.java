package android.security.keystore;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherSpi;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;

/* RSA for keystore key pairs, which are software keys here: hands the work
 * to Bouncy Castle. Also serves the provider names apps ask for on Android
 * (AndroidKeyStoreBCWorkaround, AndroidOpenSSL). */
public abstract class AndroidKeyStoreRSACipher extends CipherSpi {

	private final Cipher cipher;

	protected AndroidKeyStoreRSACipher(String transformation) {
		try {
			cipher = Cipher.getInstance(transformation, "BC");
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	public static class PKCS1Padding extends AndroidKeyStoreRSACipher {
		public PKCS1Padding() { super("RSA/ECB/PKCS1Padding"); }
	}

	public static class OAEPPadding extends AndroidKeyStoreRSACipher {
		public OAEPPadding() { super("RSA/ECB/OAEPPadding"); }
	}

	public static class OAEPWithSHA256AndMGF1Padding extends AndroidKeyStoreRSACipher {
		public OAEPWithSHA256AndMGF1Padding() { super("RSA/ECB/OAEPWithSHA-256AndMGF1Padding"); }
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
		cipher.init(opmode, key, random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		cipher.init(opmode, key, params, random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random) throws InvalidKeyException, InvalidAlgorithmParameterException {
		cipher.init(opmode, key, params, random);
	}

	@Override
	protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
		return cipher.update(input, inputOffset, inputLen);
	}

	@Override
	protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException {
		return cipher.update(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen) throws IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal(input, inputOffset, inputLen);
	}

	@Override
	protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset) throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected byte[] engineWrap(Key key) throws IllegalBlockSizeException, InvalidKeyException {
		return cipher.wrap(key);
	}

	@Override
	protected Key engineUnwrap(byte[] wrappedKey, String wrappedKeyAlgorithm, int wrappedKeyType) throws InvalidKeyException, NoSuchAlgorithmException {
		return cipher.unwrap(wrappedKey, wrappedKeyAlgorithm, wrappedKeyType);
	}
}
