package android.view.accessibility;

import java.util.ArrayList;
import java.util.List;

public class AccessibilityManager {

	public interface AccessibilityStateChangeListener {
		void onAccessibilityStateChanged(boolean enabled);
	}

	public interface TouchExplorationStateChangeListener {
		void onTouchExplorationStateChanged(boolean enabled);
	}

	public boolean isTouchExplorationEnabled() { return false; }

	public boolean isEnabled() { return false; }

	public void sendAccessibilityEvent(AccessibilityEvent event) {}

	public List getEnabledAccessibilityServiceList(int feedbackTypeFlags) {
		return new ArrayList<>();
	}

	public boolean addAccessibilityStateChangeListener(AccessibilityStateChangeListener listener) {
		return false;
	}

	public boolean addTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener) {
		return false;
	}

	public boolean removeAccessibilityStateChangeListener(AccessibilityStateChangeListener listener) {
		return false;
	}

	public boolean removeTouchExplorationStateChangeListener(TouchExplorationStateChangeListener listener) {
		return false;
	}
}
