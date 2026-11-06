package android.security.keystore;

import java.math.BigInteger;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

public class KeyGenParameterSpec {

	private String keystoreAlias;
	private int purposes;
	private int keySize;
	private String[] blockModes;
	private String[] encryptionPaddings;
	private boolean userAuthenticationRequired;
	private AlgorithmParameterSpec algorithmParameterSpec;
	private byte[] attestationChallenge;

	public static class Builder {
		private KeyGenParameterSpec spec = new KeyGenParameterSpec();

		public Builder(String keystoreAlias, int purposes) {
			spec.keystoreAlias = keystoreAlias;
			spec.purposes = purposes;
		}

		public Builder setKeySize(int keySize) {
			spec.keySize = keySize;
			return this;
		}

		public Builder setBlockModes(String[] blockModes) {
			spec.blockModes = blockModes;
			return this;
		}

		public Builder setEncryptionPaddings(String[] encryptionPaddings) {
			spec.encryptionPaddings = encryptionPaddings;
			return this;
		}

		public Builder setUserAuthenticationRequired(boolean userAuthenticationRequired) {
			spec.userAuthenticationRequired = userAuthenticationRequired;
			return this;
		}

		public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec params) {
			spec.algorithmParameterSpec = params;
			return this;
		}

		/* keys are made in software, so there is no attestation: the
		 * certificate chain never has the attestation extension */
		public Builder setAttestationChallenge(byte[] attestationChallenge) {
			spec.attestationChallenge = attestationChallenge;
			return this;
		}

		public Builder setCertificateSerialNumber(BigInteger serialNumber) {
			return this;
		}

		public Builder setCertificateSubject(X500Principal subject) {
			return this;
		}

		public Builder setCertificateNotBefore(Date notBefore) {
			return this;
		}

		public Builder setCertificateNotAfter(Date notAfter) {
			return this;
		}

		public Builder setDigests(String[] digests) {
			return this;
		}

		public Builder setSignaturePaddings(String[] signaturePaddings) {
			return this;
		}

		/* keys are kept in the Secret Service; there is no secure hardware
		 * and no user authentication to bind them to */
		public Builder setIsStrongBoxBacked(boolean isStrongBoxBacked) {
			return this;
		}

		public Builder setUnlockedDeviceRequired(boolean unlockedDeviceRequired) {
			return this;
		}

		public Builder setInvalidatedByBiometricEnrollment(boolean invalidateKey) {
			return this;
		}

		public Builder setUserAuthenticationParameters(int timeout, int type) {
			return this;
		}

		public Builder setUserAuthenticationValidityDurationSeconds(int seconds) {
			return this;
		}

		public Builder setRandomizedEncryptionRequired(boolean required) {
			return this;
		}

		public KeyGenParameterSpec build() {
			return spec;
		}
	}

	public int getKeySize() {
		return keySize;
	}

	public String[] getBlockModes() {
		return blockModes;
	}

	public int getPurposes() {
		return purposes;
	}

	public String[] getEncryptionPaddings() {
		return encryptionPaddings;
	}

	public boolean isUserAuthenticationRequired() {
		return userAuthenticationRequired;
	}

	public AlgorithmParameterSpec getAlgorithmParameterSpec() {
		return algorithmParameterSpec;
	}

	public byte[] getAttestationChallenge() {
		return attestationChallenge;
	}

	public String getKeystoreAlias() {
		return keystoreAlias;
	}
}
