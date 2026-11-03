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
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
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

public class AndroidKeyStore extends KeyStoreSpi {

	private final static String TAG = "AndroidKeyStore";

	private static native void nativeSetKey(String package_name, String alias, byte[] encoded);
	private static native byte[] nativeGetKey(String package_name, String alias);
	private static native void nativeDeleteKey(String package_name, String alias);

	private static String packageName() {
		return ATLLoadedApp.getPrimaryApplication().getApplication().getPackageName();
	}

	/* An entry is the algorithm name and the key: the encoded secret key, or
	 * for "RSA" the PKCS#8 private key (with its length) and the X.509
	 * public key. */
	private static byte[][] readEntry(String alias) {
		byte[] data = nativeGetKey(packageName(), alias);
		if (data == null)
			return null;
		ByteBuffer buffer = ByteBuffer.wrap(data);
		byte[] algorithmBytes = new byte[buffer.getInt()];
		buffer.get(algorithmBytes);
		byte[] keyBytes = new byte[buffer.remaining()];
		buffer.get(keyBytes);
		return new byte[][] {algorithmBytes, keyBytes};
	}

	static void setKeyPair(String alias, KeyPair pair) {
		byte[] algorithm = "RSA".getBytes(StandardCharsets.UTF_8);
		byte[] privateKey = pair.getPrivate().getEncoded();
		byte[] publicKey = pair.getPublic().getEncoded();
		ByteBuffer buffer = ByteBuffer.allocate(4 + algorithm.length + 4 + privateKey.length + publicKey.length);
		buffer.putInt(algorithm.length);
		buffer.put(algorithm);
		buffer.putInt(privateKey.length);
		buffer.put(privateKey);
		buffer.put(publicKey);
		nativeSetKey(packageName(), alias, buffer.array());
	}

	/* the private and the public key of an RSA entry, or null */
	private static Key[] getKeyPair(String alias) throws NoSuchAlgorithmException, UnrecoverableKeyException {
		byte[][] entry = readEntry(alias);
		if (entry == null || !"RSA".equals(new String(entry[0], StandardCharsets.UTF_8)))
			return null;
		ByteBuffer buffer = ByteBuffer.wrap(entry[1]);
		byte[] privateKey = new byte[buffer.getInt()];
		buffer.get(privateKey);
		byte[] publicKey = new byte[buffer.remaining()];
		buffer.get(publicKey);
		try {
			KeyFactory factory = KeyFactory.getInstance("RSA");
			return new Key[] {factory.generatePrivate(new PKCS8EncodedKeySpec(privateKey)),
			                  factory.generatePublic(new X509EncodedKeySpec(publicKey))};
		} catch (InvalidKeySpecException e) {
			throw new UnrecoverableKeyException(e.toString());
		}
	}

	@Override
	public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
		Slog.i(TAG, "engineGetKey alias=" + alias + " password=" + Arrays.toString(password));

		Key[] pair = getKeyPair(alias);
		if (pair != null)
			return pair[0];
		byte[][] entry = readEntry(alias);
		if (entry == null)
			return null;
		byte[] algorithmBytes = entry[0];
		byte[] keyBytes = entry[1];
		return new AndroidKeyStoreSecretKey(keyBytes, new String(algorithmBytes, StandardCharsets.UTF_8));
	}

	@Override
	public Certificate[] engineGetCertificateChain(String alias) {
		Certificate certificate = engineGetCertificate(alias);
		return certificate != null ? new Certificate[] {certificate} : new Certificate[0];
	}

	@Override
	public Certificate engineGetCertificate(String alias) {
		try {
			Key[] pair = getKeyPair(alias);
			return pair != null ? new AndroidKeyStoreCertificate((PublicKey)pair[1]) : null;
		} catch (GeneralSecurityException e) {
			Slog.w(TAG, "engineGetCertificate(" + alias + "): " + e);
			return null;
		}
	}

	@Override
	public Date engineGetCreationDate(String alias) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'engineGetCreationDate'");
	}

	@Override
	public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) {
		byte[] algorithm = key.getAlgorithm().getBytes(StandardCharsets.UTF_8);
		byte[] encoded = key.getEncoded();
		ByteBuffer buffer = ByteBuffer.allocate(4 + algorithm.length + encoded.length);
		buffer.putInt(algorithm.length);
		buffer.put(algorithm);
		buffer.put(encoded);
		nativeSetKey(ATLLoadedApp.getPrimaryApplication().getApplication().getPackageName(), alias, buffer.array());
	}

	@Override
	public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'engineSetKeyEntry'");
	}

	@Override
	public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'engineSetCertificateEntry'");
	}

	@Override
	public void engineDeleteEntry(String alias) throws KeyStoreException {
		nativeDeleteKey(packageName(), alias);
	}

	@Override
	public Enumeration<String> engineAliases() {
		Slog.i(TAG, "engineAliases() called");
		return Collections.emptyEnumeration();
	}

	@Override
	public boolean engineContainsAlias(String alias) {
		Slog.i(TAG, "engineContainsAlias(" + alias + ") called");
		return engineIsKeyEntry(alias);
	}

	@Override
	public int engineSize() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'engineSize'");
	}

	@Override
	public boolean engineIsKeyEntry(String alias) {
		byte[] data = nativeGetKey(ATLLoadedApp.getPrimaryApplication().getApplication().getPackageName(), alias);
		return data != null;
	}

	@Override
	public boolean engineIsCertificateEntry(String alias) {
		return false;
	}

	@Override
	public String engineGetCertificateAlias(Certificate cert) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'engineGetCertificateAlias'");
	}

	@Override
	public void engineStore(OutputStream stream, char[] password)
	    throws IOException, NoSuchAlgorithmException, CertificateException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'engineStore'");
	}

	@Override
	public void engineLoad(InputStream stream, char[] password)
	    throws IOException, NoSuchAlgorithmException, CertificateException {
	}
}
