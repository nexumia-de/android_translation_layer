package android.security.keystore;

import java.math.BigInteger;
import java.security.PrivateKey;
import java.security.interfaces.ECKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.ECParameterSpec;

/* The private key of an RSA or EC key pair of the AndroidKeyStore. As on Android getEncoded() is
 * null and the key is only an ECKey or RSAKey (no private parts), so only the keystore's own
 * Signature, Cipher and KeyAgreement take it, after checking its KeyAuthorizations. */
public class AndroidKeyStorePrivateKey implements PrivateKey {
	private final String alias;
	private final PrivateKey plain;
	final KeyAuthorizations auth;

	AndroidKeyStorePrivateKey(String alias, PrivateKey plain, KeyAuthorizations auth) {
		this.alias = alias;
		this.plain = plain;
		this.auth = auth;
	}

	static AndroidKeyStorePrivateKey wrap(String alias, PrivateKey plain, KeyAuthorizations auth) {
		if (plain instanceof ECPrivateKey)
			return new EC(alias, (ECPrivateKey)plain, auth);
		if (plain instanceof RSAPrivateKey)
			return new RSA(alias, (RSAPrivateKey)plain, auth);
		return new AndroidKeyStorePrivateKey(alias, plain, auth);
	}

	String alias() {
		return alias;
	}

	/* for the keystore's own operations only */
	PrivateKey plain() {
		return plain;
	}

	int sizeBits() {
		if (plain instanceof ECPrivateKey)
			return ((ECPrivateKey)plain).getParams().getCurve().getField().getFieldSize();
		if (plain instanceof RSAPrivateKey)
			return ((RSAPrivateKey)plain).getModulus().bitLength();
		return -1;
	}

	@Override
	public String getAlgorithm() {
		return plain.getAlgorithm();
	}

	@Override
	public String getFormat() {
		return null;
	}

	@Override
	public byte[] getEncoded() {
		return null;
	}

	public static class EC extends AndroidKeyStorePrivateKey implements ECKey {
		private final ECParameterSpec params;

		EC(String alias, ECPrivateKey plain, KeyAuthorizations auth) {
			super(alias, plain, auth);
			params = plain.getParams();
		}

		@Override
		public ECParameterSpec getParams() {
			return params;
		}
	}

	public static class RSA extends AndroidKeyStorePrivateKey implements RSAKey {
		private final BigInteger modulus;

		RSA(String alias, RSAPrivateKey plain, KeyAuthorizations auth) {
			super(alias, plain, auth);
			modulus = plain.getModulus();
		}

		@Override
		public BigInteger getModulus() {
			return modulus;
		}
	}
}
