package android.app.job;

import android.content.Intent;

final public class JobWorkItem {
	private final Intent intent;

	public JobWorkItem(Intent intent) {
		this.intent = intent;
	}

	public Intent getIntent() {
		return intent;
	}
}
