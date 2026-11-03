package android.service.notification;

import android.app.Notification;
import android.app.Service;
import android.atl.annotation.Export;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Slog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifications of the desktop, as Android's notification listener sees
 * those of other apps.
 *
 * The desktop's notifications are calls of org.freedesktop.Notifications.Notify
 * on the session bus; ATL watches them with a monitor connection
 * (org.freedesktop.DBus.Monitoring.BecomeMonitor, allowed for the user's own
 * bus). As this hands every notification of the user to the app, it only runs
 * when ATL_ENABLE_NOTIFICATION_LISTENER is set, the equivalent of Android's
 * notification access setting.
 */
public abstract class NotificationListenerService extends Service {
	private static final String TAG = "NotificationListener";
	public static final String SERVICE_INTERFACE = "android.service.notification.NotificationListenerService";

	public static final int REASON_CLICK = 1;
	public static final int REASON_CANCEL = 2;
	public static final int REASON_APP_CANCEL = 8;
	public static final int INTERRUPTION_FILTER_ALL = 1;
	public static final int HINT_HOST_DISABLE_EFFECTS = 1;

	private static native boolean nativeStartMonitor();

	private static final List<NotificationListenerService> listeners = new ArrayList<>();
	/* key -> notification, oldest first */
	private static final Map<String, StatusBarNotification> active = new LinkedHashMap<>();
	/* the desktop's notification id -> key */
	private static final Map<Integer, String> keysById = new java.util.HashMap<>();
	/* desktop apps that sent a notification: package name (desktop entry) -> application name */
	private static final Map<String, String> desktopSources = new java.util.HashMap<>();

	/**
	 * ATL-specific: an ApplicationInfo for a desktop app that sent a notification, so apps can
	 * show its name (PackageManager.getApplicationLabel) as for an Android app. Only sources
	 * seen here are answered, so package queries for other names still fail.
	 */
	public static android.content.pm.ApplicationInfo desktopApplicationInfo(String packageName) {
		String label;
		synchronized (desktopSources) {
			label = desktopSources.get(packageName);
		}
		if (label == null)
			return null;
		android.content.pm.ApplicationInfo info = new android.content.pm.ApplicationInfo();
		info.packageName = packageName;
		info.processName = packageName;
		info.nonLocalizedLabel = label;
		info.enabled = true;
		return info;
	}
	private static boolean monitoring;
	private static int nextId = 1;
	private static Handler mainHandler;

	public static boolean isEnabled() {
		return System.getenv("ATL_ENABLE_NOTIFICATION_LISTENER") != null;
	}

	public static class Ranking {
		private String key;

		public String getKey() {
			return key;
		}

		public int getRank() {
			return 0;
		}

		public boolean isAmbient() {
			return false;
		}

		public boolean matchesInterruptionFilter() {
			return true;
		}

		public int getImportance() {
			return 3;
		}

		public boolean canShowBadge() {
			return true;
		}
	}

	public static class RankingMap {
		public String[] getOrderedKeys() {
			synchronized (active) {
				return active.keySet().toArray(new String[0]);
			}
		}

		public boolean getRanking(String key, Ranking outRanking) {
			return false;
		}
	}

	private final class ListenerBinder extends Binder {
		NotificationListenerService service() {
			return NotificationListenerService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new ListenerBinder();
	}

	public void onListenerConnected() {}

	public void onListenerDisconnected() {}

	public void onNotificationPosted(StatusBarNotification sbn) {}

	public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
		onNotificationPosted(sbn);
	}

	public void onNotificationRemoved(StatusBarNotification sbn) {}

	public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
		onNotificationRemoved(sbn);
	}

	public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason) {
		onNotificationRemoved(sbn, rankingMap);
	}

	public void onNotificationRankingUpdate(RankingMap rankingMap) {}

	public void onInterruptionFilterChanged(int interruptionFilter) {}

	public void onListenerHintsChanged(int hints) {}

	public StatusBarNotification[] getActiveNotifications() {
		synchronized (active) {
			return active.values().toArray(new StatusBarNotification[0]);
		}
	}

	public StatusBarNotification[] getActiveNotifications(String[] keys) {
		List<StatusBarNotification> result = new ArrayList<>();
		synchronized (active) {
			for (String key : keys)
				if (active.containsKey(key))
					result.add(active.get(key));
		}
		return result.toArray(new StatusBarNotification[0]);
	}

	public RankingMap getCurrentRanking() {
		return new RankingMap();
	}

	public final int getCurrentInterruptionFilter() {
		return INTERRUPTION_FILTER_ALL;
	}

	public final int getCurrentListenerHints() {
		return 0;
	}

	public final void requestInterruptionFilter(int interruptionFilter) {}

	public final void requestListenerHints(int hints) {}

	/* forgets it here; the desktop keeps showing it */
	public final void cancelNotification(String key) {
		StatusBarNotification sbn;
		synchronized (active) {
			sbn = active.remove(key);
		}
		if (sbn != null)
			dispatchRemoved(sbn);
	}

	public final void cancelNotifications(String[] keys) {
		for (String key : keys)
			cancelNotification(key);
	}

	public final void cancelAllNotifications() {
		for (StatusBarNotification sbn : getActiveNotifications())
			cancelNotification(sbn.getKey());
	}

	public final void snoozeNotification(String key, long durationMs) {
		cancelNotification(key);
	}

	public final void requestUnbind() {
		synchronized (listeners) {
			listeners.remove(this);
		}
		onListenerDisconnected();
	}

	public static void requestRebind(ComponentName componentName) {
		Slog.i(TAG, "requestRebind " + componentName);
	}

	/** ATL internal: a listener service the app has, now bound; starts watching the desktop's notifications */
	public static void bound(IBinder binder) {
		if (!(binder instanceof ListenerBinder))
			return;
		final NotificationListenerService service = ((ListenerBinder)binder).service();
		synchronized (listeners) {
			if (listeners.contains(service))
				return;
			listeners.add(service);
			if (!monitoring) {
				try {
					monitoring = nativeStartMonitor();
				} catch (UnsatisfiedLinkError e) {
					Slog.w(TAG, "no notification monitor: " + e);
				}
				Slog.i(TAG, monitoring ? "watching the desktop's notifications" : "cannot watch the desktop's notifications");
			}
		}
		service.onListenerConnected();
	}

	private static synchronized Handler handler() {
		if (mainHandler == null)
			mainHandler = new Handler(Looper.getMainLooper());
		return mainHandler;
	}

	private static List<NotificationListenerService> listenersCopy() {
		synchronized (listeners) {
			return new ArrayList<>(listeners);
		}
	}

	private static void dispatchRemoved(final StatusBarNotification sbn) {
		handler().post(new Runnable() {
			public void run() {
				for (NotificationListenerService l : listenersCopy())
					l.onNotificationRemoved(sbn, new RankingMap(), REASON_CANCEL);
			}
		});
	}

	/* called on GDBus's worker thread for every Notify call on the session bus */
	@Export
	private static void onNotify(String appName, String desktopEntry, int replacesId, String summary, String body, String category, int urgency, boolean resident, boolean own) {
		String pkg;
		if (own) {
			/* as on Android, the app's own notifications carry its own package name */
			pkg = android.atl.ATLLoadedApp.getPrimaryApplication().pkg.packageName;
		} else {
			pkg = desktopEntry != null && !desktopEntry.isEmpty() ? desktopEntry : (appName != null && !appName.isEmpty() ? appName : "unknown");
			synchronized (desktopSources) {
				desktopSources.put(pkg, appName != null && !appName.isEmpty() ? appName : pkg);
			}
		}
		Notification notification = new Notification();
		notification.when = System.currentTimeMillis();
		notification.category = category;
		/* urgency 2 (critical) stays until dismissed, like an ongoing notification */
		notification.flags = resident || urgency == 2 ? 0x02 : 0;
		notification.extras = new Bundle();
		notification.extras.putString("android.title", summary);
		notification.extras.putCharSequence("android.text", body);
		notification.extras.putString("android.appInfo.name", appName);
		final StatusBarNotification sbn;
		synchronized (active) {
			String oldKey = replacesId != 0 ? keysById.get(replacesId) : null;
			StatusBarNotification old = oldKey == null ? null : active.remove(oldKey);
			int id = old != null ? old.getId() : nextId++;
			sbn = new StatusBarNotification(pkg, pkg, id, null, Process.myUid(), 0, notification, Process.myUserHandle(), null, notification.when);
			active.put(sbn.getKey(), sbn);
			if (replacesId != 0)
				keysById.put(replacesId, sbn.getKey());
			lastKey = sbn.getKey();
		}
		Slog.i(TAG, "desktop notification from " + pkg + " posted to " + listenersCopy().size() + " listener(s)");
		handler().post(new Runnable() {
			public void run() {
				for (NotificationListenerService l : listenersCopy())
					l.onNotificationPosted(sbn, new RankingMap());
			}
		});
	}

	private static String lastKey;

	/* the desktop's answer to the last Notify: the id it gave the notification */
	@Export
	private static void onNotifyReply(int id) {
		synchronized (active) {
			if (lastKey != null)
				keysById.put(id, lastKey);
		}
	}

	/* NotificationClosed or CloseNotification */
	@Export
	private static void onClosed(int id) {
		StatusBarNotification sbn;
		synchronized (active) {
			String key = keysById.remove(id);
			sbn = key == null ? null : active.remove(key);
		}
		if (sbn != null)
			dispatchRemoved(sbn);
	}
}
