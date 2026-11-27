package android.security.keystore;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;

/* The software implementations behind the keystore's operations: Bouncy Castle as before, else
 * the first other provider; never a keystore provider or AndroidOpenSSL (which hands out the
 * others' services), which would end up here again. */
final class Software {
	private Software() {}

	private static boolean usable(Provider provider) {
		String name = provider.getName();
		return !name.startsWith("AndroidKeyStore") && !name.equals("AndroidOpenSSL");
	}

	private interface Factory<T> {
		T get(Provider provider) throws GeneralSecurityException;
	}

	private static <T> T first(String what, Factory<T> factory) throws NoSuchAlgorithmException {
		Provider bc = Security.getProvider("BC");
		if (bc != null) {
			try {
				return factory.get(bc);
			} catch (GeneralSecurityException e) {
			}
		}
		for (Provider provider : Security.getProviders()) {
			if (provider == bc || !usable(provider))
				continue;
			try {
				return factory.get(provider);
			} catch (GeneralSecurityException e) {
			}
		}
		throw new NoSuchAlgorithmException("no software implementation of " + what);
	}

	static Cipher cipher(final String transformation) throws NoSuchAlgorithmException {
		return first(transformation, new Factory<Cipher>() {
			public Cipher get(Provider p) throws GeneralSecurityException { return Cipher.getInstance(transformation, p); }
		});
	}

	static Signature signature(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<Signature>() {
			public Signature get(Provider p) throws GeneralSecurityException { return Signature.getInstance(algorithm, p); }
		});
	}

	static Mac mac(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<Mac>() {
			public Mac get(Provider p) throws GeneralSecurityException { return Mac.getInstance(algorithm, p); }
		});
	}

	static KeyAgreement keyAgreement(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<KeyAgreement>() {
			public KeyAgreement get(Provider p) throws GeneralSecurityException { return KeyAgreement.getInstance(algorithm, p); }
		});
	}

	static KeyFactory keyFactory(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<KeyFactory>() {
			public KeyFactory get(Provider p) throws GeneralSecurityException { return KeyFactory.getInstance(algorithm, p); }
		});
	}

	static SecretKeyFactory secretKeyFactory(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<SecretKeyFactory>() {
			public SecretKeyFactory get(Provider p) throws GeneralSecurityException { return SecretKeyFactory.getInstance(algorithm, p); }
		});
	}

	static java.security.KeyPairGenerator keyPairGenerator(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<java.security.KeyPairGenerator>() {
			public java.security.KeyPairGenerator get(Provider p) throws GeneralSecurityException { return java.security.KeyPairGenerator.getInstance(algorithm, p); }
		});
	}

	static javax.crypto.KeyGenerator keyGenerator(final String algorithm) throws NoSuchAlgorithmException {
		return first(algorithm, new Factory<javax.crypto.KeyGenerator>() {
			public javax.crypto.KeyGenerator get(Provider p) throws GeneralSecurityException { return javax.crypto.KeyGenerator.getInstance(algorithm, p); }
		});
	}
}
