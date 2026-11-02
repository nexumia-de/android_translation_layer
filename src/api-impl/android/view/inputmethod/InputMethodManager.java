package android.view.inputmethod;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.view.View;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InputMethodManager {

	private static long im_context = nativeInit();
	private static View activeView = null;

	private final Context context;
	private ArrayList<InputMethodInfo> input_method_list;
	private InputMethodSubtype current_subtype;

	public InputMethodManager() {
		this(null);
	}

	public InputMethodManager(Context context) {
		this.context = context;
	}

	public boolean hideSoftInputFromWindow(IBinder windowToken, int flags) {
		nativeHideSoftInput(im_context);
		activeView = null;
		return false;
	}

	public boolean showSoftInput(View view, int flags) {
		if (view == activeView) {
			return nativeShowSoftInput(im_context, view.widget, null, 0);
		}
		EditorInfo outAttrs = new EditorInfo();
		InputConnection ic = view.onCreateInputConnection(outAttrs);
		if (ic != null) {
			activeView = view;
			return nativeShowSoftInput(im_context, view.widget, ic, outAttrs.inputType);
		}
		return false;
	}

	public boolean isFullscreenMode() { return false; }

	public boolean isActive(View view) {
		return activeView == view;
	}

	/* The input methods ATL knows are the ones of the running app (an IME
	 * runs in its own process, ATLKeyboardViewer), so the list is built from
	 * its manifest. */
	private synchronized List<InputMethodInfo> inputMethods() {
		if (input_method_list == null) {
			input_method_list = new ArrayList<InputMethodInfo>();
			if (context != null) {
				List<ResolveInfo> services = context.getPackageManager().queryIntentServices(new Intent("android.view.InputMethod"), PackageManager.GET_META_DATA);
				for (ResolveInfo ri : services)
					input_method_list.add(new InputMethodInfo(context, ri));
			}
		}
		return input_method_list;
	}

	public List<InputMethodInfo> getEnabledInputMethodList() {
		return inputMethods();
	}

	public List<InputMethodInfo> getInputMethodList() {
		return inputMethods();
	}

	public List<InputMethodSubtype> getEnabledInputMethodSubtypeList(InputMethodInfo imi, boolean allowsImplicitlySelectedSubtypes) {
		if (imi == null) {
			List<InputMethodInfo> list = inputMethods();
			if (list.isEmpty())
				return new ArrayList<InputMethodSubtype>();
			imi = list.get(0);
		}
		return new ArrayList<InputMethodSubtype>(imi.getSubtypes());
	}

	public Map<InputMethodInfo, List<InputMethodSubtype>> getShortcutInputMethodsAndSubtypes() {
		return Collections.emptyMap();
	}

	public void setInputMethodAndSubtype(IBinder token, String id, InputMethodSubtype subtype) {
		current_subtype = subtype;
	}

	public boolean setCurrentInputMethodSubtype(InputMethodSubtype subtype) {
		current_subtype = subtype;
		return true;
	}

	public boolean switchToNextInputMethod(IBinder imeToken, boolean onlyCurrentIme) {
		return false;
	}

	public boolean switchToLastInputMethod(IBinder imeToken) {
		return false;
	}

	public boolean shouldOfferSwitchingToNextInputMethod(IBinder imeToken) {
		return false;
	}

	public void showInputMethodPicker() {}

	public void showInputMethodAndSubtypeEnabler(String imiId) {}

	public void restartInput(View view) {}

	public void updateSelection(View view, int selStart, int selEnd, int candidatesStart, int candidatesEnd) {}

	public InputMethodSubtype getCurrentInputMethodSubtype() {
		if (current_subtype != null)
			return current_subtype;
		List<InputMethodSubtype> subtypes = getEnabledInputMethodSubtypeList(null, true);
		return subtypes.isEmpty() ? new InputMethodSubtype() : subtypes.get(0);
	}

	private static native long nativeInit();
	private native boolean nativeShowSoftInput(long im_context, long widget, InputConnection ic, int inputType);
	private native void nativeHideSoftInput(long im_context);
}
