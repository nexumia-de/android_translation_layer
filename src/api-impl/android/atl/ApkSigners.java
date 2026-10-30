package android.atl;

import android.content.pm.Signature;
import android.util.Slog;
import android.util.apk.ApkSignatureSchemeV2Verifier;
import android.util.apk.ApkSignatureSchemeV3Verifier;
import android.util.apk.SignatureNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * The signers of an APK, verified over the whole file like Android's package manager does before
 * an install: APK Signature Scheme v3, else v2, else the v1 JAR signature of every entry.
 * PackageParser.collectCertificates only reads the v1 signature of AndroidManifest.xml, which
 * neither covers the code nor finds v2/v3-only signatures.
 */
public class ApkSigners {
	private static final String TAG = "ApkSigners";

	/* the signers of the APK */
	public final Signature[] current;
	/* v3 key rotation: every signer the app had, oldest first, the current one last; empty without */
	public final Signature[] lineage;
	public final String scheme;

	private ApkSigners(Signature[] current, Signature[] lineage, String scheme) {
		this.current = current;
		this.lineage = lineage;
		this.scheme = scheme;
	}

	/**
	 * Returns the verified signers, or null if the APK is unsigned, or a signature or digest does
	 * not match (a changed file).
	 */
	public static ApkSigners verify(String apkPath) {
		try {
			ApkSignatureSchemeV3Verifier.VerifiedSigner v3 = ApkSignatureSchemeV3Verifier.verify(apkPath);
			Signature[] lineage = new Signature[0];
			if (v3.por != null)
				lineage = toSignatures(v3.por.certs.toArray(new X509Certificate[0]));
			return new ApkSigners(toSignatures(v3.certs), lineage, "v3");
		} catch (SignatureNotFoundException e) {
			// not signed with v3
		} catch (SecurityException | IOException | CertificateEncodingException e) {
			Slog.w(TAG, apkPath + ": APK Signature Scheme v3 does not verify", e);
			return null;
		}
		try {
			X509Certificate[][] v2 = ApkSignatureSchemeV2Verifier.verify(apkPath);
			List<X509Certificate> signers = new ArrayList<>();
			for (X509Certificate[] chain : v2)
				signers.add(chain[0]);
			return new ApkSigners(toSignatures(signers.toArray(new X509Certificate[0])), new Signature[0], "v2");
		} catch (SignatureNotFoundException e) {
			// not signed with v2
		} catch (SecurityException | IOException | CertificateEncodingException e) {
			Slog.w(TAG, apkPath + ": APK Signature Scheme v2 does not verify", e);
			return null;
		}
		try {
			Signature[] v1 = verifyJar(apkPath);
			if (v1 == null)
				Slog.w(TAG, apkPath + ": not signed");
			return v1 == null ? null : new ApkSigners(v1, new Signature[0], "v1");
		} catch (SecurityException | IOException | CertificateEncodingException e) {
			Slog.w(TAG, apkPath + ": JAR signature does not verify", e);
			return null;
		}
	}

	/**
	 * Whether an APK signed by this may replace one signed by installed: the same signers, or,
	 * with v3 key rotation, the installed signer is one this APK's lineage rotated away from.
	 */
	public boolean canUpdate(ApkSigners installed) {
		HashSet<Signature> now = new HashSet<>(Arrays.asList(current));
		if (now.equals(new HashSet<>(Arrays.asList(installed.current))))
			return true;
		return installed.current.length == 1 && Arrays.asList(lineage).contains(installed.current[0]);
	}

	/* every entry outside META-INF/ must be signed, and by the same signers; reading an entry
	 * checks its digest against the signed manifest */
	private static Signature[] verifyJar(String apkPath) throws IOException, CertificateEncodingException {
		try (JarFile jar = new JarFile(apkPath, true)) {
			Certificate[] signers = null;
			byte[] buffer = new byte[8192];
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (entry.isDirectory() || entry.getName().startsWith("META-INF/"))
					continue;
				try (InputStream in = jar.getInputStream(entry)) {
					while (in.read(buffer) != -1)
						;
				}
				Certificate[] certs = entry.getCertificates();
				if (certs == null || certs.length == 0) {
					if (signers != null)
						throw new SecurityException("unsigned entry " + entry.getName());
					return null; // the first entry is unsigned: an unsigned APK
				}
				if (signers == null)
					signers = certs;
				else if (!new HashSet<>(Arrays.asList(signers)).equals(new HashSet<>(Arrays.asList(certs))))
					throw new SecurityException("entry " + entry.getName() + " has other signers");
			}
			if (signers == null)
				return null;
			Signature[] result = new Signature[signers.length];
			for (int i = 0; i < signers.length; i++)
				result[i] = new Signature(signers[i].getEncoded());
			return result;
		}
	}

	private static Signature[] toSignatures(X509Certificate[] certs) throws CertificateEncodingException {
		Signature[] result = new Signature[certs.length];
		for (int i = 0; i < certs.length; i++)
			result[i] = new Signature(certs[i].getEncoded());
		return result;
	}
}
