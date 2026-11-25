package android.os;

/* ADPF hint sessions (API 31): there is no performance hint service, which Android reports with
 * createHintSession returning null and a preferred update rate of -1. Apps whose minSdk is 31 or
 * higher get this service without checking the SDK version. */
public final class PerformanceHintManager {

	public PerformanceHintManager() {}

	public Session createHintSession(int[] tids, long initialTargetWorkDurationNanos) {
		return null;
	}

	public long getPreferredUpdateRateNanos() {
		return -1;
	}

	public static class Session implements java.io.Closeable {
		public void updateTargetWorkDuration(long targetDurationNanos) {}
		public void reportActualWorkDuration(long actualDurationNanos) {}
		public void setThreads(int[] tids) {}
		public void setPreferPowerEfficiency(boolean enabled) {}
		public void close() {}
	}
}
