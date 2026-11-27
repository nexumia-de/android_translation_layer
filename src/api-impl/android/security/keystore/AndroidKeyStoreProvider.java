package android.security.keystore;

import java.security.Provider;

/* The "AndroidKeyStore" provider. It comes first (Context), so that its operations win for
 * keystore keys; SupportedKeyClasses leaves every other key to the other providers. */
public final class AndroidKeyStoreProvider {
	private static final String PACKAGE = "android.security.keystore.";
	private static final String SECRET_KEY = PACKAGE + "AndroidKeyStoreSecretKey";
	private static final String PRIVATE_KEY = PACKAGE + "AndroidKeyStorePrivateKey";

	private AndroidKeyStoreProvider() {}

	private static void put(Provider provider, String type, String algorithm, String className, String keyClasses) {
		provider.put(type + "." + algorithm, className);
		if (keyClasses != null)
			provider.put(type + "." + algorithm + " SupportedKeyClasses", keyClasses);
	}

	private static void rsaCiphers(Provider provider, String keyClasses) {
		for (String padding : AndroidKeyStoreRSACipher.PADDINGS)
			put(provider, "Cipher", "RSA/ECB/" + padding, PACKAGE + "AndroidKeyStoreRSACipher$" + padding.replace("-", ""), keyClasses);
	}

	public static Provider create() {
		Provider provider = new Provider("AndroidKeyStore", 1.0, "Android KeyStore provider") {};
		provider.put("KeyStore.AndroidKeyStore", PACKAGE + "AndroidKeyStore");

		for (String algorithm : KeyGenerator.ALGORITHMS)
			put(provider, "KeyGenerator", algorithm, PACKAGE + "KeyGenerator$" + algorithm, null);
		put(provider, "KeyPairGenerator", "RSA", PACKAGE + "AndroidKeyStoreKeyPairGenerator$RSA", null);
		put(provider, "KeyPairGenerator", "EC", PACKAGE + "AndroidKeyStoreKeyPairGenerator$EC", null);

		String cipher = PACKAGE + "AndroidKeyStoreCipher$";
		put(provider, "Cipher", "AES/GCM/NoPadding", cipher + "AesGcmNoPadding", SECRET_KEY);
		put(provider, "Cipher", "AES/CBC/NoPadding", cipher + "AesCbcNoPadding", SECRET_KEY);
		put(provider, "Cipher", "AES/CBC/PKCS7Padding", cipher + "AesCbcPkcs7Padding", SECRET_KEY);
		put(provider, "Cipher", "AES/CTR/NoPadding", cipher + "AesCtrNoPadding", SECRET_KEY);
		put(provider, "Cipher", "AES/ECB/NoPadding", cipher + "AesEcbNoPadding", SECRET_KEY);
		put(provider, "Cipher", "AES/ECB/PKCS7Padding", cipher + "AesEcbPkcs7Padding", SECRET_KEY);
		rsaCiphers(provider, PRIVATE_KEY);

		for (String algorithm : AndroidKeyStoreSignature.ALGORITHMS)
			put(provider, "Signature", algorithm, AndroidKeyStoreSignature.className(algorithm), PRIVATE_KEY);
		provider.put("Alg.Alias.Signature.ECDSA", "SHA1withECDSA");
		for (String algorithm : AndroidKeyStoreMac.ALGORITHMS)
			put(provider, "Mac", algorithm, PACKAGE + "AndroidKeyStoreMac$" + algorithm, SECRET_KEY);
		put(provider, "KeyAgreement", "ECDH", PACKAGE + "AndroidKeyStoreKeyAgreement", PRIVATE_KEY);

		put(provider, "KeyFactory", "EC", PACKAGE + "AndroidKeyStoreKeyFactory$EC", null);
		put(provider, "KeyFactory", "RSA", PACKAGE + "AndroidKeyStoreKeyFactory$RSA", null);
		for (String algorithm : AndroidKeyStoreSecretKeyFactory.ALGORITHMS)
			put(provider, "SecretKeyFactory", algorithm, PACKAGE + "AndroidKeyStoreSecretKeyFactory$" + algorithm, null);
		return provider;
	}

	/* the name apps ask for on Android for RSA with keystore keys; also takes public keys */
	public static Provider createBCWorkaround() {
		Provider provider = new Provider("AndroidKeyStoreBCWorkaround", 1.0, "RSA for Android KeyStore keys") {};
		rsaCiphers(provider, null);
		return provider;
	}
}
