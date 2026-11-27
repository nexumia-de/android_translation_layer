package android.security.keystore;

import java.security.KeyStore.ProtectionParameter;
import java.util.Date;

/* For keys an app imports with KeyStore.setEntry; ATL keeps the same authorizations as for
 * generated keys (KeyAuthorizations). */
public final class KeyProtection implements ProtectionParameter {
	private int purposes;
	private String[] encryptionPaddings;
	private String[] signaturePaddings;
	private String[] digests;
	private String[] blockModes;
	private boolean randomizedEncryptionRequired = true;
	private boolean userAuthenticationRequired;
	private int userAuthenticationValidityDurationSeconds = 0;
	private int userAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG;
	private boolean userPresenceRequired;
	private boolean userAuthenticationValidWhileOnBody;
	private boolean invalidatedByBiometricEnrollment = true;
	private boolean userConfirmationRequired;
	private boolean unlockedDeviceRequired;
	private boolean isStrongBoxBacked;
	private int maxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;
	private Date keyValidityStart;
	private Date keyValidityForOriginationEnd;
	private Date keyValidityForConsumptionEnd;

	private KeyProtection() {}

	public static final class Builder {
		private final KeyProtection p = new KeyProtection();

		public Builder(int purposes) {
			p.purposes = purposes;
		}

		public Builder setKeyValidityStart(Date startDate) { p.keyValidityStart = startDate; return this; }
		public Builder setKeyValidityEnd(Date endDate) { p.keyValidityForOriginationEnd = endDate; p.keyValidityForConsumptionEnd = endDate; return this; }
		public Builder setKeyValidityForOriginationEnd(Date endDate) { p.keyValidityForOriginationEnd = endDate; return this; }
		public Builder setKeyValidityForConsumptionEnd(Date endDate) { p.keyValidityForConsumptionEnd = endDate; return this; }
		public Builder setEncryptionPaddings(String... paddings) { p.encryptionPaddings = paddings; return this; }
		public Builder setSignaturePaddings(String... paddings) { p.signaturePaddings = paddings; return this; }
		public Builder setDigests(String... digests) { p.digests = digests; return this; }
		public Builder setBlockModes(String... blockModes) { p.blockModes = blockModes; return this; }
		public Builder setRandomizedEncryptionRequired(boolean required) { p.randomizedEncryptionRequired = required; return this; }
		public Builder setUserAuthenticationRequired(boolean required) { p.userAuthenticationRequired = required; return this; }
		public Builder setUserConfirmationRequired(boolean required) { p.userConfirmationRequired = required; return this; }

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
			p.userAuthenticationValidityDurationSeconds = timeout;
			p.userAuthenticationType = type;
			return this;
		}

		public Builder setUserPresenceRequired(boolean required) { p.userPresenceRequired = required; return this; }
		public Builder setUserAuthenticationValidWhileOnBody(boolean remainsValid) { p.userAuthenticationValidWhileOnBody = remainsValid; return this; }
		public Builder setInvalidatedByBiometricEnrollment(boolean invalidateKey) { p.invalidatedByBiometricEnrollment = invalidateKey; return this; }
		public Builder setBoundToSpecificSecureUserId(long secureUserId) { return this; }
		public Builder setCriticalToDeviceEncryption(boolean critical) { return this; }
		public Builder setUnlockedDeviceRequired(boolean unlockedDeviceRequired) { p.unlockedDeviceRequired = unlockedDeviceRequired; return this; }
		/* there is no StrongBox: importing such a key fails */
		public Builder setIsStrongBoxBacked(boolean isStrongBoxBacked) { p.isStrongBoxBacked = isStrongBoxBacked; return this; }
		public Builder setMaxUsageCount(int maxUsageCount) { p.maxUsageCount = maxUsageCount; return this; }
		public Builder setRollbackResistant(boolean rollbackResistant) { return this; }

		public KeyProtection build() {
			return p;
		}
	}

	public int getPurposes() { return purposes; }
	public String[] getEncryptionPaddings() { return encryptionPaddings != null ? encryptionPaddings.clone() : new String[0]; }
	public String[] getSignaturePaddings() { return signaturePaddings != null ? signaturePaddings.clone() : new String[0]; }
	public String[] getDigests() { return digests != null ? digests.clone() : new String[0]; }
	public boolean isDigestsSpecified() { return digests != null; }
	public String[] getBlockModes() { return blockModes != null ? blockModes.clone() : new String[0]; }
	public boolean isRandomizedEncryptionRequired() { return randomizedEncryptionRequired; }
	public boolean isUserAuthenticationRequired() { return userAuthenticationRequired; }
	public boolean isUserConfirmationRequired() { return userConfirmationRequired; }
	public int getUserAuthenticationValidityDurationSeconds() {
		return userAuthenticationValidityDurationSeconds == 0 ? -1 : userAuthenticationValidityDurationSeconds;
	}
	public int getUserAuthenticationType() { return userAuthenticationType; }
	public boolean isUserPresenceRequired() { return userPresenceRequired; }
	public boolean isUserAuthenticationValidWhileOnBody() { return userAuthenticationValidWhileOnBody; }
	public boolean isInvalidatedByBiometricEnrollment() { return invalidatedByBiometricEnrollment; }
	public boolean isUnlockedDeviceRequired() { return unlockedDeviceRequired; }
	public boolean isStrongBoxBacked() { return isStrongBoxBacked; }
	public int getMaxUsageCount() { return maxUsageCount; }
	public Date getKeyValidityStart() { return keyValidityStart; }
	public Date getKeyValidityForOriginationEnd() { return keyValidityForOriginationEnd; }
	public Date getKeyValidityForConsumptionEnd() { return keyValidityForConsumptionEnd; }
}
