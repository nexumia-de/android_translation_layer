package android.app.job;

import android.app.Service;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;

public abstract class JobService extends Service {
	private static final String TAG = "JobService";

	public abstract boolean onStartJob(JobParameters params);

	public abstract boolean onStopJob(JobParameters params);

	public void jobFinished(JobParameters params, boolean needsReschedule) {
		Slog.i(TAG, "jobFinished(" + params + ", " + needsReschedule + ") called");
		params.jobInfo.running = false;
		if (!needsReschedule)
			params.jobInfo.failedAttempts = 0;
		if (needsReschedule || params.jobInfo.periodicMillis != 0) {
			long delay = needsReschedule ? params.jobInfo.nextBackoffMillis() : params.jobInfo.periodicMillis;
			new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
				@Override
				public void run() {
					if (JobScheduler.pendingJobs.get(params.jobInfo.getId()) != params.jobInfo)
						return; // cancelled meanwhile
					params.jobInfo.running = true;
					boolean result = onStartJob(params);
					Slog.i(TAG, "onStartJob() returned " + result);
				}
			}, delay);
		} else {
			JobScheduler.pendingJobs.remove(params.jobInfo.getId());
		}
	}
}
