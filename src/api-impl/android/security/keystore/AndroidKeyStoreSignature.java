package android.security.keystore;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.InvalidParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.ProviderException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.SignatureSpi;
import java.security.spec.AlgorithmParameterSpec;

/* Signatures with keystore private keys, in software, after the key's purposes and user
 * authentication are checked. Verifying with a public key needs nothing. */
public abstract class AndroidKeyStoreSignature extends SignatureSpi {
	private final Signature signature;
	private AndroidKeyStorePrivateKey key; // null while verifying

	protected AndroidKeyStoreSignature(String algorithm) {
		try {
			signature = Software.signature(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new ProviderException(e);
		}
	}

	public static class NONEwithECDSA extends AndroidKeyStoreSignature { public NONEwithECDSA() { super("NONEwithECDSA"); } }
	public static class SHA1withECDSA extends AndroidKeyStoreSignature { public SHA1withECDSA() { super("SHA1withECDSA"); } }
	public static class SHA224withECDSA extends AndroidKeyStoreSignature { public SHA224withECDSA() { super("SHA224withECDSA"); } }
	public static class SHA256withECDSA extends AndroidKeyStoreSignature { public SHA256withECDSA() { super("SHA256withECDSA"); } }
	public static class SHA384withECDSA extends AndroidKeyStoreSignature { public SHA384withECDSA() { super("SHA384withECDSA"); } }
	public static class SHA512withECDSA extends AndroidKeyStoreSignature { public SHA512withECDSA() { super("SHA512withECDSA"); } }
	public static class NONEwithRSA extends AndroidKeyStoreSignature { public NONEwithRSA() { super("NONEwithRSA"); } }
	public static class MD5withRSA extends AndroidKeyStoreSignature { public MD5withRSA() { super("MD5withRSA"); } }
	public static class SHA1withRSA extends AndroidKeyStoreSignature { public SHA1withRSA() { super("SHA1withRSA"); } }
	public static class SHA224withRSA extends AndroidKeyStoreSignature { public SHA224withRSA() { super("SHA224withRSA"); } }
	public static class SHA256withRSA extends AndroidKeyStoreSignature { public SHA256withRSA() { super("SHA256withRSA"); } }
	public static class SHA384withRSA extends AndroidKeyStoreSignature { public SHA384withRSA() { super("SHA384withRSA"); } }
	public static class SHA512withRSA extends AndroidKeyStoreSignature { public SHA512withRSA() { super("SHA512withRSA"); } }
	public static class SHA1withRSAPSS extends AndroidKeyStoreSignature { public SHA1withRSAPSS() { super("SHA1withRSA/PSS"); } }
	public static class SHA224withRSAPSS extends AndroidKeyStoreSignature { public SHA224withRSAPSS() { super("SHA224withRSA/PSS"); } }
	public static class SHA256withRSAPSS extends AndroidKeyStoreSignature { public SHA256withRSAPSS() { super("SHA256withRSA/PSS"); } }
	public static class SHA384withRSAPSS extends AndroidKeyStoreSignature { public SHA384withRSAPSS() { super("SHA384withRSA/PSS"); } }
	public static class SHA512withRSAPSS extends AndroidKeyStoreSignature { public SHA512withRSAPSS() { super("SHA512withRSA/PSS"); } }

	/* the algorithm names, for the provider (Context) */
	static final String[] ALGORITHMS = {
		"NONEwithECDSA", "SHA1withECDSA", "SHA224withECDSA", "SHA256withECDSA", "SHA384withECDSA", "SHA512withECDSA",
		"NONEwithRSA", "MD5withRSA", "SHA1withRSA", "SHA224withRSA", "SHA256withRSA", "SHA384withRSA", "SHA512withRSA",
		"SHA1withRSA/PSS", "SHA224withRSA/PSS", "SHA256withRSA/PSS", "SHA384withRSA/PSS", "SHA512withRSA/PSS",
	};

	static String className(String algorithm) {
		return AndroidKeyStoreSignature.class.getName() + "$" + algorithm.replace("/", "");
	}

	@Override
	protected void engineInitSign(PrivateKey privateKey) throws InvalidKeyException {
		engineInitSign(privateKey, null);
	}

	@Override
	protected void engineInitSign(PrivateKey privateKey, SecureRandom random) throws InvalidKeyException {
		if (!(privateKey instanceof AndroidKeyStorePrivateKey))
			throw new InvalidKeyException("Unsupported key: " + (privateKey != null ? privateKey.getClass().getName() : "null"));
		AndroidKeyStorePrivateKey k = (AndroidKeyStorePrivateKey)privateKey;
		k.auth.checkInit(KeyProperties.PURPOSE_SIGN, this);
		key = k;
		if (random != null)
			signature.initSign(k.plain(), random);
		else
			signature.initSign(k.plain());
	}

	@Override
	protected void engineInitVerify(PublicKey publicKey) throws InvalidKeyException {
		key = null;
		signature.initVerify(publicKey);
	}

	private void checkAllowed() throws SignatureException {
		if (key != null && !key.auth.operationAllowed(this))
			throw new SignatureException("Key user not authenticated");
	}

	@Override
	protected void engineUpdate(byte b) throws SignatureException {
		checkAllowed();
		signature.update(b);
	}

	@Override
	protected void engineUpdate(byte[] b, int off, int len) throws SignatureException {
		checkAllowed();
		signature.update(b, off, len);
	}

	@Override
	protected byte[] engineSign() throws SignatureException {
		checkAllowed();
		return signature.sign();
	}

	@Override
	protected boolean engineVerify(byte[] sigBytes) throws SignatureException {
		return signature.verify(sigBytes);
	}

	@Override
	protected void engineSetParameter(AlgorithmParameterSpec params) throws InvalidAlgorithmParameterException {
		signature.setParameter(params);
	}

	@Override
	@Deprecated
	protected void engineSetParameter(String param, Object value) throws InvalidParameterException {
		throw new InvalidParameterException();
	}

	@Override
	@Deprecated
	protected Object engineGetParameter(String param) throws InvalidParameterException {
		return null;
	}
}
