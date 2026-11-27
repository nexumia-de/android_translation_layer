package android.security.keystore;

import android.atl.ATLLoadedApp;
import android.util.Slog;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import javax.crypto.SecretKey;

/* The app's keys, kept in the Secret Service through libsecret: inside the app's own Flatpak that
 * is a keyring file in the app's data, encrypted with the app's own secret from the Secret portal
 * (docs/keystore-auth.md). Keys come back as AndroidKeyStoreSecretKey / AndroidKeyStorePrivateKey,
 * whose material only the keystore's own operations use, after checking the KeyAuthorizations
 * stored with the key. */
public class AndroidKeyStore extends KeyStoreSpi {

	private final static String TAG = "AndroidKeyStore";

	/* "ATLK": an entry with KeyAuthorizations; entries without it are from before (legacy) */
	private static final int MAGIC = 0x41544c4b;

	private static native void nativeSetKey(String package_name, String alias, byte[] encoded);
	private static native byte[] nativeGetKey(String package_name, String alias);
	private static native void nativeDeleteKey(String package_name, String alias);
	private static native String[] nativeAliases(String package_name);

	private static String packageName() {
		return ATLLoadedApp.getPrimaryApplication().getApplication().getPackageName();
	}

	private static final class Entry {
		KeyAuthorizations auth;
		String algorithm;
		byte[] key; // the encoded secret key, or for "RSA"/"EC" the private key's length, PKCS#8 and X.509
	}

	/* [MAGIC, length, authorizations] algorithm length, algorithm, key */
	private static Entry readEntry(String alias) {
		byte[] data = nativeGetKey(packageName(), alias);
		if (data == null)
			return null;
		Entry entry = new Entry();
		ByteBuffer buffer = ByteBuffer.wrap(data);
		int first = buffer.getInt();
		if (first == MAGIC) {
			byte[] auth = new byte[buffer.getInt()];
			buffer.get(auth);
			try {
				entry.auth = KeyAuthorizations.fromBytes(auth);
			} catch (IOException e) {
				Slog.w(TAG, "entry " + alias + ": " + e);
				return null;
			}
			first = buffer.getInt();
		} else {
			entry.auth = KeyAuthorizations.legacy();
		}
		byte[] algorithm = new byte[first];
		buffer.get(algorithm);
		entry.algorithm = new String(algorithm, StandardCharsets.UTF_8);
		entry.key = new byte[buffer.remaining()];
		buffer.get(entry.key);
		return entry;
	}

	private static void writeEntry(String alias, String keyAlgorithm, byte[] key, KeyAuthorizations auth) {
		byte[] algorithm = keyAlgorithm.getBytes(StandardCharsets.UTF_8);
		byte[] authBytes = auth.toBytes();
		ByteBuffer buffer = ByteBuffer.allocate(8 + authBytes.length + 4 + algorithm.length + key.length);
		buffer.putInt(MAGIC);
		buffer.putInt(authBytes.length);
		buffer.put(authBytes);
		buffer.putInt(algorithm.length);
		buffer.put(algorithm);
		buffer.put(key);
		nativeSetKey(packageName(), alias, buffer.array());
	}

	private static boolean isPair(String algorithm) {
		return "RSA".equals(algorithm) || "EC".equals(algorithm);
	}

	static void setKeyPair(String alias, String keyAlgorithm, PrivateKey privateKey, PublicKey publicKey, KeyAuthorizations auth) {
		byte[] priv = privateKey.getEncoded();
		byte[] pub = publicKey.getEncoded();
		ByteBuffer buffer = ByteBuffer.allocate(4 + priv.length + pub.length);
		buffer.putInt(priv.length);
		buffer.put(priv);
		buffer.put(pub);
		writeEntry(alias, keyAlgorithm, buffer.array(), auth);
	}

	static void setSecretKey(String alias, byte[] material, String algorithm, KeyAuthorizations auth) {
		writeEntry(alias, algorithm, material, auth);
	}

	/* the plain private and public key of an RSA or EC entry */
	private static Key[] keyPair(Entry entry) throws NoSuchAlgorithmException, UnrecoverableKeyException {
		ByteBuffer buffer = ByteBuffer.wrap(entry.key);
		byte[] privateKey = new byte[buffer.getInt()];
		buffer.get(privateKey);
		byte[] publicKey = new byte[buffer.remaining()];
		buffer.get(publicKey);
		try {
			KeyFactory factory = Software.keyFactory(entry.algorithm);
			return new Key[] {factory.generatePrivate(new PKCS8EncodedKeySpec(privateKey)),
			                  factory.generatePublic(new X509EncodedKeySpec(publicKey))};
		} catch (InvalidKeySpecException e) {
			throw new UnrecoverableKeyException(e.toString());
		}
	}

	@Override
	public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
		Entry entry = readEntry(alias);
		if (entry == null)
			return null;
		if (isPair(entry.algorithm))
			return AndroidKeyStorePrivateKey.wrap(alias, (PrivateKey)keyPair(entry)[0], entry.auth);
		return new AndroidKeyStoreSecretKey(alias, entry.key, entry.algorithm, entry.auth);
	}

	@Override
	public Certificate[] engineGetCertificateChain(String alias) {
		Certificate certificate = engineGetCertificate(alias);
		return certificate != null ? new Certificate[] {certificate} : null;
	}

	/* a certificate holding the public key; never an attestation, the keys are software keys */
	@Override
	public Certificate engineGetCertificate(String alias) {
		try {
			Entry entry = readEntry(alias);
			if (entry == null || !isPair(entry.algorithm))
				return null;
			return new AndroidKeyStoreCertificate((PublicKey)keyPair(entry)[1]);
		} catch (GeneralSecurityException e) {
			Slog.w(TAG, "engineGetCertificate(" + alias + "): " + e);
			return null;
		}
	}

	@Override
	public Date engineGetCreationDate(String alias) {
		Entry entry = readEntry(alias);
		return entry != null ? entry.auth.creationDate() : null;
	}

	@Override
	public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
		setKeyEntry(alias, key, chain, null);
	}

	/* importing a key: with KeyProtection's authorizations, else none (as stored before) */
	private void setKeyEntry(String alias, Key key, Certificate[] chain, KeyProtection protection) throws KeyStoreException {
		if (protection != null && protection.isStrongBoxBacked())
			throw new KeyStoreException("Failed to import key", new StrongBoxUnavailableException("No StrongBox available"));
		KeyAuthorizations auth = protection != null ? KeyAuthorizations.of(protection) : null;
		if (auth != null) {
			try {
				auth.checkCreate();
			} catch (IllegalStateException e) {
				throw new KeyStoreException(e.getMessage(), e);
			}
		}
		if (key instanceof AndroidKeyStoreSecretKey) {
			AndroidKeyStoreSecretKey k = (AndroidKeyStoreSecretKey)key;
			setSecretKey(alias, k.plain().getEncoded(), k.getAlgorithm(), auth != null ? auth : k.auth);
		} else if (key instanceof AndroidKeyStorePrivateKey) {
			AndroidKeyStorePrivateKey k = (AndroidKeyStorePrivateKey)key;
			if (chain == null || chain.length == 0)
				throw new KeyStoreException("a private key needs its certificate chain");
			setKeyPair(alias, k.getAlgorithm(), k.plain(), chain[0].getPublicKey(), auth != null ? auth : k.auth);
		} else if (key instanceof PrivateKey) {
			if (chain == null || chain.length == 0)
				throw new KeyStoreException("a private key needs its certificate chain");
			if (key.getEncoded() == null)
				throw new KeyStoreException("the private key cannot be encoded");
			setKeyPair(alias, key.getAlgorithm(), (PrivateKey)key, chain[0].getPublicKey(),
			           auth != null ? auth : KeyAuthorizations.legacy());
		} else if (key instanceof SecretKey) {
			byte[] encoded = key.getEncoded();
			if (encoded == null)
				throw new KeyStoreException("the secret key cannot be encoded");
			setSecretKey(alias, encoded, key.getAlgorithm(), auth != null ? auth : KeyAuthorizations.legacy());
		} else {
			throw new KeyStoreException("unsupported key " + (key != null ? key.getClass().getName() : "null"));
		}
	}

	@Override
	public void engineSetEntry(String alias, KeyStore.Entry entry, KeyStore.ProtectionParameter param) throws KeyStoreException {
		if (param != null && !(param instanceof KeyProtection))
			throw new KeyStoreException("Unsupported protection parameter " + param.getClass().getName() + ", use KeyProtection");
		KeyProtection protection = (KeyProtection)param;
		if (entry instanceof KeyStore.PrivateKeyEntry) {
			KeyStore.PrivateKeyEntry e = (KeyStore.PrivateKeyEntry)entry;
			setKeyEntry(alias, e.getPrivateKey(), e.getCertificateChain(), protection);
		} else if (entry instanceof KeyStore.SecretKeyEntry) {
			setKeyEntry(alias, ((KeyStore.SecretKeyEntry)entry).getSecretKey(), null, protection);
		} else {
			throw new KeyStoreException("Unsupported entry " + (entry != null ? entry.getClass().getName() : "null"));
		}
	}

	@Override
	public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
		throw new KeyStoreException("Operation not supported because key encoding is unknown");
	}

	@Override
	public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
		throw new KeyStoreException("certificate entries are not supported");
	}

	@Override
	public void engineDeleteEntry(String alias) throws KeyStoreException {
		nativeDeleteKey(packageName(), alias);
	}

	@Override
	public Enumeration<String> engineAliases() {
		String[] aliases = nativeAliases(packageName());
		return Collections.enumeration(aliases != null ? Arrays.asList(aliases) : Collections.<String>emptyList());
	}

	@Override
	public boolean engineContainsAlias(String alias) {
		return engineIsKeyEntry(alias);
	}

	@Override
	public int engineSize() {
		String[] aliases = nativeAliases(packageName());
		return aliases != null ? aliases.length : 0;
	}

	@Override
	public boolean engineIsKeyEntry(String alias) {
		return nativeGetKey(packageName(), alias) != null;
	}

	@Override
	public boolean engineIsCertificateEntry(String alias) {
		return false;
	}

	@Override
	public String engineGetCertificateAlias(Certificate cert) {
		if (cert == null)
			return null;
		for (Enumeration<String> e = engineAliases(); e.hasMoreElements();) {
			String alias = e.nextElement();
			Certificate own = engineGetCertificate(alias);
			if (own != null && Arrays.equals(own.getPublicKey().getEncoded(), cert.getPublicKey().getEncoded()))
				return alias;
		}
		return null;
	}

	@Override
	public void engineStore(OutputStream stream, char[] password)
	    throws IOException, NoSuchAlgorithmException, CertificateException {
		throw new UnsupportedOperationException("Can not serialize AndroidKeyStore to OutputStream");
	}

	@Override
	public void engineLoad(InputStream stream, char[] password)
	    throws IOException, NoSuchAlgorithmException, CertificateException {
		if (stream != null)
			throw new IllegalArgumentException("InputStream not supported");
	}
}
