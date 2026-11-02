package android.service.notification;

import android.app.Notification;
import android.os.Parcelable;
import android.os.Process;
import android.os.UserHandle;

public class StatusBarNotification implements Parcelable {
	private final String pkg;
	private final String opPkg;
	private final int id;
	private final String tag;
	private final int uid;
	private final Notification notification;
	private final UserHandle user;
	private final long postTime;
	private final String key;

	public StatusBarNotification(String pkg, String opPkg, int id, String tag, int uid, int initialPid, Notification notification, UserHandle user,
	                             String overrideGroupKey, long postTime) {
		this.pkg = pkg;
		this.opPkg = opPkg;
		this.id = id;
		this.tag = tag;
		this.uid = uid;
		this.notification = notification;
		this.user = user;
		this.postTime = postTime;
		this.key = "0|" + pkg + "|" + id + "|" + tag + "|" + uid;
	}

	public StatusBarNotification(String pkg, String opPkg, int id, String tag, int uid, int initialPid, int score, Notification notification,
	                             UserHandle user, long postTime) {
		this(pkg, opPkg, id, tag, uid, initialPid, notification, user, null, postTime);
	}

	public String getPackageName() {
		return pkg;
	}

	public String getOpPkg() {
		return opPkg;
	}

	public int getId() {
		return id;
	}

	public String getTag() {
		return tag;
	}

	public int getUid() {
		return uid;
	}

	public Notification getNotification() {
		return notification;
	}

	public UserHandle getUser() {
		return user != null ? user : Process.myUserHandle();
	}

	public long getPostTime() {
		return postTime;
	}

	public String getKey() {
		return key;
	}

	public String getGroupKey() {
		return key;
	}

	public String getOverrideGroupKey() {
		return null;
	}

	public boolean isGroup() {
		return false;
	}

	public boolean isAppGroup() {
		return false;
	}

	/* FLAG_ONGOING_EVENT */
	public boolean isOngoing() {
		return notification != null && (notification.flags & 0x02) != 0;
	}

	/* neither FLAG_ONGOING_EVENT nor FLAG_NO_CLEAR */
	public boolean isClearable() {
		return notification == null || (notification.flags & (0x02 | 0x20)) == 0;
	}

	@Override
	public String toString() {
		return "StatusBarNotification(pkg=" + pkg + " id=" + id + " key=" + key + ")";
	}
}
