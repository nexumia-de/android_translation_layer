package android.os;

public final class PowerManager {
	public static final int PARTIAL_WAKE_LOCK = 0x00000001;
	public static final int SCREEN_DIM_WAKE_LOCK = 0x00000006;
	public static final int SCREEN_BRIGHT_WAKE_LOCK = 0x0000000a;
	public static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 0x00000020;
	public static final int ACQUIRE_CAUSES_WAKEUP = 0x10000000;
	public static final int ON_AFTER_RELEASE = 0x20000000;
	public static final int RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY = 1;
	private static final int WAKE_LOCK_LEVEL_MASK = 0x0000ffff;

	/* inhibit flags of GtkApplicationInhibitFlags */
	private static final int INHIBIT_SUSPEND = 4;
	private static final int INHIBIT_IDLE = 8;

	/*
	 * A held partial wake lock inhibits suspend, a screen wake lock also
	 * the idle screen blank (GtkApplication inhibit: gnome-session, or the
	 * Inhibit portal in a Flatpak). The process holds at most one inhibit
	 * per kind, while any lock of that kind is held.
	 */
	private static int heldPartial;
	private static int heldScreen;

	private static void changeHeld(int level, int delta) {
		synchronized (PowerManager.class) {
			if (level == PARTIAL_WAKE_LOCK) {
				heldPartial += delta;
				if (heldPartial == (delta > 0 ? 1 : 0))
					nativeInhibit(INHIBIT_SUSPEND, delta > 0, "Android app holds a wake lock");
			} else if (level == SCREEN_DIM_WAKE_LOCK || level == SCREEN_BRIGHT_WAKE_LOCK || level == FULL_WAKE_LOCK) {
				heldScreen += delta;
				if (heldScreen == (delta > 0 ? 1 : 0))
					nativeInhibit(INHIBIT_SUSPEND | INHIBIT_IDLE, delta > 0, "Android app keeps the screen on");
			}
		}
	}

	public final class WakeLock {
		private final int level;
		private final String tag;
		private boolean held;
		private boolean refCounted = true;
		private int count;
		private Thread timeout;

		WakeLock(int level, String tag) {
			this.level = level;
			this.tag = tag;
		}

		public void setReferenceCounted(boolean referenceCounted) {
			refCounted = referenceCounted;
		}

		public void acquire() {
			synchronized (this) {
				if (refCounted)
					count++;
				if (!held) {
					held = true;
					changeHeld(level, 1);
				}
			}
		}

		public void release() {
			release(0);
		}

		public void release(int flags) {
			synchronized (this) {
				if (refCounted) {
					if (count > 0)
						count--;
					if (count > 0)
						return;
				}
				if (held) {
					held = false;
					changeHeld(level, -1);
				}
			}
		}

		public boolean isHeld() {
			return held;
		}

		public void acquire(final long timeout) {
			acquire();
			Thread t = new Thread(new Runnable() {
				public void run() {
					try {
						Thread.sleep(timeout);
					} catch (InterruptedException e) {
						return;
					}
					release();
				}
			}, "WakeLock timeout " + tag);
			t.setDaemon(true);
			t.start();
		}

		public void setWorkSource(WorkSource ws) {}

		@Override
		public String toString() {
			return "WakeLock{" + tag + " level=" + level + " held=" + held + "}";
		}
	}

	public WakeLock newWakeLock(int levelAndFlags, String tag) {
		return new WakeLock(levelAndFlags & WAKE_LOCK_LEVEL_MASK, tag);
	}

	public void goToSleep(long time) {
		// This requires DEVICE_POWER permission which is only possible for system processes. Any normal application gets a SecurityException.
		throw new SecurityException();
	}

	public void userActivity(long dummy, boolean dummy2) {}

	public static final int FULL_WAKE_LOCK = 0x1a;

	public boolean isPowerSaveMode() {
		return false;
	}

	private static native boolean nativeIsScreenOn();
	private static native void nativeInhibit(int flags, boolean inhibit, String reason);

	private static boolean screenOn = true;
	private static long screenOnChecked;

	/* asked at most once a second; apps call this for every event */
	public boolean isScreenOn() {
		long now = SystemClock.uptimeMillis();
		synchronized (PowerManager.class) {
			if (screenOnChecked == 0 || now - screenOnChecked > 1000) {
				screenOn = nativeIsScreenOn();
				screenOnChecked = now;
			}
			return screenOn;
		}
	}

	public boolean isIgnoringBatteryOptimizations(String packageName) {
		return true;
	}

	public boolean isDeviceIdleMode() {
		return false;
	}

	public boolean isInteractive() {
		return isScreenOn();
	}

	public boolean isWakeLockLevelSupported(int level) {
		return level == PARTIAL_WAKE_LOCK || level == SCREEN_DIM_WAKE_LOCK || level == SCREEN_BRIGHT_WAKE_LOCK || level == FULL_WAKE_LOCK;
	}
}
