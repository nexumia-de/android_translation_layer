package android.util;

/**
 * Reports a resource that was not closed before it was garbage collected.
 * Apps call warnIfOpen from finalize(); this logs instead of keeping a
 * stack trace of every open, as Android does only with StrictMode on.
 */
public final class CloseGuard {
	private static final String TAG = "CloseGuard";

	private String closeMethodName;

	public CloseGuard() {}

	public void open(String closeMethodName) {
		if (closeMethodName == null)
			throw new NullPointerException("closeMethodName == null");
		this.closeMethodName = closeMethodName;
	}

	public void close() {
		closeMethodName = null;
	}

	public void warnIfOpen() {
		if (closeMethodName != null)
			Log.w(TAG, "A resource was acquired but never released; see " + closeMethodName);
	}
}
