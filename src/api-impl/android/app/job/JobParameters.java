package android.app.job;

import android.net.Network;
import android.net.Uri;
import android.os.Parcelable;
import android.os.PersistableBundle;

public class JobParameters implements Parcelable {

	public static final Creator<JobParameters> CREATOR = null;

	JobInfo jobInfo;

	JobParameters(JobInfo jobInfo) {
		this.jobInfo = jobInfo;
	}

	public static final int STOP_REASON_UNDEFINED = 0;

	public int getJobId() {
		return jobInfo.getId();
	}

	public int getStopReason() {
		return STOP_REASON_UNDEFINED;
	}

	/* JobScheduler.enqueue does not keep work items, so there is never work to hand out */
	public JobWorkItem dequeueWork() {
		return null;
	}

	public void completeWork(JobWorkItem work) {}

	public PersistableBundle getExtras() {
		return jobInfo.getExtras();
	}

	public Uri[] getTriggeredContentUris() {
		return new Uri[0];
	}

	public String[] getTriggeredContentAuthorities() {
		return new String[0];
	}

	/* jobs run on the active network */
	public Network getNetwork() {
		return new Network();
	}
}
