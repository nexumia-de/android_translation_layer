package android.view.accessibility;

import android.os.Parcelable;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/* No accessibility service runs in ATL (AccessibilityManager.isEnabled is
 * false), so records are only filled and read by the app itself. */
public class AccessibilityRecord {
	private View source;
	private int virtualDescendantId = -1;
	private CharSequence className;
	private CharSequence contentDescription;
	private CharSequence beforeText;
	private final List<CharSequence> text = new ArrayList<CharSequence>();
	private Parcelable parcelableData;
	private boolean enabled = true, checked, password, fullScreen, scrollable;
	private int itemCount = -1, currentItemIndex = -1, fromIndex = -1, toIndex = -1;
	private int addedCount = -1, removedCount = -1;
	private int scrollX, scrollY, maxScrollX, maxScrollY, scrollDeltaX = -1, scrollDeltaY = -1;
	private int windowId = -1;

	public AccessibilityRecord() {}

	public AccessibilityRecord(AccessibilityRecord record) {
		source = record.source;
		virtualDescendantId = record.virtualDescendantId;
		className = record.className;
		contentDescription = record.contentDescription;
		beforeText = record.beforeText;
		text.addAll(record.text);
		parcelableData = record.parcelableData;
		enabled = record.enabled;
		checked = record.checked;
		password = record.password;
		fullScreen = record.fullScreen;
		scrollable = record.scrollable;
		itemCount = record.itemCount;
		currentItemIndex = record.currentItemIndex;
		fromIndex = record.fromIndex;
		toIndex = record.toIndex;
		addedCount = record.addedCount;
		removedCount = record.removedCount;
		scrollX = record.scrollX;
		scrollY = record.scrollY;
		maxScrollX = record.maxScrollX;
		maxScrollY = record.maxScrollY;
		scrollDeltaX = record.scrollDeltaX;
		scrollDeltaY = record.scrollDeltaY;
		windowId = record.windowId;
	}

	public static AccessibilityRecord obtain() { return new AccessibilityRecord(); }
	public static AccessibilityRecord obtain(AccessibilityRecord record) { return new AccessibilityRecord(record); }
	public void recycle() {}

	public void setSource(View source) { setSource(source, -1); }
	public void setSource(View root, int virtualDescendantId) {
		this.source = root;
		this.virtualDescendantId = virtualDescendantId;
	}
	public AccessibilityNodeInfo getSource() { return null; }
	public int getWindowId() { return windowId; }

	public boolean isChecked() { return checked; }
	public void setChecked(boolean checked) { this.checked = checked; }
	public boolean isEnabled() { return enabled; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public boolean isPassword() { return password; }
	public void setPassword(boolean password) { this.password = password; }
	public boolean isFullScreen() { return fullScreen; }
	public void setFullScreen(boolean fullScreen) { this.fullScreen = fullScreen; }
	public boolean isScrollable() { return scrollable; }
	public void setScrollable(boolean scrollable) { this.scrollable = scrollable; }

	public int getItemCount() { return itemCount; }
	public void setItemCount(int itemCount) { this.itemCount = itemCount; }
	public int getCurrentItemIndex() { return currentItemIndex; }
	public void setCurrentItemIndex(int currentItemIndex) { this.currentItemIndex = currentItemIndex; }
	public int getFromIndex() { return fromIndex; }
	public void setFromIndex(int fromIndex) { this.fromIndex = fromIndex; }
	public int getToIndex() { return toIndex; }
	public void setToIndex(int toIndex) { this.toIndex = toIndex; }
	public int getAddedCount() { return addedCount; }
	public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
	public int getRemovedCount() { return removedCount; }
	public void setRemovedCount(int removedCount) { this.removedCount = removedCount; }

	public int getScrollX() { return scrollX; }
	public void setScrollX(int scrollX) { this.scrollX = scrollX; }
	public int getScrollY() { return scrollY; }
	public void setScrollY(int scrollY) { this.scrollY = scrollY; }
	public int getMaxScrollX() { return maxScrollX; }
	public void setMaxScrollX(int maxScrollX) { this.maxScrollX = maxScrollX; }
	public int getMaxScrollY() { return maxScrollY; }
	public void setMaxScrollY(int maxScrollY) { this.maxScrollY = maxScrollY; }
	public int getScrollDeltaX() { return scrollDeltaX; }
	public void setScrollDeltaX(int scrollDeltaX) { this.scrollDeltaX = scrollDeltaX; }
	public int getScrollDeltaY() { return scrollDeltaY; }
	public void setScrollDeltaY(int scrollDeltaY) { this.scrollDeltaY = scrollDeltaY; }

	public CharSequence getClassName() { return className; }
	public void setClassName(CharSequence className) { this.className = className; }
	public List<CharSequence> getText() { return text; }
	public CharSequence getBeforeText() { return beforeText; }
	public void setBeforeText(CharSequence beforeText) { this.beforeText = beforeText; }
	public CharSequence getContentDescription() { return contentDescription; }
	public void setContentDescription(CharSequence contentDescription) { this.contentDescription = contentDescription; }
	public Parcelable getParcelableData() { return parcelableData; }
	public void setParcelableData(Parcelable parcelableData) { this.parcelableData = parcelableData; }
}
