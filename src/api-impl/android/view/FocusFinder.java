package android.view;

public class FocusFinder {

	public static FocusFinder getInstance() {
		return new FocusFinder();
	}

	public View findNextFocusFromRect(ViewGroup root, android.graphics.Rect focusedRect, int direction) {
		return null;
	}

	public View findNextFocus(ViewGroup root, View focused, int direction) {
		return null;
	}
}
