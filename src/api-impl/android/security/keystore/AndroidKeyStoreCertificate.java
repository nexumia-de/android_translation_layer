package android.security.keystore;

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;

/* Holds the public key of an RSA key pair. Android would make a self-signed
 * X.509 certificate; apps use it to get the public key. getEncoded returns
 * the key's X.509 SubjectPublicKeyInfo, not a certificate. */
public class AndroidKeyStoreCertificate extends Certificate {

	private final PublicKey publicKey;

	public AndroidKeyStoreCertificate(PublicKey publicKey) {
		super("X.509");
		this.publicKey = publicKey;
	}

	@Override
	public byte[] getEncoded() throws CertificateEncodingException {
		return publicKey.getEncoded();
	}

	@Override
	public void verify(PublicKey key) throws CertificateException {
		throw new CertificateException("keystore certificates are not signed");
	}

	@Override
	public void verify(PublicKey key, String sigProvider) throws CertificateException {
		verify(key);
	}

	@Override
	public String toString() {
		return "AndroidKeyStoreCertificate(" + publicKey.getAlgorithm() + ")";
	}

	@Override
	public PublicKey getPublicKey() {
		return publicKey;
	}
}
