import android.atl.ATLAuth;
import android.os.SystemClock;
import android.security.keystore.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/* Checks ATL's AndroidKeyStore on a desktop JVM (OpenJDK 17, its own providers instead of Bouncy
 * Castle): the real classes, with libsecret replaced by a map and the permission service by
 * settable answers (tools/keystore-test.sh). Run it after changing src/api-impl/android/security/
 * keystore or android/atl/ATLAuth.java. */
public class KeystoreTest {
	static int failures = 0, passes = 0;
	static void check(boolean ok, String what) {
		if (ok) { passes++; System.out.println("ok   " + what); } else { failures++; System.out.println("FAIL " + what); }
	}
	interface Body { void run() throws Exception; }
	static void expect(Class<? extends Throwable> type, String what, Body body) {
		try { body.run(); check(false, what + " (no exception)"); }
		catch (Throwable t) {
			Throwable c = t; boolean found = false;
			while (c != null) { if (type.isInstance(c)) found = true; c = c.getCause(); }
			check(found, what + " -> " + t);
		}
	}

	public static void main(String[] args) throws Exception {
		Security.insertProviderAt(AndroidKeyStoreProvider.create(), 1);
		Security.addProvider(AndroidKeyStoreProvider.createBCWorkaround());
		KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
		ks.load(null);

		// 1 AES-GCM without authentication
		javax.crypto.KeyGenerator kg = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore");
		kg.init(new KeyGenParameterSpec.Builder("plain", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
		SecretKey key = kg.generateKey();
		check(key.getEncoded() == null, "secret key material not exportable");
		Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
		c.init(Cipher.ENCRYPT_MODE, key);
		check(c.getProvider().getName().equals("AndroidKeyStore"), "keystore cipher picked for keystore key");
		check(c.getIV().length == 12, "GCM IV is 12 bytes");
		c.doFinal("hello".getBytes());
		SecretKey loaded = (SecretKey)ks.getKey("plain", null);
		Cipher enc = Cipher.getInstance("AES/GCM/NoPadding"); enc.init(Cipher.ENCRYPT_MODE, key); byte[] iv = enc.getIV(); byte[] ct2 = enc.doFinal("hello".getBytes());
		Cipher dec = Cipher.getInstance("AES/GCM/NoPadding"); dec.init(Cipher.DECRYPT_MODE, loaded, new GCMParameterSpec(128, iv));
		check(new String(dec.doFinal(ct2)).equals("hello"), "AES-GCM round trip with reloaded key");
		expect(InvalidAlgorithmParameterException.class, "caller IV refused when randomized encryption required", () -> {
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, new byte[12]));
		});
		KeyInfo info = (KeyInfo)SecretKeyFactory.getInstance("AES", "AndroidKeyStore").getKeySpec(key, KeyInfo.class);
		check(!info.isInsideSecureHardware() && info.getSecurityLevel() == KeyProperties.SECURITY_LEVEL_SOFTWARE && info.getKeySize() == 256, "KeyInfo: software, 256 bit");
		expect(InvalidKeyException.class, "decrypt-only purpose enforced", () -> {
			javax.crypto.KeyGenerator g = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore");
			g.init(new KeyGenParameterSpec.Builder("enconly", KeyProperties.PURPOSE_ENCRYPT).build());
			SecretKey k = g.generateKey();
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, new byte[12]));
		});

		// 2 a plain software AES key still goes to the software provider
		SecretKey soft = new SecretKeySpec(new byte[16], "AES");
		Cipher sc = Cipher.getInstance("AES/GCM/NoPadding"); sc.init(Cipher.ENCRYPT_MODE, soft);
		check(!sc.getProvider().getName().startsWith("AndroidKeyStore"), "software key uses software provider (" + sc.getProvider().getName() + ")");

		// 3 legacy entry (format before this change)
		byte[] alg = "AES".getBytes(StandardCharsets.UTF_8); byte[] raw = new byte[16];
		ByteBuffer b = ByteBuffer.allocate(4 + alg.length + raw.length); b.putInt(alg.length); b.put(alg); b.put(raw);
		storeRaw("legacy", b.array());
		SecretKey legacy = (SecretKey)ks.getKey("legacy", null);
		Cipher lc = Cipher.getInstance("AES/GCM/NoPadding"); lc.init(Cipher.ENCRYPT_MODE, legacy, new GCMParameterSpec(128, new byte[12]));
		check(lc.doFinal(new byte[3]).length == 19, "legacy entry readable, no auth, caller IV allowed as before");
		check(Collections.list(ks.aliases()).containsAll(Arrays.asList("plain", "legacy", "enconly")) && ks.size() == 3, "aliases and size");
		// a legacy EC key pair: algorithm, private key length, PKCS#8, X.509
		KeyPair old = KeyPairGenerator.getInstance("EC", Software()).generateKeyPair();
		byte[] ec = "EC".getBytes(StandardCharsets.UTF_8), priv = old.getPrivate().getEncoded(), pub = old.getPublic().getEncoded();
		ByteBuffer lb = ByteBuffer.allocate(4 + ec.length + 4 + priv.length + pub.length);
		lb.putInt(ec.length); lb.put(ec); lb.putInt(priv.length); lb.put(priv); lb.put(pub);
		storeRaw("legacy-ec", lb.array());
		Signature ls = Signature.getInstance("SHA256withECDSA"); ls.initSign((PrivateKey)ks.getKey("legacy-ec", null)); ls.update(new byte[2]);
		Signature lv = Signature.getInstance("SHA256withECDSA"); lv.initVerify(old.getPublic()); lv.update(new byte[2]);
		check(lv.verify(ls.sign()) && ks.getCertificate("legacy-ec").getPublicKey().equals(old.getPublic()), "legacy EC key pair signs, certificate has its public key");
		ks.deleteEntry("legacy-ec");
		check(ks.getCreationDate("plain").getTime() > 0, "creation date");

		// 4 per-operation biometric key
		kg.init(new KeyGenParameterSpec.Builder("perop", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
			.setUserAuthenticationRequired(true).build());
		SecretKey perop = kg.generateKey();
		Cipher pc = Cipher.getInstance("AES/GCM/NoPadding"); pc.init(Cipher.ENCRYPT_MODE, perop);
		final Cipher pcf = pc;
		expect(IllegalBlockSizeException.class, "per-op key: doFinal before confirmation", () -> pcf.doFinal(new byte[1]));
		ATLAuth.confirmed(ATLAuth.AUTH_BIOMETRIC_STRONG, true);
		check(pc.doFinal(new byte[1]).length == 17, "per-op key: works after BiometricPrompt confirmation");
		pc.init(Cipher.ENCRYPT_MODE, perop);
		expect(IllegalBlockSizeException.class, "per-op key: a new operation needs a new confirmation", () -> pcf.doFinal(new byte[1]));
		Cipher pc2 = Cipher.getInstance("AES/GCM/NoPadding"); pc2.init(Cipher.ENCRYPT_MODE, perop);
		ATLAuth.confirmed(ATLAuth.AUTH_DEVICE_CREDENTIAL, true);
		expect(IllegalBlockSizeException.class, "per-op biometric key: password does not unlock it", () -> pc2.doFinal(new byte[1]));

		// 5 time-bound key: fingerprint or password within 30 s
		kg.init(new KeyGenParameterSpec.Builder("timed", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
			.setUserAuthenticationRequired(true).setUserAuthenticationParameters(30, KeyProperties.AUTH_BIOMETRIC_STRONG | KeyProperties.AUTH_DEVICE_CREDENTIAL).build());
		SecretKey timed = kg.generateKey();
		SystemClock.offset += 3600_000; // older confirmations expire
		expect(UserNotAuthenticatedException.class, "timed key: init without recent confirmation", () -> {
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.ENCRYPT_MODE, timed);
		});
		ATLAuth.confirmed(ATLAuth.AUTH_DEVICE_CREDENTIAL, false);
		Cipher tc = Cipher.getInstance("AES/GCM/NoPadding"); tc.init(Cipher.ENCRYPT_MODE, timed);
		check(tc.doFinal(new byte[1]).length == 17, "timed key: works after password confirmation");
		SystemClock.offset += 31_000;
		expect(UserNotAuthenticatedException.class, "timed key: expired after 30 s", () -> {
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.ENCRYPT_MODE, timed);
		});

		// 6 a new fingerprint invalidates biometric keys
		ATLAuth.fakeAuthenticators = "15 def"; ATLAuth.resetCache();
		expect(KeyPermanentlyInvalidatedException.class, "per-op key invalidated by new enrollment", () -> {
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.ENCRYPT_MODE, (SecretKey)ks.getKey("perop", null));
		});
		ATLAuth.confirmed(ATLAuth.AUTH_DEVICE_CREDENTIAL, false);
		Cipher still = Cipher.getInstance("AES/GCM/NoPadding"); still.init(Cipher.ENCRYPT_MODE, (SecretKey)ks.getKey("plain", null));
		check(true, "key without auth unaffected by enrollment change");

		// 7 creation rules
		ATLAuth.fakeAuthenticators = "32768 "; ATLAuth.resetCache(); // only the password, no fingerprint
		expect(IllegalStateException.class, "biometric-only key needs an enrolled fingerprint", () -> {
			kg.init(new KeyGenParameterSpec.Builder("nofp", KeyProperties.PURPOSE_ENCRYPT).setUserAuthenticationRequired(true).build()); kg.generateKey();
		});
		ATLAuth.fakeAuthenticators = "0 "; ATLAuth.resetCache();
		expect(IllegalStateException.class, "auth key needs a secure lock screen", () -> {
			kg.init(new KeyGenParameterSpec.Builder("nolock", KeyProperties.PURPOSE_ENCRYPT).setUserAuthenticationRequired(true)
				.setUserAuthenticationParameters(10, KeyProperties.AUTH_DEVICE_CREDENTIAL).build()); kg.generateKey();
		});
		ATLAuth.fakeAuthenticators = "32783 def"; ATLAuth.resetCache();
		expect(StrongBoxUnavailableException.class, "StrongBox not available", () -> {
			kg.init(new KeyGenParameterSpec.Builder("sb", KeyProperties.PURPOSE_ENCRYPT).setIsStrongBoxBacked(true).build()); kg.generateKey();
		});

		// 8 unlocked device required
		kg.init(new KeyGenParameterSpec.Builder("unlocked", KeyProperties.PURPOSE_ENCRYPT).setUnlockedDeviceRequired(true).build());
		SecretKey unlocked = kg.generateKey();
		ATLAuth.fakeLocked = "true"; ATLAuth.resetCache();
		expect(UserNotAuthenticatedException.class, "unlocked-device key refused while locked", () -> {
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.ENCRYPT_MODE, unlocked);
		});
		ATLAuth.fakeLocked = "false"; ATLAuth.resetCache();
		Cipher uc = Cipher.getInstance("AES/GCM/NoPadding"); uc.init(Cipher.ENCRYPT_MODE, unlocked);
		check(true, "unlocked-device key works while unlocked");

		// 9 EC
		KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
		kpg.initialize(new KeyGenParameterSpec.Builder("ec", KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY | KeyProperties.PURPOSE_AGREE_KEY)
			.setDigests(KeyProperties.DIGEST_SHA256).build());
		KeyPair pair = kpg.generateKeyPair();
		check(pair.getPrivate().getEncoded() == null && pair.getPrivate() instanceof java.security.interfaces.ECKey
			&& !(pair.getPrivate() instanceof java.security.interfaces.ECPrivateKey), "EC private key: not exportable, ECKey only");
		Signature s = Signature.getInstance("SHA256withECDSA");
		s.initSign((PrivateKey)ks.getKey("ec", null)); s.update("msg".getBytes()); byte[] sig = s.sign();
		check(s.getProvider().getName().equals("AndroidKeyStore"), "keystore signature picked");
		Signature v = Signature.getInstance("SHA256withECDSA");
		v.initVerify(ks.getCertificate("ec").getPublicKey()); v.update("msg".getBytes());
		check(v.verify(sig), "EC signature verifies with certificate's public key (" + v.getProvider().getName() + ")");
		KeyPairGenerator softGen = KeyPairGenerator.getInstance("EC", Software());
		KeyPair softPair = softGen.generateKeyPair();
		Signature ss = Signature.getInstance("SHA256withECDSA"); ss.initSign(softPair.getPrivate()); ss.update(new byte[1]); ss.sign();
		check(!ss.getProvider().getName().startsWith("AndroidKeyStore"), "software EC key signs with software provider");
		KeyFactory kf = KeyFactory.getInstance("EC");
		PublicKey parsed = kf.generatePublic(new X509EncodedKeySpec(softPair.getPublic().getEncoded()));
		check(parsed.equals(softPair.getPublic()) || Arrays.equals(parsed.getEncoded(), softPair.getPublic().getEncoded()), "unnamed KeyFactory still parses public keys");
		KeyInfo ecInfo = KeyFactory.getInstance("EC", "AndroidKeyStore").getKeySpec(pair.getPrivate(), KeyInfo.class);
		check(ecInfo.getKeySize() == 256 && !ecInfo.isInsideSecureHardware(), "EC KeyInfo");
		KeyAgreement ka = KeyAgreement.getInstance("ECDH"); ka.init(pair.getPrivate()); ka.doPhase(softPair.getPublic(), true);
		KeyAgreement kb = KeyAgreement.getInstance("ECDH"); kb.init(softPair.getPrivate()); kb.doPhase(pair.getPublic(), true);
		check(Arrays.equals(ka.generateSecret(), kb.generateSecret()), "ECDH with keystore key");
		expect(InvalidKeyException.class, "sign purpose enforced for EC", () -> {
			kpg.initialize(new KeyGenParameterSpec.Builder("ecverify", KeyProperties.PURPOSE_VERIFY).build());
			KeyPair p = kpg.generateKeyPair(); Signature x = Signature.getInstance("SHA256withECDSA"); x.initSign(p.getPrivate());
		});

		// 10 RSA
		KeyPairGenerator rpg = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
		rpg.initialize(new KeyGenParameterSpec.Builder("rsa", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_SIGN)
			.setKeySize(2048).build());
		KeyPair rsa = rpg.generateKeyPair();
		Cipher re = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidKeyStoreBCWorkaround"); re.init(Cipher.ENCRYPT_MODE, rsa.getPublic());
		byte[] rct = re.doFinal("secret".getBytes());
		Cipher rd = Cipher.getInstance("RSA/ECB/PKCS1Padding"); rd.init(Cipher.DECRYPT_MODE, ks.getKey("rsa", null));
		check(new String(rd.doFinal(rct)).equals("secret"), "RSA decrypt with keystore key (" + rd.getProvider().getName() + ")");
		for (String padding : new String[] {"OAEPWithSHA-1AndMGF1Padding", "OAEPWithSHA-256AndMGF1Padding", "OAEPPadding"}) {
			Cipher oe = Cipher.getInstance("RSA/ECB/" + padding); oe.init(Cipher.ENCRYPT_MODE, rsa.getPublic());
			byte[] oct = oe.doFinal("oaep".getBytes());
			Cipher od = Cipher.getInstance("RSA/ECB/" + padding); od.init(Cipher.DECRYPT_MODE, ks.getKey("rsa", null));
			check(new String(od.doFinal(oct)).equals("oaep"), "RSA " + padding + " with keystore key (" + od.getProvider().getName() + ")");
		}
		Signature rs = Signature.getInstance("SHA256withRSA"); rs.initSign(rsa.getPrivate()); rs.update(new byte[4]);
		Signature rv = Signature.getInstance("SHA256withRSA"); rv.initVerify(rsa.getPublic()); rv.update(new byte[4]);
		check(rv.verify(rs.sign()), "RSA signature");
		KeyStore.Entry entry = ks.getEntry("rsa", new KeyStore.PasswordProtection(null)); // libcore also takes null
		check(entry instanceof KeyStore.PrivateKeyEntry, "getEntry gives a PrivateKeyEntry");

		// 11 HMAC
		javax.crypto.KeyGenerator hg = javax.crypto.KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore");
		hg.init(new KeyGenParameterSpec.Builder("hmac", KeyProperties.PURPOSE_SIGN).build());
		SecretKey hk = hg.generateKey();
		Mac m = Mac.getInstance("HmacSHA256"); m.init(hk);
		check(m.doFinal(new byte[3]).length == 32 && m.getProvider().getName().equals("AndroidKeyStore"), "HMAC with keystore key");

		// 12 import with KeyProtection
		ks.setEntry("imported", new KeyStore.SecretKeyEntry(new SecretKeySpec(new byte[32], "AES")),
			new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setUserAuthenticationRequired(true)
				.setUserAuthenticationParameters(5, KeyProperties.AUTH_DEVICE_CREDENTIAL).build());
		SystemClock.offset += 3600_000;
		expect(UserNotAuthenticatedException.class, "imported key keeps KeyProtection's authentication", () -> {
			Cipher x = Cipher.getInstance("AES/GCM/NoPadding"); x.init(Cipher.ENCRYPT_MODE, (SecretKey)ks.getKey("imported", null));
		});
		ks.deleteEntry("imported");
		check(!ks.containsAlias("imported"), "delete");

		System.out.println(passes + " passed, " + failures + " failed");
		System.exit(failures == 0 ? 0 : 1);
	}
	@SuppressWarnings("unchecked")
	static void storeRaw(String alias, byte[] data) throws Exception {
		java.lang.reflect.Field f = Class.forName("android.security.keystore.AndroidKeyStore").getDeclaredField("store");
		f.setAccessible(true); ((Map<String, byte[]>)f.get(null)).put(alias, data);
	}
	static Provider Software() { for (Provider p : Security.getProviders()) if (p.getService("KeyPairGenerator", "EC") != null && !p.getName().startsWith("AndroidKeyStore")) return p; return null; }
}
