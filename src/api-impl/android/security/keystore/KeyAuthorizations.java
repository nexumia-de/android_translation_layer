package android.security.keystore;

import android.atl.ATLAuth;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Date;

/* What a keystore key may be used for and when, kept with the key (docs/keystore-auth.md):
 * purposes, user authentication (a BiometricPrompt confirmation for every use or within a time),
 * unlocked device, invalidation when a fingerprint is added. The keystore's own Cipher, Mac and
 * Signature check it; the key material never leaves them (getEncoded() is null). Keys stored before
 * this have no authorizations: every purpose, no authentication. */
public final class KeyAuthorizations {
	/* the version of the serialized form */
	private static final int VERSION = 1;

	public final int purposes;
	public final boolean userAuthenticationRequired;
	/* seconds a confirmation stays valid; 0: every use needs its own */
	public final int timeoutSeconds;
	/* KeyProperties.AUTH_* */
	public final int authenticationTypes;
	public final boolean unlockedDeviceRequired;
	public final boolean invalidatedByBiometricEnrollment;
	/* ATLAuth.enrollment() when the key was made, if invalidatedByBiometricEnrollment applies */
	public final String enrollment;
	public final boolean randomizedEncryptionRequired;
	public final long creationTime;
	public final int origin;

	private KeyAuthorizations(int purposes, boolean userAuthenticationRequired, int timeoutSeconds, int authenticationTypes,
	                          boolean unlockedDeviceRequired, boolean invalidatedByBiometricEnrollment, String enrollment,
	                          boolean randomizedEncryptionRequired, long creationTime, int origin) {
		this.purposes = purposes;
		this.userAuthenticationRequired = userAuthenticationRequired;
		this.timeoutSeconds = timeoutSeconds;
		this.authenticationTypes = authenticationTypes;
		this.unlockedDeviceRequired = unlockedDeviceRequired;
		this.invalidatedByBiometricEnrollment = invalidatedByBiometricEnrollment;
		this.enrollment = enrollment;
		this.randomizedEncryptionRequired = randomizedEncryptionRequired;
		this.creationTime = creationTime;
		this.origin = origin;
	}

	/* a key stored before authorizations were kept */
	static KeyAuthorizations legacy() {
		return new KeyAuthorizations(~0, false, 0, 0, false, false, "", false, 0, KeyProperties.ORIGIN_UNKNOWN);
	}

	static KeyAuthorizations make(int purposes, boolean authRequired, int timeoutSeconds, int authTypes,
	                              boolean unlockedDeviceRequired, boolean invalidatedByEnrollment,
	                              boolean randomizedEncryptionRequired, int origin) {
		if (!authRequired) {
			timeoutSeconds = 0;
			authTypes = 0;
			invalidatedByEnrollment = false;
		}
		/* Android: only keys bound to a biometric are invalidated by a new one */
		boolean biometric = (authTypes & KeyProperties.AUTH_BIOMETRIC_STRONG) != 0;
		invalidatedByEnrollment = invalidatedByEnrollment && biometric;
		String enrollment = invalidatedByEnrollment ? ATLAuth.enrollment() : "";
		return new KeyAuthorizations(purposes, authRequired, timeoutSeconds, authTypes, unlockedDeviceRequired,
		                             invalidatedByEnrollment, enrollment, randomizedEncryptionRequired,
		                             System.currentTimeMillis(), origin);
	}

	static KeyAuthorizations of(KeyGenParameterSpec spec) {
		return make(spec.getPurposes(), spec.isUserAuthenticationRequired(), Math.max(spec.getUserAuthenticationValidityDurationSeconds(), 0),
		            spec.getUserAuthenticationType(), spec.isUnlockedDeviceRequired(), spec.isInvalidatedByBiometricEnrollment(),
		            spec.isRandomizedEncryptionRequired(), KeyProperties.ORIGIN_GENERATED);
	}

	static KeyAuthorizations of(KeyProtection protection) {
		return make(protection.getPurposes(), protection.isUserAuthenticationRequired(),
		            Math.max(protection.getUserAuthenticationValidityDurationSeconds(), 0), protection.getUserAuthenticationType(),
		            protection.isUnlockedDeviceRequired(), protection.isInvalidatedByBiometricEnrollment(),
		            protection.isRandomizedEncryptionRequired(), KeyProperties.ORIGIN_IMPORTED);
	}

	/* Android's checks when a key bound to user authentication is made */
	void checkCreate() {
		if (!userAuthenticationRequired)
			return;
		int available = ATLAuth.available();
		if (available == 0)
			throw new IllegalStateException("Secure lock screen must be enabled to create keys requiring user authentication");
		if (authenticationTypes == KeyProperties.AUTH_BIOMETRIC_STRONG && (available & ATLAuth.BIOMETRIC_STRONG) == 0)
			throw new IllegalStateException("At least one biometric must be enrolled to create keys requiring user authentication for every use");
	}

	/* before an operation starts (Cipher.init, Signature.initSign, Mac.init, ...) */
	void checkInit(int purpose, Object operation) throws InvalidKeyException {
		if ((purposes & purpose) == 0)
			throw new InvalidKeyException("Incompatible purpose");
		if (unlockedDeviceRequired && ATLAuth.deviceLocked())
			throw new UserNotAuthenticatedException("Device is locked");
		if (!userAuthenticationRequired)
			return;
		if (invalidatedByBiometricEnrollment && !enrollment.equals(ATLAuth.enrollment()))
			throw new KeyPermanentlyInvalidatedException("Biometric enrollment changed since the key was made");
		if (timeoutSeconds > 0) {
			if (!ATLAuth.confirmedWithin(authenticationTypes, timeoutSeconds))
				throw new UserNotAuthenticatedException();
		} else {
			/* the operation may start; it runs once BiometricPrompt confirms it (CryptoObject) */
			ATLAuth.awaitConfirmation(operation, authenticationTypes);
		}
	}

	/* before an operation's data is processed: keys that need a confirmation for every use */
	boolean operationAllowed(Object operation) {
		return !userAuthenticationRequired || timeoutSeconds > 0 || ATLAuth.isAuthorized(operation);
	}

	Date creationDate() {
		return new Date(creationTime);
	}

	byte[] toBytes() {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(VERSION);
			out.writeInt(purposes);
			out.writeBoolean(userAuthenticationRequired);
			out.writeInt(timeoutSeconds);
			out.writeInt(authenticationTypes);
			out.writeBoolean(unlockedDeviceRequired);
			out.writeBoolean(invalidatedByBiometricEnrollment);
			out.writeUTF(enrollment);
			out.writeBoolean(randomizedEncryptionRequired);
			out.writeLong(creationTime);
			out.writeInt(origin);
			out.flush();
			return bytes.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	static KeyAuthorizations fromBytes(byte[] data) throws IOException {
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		int version = in.readInt();
		if (version != VERSION)
			throw new IOException("unknown key authorizations version " + version);
		return new KeyAuthorizations(in.readInt(), in.readBoolean(), in.readInt(), in.readInt(), in.readBoolean(),
		                             in.readBoolean(), in.readUTF(), in.readBoolean(), in.readLong(), in.readInt());
	}
}
