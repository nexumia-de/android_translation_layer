package android.content.pm;

import java.util.Collections;
import java.util.List;

public class ShortcutManager {
	public void removeAllDynamicShortcuts() {
	}

	public List getShortcuts(int matchFlags) {
		return Collections.emptyList();
	}

	// published shortcuts are a launcher concept that has no equivalent outside AOSP
	public List<ShortcutInfo> getDynamicShortcuts() {
		return Collections.emptyList();
	}

	public List<ShortcutInfo> getPinnedShortcuts() {
		return Collections.emptyList();
	}

	public void removeLongLivedShortcuts(List<String> shortcutIds) {
	}

	public void reportShortcutUsed(String shortcutId) {
	}

	public boolean setDynamicShortcuts(List<ShortcutInfo> shortcutInfoList) {
		return true;
	}

	public void removeDynamicShortcuts(List<String> shortcutIds) {
	}

	public boolean addDynamicShortcuts(List<ShortcutInfo> shortcutInfoList) {
		return true;
	}

	public boolean updateShortcuts(List<ShortcutInfo> shortcutInfoList) {
		return true;
	}

	public void enableShortcuts(List<String> shortcutIds) {
	}

	public void disableShortcuts(List<String> shortcutIds) {
	}

	public void pushDynamicShortcut(ShortcutInfo shortcut) {
	}

	public int getMaxShortcutCountPerActivity() {
		return 4;
	}

	public boolean isRequestPinShortcutSupported() {
		return false;
	}
}
