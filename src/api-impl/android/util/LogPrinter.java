package android.util;

/**
 * Implementation of a {@link android.util.Printer} that sends its output
 * to the system log.
 */
public class LogPrinter implements Printer {
	private final int mPriority;
	private final String mTag;

	public LogPrinter(int priority, String tag) {
		mPriority = priority;
		mTag = tag;
	}

	public void println(String x) {
		Log.println(mPriority, mTag, x);
	}
}
