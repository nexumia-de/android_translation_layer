package android.view.accessibility;

import java.util.ArrayList;
import java.util.List;

/* See AccessibilityRecord: no accessibility service receives events in ATL. */
public final class AccessibilityEvent extends AccessibilityRecord {
	public static final int INVALID_POSITION = -1;
	public static final int TYPE_VIEW_CLICKED = 0x00000001;
	public static final int TYPE_VIEW_LONG_CLICKED = 0x00000002;
	public static final int TYPE_VIEW_SELECTED = 0x00000004;
	public static final int TYPE_VIEW_FOCUSED = 0x00000008;
	public static final int TYPE_VIEW_TEXT_CHANGED = 0x00000010;
	public static final int TYPE_WINDOW_STATE_CHANGED = 0x00000020;
	public static final int TYPE_NOTIFICATION_STATE_CHANGED = 0x00000040;
	public static final int TYPE_VIEW_HOVER_ENTER = 0x00000080;
	public static final int TYPE_VIEW_HOVER_EXIT = 0x00000100;
	public static final int TYPE_TOUCH_EXPLORATION_GESTURE_START = 0x00000200;
	public static final int TYPE_TOUCH_EXPLORATION_GESTURE_END = 0x00000400;
	public static final int TYPE_WINDOW_CONTENT_CHANGED = 0x00000800;
	public static final int TYPE_VIEW_SCROLLED = 0x00001000;
	public static final int TYPE_VIEW_TEXT_SELECTION_CHANGED = 0x00002000;
	public static final int TYPE_ANNOUNCEMENT = 0x00004000;
	public static final int TYPE_VIEW_ACCESSIBILITY_FOCUSED = 0x00008000;
	public static final int TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED = 0x00010000;
	public static final int TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY = 0x00020000;
	public static final int TYPE_GESTURE_DETECTION_START = 0x00040000;
	public static final int TYPE_GESTURE_DETECTION_END = 0x00080000;
	public static final int TYPE_TOUCH_INTERACTION_START = 0x00100000;
	public static final int TYPE_TOUCH_INTERACTION_END = 0x00200000;
	public static final int TYPE_WINDOWS_CHANGED = 0x00400000;
	public static final int TYPE_VIEW_CONTEXT_CLICKED = 0x00800000;
	public static final int TYPE_ASSIST_READING_CONTEXT = 0x01000000;
	public static final int TYPES_ALL_MASK = 0xFFFFFFFF;
	public static final int CONTENT_CHANGE_TYPE_UNDEFINED = 0x00000000;
	public static final int CONTENT_CHANGE_TYPE_SUBTREE = 0x00000001;
	public static final int CONTENT_CHANGE_TYPE_TEXT = 0x00000002;
	public static final int CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION = 0x00000004;
	public static final int CONTENT_CHANGE_TYPE_STATE_DESCRIPTION = 0x00000040;
	public static final int CONTENT_CHANGE_TYPE_PANE_TITLE = 0x00000008;
	public static final int CONTENT_CHANGE_TYPE_PANE_APPEARED = 0x00000010;
	public static final int CONTENT_CHANGE_TYPE_PANE_DISAPPEARED = 0x00000020;

	private int eventType;
	private long eventTime;
	private CharSequence packageName;
	private int contentChangeTypes;
	private int movementGranularity;
	private int action;
	private final List<AccessibilityRecord> records = new ArrayList<AccessibilityRecord>();

	public AccessibilityEvent() {}

	public AccessibilityEvent(int eventType) { this.eventType = eventType; }

	public AccessibilityEvent(AccessibilityEvent event) {
		super(event);
		eventType = event.eventType;
		eventTime = event.eventTime;
		packageName = event.packageName;
		contentChangeTypes = event.contentChangeTypes;
		movementGranularity = event.movementGranularity;
		action = event.action;
		records.addAll(event.records);
	}

	public static AccessibilityEvent obtain() { return new AccessibilityEvent(); }
	public static AccessibilityEvent obtain(int eventType) { return new AccessibilityEvent(eventType); }
	public static AccessibilityEvent obtain(AccessibilityEvent event) { return new AccessibilityEvent(event); }

	public int getEventType() { return eventType; }
	public void setEventType(int eventType) { this.eventType = eventType; }
	public long getEventTime() { return eventTime; }
	public void setEventTime(long eventTime) { this.eventTime = eventTime; }
	public CharSequence getPackageName() { return packageName; }
	public void setPackageName(CharSequence packageName) { this.packageName = packageName; }
	public int getContentChangeTypes() { return contentChangeTypes; }
	public void setContentChangeTypes(int changeTypes) { this.contentChangeTypes = changeTypes; }
	public int getMovementGranularity() { return movementGranularity; }
	public void setMovementGranularity(int granularity) { this.movementGranularity = granularity; }
	public int getAction() { return action; }
	public void setAction(int action) { this.action = action; }

	public int getRecordCount() { return records.size(); }
	public void appendRecord(AccessibilityRecord record) { records.add(record); }
	public AccessibilityRecord getRecord(int index) { return records.get(index); }

	public static String eventTypeToString(int eventType) { return "0x" + Integer.toHexString(eventType); }
}
