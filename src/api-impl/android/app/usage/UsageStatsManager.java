package android.app.usage;

public class UsageStatsManager {
	public static final int STANDBY_BUCKET_ACTIVE = 10;

	/* the app is in use, so it is not restricted */
	public int getAppStandbyBucket() {
		return STANDBY_BUCKET_ACTIVE;
	}
}
