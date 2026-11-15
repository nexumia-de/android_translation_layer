package android.app.job;

import android.content.ComponentName;
import android.net.Uri;
import android.os.PersistableBundle;

public class JobInfo {
	public static final int NETWORK_TYPE_NONE = 0;
	public static final int NETWORK_TYPE_ANY = 1;
	public static final int NETWORK_TYPE_UNMETERED = 2;
	public static final int NETWORK_TYPE_NOT_ROAMING = 3;
	public static final int NETWORK_TYPE_CELLULAR = 4;

	public static final int BACKOFF_POLICY_LINEAR = 0;
	public static final int BACKOFF_POLICY_EXPONENTIAL = 1;
	public static final long DEFAULT_INITIAL_BACKOFF_MILLIS = 30000L;
	public static final long MAX_BACKOFF_DELAY_MILLIS = 5 * 60 * 60 * 1000L;
	private static final long MIN_BACKOFF_MILLIS = 10000L;

	private ComponentName service;
	long initialBackoffMillis = DEFAULT_INITIAL_BACKOFF_MILLIS;
	int backoffPolicy = BACKOFF_POLICY_EXPONENTIAL;
	int failedAttempts; // reschedules in a row, for the backoff
	private PersistableBundle extras;
	long periodicMillis;
	private int id;
	boolean running;
	long minLatencyMillis;
	int networkType;

	public JobInfo() {}

	public ComponentName getService() {
		return service;
	}

	public PersistableBundle getExtras() {
		return extras;
	}

	public int getId() {
		return id;
	}

	public long getInitialBackoffMillis() {
		return initialBackoffMillis;
	}

	public int getBackoffPolicy() {
		return backoffPolicy;
	}

	/* the delay before the next try of a job that asked to be rescheduled, as Android's
	 * JobSchedulerService computes it: linear or exponential in the number of tries, at most 5 h */
	long nextBackoffMillis() {
		failedAttempts++;
		long initial = Math.max(initialBackoffMillis, MIN_BACKOFF_MILLIS);
		long delay;
		if (backoffPolicy == BACKOFF_POLICY_LINEAR)
			delay = initial * failedAttempts;
		else
			delay = initial << Math.min(failedAttempts - 1, 20);
		return Math.min(delay, MAX_BACKOFF_DELAY_MILLIS);
	}

	public static long getMinPeriodMillis() {
		return 1000;
	}

	public String toString() {
		return "JobInfo{"
		     + "jobService=" + service
		     + ", initialBackoffMillis=" + initialBackoffMillis
		     + ", backoffPolicy=" + backoffPolicy
		     + ", extras=" + extras
		     + ", periodicMillis=" + periodicMillis
		     + ", minLatencyMillis=" + minLatencyMillis
		     + ", networkType=" + networkType
		     + ", id=" + id
		     + '}';
	}

	public static final class Builder {

		private JobInfo jobInfo;

		public Builder(int jobId, ComponentName jobService) {
			jobInfo = new JobInfo();
			jobInfo.id = jobId;
			jobInfo.service = jobService;
		}

		public Builder setBackoffCriteria(long initialBackoffMillis, int backoffPolicy) {
			jobInfo.initialBackoffMillis = initialBackoffMillis;
			jobInfo.backoffPolicy = backoffPolicy;
			return this;
		}

		public Builder setExtras(PersistableBundle extras) {
			jobInfo.extras = extras;
			return this;
		}

		public Builder setMinimumLatency(long minLatencyMillis) {
			jobInfo.minLatencyMillis = minLatencyMillis;
			return this;
		}

		public Builder setOverrideDeadline(long a) {
			return this;
		}

		public Builder setPeriodic(long dummy) {
			jobInfo.periodicMillis = dummy;
			return this;
		}

		public Builder setPersisted(boolean persisted) {
			return this;
		}

		/* only "some network" is checked when the job runs, not the request's capabilities */
		public Builder setRequiredNetwork(android.net.NetworkRequest networkRequest) {
			jobInfo.networkType = networkRequest != null ? NETWORK_TYPE_ANY : NETWORK_TYPE_NONE;
			return this;
		}

		public Builder setTraceTag(String traceTag) {
			return this;
		}

		public Builder setTriggerContentUpdateDelay(long durationMs) {
			return this;
		}

		public Builder setTriggerContentMaxDelay(long durationMs) {
			return this;
		}

		public Builder setRequiredNetworkType(int networkType) {
			jobInfo.networkType = networkType;
			return this;
		}

		public Builder setRequiresCharging(boolean requires_charging) {
			return this;
		}

		public Builder setRequiresDeviceIdle(boolean requires_device_idle) {
			return this;
		}

		public Builder setRequiresBatteryNotLow(boolean requires_battery_not_low) {
			return this;
		}

		public Builder setRequiresStorageNotLow(boolean requires_storage_not_low) {
			return this;
		}

		public Builder addTriggerContentUri(TriggerContentUri triggerContentUri) {
			return this;
		}

		public Builder setPeriodic(long intervalMillis, long flexMillis) {
			return setPeriodic(intervalMillis);
		}

		public Builder setImportantWhileForeground(boolean importantWhileForeground) {
			return this;
		}

		public Builder setPrefetch(boolean prefetch) {
			return this;
		}

		public Builder setExpedited(boolean expedited) {
			return this;
		}

		public JobInfo build() {
			return jobInfo;
		}
	}

	public static class TriggerContentUri {
		public TriggerContentUri(Uri uri, int flags) {}
	}
}
