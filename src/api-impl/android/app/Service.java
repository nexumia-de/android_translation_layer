package android.app;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.IBinder;

public abstract class Service extends ContextWrapper {

	public static final int START_STICKY_COMPATIBILITY = 0;
	public static final int START_STICKY = 1;
	public static final int START_NOT_STICKY = 2;
	public static final int START_REDELIVER_INTENT = 3;
	public static final int STOP_FOREGROUND_REMOVE = 1;
	public static final int STOP_FOREGROUND_DETACH = 2;

	private int notification_id;
	private boolean foreground;
	private int last_start_id;

	public Service() {
		super(null);
	}

	public void onCreate() {
		System.out.println("Service.onCreate() called");
	}

	public void onDestroy() {
		System.out.println("Service.onDestroy() called");
	}

	public abstract IBinder onBind(Intent intent);

	public int onStartCommand(Intent intent, int flags, int startId) {
		System.out.println("Service.onStartCommand(" + intent + ", " + flags + ", " + startId + ") called");
		return 0;
	}

	/* A foreground service keeps the process running after its last window
	 * closed, like on Android, where only background services are stopped. */
	public void startForeground(int id, Notification notification) {
		if (notification_id != 0 && notification_id != id)
			new NotificationManager().cancel(notification_id);
		this.notification_id = id;
		if (notification != null) {
			notification.ongoing = true;
			new NotificationManager().notify(id, notification);
		}
		if (!foreground) {
			foreground = true;
			nativeHold(true);
		}
	}

	public void startForeground(int id, Notification notification, int foregroundServiceType) {
		startForeground(id, notification);
	}

	public void stopForeground(boolean remove) {
		stopForeground(remove ? STOP_FOREGROUND_REMOVE : STOP_FOREGROUND_DETACH);
	}

	public void stopForeground(int flags) {
		if ((flags & STOP_FOREGROUND_REMOVE) != 0 && notification_id != 0)
			new NotificationManager().cancel(notification_id);
		if (foreground) {
			foreground = false;
			nativeHold(false);
		}
	}

	public final int getForegroundServiceType() {
		return 0;
	}

	/** @hide */
	public int atl_next_start_id() {
		return ++last_start_id;
	}

	/** @hide called when the service is stopped, before onDestroy */
	public void atl_stopped() {
		stopForeground(STOP_FOREGROUND_REMOVE);
	}

	public Application getApplication() {
		return this.get_atl_loaded_app().getApplication();
	}

	public void stopSelf(int startId) {
		stopSelfResult(startId);
	}

	public void stopSelf() {
		stopSelfResult(-1);
	}

	/* stops only if startId is the latest start request (or -1), as on Android */
	public boolean stopSelfResult(int startId) {
		if (startId >= 0 && startId != last_start_id)
			return false;
		get_atl_loaded_app().stopService(this);
		return true;
	}

	public void attachBaseContext(Context newBase) {
		super.attachBaseContext(newBase);
		System.out.println("Service.attachBaseContext(" + newBase + ") called");
	}

	private static native void nativeHold(boolean hold);
}
