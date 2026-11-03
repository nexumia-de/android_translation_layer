package android.app;

import android.atl.ATLLoadedApp;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Slog;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Alarms run on the clocks Android uses: ELAPSED_REALTIME* on the boot clock
 * (which counts suspend), RTC* on the wall clock. The phone is not woken for
 * *_WAKEUP alarms by the app: the system wakes it in fixed windows
 * (docs/decisions/wake-windows.md), and due alarms run within a few seconds
 * of each wake-up, each under a partial wake lock. The next *_WAKEUP alarm is
 * published for the system's wake broker.
 */
public class AlarmManager {
	private static final String TAG = "AlarmManager";

	public static final int RTC_WAKEUP = 0;
	public static final int RTC = 1;
	public static final int ELAPSED_REALTIME_WAKEUP = 2;
	public static final int ELAPSED_REALTIME = 3;
	public static final long INTERVAL_FIFTEEN_MINUTES = 15 * 60 * 1000;
	public static final long INTERVAL_HALF_HOUR = 2 * INTERVAL_FIFTEEN_MINUTES;
	public static final long INTERVAL_HOUR = 2 * INTERVAL_HALF_HOUR;
	public static final long INTERVAL_HALF_DAY = 12 * INTERVAL_HOUR;
	public static final long INTERVAL_DAY = 2 * INTERVAL_HALF_DAY;
	public static final String ACTION_NEXT_ALARM_CLOCK_CHANGED = "android.app.action.NEXT_ALARM_CLOCK_CHANGED";
	public static final String ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED";

	/* the scheduler wakes at least this often, so alarms that fell due during
	 * suspend (Object.wait's clock stops there) run soon after the wake-up */
	private static final long MAX_WAIT_MS = 5000;
	/* longest hold of the phone for one alarm's delivery */
	private static final long DELIVERY_WAKE_LOCK_MS = 10000;

	private static class Alarm {
		int type;
		long trigger; /* in the clock of type */
		long interval; /* 0: one-shot */
		PendingIntent operation;
		OnAlarmListener listener;
		Handler handler;
		AlarmClockInfo alarmClock;
	}

	private static final Object lock = new Object();
	private static final List<Alarm> alarms = new ArrayList<>();
	private static Thread scheduler;
	private static PowerManager.WakeLock deliveryWakeLock;
	private static long publishedWakeup = -1;

	private static boolean elapsed(int type) {
		return type == ELAPSED_REALTIME || type == ELAPSED_REALTIME_WAKEUP;
	}

	private static boolean wakeup(int type) {
		return type == RTC_WAKEUP || type == ELAPSED_REALTIME_WAKEUP;
	}

	private static long now(int type) {
		return elapsed(type) ? SystemClock.elapsedRealtime() : System.currentTimeMillis();
	}

	/* --- scheduling --- */

	private static void schedule(Alarm a) {
		synchronized (lock) {
			removeLocked(a.operation, a.listener);
			alarms.add(a);
			if (scheduler == null) {
				scheduler = new Thread(new Runnable() {
					@Override
					public void run() {
						loop();
					}
				}, "AlarmManager");
				scheduler.setDaemon(true);
				scheduler.start();
			}
			lock.notifyAll();
		}
		Slog.i(TAG, "scheduled " + describe(a));
	}

	private static boolean removeLocked(PendingIntent operation, OnAlarmListener listener) {
		boolean removed = false;
		for (int i = alarms.size() - 1; i >= 0; i--) {
			Alarm a = alarms.get(i);
			if ((operation != null && operation.equals(a.operation)) || (listener != null && a.listener == listener)) {
				alarms.remove(i);
				removed = true;
			}
		}
		return removed;
	}

	private static void loop() {
		while (true) {
			List<Alarm> due = new ArrayList<>();
			synchronized (lock) {
				long wait = MAX_WAIT_MS;
				for (int i = alarms.size() - 1; i >= 0; i--) {
					Alarm a = alarms.get(i);
					long delay = a.trigger - now(a.type);
					if (delay > 0) {
						wait = Math.min(wait, delay);
						continue;
					}
					due.add(a);
					if (a.interval > 0) {
						long t = now(a.type);
						while (a.trigger <= t) /* missed repeats are not delivered again, as on Android */
							a.trigger += a.interval;
						wait = Math.min(wait, a.trigger - t);
					} else {
						alarms.remove(i);
					}
				}
				publishNextWakeupLocked();
				if (due.isEmpty()) {
					try {
						lock.wait(Math.max(1, wait));
					} catch (InterruptedException e) {
					}
					continue;
				}
			}
			for (Alarm a : due)
				deliver(a);
		}
	}

	private static void deliver(final Alarm a) {
		final PowerManager.WakeLock wakeLock = deliveryWakeLock();
		wakeLock.acquire(DELIVERY_WAKE_LOCK_MS);
		Runnable run = new Runnable() {
			@Override
			public void run() {
				try {
					Slog.i(TAG, "delivering " + describe(a));
					if (a.listener != null)
						a.listener.onAlarm();
					else
						a.operation.send();
				} catch (RuntimeException e) {
					Slog.w(TAG, "alarm delivery failed", e);
				} finally {
					wakeLock.release();
				}
			}
		};
		Handler handler = a.handler != null ? a.handler : new Handler(Looper.getMainLooper());
		if (!handler.post(run))
			wakeLock.release();
	}

	private static PowerManager.WakeLock deliveryWakeLock() {
		synchronized (lock) {
			if (deliveryWakeLock == null)
				deliveryWakeLock = new PowerManager().newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlarmManager delivery");
			return deliveryWakeLock;
		}
	}

	/* --- next wake-up for the system's wake broker --- */

	private static File wakeupFile() {
		String runtime = System.getenv("XDG_RUNTIME_DIR");
		if (runtime == null)
			return null;
		String flatpakId = System.getenv("FLATPAK_ID");
		if (flatpakId != null)
			return new File(runtime + "/app/" + flatpakId + "/atl-next-wakeup");
		try {
			return new File(runtime + "/atl-wake/" + ATLLoadedApp.getPrimaryApplication().pkg.packageName);
		} catch (RuntimeException e) {
			return null;
		}
	}

	/* wall-clock milliseconds of the earliest *_WAKEUP alarm, or nothing */
	private static void publishNextWakeupLocked() {
		long next = Long.MAX_VALUE;
		for (Alarm a : alarms) {
			if (!wakeup(a.type))
				continue;
			long wall = elapsed(a.type) ? System.currentTimeMillis() + (a.trigger - SystemClock.elapsedRealtime()) : a.trigger;
			next = Math.min(next, wall);
		}
		if (next == publishedWakeup)
			return;
		publishedWakeup = next;
		File file = wakeupFile();
		if (file == null)
			return;
		try {
			if (next == Long.MAX_VALUE) {
				file.delete();
			} else {
				file.getParentFile().mkdirs();
				File tmp = new File(file.getPath() + ".tmp");
				try (FileOutputStream out = new FileOutputStream(tmp)) {
					out.write((next / 1000 + "\n").getBytes());
				}
				tmp.renameTo(file);
			}
		} catch (Exception e) {
			Slog.w(TAG, "cannot publish the next wake-up: " + e);
		}
	}

	private static String describe(Alarm a) {
		return "alarm type " + a.type + " in " + (a.trigger - now(a.type)) + " ms" + (a.interval > 0 ? " every " + a.interval + " ms" : "") + ": " + (a.listener != null ? a.listener : a.operation);
	}

	private static Alarm alarm(int type, long trigger, long interval, PendingIntent operation) {
		Alarm a = new Alarm();
		a.type = type;
		a.trigger = trigger;
		a.interval = interval;
		a.operation = operation;
		return a;
	}

	private static Alarm alarm(int type, long trigger, OnAlarmListener listener, Handler handler) {
		Alarm a = new Alarm();
		a.type = type;
		a.trigger = trigger;
		a.listener = listener;
		a.handler = handler;
		return a;
	}

	/* --- API --- */

	public void set(int type, long triggerAtMillis, PendingIntent operation) {
		schedule(alarm(type, triggerAtMillis, 0, operation));
	}

	public void set(int type, long triggerAtMillis, String tag, OnAlarmListener listener, Handler targetHandler) {
		schedule(alarm(type, triggerAtMillis, listener, targetHandler));
	}

	public void setExact(int type, long triggerAtMillis, PendingIntent operation) {
		set(type, triggerAtMillis, operation);
	}

	public void setExact(int type, long triggerAtMillis, String tag, OnAlarmListener listener, Handler targetHandler) {
		set(type, triggerAtMillis, tag, listener, targetHandler);
	}

	public void setWindow(int type, long windowStartMillis, long windowLengthMillis, PendingIntent operation) {
		set(type, windowStartMillis, operation);
	}

	public void setWindow(int type, long windowStartMillis, long windowLengthMillis, String tag, OnAlarmListener listener, Handler targetHandler) {
		set(type, windowStartMillis, tag, listener, targetHandler);
	}

	public void setAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
		set(type, triggerAtMillis, operation);
	}

	public void setExactAndAllowWhileIdle(int type, long triggerAtMillis, PendingIntent operation) {
		set(type, triggerAtMillis, operation);
	}

	public void setRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
		schedule(alarm(type, triggerAtMillis, Math.max(intervalMillis, 60000), operation));
	}

	public void setInexactRepeating(int type, long triggerAtMillis, long intervalMillis, PendingIntent operation) {
		setRepeating(type, triggerAtMillis, intervalMillis, operation);
	}

	public void setAlarmClock(AlarmClockInfo info, PendingIntent operation) {
		Alarm a = alarm(RTC_WAKEUP, info.getTriggerTime(), 0, operation);
		a.alarmClock = info;
		schedule(a);
	}

	public AlarmClockInfo getNextAlarmClock() {
		AlarmClockInfo next = null;
		synchronized (lock) {
			for (Alarm a : alarms)
				if (a.alarmClock != null && (next == null || a.alarmClock.getTriggerTime() < next.getTriggerTime()))
					next = a.alarmClock;
		}
		return next;
	}

	public void cancel(PendingIntent operation) {
		synchronized (lock) {
			removeLocked(operation, null);
			lock.notifyAll();
		}
	}

	public void cancel(OnAlarmListener listener) {
		synchronized (lock) {
			removeLocked(null, listener);
			lock.notifyAll();
		}
	}

	public void cancelAll() {
		synchronized (lock) {
			alarms.clear();
			lock.notifyAll();
		}
	}

	public boolean canScheduleExactAlarms() {
		return true;
	}

	public static class AlarmClockInfo implements Parcelable {
		private long mTriggerTime;
		private PendingIntent mPendingIntent;
		public AlarmClockInfo(long triggerTime, PendingIntent showIntent) {
			mTriggerTime = triggerTime;
			mPendingIntent = showIntent;
		}

		public long getTriggerTime() {
			return mTriggerTime;
		}

		public PendingIntent getShowIntent() {
			return mPendingIntent;
		}
	}

	public static interface OnAlarmListener {
		void onAlarm();
	}
}
