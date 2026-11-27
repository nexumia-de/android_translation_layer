package android.security.keystore;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

/* Everything an app asks for is kept: KeyAuthorizations stores what ATL enforces (purposes, user
 * authentication, unlocked device, invalidation by a new fingerprint, random IVs); StrongBox fails
 * with StrongBoxUnavailableException as on phones without one; an attestation challenge gives no
 * attestation, since the keys are software keys (docs/keystore-auth.md). */
public final class KeyGenParameterSpec implements AlgorithmParameterSpec {

	private String keystoreAlias;
	private int purposes;
	private int keySize = -1;
	private AlgorithmParameterSpec algorithmParameterSpec;
	private X500Principal certificateSubject;
	private BigInteger certificateSerialNumber;
	private Date certificateNotBefore;
	private Date certificateNotAfter;
	private String[] digests;
	private String[] encryptionPaddings;
	private String[] signaturePaddings;
	private String[] blockModes;
	private boolean randomizedEncryptionRequired = true;
	private boolean userAuthenticationRequired;
	private int userAuthenticationValidityDurationSeconds = 0;
	private int userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG;
	private boolean userPresenceRequired;
	private byte[] attestationChallenge;
	private boolean uniqueIdIncluded;
	private boolean userAuthenticationValidWhileOnBody;
	private boolean invalidatedByBiometricEnrollment = true;
	private boolean isStrongBoxBacked;
	private boolean userConfirmationRequired;
	private boolean unlockedDeviceRequired;
	private boolean criticalToDeviceEncryption;
	private int maxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;
	private Date keyValidityStart;
	private Date keyValidityForOriginationEnd;
	private Date keyValidityForConsumptionEnd;

	private KeyGenParameterSpec() {}

	public static final class Builder {
		private final KeyGenParameterSpec spec = new KeyGenParameterSpec();

		public Builder(String keystoreAlias, int purposes) {
			if (keystoreAlias == null)
				throw new NullPointerException("keystoreAlias == null");
			if (keystoreAlias.isEmpty())
				throw new IllegalArgumentException("keystoreAlias must not be empty");
			spec.keystoreAlias = keystoreAlias;
			spec.purposes = purposes;
		}

		public Builder(KeyGenParameterSpec source) {
			this(source.keystoreAlias, source.purposes);
			copy(source, spec);
		}

		public Builder setKeySize(int keySize) {
			if (keySize < 0)
				throw new IllegalArgumentException("keySize < 0");
			spec.keySize = keySize;
			return this;
		}

		public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec params) {
			if (params == null)
				throw new NullPointerException("spec == null");
			spec.algorithmParameterSpec = params;
			return this;
		}

		public Builder setCertificateSubject(X500Principal subject) {
			spec.certificateSubject = subject;
			return this;
		}

		public Builder setCertificateSerialNumber(BigInteger serialNumber) {
			spec.certificateSerialNumber = serialNumber;
			return this;
		}

		public Builder setCertificateNotBefore(Date date) {
			spec.certificateNotBefore = date;
			return this;
		}

		public Builder setCertificateNotAfter(Date date) {
			spec.certificateNotAfter = date;
			return this;
		}

		public Builder setKeyValidityStart(Date startDate) {
			spec.keyValidityStart = startDate;
			return this;
		}

		public Builder setKeyValidityEnd(Date endDate) {
			spec.keyValidityForOriginationEnd = endDate;
			spec.keyValidityForConsumptionEnd = endDate;
			return this;
		}

		public Builder setKeyValidityForOriginationEnd(Date endDate) {
			spec.keyValidityForOriginationEnd = endDate;
			return this;
		}

		public Builder setKeyValidityForConsumptionEnd(Date endDate) {
			spec.keyValidityForConsumptionEnd = endDate;
			return this;
		}

		public Builder setDigests(String... digests) {
			spec.digests = digests;
			return this;
		}

		public Builder setEncryptionPaddings(String... paddings) {
			spec.encryptionPaddings = paddings;
			return this;
		}

		public Builder setSignaturePaddings(String... paddings) {
			spec.signaturePaddings = paddings;
			return this;
		}

		public Builder setBlockModes(String... blockModes) {
			spec.blockModes = blockModes;
			return this;
		}

		public Builder setRandomizedEncryptionRequired(boolean required) {
			spec.randomizedEncryptionRequired = required;
			return this;
		}

		public Builder setUserAuthenticationRequired(boolean required) {
			spec.userAuthenticationRequired = required;
			return this;
		}

		public Builder setUserConfirmationRequired(boolean required) {
			spec.userConfirmationRequired = required;
			return this;
		}

		/* -1: every use needs its own biometric confirmation; else a fingerprint or the password
		 * within that many seconds */
		@Deprecated
		public Builder setUserAuthenticationValidityDurationSeconds(int seconds) {
			if (seconds < -1)
				throw new IllegalArgumentException("seconds must be -1 or larger");
			if (seconds == -1)
				return setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG);
			return setUserAuthenticationParameters(seconds, KeyProperties.AUTH_DEVICE_CREDENTIAL | KeyProperties.AUTH_BIOMETRIC_STRONG);
		}

		public Builder setUserAuthenticationParameters(int timeout, int type) {
			if (timeout < 0)
				throw new IllegalArgumentException("timeout must be 0 or larger");
			spec.userAuthenticationValidityDurationSeconds = timeout;
			spec.userAuthenticationType = type;
			return this;
		}

		public Builder setUserPresenceRequired(boolean required) {
			spec.userPresenceRequired = required;
			return this;
		}

		/* the keys are software keys: the certificate chain never has the attestation extension */
		public Builder setAttestationChallenge(byte[] attestationChallenge) {
			spec.attestationChallenge = attestationChallenge;
			return this;
		}

		public Builder setDevicePropertiesAttestationIncluded(boolean included) {
			return this;
		}

		public Builder setAttestKeyAlias(String alias) {
			return this;
		}

		public Builder setUniqueIdIncluded(boolean uniqueIdIncluded) {
			spec.uniqueIdIncluded = uniqueIdIncluded;
			return this;
		}

		public Builder setUserAuthenticationValidWhileOnBody(boolean remainsValid) {
			spec.userAuthenticationValidWhileOnBody = remainsValid;
			return this;
		}

		public Builder setInvalidatedByBiometricEnrollment(boolean invalidateKey) {
			spec.invalidatedByBiometricEnrollment = invalidateKey;
			return this;
		}

		/* there is no StrongBox: generating such a key fails with StrongBoxUnavailableException */
		public Builder setIsStrongBoxBacked(boolean isStrongBoxBacked) {
			spec.isStrongBoxBacked = isStrongBoxBacked;
			return this;
		}

		public Builder setUnlockedDeviceRequired(boolean unlockedDeviceRequired) {
			spec.unlockedDeviceRequired = unlockedDeviceRequired;
			return this;
		}

		public Builder setCriticalToDeviceEncryption(boolean critical) {
			spec.criticalToDeviceEncryption = critical;
			return this;
		}

		public Builder setMaxUsageCount(int maxUsageCount) {
			spec.maxUsageCount = maxUsageCount;
			return this;
		}

		public KeyGenParameterSpec build() {
			KeyGenParameterSpec built = new KeyGenParameterSpec();
			built.keystoreAlias = spec.keystoreAlias;
			built.purposes = spec.purposes;
			copy(spec, built);
			return built;
		}
	}

	private static void copy(KeyGenParameterSpec from, KeyGenParameterSpec to) {
		to.keySize = from.keySize;
		to.algorithmParameterSpec = from.algorithmParameterSpec;
		to.certificateSubject = from.certificateSubject;
		to.certificateSerialNumber = from.certificateSerialNumber;
		to.certificateNotBefore = from.certificateNotBefore;
		to.certificateNotAfter = from.certificateNotAfter;
		to.digests = from.digests;
		to.encryptionPaddings = from.encryptionPaddings;
		to.signaturePaddings = from.signaturePaddings;
		to.blockModes = from.blockModes;
		to.randomizedEncryptionRequired = from.randomizedEncryptionRequired;
		to.userAuthenticationRequired = from.userAuthenticationRequired;
		to.userAuthenticationValidityDurationSeconds = from.userAuthenticationValidityDurationSeconds;
		to.userAuthenticationType = from.userAuthenticationType;
		to.userPresenceRequired = from.userPresenceRequired;
		to.attestationChallenge = from.attestationChallenge;
		to.uniqueIdIncluded = from.uniqueIdIncluded;
		to.userAuthenticationValidWhileOnBody = from.userAuthenticationValidWhileOnBody;
		to.invalidatedByBiometricEnrollment = from.invalidatedByBiometricEnrollment;
		to.isStrongBoxBacked = from.isStrongBoxBacked;
		to.userConfirmationRequired = from.userConfirmationRequired;
		to.unlockedDeviceRequired = from.unlockedDeviceRequired;
		to.criticalToDeviceEncryption = from.criticalToDeviceEncryption;
		to.maxUsageCount = from.maxUsageCount;
		to.keyValidityStart = from.keyValidityStart;
		to.keyValidityForOriginationEnd = from.keyValidityForOriginationEnd;
		to.keyValidityForConsumptionEnd = from.keyValidityForConsumptionEnd;
	}

	public String getKeystoreAlias() { return keystoreAlias; }
	public int getPurposes() { return purposes; }
	public int getKeySize() { return keySize; }
	public AlgorithmParameterSpec getAlgorithmParameterSpec() { return algorithmParameterSpec; }
	public X500Principal getCertificateSubject() { return certificateSubject; }
	public BigInteger getCertificateSerialNumber() { return certificateSerialNumber; }
	public Date getCertificateNotBefore() { return certificateNotBefore; }
	public Date getCertificateNotAfter() { return certificateNotAfter; }
	public Date getKeyValidityStart() { return keyValidityStart; }
	public Date getKeyValidityForOriginationEnd() { return keyValidityForOriginationEnd; }
	public Date getKeyValidityForConsumptionEnd() { return keyValidityForConsumptionEnd; }
	public String[] getDigests() { return digests != null ? digests.clone() : new String[0]; }
	public boolean isDigestsSpecified() { return digests != null; }
	public String[] getEncryptionPaddings() { return encryptionPaddings != null ? encryptionPaddings.clone() : new String[0]; }
	public String[] getSignaturePaddings() { return signaturePaddings != null ? signaturePaddings.clone() : new String[0]; }
	public String[] getBlockModes() { return blockModes != null ? blockModes.clone() : new String[0]; }
	public boolean isRandomizedEncryptionRequired() { return randomizedEncryptionRequired; }
	public boolean isUserAuthenticationRequired() { return userAuthenticationRequired; }
	public boolean isUserConfirmationRequired() { return userConfirmationRequired; }
	/* -1 for keys that need a confirmation for every use, as on Android */
	public int getUserAuthenticationValidityDurationSeconds() {
		return userAuthenticationValidityDurationSeconds == 0 ? -1 : userAuthenticationValidityDurationSeconds;
	}
	public int getUserAuthenticationType() { return userAuthenticationType; }
	public boolean isUserPresenceRequired() { return userPresenceRequired; }
	public byte[] getAttestationChallenge() { return attestationChallenge != null ? attestationChallenge.clone() : null; }
	public boolean isUniqueIdIncluded() { return uniqueIdIncluded; }
	public boolean isUserAuthenticationValidWhileOnBody() { return userAuthenticationValidWhileOnBody; }
	public boolean isInvalidatedByBiometricEnrollment() { return invalidatedByBiometricEnrollment; }
	public boolean isStrongBoxBacked() { return isStrongBoxBacked; }
	public boolean isUnlockedDeviceRequired() { return unlockedDeviceRequired; }
	public boolean isCriticalToDeviceEncryption() { return criticalToDeviceEncryption; }
	public int getMaxUsageCount() { return maxUsageCount; }
}
