package android.app;

import android.app.Notification.MediaStyle;
import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import java.util.Collections;
import java.util.List;

public class NotificationManager {

	private static int mpris_notification_id = -1;

	public void cancelAll() {}

	/* importance of the app's channels, and the ids shown so far (for setOnlyAlertOnce) */
	private static final java.util.Map<String, Integer> channel_importance = new java.util.HashMap<>();
	private static final java.util.Set<Integer> shown_ids = new java.util.HashSet<>();

	/* GNotificationPriority: 0 LOW, 1 NORMAL, 2 HIGH, 3 URGENT. A notification that is updated
	 * while it is shown (progress, setOnlyAlertOnce, ongoing) or that the app marks as unimportant
	 * becomes LOW, so the shell (and a watch bridge) does not alert at every update, as on Android. */
	private static int gnotification_priority(int id, Notification notification) {
		boolean already_shown;
		synchronized (shown_ids) {
			already_shown = !shown_ids.add(id);
		}
		Integer importance = null;
		if (notification.channelId != null)
			synchronized (channel_importance) {
				importance = channel_importance.get(notification.channelId);
			}
		if (importance != null && importance <= 2) // IMPORTANCE_LOW, MIN, NONE
			return 0;
		if (notification.priority < 0) // PRIORITY_LOW, PRIORITY_MIN
			return 0;
		if (notification.hasProgress || (notification.onlyAlertOnce && already_shown) || (notification.ongoing && already_shown))
			return 0;
		if ((importance != null && importance >= 4) || notification.priority > 0) // IMPORTANCE_HIGH, PRIORITY_HIGH/MAX
			return 2;
		return 1;
	}

	public void notify(String tag, int id, Notification notification) {
		if (notification.style instanceof MediaStyle) { // MPRIS content is handled by MediaSession implementation
			if (mpris_notification_id == -1) {
				Application application = ATLLoadedApp.getPrimaryApplication().getApplication();
				nativeShowMPRIS(application.getPackageName(), application.get_app_label());
				mpris_notification_id = id;
			}
			return;
		}

		System.out.println("notify(" + tag + ", " + id + ", " + notification + ") called");
		long builder = nativeInitBuilder();
		for (Notification.Action action : notification.actions) {
			int intentType = -1;
			Intent intent = null;
			if (action.intent != null) {
				intentType = action.intent.type;
				intent = action.intent.intent;
			}
			nativeAddAction(builder, action.title, intentType, intent);
		}
		int intentType = -1;
		Intent intent = null;
		if (notification.intent != null) {
			intentType = notification.intent.type;
			intent = notification.intent.intent;
		}
		String iconPath = notification.iconPath == null || notification.iconPath.endsWith(".xml") ? null : notification.iconPath;
		nativeShowNotification(builder, id, notification.title, notification.text, iconPath, notification.ongoing, gnotification_priority(id, notification), intentType, intent);
	}

	public void notify(int id, Notification notification) {
		notify(null, id, notification);
	}

	public void cancel(String tag, final int id) {
		// remove_notification doesn't work reliably when sent directly after add_notification in GNOME session.
		// So we give some extra delay here.
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				synchronized (shown_ids) {
					shown_ids.remove(id);
				}
				if (mpris_notification_id == id) {
					mpris_notification_id = -1;
					nativeCancelMPRIS();
				} else {
					nativeCancel(id);
				}
			}
		}, 100);
	}

	public void cancel(int id) {
		cancel(null, id);
	}

	public void createNotificationChannel(NotificationChannel channel) {
		if (channel != null && channel.getId() != null)
			synchronized (channel_importance) {
				channel_importance.put(channel.getId(), channel.getImportance());
			}
	}

	protected native long nativeInitBuilder();
	protected native void nativeAddAction(long builder, String title, int intentType, Intent intent);
	protected native void nativeShowNotification(long builder, int id, String title, String text, String iconPath, boolean ongoing, int priority, int intentType, Intent intent);
	protected native void nativeShowMPRIS(String packageName, String identiy);
	protected native void nativeCancel(int id);
	protected native void nativeCancelMPRIS();

	public void createNotificationChannelGroup(NotificationChannelGroup v) {}

	public List<NotificationChannel> getNotificationChannels() {
		return Collections.emptyList();
	}

	public List<NotificationChannelGroup> getNotificationChannelGroups() {
		return Collections.emptyList();
	}

	public boolean areNotificationsEnabled() {
		return true;
	}

	public void deleteNotificationChannel(String channelId) {}

	public void createNotificationChannelGroups(List<NotificationChannelGroup> groups) {}

	public void createNotificationChannels(List<NotificationChannel> channels) {}

	public StatusBarNotification[] getActiveNotifications() {
		return new StatusBarNotification[0];
	}

	public NotificationChannel getNotificationChannel(String channel) {
		return null;
	}

	/* no other app posts notifications for this one */
	public String getNotificationDelegate() {
		return null;
	}

	public void setNotificationDelegate(String delegate) {}

	public static final int INTERRUPTION_FILTER_UNKNOWN = 0;
	public static final int INTERRUPTION_FILTER_ALL = 1;
	public static final int INTERRUPTION_FILTER_PRIORITY = 2;
	public static final int INTERRUPTION_FILTER_NONE = 3;
	public static final int INTERRUPTION_FILTER_ALARMS = 4;

	/* do-not-disturb belongs to the desktop, which apps cannot change from here */
	public boolean isNotificationPolicyAccessGranted() {
		return false;
	}

	public int getCurrentInterruptionFilter() {
		return INTERRUPTION_FILTER_ALL;
	}

	public void setInterruptionFilter(int interruptionFilter) {}

	public Policy getNotificationPolicy() {
		return new Policy(0, 0, 0);
	}

	public void setNotificationPolicy(Policy policy) {}

	public static class Policy {
		public static final int PRIORITY_CATEGORY_REMINDERS = 1;
		public static final int PRIORITY_CATEGORY_EVENTS = 2;
		public static final int PRIORITY_CATEGORY_MESSAGES = 4;
		public static final int PRIORITY_CATEGORY_CALLS = 8;
		public static final int PRIORITY_CATEGORY_REPEAT_CALLERS = 16;
		public static final int PRIORITY_CATEGORY_ALARMS = 32;
		public static final int PRIORITY_CATEGORY_MEDIA = 64;
		public static final int PRIORITY_CATEGORY_SYSTEM = 128;
		public static final int PRIORITY_SENDERS_ANY = 0;
		public static final int PRIORITY_SENDERS_CONTACTS = 1;
		public static final int PRIORITY_SENDERS_STARRED = 2;

		public final int priorityCategories;
		public final int priorityCallSenders;
		public final int priorityMessageSenders;
		public final int suppressedVisualEffects;

		public Policy(int priorityCategories, int priorityCallSenders, int priorityMessageSenders) {
			this(priorityCategories, priorityCallSenders, priorityMessageSenders, 0);
		}

		public Policy(int priorityCategories, int priorityCallSenders, int priorityMessageSenders, int suppressedVisualEffects) {
			this.priorityCategories = priorityCategories;
			this.priorityCallSenders = priorityCallSenders;
			this.priorityMessageSenders = priorityMessageSenders;
			this.suppressedVisualEffects = suppressedVisualEffects;
		}
	}
}
