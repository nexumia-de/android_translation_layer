package android.security.keystore;

import java.security.spec.KeySpec;
import java.util.Date;

/* What SecretKeyFactory/KeyFactory.getKeySpec(key, KeyInfo.class) reports for a keystore key: the
 * truth for ATL, i.e. a software key outside secure hardware; user authentication is enforced by
 * ATL, not by secure hardware. */
public class KeyInfo implements KeySpec {
	private final String alias;
	private final int keySize;
	private final KeyAuthorizations auth;

	KeyInfo(String alias, int keySize, KeyAuthorizations auth) {
		this.alias = alias;
		this.keySize = keySize;
		this.auth = auth;
	}

	public String getKeystoreAlias() { return alias; }
	public boolean isInsideSecureHardware() { return false; }
	public int getSecurityLevel() { return KeyProperties.SECURITY_LEVEL_SOFTWARE; }
	public int getOrigin() { return auth.origin; }
	public int getKeySize() { return keySize; }
	public Date getKeyValidityStart() { return null; }
	public Date getKeyValidityForConsumptionEnd() { return null; }
	public Date getKeyValidityForOriginationEnd() { return null; }
	public int getPurposes() { return auth.purposes; }
	public String[] getBlockModes() { return new String[0]; }
	public String[] getEncryptionPaddings() { return new String[0]; }
	public String[] getSignaturePaddings() { return new String[0]; }
	public String[] getDigests() { return new String[0]; }
	public boolean isUserAuthenticationRequired() { return auth.userAuthenticationRequired; }
	public boolean isUserConfirmationRequired() { return false; }
	public int getUserAuthenticationValidityDurationSeconds() {
		return auth.userAuthenticationRequired && auth.timeoutSeconds == 0 ? -1 : auth.timeoutSeconds;
	}
	public int getUserAuthenticationType() { return auth.authenticationTypes; }
	public boolean isUserAuthenticationRequirementEnforcedBySecureHardware() { return false; }
	public boolean isUserAuthenticationValidWhileOnBody() { return false; }
	public boolean isTrustedUserPresenceRequired() { return false; }
	public boolean isInvalidatedByBiometricEnrollment() { return auth.invalidatedByBiometricEnrollment; }
	public int getRemainingUsageCount() { return KeyProperties.UNRESTRICTED_USAGE_COUNT; }
}
