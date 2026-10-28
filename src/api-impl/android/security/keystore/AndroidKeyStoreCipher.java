package android.security.keystore;

import java.nio.ByteBuffer;
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
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public abstract class AndroidKeyStoreCipher extends CipherSpi {

	protected Cipher cipher;

	protected AndroidKeyStoreCipher(String transformation) {
		try {
			cipher = Cipher.getInstance(transformation, "BC");
		} catch (GeneralSecurityException e) {
			throw new RuntimeException(e);
		}
	}

	/* like Android's keystore: the IV is always generated for encryption and has 12 bytes,
	 * which apps such as Tink check, while BouncyCastle on its own makes 16 */
	public static class AesGcmNoPadding extends AndroidKeyStoreCipher {
		private static final int IV_LENGTH = 12;
		private static final int TAG_LENGTH_BITS = 128;

		public AesGcmNoPadding() {
			super("AES/GCM/NoPadding");
		}

		@Override
		protected void engineInit(int opmode, Key key, SecureRandom random) throws InvalidKeyException {
			if (opmode == Cipher.DECRYPT_MODE || opmode == Cipher.UNWRAP_MODE)
				throw new InvalidKeyException("IV required when decrypting");
			byte[] iv = new byte[IV_LENGTH];
			(random != null ? random : new SecureRandom()).nextBytes(iv);
			try {
				cipher.init(opmode, plainKey(key), new GCMParameterSpec(TAG_LENGTH_BITS, iv), random);
			} catch (InvalidAlgorithmParameterException e) {
				throw new InvalidKeyException(e);
			}
		}
	}

	protected static Key plainKey(Key key) {
		return new SecretKeySpec(key.getEncoded(), key.getAlgorithm());
	}

	@Override
	protected void engineSetMode(String mode) throws NoSuchAlgorithmException {
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
	protected void engineInit(int opmode, Key key, AlgorithmParameterSpec params, SecureRandom random)
	    throws InvalidKeyException, InvalidAlgorithmParameterException {
		if (params == null)
			engineInit(opmode, key, random);
		else
			cipher.init(opmode, plainKey(key), params, random);
	}

	@Override
	protected void engineInit(int opmode, Key key, AlgorithmParameters params, SecureRandom random)
	    throws InvalidKeyException, InvalidAlgorithmParameterException {
		if (params == null)
			engineInit(opmode, key, random);
		else
			cipher.init(opmode, plainKey(key), params, random);
	}

	@Override
	protected byte[] engineUpdate(byte[] input, int inputOffset, int inputLen) {
		return cipher.update(input, inputOffset, inputLen);
	}

	@Override
	protected int engineUpdate(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
	    throws ShortBufferException {
		return cipher.update(input, inputOffset, inputLen, output, outputOffset);
	}

	@Override
	protected void engineUpdateAAD(byte[] src, int offset, int len) {
		cipher.updateAAD(src, offset, len);
	}

	@Override
	protected void engineUpdateAAD(ByteBuffer src) {
		cipher.updateAAD(src);
	}

	@Override
	protected byte[] engineDoFinal(byte[] input, int inputOffset, int inputLen)
	    throws IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal(input, inputOffset, inputLen);
	}

	@Override
	protected int engineDoFinal(byte[] input, int inputOffset, int inputLen, byte[] output, int outputOffset)
	    throws ShortBufferException, IllegalBlockSizeException, BadPaddingException {
		return cipher.doFinal(input, inputOffset, inputLen, output, outputOffset);
	}
}
