package android.atl;

import android.os.SystemClock;
import java.util.Map;
import java.util.WeakHashMap;

/* Confirming the phone's owner (docs/keystore-auth.md): the host's permission service checks the
 * fingerprint through fingerprintd and the password through Phosh's polkit prompt
 * (src/installer-service/auth.c); this class asks it and remembers the confirmations for the
 * keystore. Outside an app's own Flatpak (development runs) nothing can confirm. */
public final class ATLAuth {
	/* android.hardware.biometrics.BiometricManager.Authenticators */
	public static final int BIOMETRIC_STRONG = 0x000F;
	public static final int BIOMETRIC_WEAK = 0x00FF;
	public static final int DEVICE_CREDENTIAL = 0x8000;
	/* android.security.keystore.KeyProperties.AUTH_* */
	public static final int AUTH_BIOMETRIC_STRONG = 1;
	public static final int AUTH_DEVICE_CREDENTIAL = 2;

	private static int available;
	private static String enrollment;
	private static long asked_at;

	/* elapsedRealtime of the last confirmation per AUTH_* type, 0 = none yet */
	private static long last_biometric;
	private static long last_credential;

	/* keystore operations of keys that need a confirmation for every use, waiting for one:
	 * operation -> the AUTH_* types its key accepts */
	private static final Map<Object, Integer> awaiting = new WeakHashMap<>();
	private static final Map<Object, Boolean> authorized = new WeakHashMap<>();

	private ATLAuth() {}

	private static void refresh() {
		long now = SystemClock.elapsedRealtime();
		if (enrollment != null && now - asked_at < 2000)
			return;
		available = 0;
		enrollment = "";
		if (ATLPaths.in_flatpak) {
			String reply = nativeCall("Authenticators"); // "<bits> <enrollment>"
			if (reply != null) {
				int space = reply.indexOf(' ');
				try {
					available = Integer.parseInt(space < 0 ? reply : reply.substring(0, space));
					enrollment = space < 0 ? "" : reply.substring(space + 1);
				} catch (NumberFormatException e) {
				}
			}
		}
		asked_at = now;
	}

	/* the Authenticators bits that can confirm now */
	public static synchronized int available() {
		refresh();
		return available;
	}

	/* changes when a finger is added or removed; "" without fingerprints */
	public static synchronized String enrollment() {
		refresh();
		return enrollment;
	}

	private static String call(String method) {
		if (!ATLPaths.in_flatpak)
			return "unavailable";
		String reply = nativeCall(method);
		return reply != null ? reply : "unavailable";
	}

	/* one fingerprint attempt; blocks until the press (at most a minute), so not on the main thread:
	 * "match", "no-match", "retry", "lockout", "canceled", "unavailable" or "error" */
	public static String verifyFingerprint() {
		return call("VerifyFingerprint");
	}

	/* the desktop's password prompt; blocks: "yes", "no" or "unavailable" */
	public static String confirmCredential() {
		return call("ConfirmCredential");
	}

	/* ends a running verifyFingerprint or confirmCredential of this app */
	public static void cancel() {
		if (ATLPaths.in_flatpak)
			nativeCall("CancelAuthentication");
	}

	private static boolean locked;
	private static long locked_asked_at;

	/* whether the lock screen is up; asked at most once a second (apps call
	 * KeyguardManager.isKeyguardLocked often) */
	public static synchronized boolean deviceLocked() {
		if (!ATLPaths.in_flatpak)
			return false;
		long now = SystemClock.elapsedRealtime();
		if (locked_asked_at == 0 || now - locked_asked_at >= 1000) {
			locked = "true".equals(nativeCall("DeviceLocked"));
			locked_asked_at = now;
		}
		return locked;
	}

	/* a successful confirmation of the given AUTH_* type, from BiometricPrompt */
	public static synchronized void confirmed(int type, boolean for_operation) {
		long now = SystemClock.elapsedRealtime();
		if (type == AUTH_BIOMETRIC_STRONG)
			last_biometric = now;
		else
			last_credential = now;
		/* the operations waiting now; the prompt's CryptoObject is one of them */
		if (for_operation) {
			for (Map.Entry<Object, Integer> entry : awaiting.entrySet())
				if ((entry.getValue() & type) != 0)
					authorized.put(entry.getKey(), Boolean.TRUE);
			awaiting.keySet().removeAll(authorized.keySet());
		}
	}

	/* whether one of the AUTH_* types was confirmed in the last `seconds` seconds */
	public static synchronized boolean confirmedWithin(int types, int seconds) {
		long now = SystemClock.elapsedRealtime();
		long limit = seconds * 1000L;
		return ((types & AUTH_BIOMETRIC_STRONG) != 0 && last_biometric != 0 && now - last_biometric <= limit) ||
		    ((types & AUTH_DEVICE_CREDENTIAL) != 0 && last_credential != 0 && now - last_credential <= limit);
	}

	/* a keystore operation that needs a confirmation for its own use (it was just initialised) */
	public static synchronized void awaitConfirmation(Object operation, int types) {
		authorized.remove(operation);
		awaiting.put(operation, types);
	}

	public static synchronized boolean isAuthorized(Object operation) {
		return authorized.containsKey(operation);
	}

	/* the reply of de.nexumia.atl.Permissions.<method> as text, null if the service cannot be reached */
	private static native String nativeCall(String method);
}
