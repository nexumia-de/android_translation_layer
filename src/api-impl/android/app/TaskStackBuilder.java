package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.util.ArrayList;

/* without the manifest's parent activities: an intent is added alone, as for an activity
 * that declares no parent */
public class TaskStackBuilder {
	private final ArrayList<Intent> intents = new ArrayList<>();
	private final Context context;

	private TaskStackBuilder(Context context) {
		this.context = context;
	}

	public static TaskStackBuilder create(Context context) {
		return new TaskStackBuilder(context);
	}

	public TaskStackBuilder addNextIntent(Intent nextIntent) {
		intents.add(nextIntent);
		return this;
	}

	public TaskStackBuilder addNextIntentWithParentStack(Intent nextIntent) {
		return addNextIntent(nextIntent);
	}

	public TaskStackBuilder addParentStack(Activity sourceActivity) {
		return this;
	}

	public TaskStackBuilder addParentStack(Class<?> sourceActivityClass) {
		return this;
	}

	public TaskStackBuilder addParentStack(ComponentName sourceActivityName) {
		return this;
	}

	public int getIntentCount() {
		return intents.size();
	}

	public Intent editIntentAt(int index) {
		return intents.get(index);
	}

	public Intent[] getIntents() {
		return intents.toArray(new Intent[0]);
	}

	public void startActivities() {
		startActivities(null);
	}

	public void startActivities(Bundle options) {
		if (intents.isEmpty())
			throw new IllegalStateException("No intents added to TaskStackBuilder; cannot startActivities");
		context.startActivities(getIntents(), options);
	}

	public PendingIntent getPendingIntent(int requestCode, int flags) {
		return getPendingIntent(requestCode, flags, null);
	}

	public PendingIntent getPendingIntent(int requestCode, int flags, Bundle options) {
		if (intents.isEmpty())
			throw new IllegalStateException("No intents added to TaskStackBuilder; cannot getPendingIntent");
		/* the top of the stack is what the user sees */
		return PendingIntent.getActivity(context, requestCode, intents.get(intents.size() - 1), flags);
	}
}
