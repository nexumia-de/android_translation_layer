package android.view.autofill;

import android.graphics.Rect;
import android.view.View;

public class AutofillManager {

	public static abstract class AutofillCallback {}

	public interface AutofillClient {}

	public void registerCallback(AutofillCallback callback) {}

	public void unregisterCallback(AutofillCallback callback) {}

	public void notifyViewEntered(View view, int id, Rect bounds) {}

	public void notifyValueChanged(View view, int id, AutofillValue value) {}

	public void notifyViewExited(View view, int id) {}

	/* ATL has no autofill service, so nothing is ever filled or saved */
	public boolean isEnabled() {
		return false;
	}

	public boolean isAutofillSupported() {
		return false;
	}

	public void notifyViewEntered(View view) {}

	public void notifyViewExited(View view) {}

	public void notifyValueChanged(View view) {}

	public void notifyViewVisibilityChanged(View view, boolean isVisible) {}

	public void notifyViewVisibilityChanged(View view, int virtualId, boolean isVisible) {}

	public void requestAutofill(View view) {}

	public void requestAutofill(View view, int virtualId, Rect absBounds) {}

	public void commit() {}

	public void cancel() {}
}
