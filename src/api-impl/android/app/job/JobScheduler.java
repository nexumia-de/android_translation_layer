package android.app.job;

import android.atl.ATLLoadedApp;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;
import java.util.*;

public class JobScheduler {
	private static final String TAG = "JobScheduler";

	static Map<Integer, JobInfo> pendingJobs = new HashMap<>();
	private static IdentityHashMap<Class<? extends JobService>, JobService> runningServices = new IdentityHashMap<>();

	private final Context context;

	public JobScheduler(Context context) {
		this.context = context;
	}

	/**
	 * Retrieve all jobs that have been scheduled by the calling application.
	 *
	 * @return a list of all of the app's scheduled jobs.  This includes jobs that are
	 *     currently started as well as those that are still waiting to run.
	 */
	public List<JobInfo> getAllPendingJobs() {
		return new ArrayList<>(pendingJobs.values());
	};

	public JobInfo getPendingJob(int jobId) {
		return pendingJobs.get(jobId);
	}

	/* namespaces (API 34) only separate job ids; the app's jobs share one map here */
	public JobScheduler forNamespace(String namespace) {
		return this;
	}

	public void cancelAll() {
		for (Integer id : new ArrayList<>(pendingJobs.keySet()))
			cancel(id);
	}

	public int enqueue(JobInfo job, JobWorkItem work) {
		return 1; //RESULT_SUCCESS
	}

	public int schedule(JobInfo job) {
		Slog.i(TAG, "JobScheduler.schedule() called with job: " + job);
		if (pendingJobs.containsKey(job.getId()))
			return 1; //RESULT_SUCCESS
		pendingJobs.put(job.getId(), job);
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				if (pendingJobs.get(job.getId()) != job)
					return; // cancelled meanwhile
				/* the network a job needs, such as an app store's updates on Wi-Fi only; other
				 * constraints (idle, charging) are not checked */
				if (!networkSatisfied(job)) {
					Slog.i(TAG, "job " + job.getId() + " waits for its network");
					new Handler(Looper.getMainLooper()).postDelayed(this, NETWORK_RECHECK_MILLIS);
					return;
				}
				try {
					String className = job.getService().getClassName();
					Class<? extends JobService> cls = context.get_atl_loaded_app()
					                                      .loadClass(className)
					                                      .asSubclass(JobService.class);
					if (!runningServices.containsKey(cls)) {
						JobService service = cls.getConstructor().newInstance();
						ATLLoadedApp atlLoadedApp = context.get_atl_loaded_app();
						service.attachBaseContext(atlLoadedApp.createContext(null, null,
						                                                     atlLoadedApp.pkg.applicationInfo.theme));
						service.onCreate();
						runningServices.put(cls, service);
					}
					Slog.i(TAG, "starting job " + job.getId());
					job.running = true;
					boolean result = runningServices.get(cls).onStartJob(new JobParameters(job));
					Slog.i(TAG, "onStartJob() returned " + result);
				} catch (ReflectiveOperationException e) {
					e.printStackTrace();
				}
			}
		}, job.minLatencyMillis);
		return 1; //RESULT_SUCCESS
	}

	private static final long NETWORK_RECHECK_MILLIS = 60 * 1000;

	private boolean networkSatisfied(JobInfo job) {
		if (job.networkType == JobInfo.NETWORK_TYPE_NONE)
			return true;
		ConnectivityManager connectivity = new ConnectivityManager();
		NetworkInfo network = connectivity.getActiveNetworkInfo();
		if (network == null || !network.isConnected())
			return false;
		return job.networkType != JobInfo.NETWORK_TYPE_UNMETERED || !connectivity.isActiveNetworkMetered();
	}

	public void cancel(int id) {
		JobInfo job = pendingJobs.remove(id);
		if (job != null && job.running) {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					JobService service = runningServices.get(job.getService().getClass());
					if (service != null) {
						JobParameters params = new JobParameters(job);
						service.onStopJob(params);
						job.running = false;
					}
				}
			});
		}
	}
}
