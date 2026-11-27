package android.hardware.biometrics;

import android.app.AlertDialog;
import android.atl.ATLAuth;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.security.Signature;
import java.util.concurrent.Executor;
import javax.crypto.Cipher;
import javax.crypto.Mac;

/* Confirms the phone's owner (docs/keystore-auth.md). The fingerprint is checked by the host's
 * permission service through fingerprintd while this dialog asks for it; the password ("Use
 * password", when the app allows the device credential) by Phosh's own polkit prompt. A success
 * unlocks the app's keystore keys: those valid for a while after a confirmation, and with a
 * CryptoObject the operations waiting for one. */
public class BiometricPrompt {
	public static final int BIOMETRIC_ACQUIRED_GOOD = 0;
	public static final int BIOMETRIC_ACQUIRED_PARTIAL = 1;
	public static final int BIOMETRIC_ACQUIRED_INSUFFICIENT = 2;
	public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
	public static final int BIOMETRIC_ERROR_UNABLE_TO_PROCESS = 2;
	public static final int BIOMETRIC_ERROR_TIMEOUT = 3;
	public static final int BIOMETRIC_ERROR_NO_SPACE = 4;
	public static final int BIOMETRIC_ERROR_CANCELED = 5;
	public static final int BIOMETRIC_ERROR_LOCKOUT = 7;
	public static final int BIOMETRIC_ERROR_VENDOR = 8;
	public static final int BIOMETRIC_ERROR_LOCKOUT_PERMANENT = 9;
	public static final int BIOMETRIC_ERROR_USER_CANCELED = 10;
	public static final int BIOMETRIC_ERROR_NO_BIOMETRICS = 11;
	public static final int BIOMETRIC_ERROR_HW_NOT_PRESENT = 12;
	public static final int BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL = 14;
	public static final int BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15;
	public static final int AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL = 1;
	public static final int AUTHENTICATION_RESULT_TYPE_BIOMETRIC = 2;

	private final Context context;
	private final CharSequence title;
	private final CharSequence subtitle;
	private final CharSequence description;
	private final CharSequence negativeText;
	private final Executor negativeExecutor;
	private final DialogInterface.OnClickListener negativeListener;
	private final int authenticators;

	private BiometricPrompt(Builder b) {
		context = b.context;
		title = b.title;
		subtitle = b.subtitle;
		description = b.description;
		negativeText = b.negativeText;
		negativeExecutor = b.negativeExecutor;
		negativeListener = b.negativeListener;
		authenticators = b.allowedAuthenticators != 0 ? b.allowedAuthenticators
		                 : (b.deviceCredentialAllowed ? ATLAuth.BIOMETRIC_WEAK | ATLAuth.DEVICE_CREDENTIAL : ATLAuth.BIOMETRIC_WEAK);
	}

	public CharSequence getTitle() { return title; }
	public CharSequence getSubtitle() { return subtitle; }
	public CharSequence getDescription() { return description; }
	public CharSequence getNegativeButtonText() { return negativeText; }
	public int getAllowedAuthenticators() { return authenticators; }
	public boolean isDeviceCredentialAllowed() { return (authenticators & ATLAuth.DEVICE_CREDENTIAL) != 0; }

	public void authenticate(CancellationSignal cancel, Executor executor, AuthenticationCallback callback) {
		start(null, cancel, executor, callback);
	}

	public void authenticate(CryptoObject crypto, CancellationSignal cancel, Executor executor, AuthenticationCallback callback) {
		if (crypto == null)
			throw new IllegalArgumentException("Must supply a crypto object");
		if (isDeviceCredentialAllowed() && Build.VERSION.SDK_INT < 30)
			throw new IllegalArgumentException("Device credential not supported with crypto");
		start(crypto, cancel, executor, callback);
	}

	private void start(CryptoObject crypto, CancellationSignal cancel, Executor executor, AuthenticationCallback callback) {
		if (cancel == null || executor == null || callback == null)
			throw new IllegalArgumentException("Must supply a cancellation signal, an executor and a callback");
		new Session(crypto, cancel, executor, callback).begin();
	}

	/* one authenticate call: the dialog, the fingerprint attempts on a thread, and the one result */
	private class Session {
		private final CryptoObject crypto;
		private final CancellationSignal cancel;
		private final Executor executor;
		private final AuthenticationCallback callback;
		private final Handler main = new Handler(Looper.getMainLooper());
		private final boolean biometric;
		private final boolean credential;
		private AlertDialog dialog;
		private boolean finished;
		private volatile boolean usingCredential;

		Session(CryptoObject crypto, CancellationSignal cancel, Executor executor, AuthenticationCallback callback) {
			this.crypto = crypto;
			this.cancel = cancel;
			this.executor = executor;
			this.callback = callback;
			int available = ATLAuth.available();
			biometric = (authenticators & ATLAuth.BIOMETRIC_WEAK) != 0 && (available & ATLAuth.BIOMETRIC_STRONG) != 0;
			credential = (authenticators & ATLAuth.DEVICE_CREDENTIAL) != 0 && (available & ATLAuth.DEVICE_CREDENTIAL) != 0;
		}

		void begin() {
			if (!biometric && !credential) {
				if (finish())
					deliverError(isDeviceCredentialAllowed() ? BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL : BIOMETRIC_ERROR_NO_BIOMETRICS,
					             isDeviceCredentialAllowed() ? "No password or fingerprint set up" : "No fingerprint enrolled");
				return;
			}
			cancel.setOnCancelListener(new CancellationSignal.OnCancelListener() {
				@Override
				public void onCancel() {
					if (finish()) {
						ATLAuth.cancel();
						deliverError(BIOMETRIC_ERROR_CANCELED, "Authentication canceled");
					}
				}
			});
			if (biometric) {
				main.post(new Runnable() {
					@Override
					public void run() {
						showDialog();
					}
				});
				thread(new Runnable() {
					@Override
					public void run() {
						fingerprintLoop();
					}
				});
			} else {
				useCredential();
			}
		}

		/* true for the one caller that ends the session */
		private boolean finish() {
			synchronized (this) {
				if (finished)
					return false;
				finished = true;
			}
			closeDialog();
			return true;
		}

		private synchronized boolean isFinished() {
			return finished;
		}

		private void closeDialog() {
			main.post(new Runnable() {
				@Override
				public void run() {
					if (dialog != null) {
						AlertDialog d = dialog;
						dialog = null; // before dismiss, so the dismiss listener knows
						d.dismiss();
					}
				}
			});
		}

		private void showDialog() {
			if (isFinished() || usingCredential)
				return;
			StringBuilder message = new StringBuilder();
			if (subtitle != null)
				message.append(subtitle).append("\n\n");
			if (description != null)
				message.append(description).append("\n\n");
			message.append("Touch the fingerprint sensor.");
			final AlertDialog d = new AlertDialog(context);
			d.setTitle(title);
			d.setMessage(message);
			d.setCancelable(true);
			if (credential) {
				d.setButton(DialogInterface.BUTTON_POSITIVE, "Use password", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface di, int which) {
						switchToCredential();
					}
				});
				d.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface di, int which) {
						userCanceled();
					}
				});
			} else {
				d.setButton(DialogInterface.BUTTON_NEGATIVE, negativeText, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(final DialogInterface di, final int which) {
						if (!finish())
							return;
						ATLAuth.cancel();
						if (negativeListener != null)
							(negativeExecutor != null ? negativeExecutor : executor).execute(new Runnable() {
								@Override
								public void run() {
									negativeListener.onClick(di, DialogInterface.BUTTON_NEGATIVE);
								}
							});
						else
							deliverError(BIOMETRIC_ERROR_USER_CANCELED, "Authentication canceled by user");
					}
				});
			}
			/* closed another way (back button, window closed): canceled by the user */
			d.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface di) {
					if (dialog == d) {
						dialog = null;
						if (!usingCredential)
							userCanceled();
					}
				}
			});
			dialog = d;
			d.show();
		}

		private void setMessage(final String text) {
			main.post(new Runnable() {
				@Override
				public void run() {
					if (dialog != null)
						dialog.setMessage(text);
				}
			});
		}

		private void userCanceled() {
			if (finish()) {
				ATLAuth.cancel();
				deliverError(BIOMETRIC_ERROR_USER_CANCELED, "Authentication canceled by user");
			}
		}

		/* "Use password": ends the fingerprint attempt, then Phosh's password prompt */
		private void switchToCredential() {
			synchronized (this) {
				if (finished || usingCredential)
					return;
				usingCredential = true;
			}
			closeDialog();
			thread(new Runnable() {
				@Override
				public void run() {
					ATLAuth.cancel();
					useCredential();
				}
			});
		}

		private void useCredential() {
			thread(new Runnable() {
				@Override
				public void run() {
					String result = ATLAuth.confirmCredential();
					if ("unavailable".equals(result) && biometric) {
						/* the fingerprint loop may have started one more attempt meanwhile,
						 * which keeps the service busy for this app */
						ATLAuth.cancel();
						result = ATLAuth.confirmCredential();
					}
					if ("yes".equals(result))
						success(AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL, ATLAuth.AUTH_DEVICE_CREDENTIAL);
					else if (finish())
						deliverError("unavailable".equals(result) ? BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL : BIOMETRIC_ERROR_USER_CANCELED,
						             "unavailable".equals(result) ? "Password confirmation is not available" : "Authentication canceled by user");
				}
			});
		}

		private void fingerprintLoop() {
			while (!isFinished() && !usingCredential) {
				String result = ATLAuth.verifyFingerprint();
				if (isFinished() || usingCredential)
					return;
				switch (result) {
					case "match":
						success(AUTHENTICATION_RESULT_TYPE_BIOMETRIC, ATLAuth.AUTH_BIOMETRIC_STRONG);
						return;
					case "no-match":
						setMessage("Not recognized. Try again.");
						executor.execute(new Runnable() {
							@Override
							public void run() {
								callback.onAuthenticationFailed();
							}
						});
						break;
					case "retry":
						setMessage("Press your finger firmly on the sensor.");
						executor.execute(new Runnable() {
							@Override
							public void run() {
								callback.onAuthenticationHelp(BIOMETRIC_ACQUIRED_INSUFFICIENT, "Press firmly on the sensor");
							}
						});
						break;
					case "lockout":
						if (credential) {
							setMessage("Too many attempts. Use your password.");
							return;
						}
						if (finish())
							deliverError(BIOMETRIC_ERROR_LOCKOUT, "Too many attempts. Try again later.");
						return;
					case "canceled":
						/* a minute without a press */
						if (finish())
							deliverError(BIOMETRIC_ERROR_TIMEOUT, "Authentication timed out");
						return;
					default: // "unavailable" (sensor busy), "error"
						if (credential) {
							setMessage("The fingerprint sensor is not available. Use your password.");
							return;
						}
						if (finish())
							deliverError(BIOMETRIC_ERROR_HW_UNAVAILABLE, "The fingerprint sensor is not available");
						return;
				}
			}
		}

		private void success(int resultType, int authType) {
			if (!finish())
				return;
			/* before the callback: it usually goes on with the CryptoObject at once */
			ATLAuth.confirmed(authType, crypto != null);
			final AuthenticationResult result = new AuthenticationResult(crypto, resultType);
			executor.execute(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticationSucceeded(result);
				}
			});
		}

		private void deliverError(final int code, final String text) {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					callback.onAuthenticationError(code, text);
				}
			});
		}
	}

	private static void thread(Runnable r) {
		Thread t = new Thread(r, "BiometricPrompt");
		t.setDaemon(true);
		t.start();
	}

	public static class CryptoObject {
		private final Object crypto;

		public CryptoObject(Signature signature) { crypto = signature; }
		public CryptoObject(Cipher cipher) { crypto = cipher; }
		public CryptoObject(Mac mac) { crypto = mac; }

		public Signature getSignature() { return crypto instanceof Signature ? (Signature)crypto : null; }
		public Cipher getCipher() { return crypto instanceof Cipher ? (Cipher)crypto : null; }
		public Mac getMac() { return crypto instanceof Mac ? (Mac)crypto : null; }
	}

	public static class AuthenticationResult {
		private final CryptoObject crypto;
		private final int type;

		public AuthenticationResult(CryptoObject crypto, int type) {
			this.crypto = crypto;
			this.type = type;
		}

		public CryptoObject getCryptoObject() { return crypto; }
		public int getAuthenticationType() { return type; }
	}

	public static abstract class AuthenticationCallback {
		public void onAuthenticationError(int errorCode, CharSequence errString) {}
		public void onAuthenticationHelp(int helpCode, CharSequence helpString) {}
		public void onAuthenticationSucceeded(AuthenticationResult result) {}
		public void onAuthenticationFailed() {}
	}

	public static class Builder {
		private final Context context;
		private CharSequence title;
		private CharSequence subtitle;
		private CharSequence description;
		private CharSequence negativeText;
		private Executor negativeExecutor;
		private DialogInterface.OnClickListener negativeListener;
		private boolean deviceCredentialAllowed;
		private int allowedAuthenticators;

		public Builder(Context context) { this.context = context; }
		public Builder setTitle(CharSequence title) { this.title = title; return this; }
		public Builder setSubtitle(CharSequence subtitle) { this.subtitle = subtitle; return this; }
		public Builder setDescription(CharSequence description) { this.description = description; return this; }
		public Builder setNegativeButton(CharSequence text, Executor executor, DialogInterface.OnClickListener listener) {
			negativeText = text;
			negativeExecutor = executor;
			negativeListener = listener;
			return this;
		}
		public Builder setConfirmationRequired(boolean required) { return this; }
		public Builder setDeviceCredentialAllowed(boolean allowed) { deviceCredentialAllowed = allowed; return this; }
		public Builder setAllowedAuthenticators(int authenticators) { allowedAuthenticators = authenticators; return this; }

		public BiometricPrompt build() {
			if (title == null || title.length() == 0)
				throw new IllegalArgumentException("Title must be set and non-empty");
			BiometricPrompt prompt = new BiometricPrompt(this);
			if (!prompt.isDeviceCredentialAllowed() && negativeText == null)
				throw new IllegalArgumentException("Negative text must be set and non-empty");
			if (prompt.isDeviceCredentialAllowed() && negativeText != null)
				throw new IllegalArgumentException("Can't have both negative button behavior and device credential enabled");
			return prompt;
		}
	}
}
