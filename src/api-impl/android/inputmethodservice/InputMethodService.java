package android.inputmethodservice;

import android.app.Dialog;
import android.atl.ATLKeyboardDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Region;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodSubtype;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import android.widget.LinearLayout;

public class InputMethodService extends AbstractInputMethodService {
	private LinearLayout kb_box;
	private View kb_view;
	private View candidates_view;
	private Dialog kb_dialog;

	class ATLInputConnection extends BaseInputConnection {
		protected long nativePtr;

		private native long nativeInit();
		private native boolean nativeSetCompositingText(long ptr, String text, int newCursorPosition);
		private native boolean nativeSetCompositingRegion(long ptr, int start, int end);
		private native boolean nativeFinishComposingText(long ptr);
		private native boolean nativeCommitText(long ptr, String text, int newCursorPosition);
		private native boolean nativeDeleteSurroundingText(long ptr, int beforeLength, int afterLength);
		private native boolean nativeSetSelection(long ptr, int start, int end);
		private native boolean nativeSendKeyEvent(long ptr, long time, long key, long state);
		/* the focused field's text around the cursor, from input-method-v2 (UTF-8, cursor and
		 * anchor in bytes): [before cursor, selected, after] */
		private native byte[] nativeGetSurroundingText(long ptr);
		private native int nativeGetSurroundingCursor(long ptr);
		private native int nativeGetSurroundingAnchor(long ptr);

		private String[] surrounding() {
			byte[] text = nativeGetSurroundingText(nativePtr);
			int cursor = Math.min(nativeGetSurroundingCursor(nativePtr), text.length);
			int anchor = Math.min(nativeGetSurroundingAnchor(nativePtr), text.length);
			int start = Math.min(cursor, anchor);
			int end = Math.max(cursor, anchor);
			java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
			return new String[] {new String(text, 0, start, utf8), new String(text, start, end - start, utf8), new String(text, end, text.length - end, utf8)};
		}

		@Override
		public CharSequence getTextBeforeCursor(int length, int flags) {
			String before = surrounding()[0];
			return before.substring(Math.max(0, before.length() - length));
		}

		@Override
		public CharSequence getSelectedText(int flags) {
			String selected = surrounding()[1];
			return selected.isEmpty() ? null : selected;
		}

		@Override
		public CharSequence getTextAfterCursor(int length, int flags) {
			String after = surrounding()[2];
			return after.substring(0, Math.min(after.length(), length));
		}

		@Override
		public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
			String[] parts = surrounding();
			ExtractedText et = new ExtractedText();
			et.text = parts[0] + parts[1] + parts[2];
			et.startOffset = 0;
			et.selectionStart = parts[0].length();
			et.selectionEnd = parts[0].length() + parts[1].length();
			return et;
		}

		/* Android counts UTF-16 chars, the protocol UTF-8 bytes */
		private int utf8Length(String s) {
			return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
		}

		ATLInputConnection() {
			super(null, false);
			nativePtr = nativeInit();
		}

		@Override
		public boolean setComposingText(CharSequence text, int newCursorPosition) {
			return nativeSetCompositingText(nativePtr, text.toString(), newCursorPosition);
		}

		@Override
		public boolean setComposingRegion(int start, int end) {
			return nativeSetCompositingRegion(nativePtr, start, end);
		}

		@Override
		public boolean finishComposingText() {
			return nativeFinishComposingText(nativePtr);
		}

		@Override
		public boolean commitText(CharSequence text, int newCursorPosition) {
			return nativeCommitText(nativePtr, text.toString(), newCursorPosition);
		}

		@Override
		public boolean deleteSurroundingText(int beforeLength, int afterLength) {
			System.out.println("ATLKeyboardIMS: deleteSurroundingText(" + beforeLength + ", " + afterLength + ")");
			String[] parts = surrounding();
			int beforeStart = Math.max(0, parts[0].length() - beforeLength);
			// do not cut a surrogate pair: take the whole code point
			if (beforeStart > 0 && Character.isLowSurrogate(parts[0].charAt(beforeStart)) && Character.isHighSurrogate(parts[0].charAt(beforeStart - 1)))
				beforeStart--;
			int afterEnd = Math.min(parts[2].length(), afterLength);
			if (afterEnd > 0 && afterEnd < parts[2].length() && Character.isHighSurrogate(parts[2].charAt(afterEnd - 1)) && Character.isLowSurrogate(parts[2].charAt(afterEnd)))
				afterEnd++;
			String before = parts[0].substring(beforeStart);
			String after = parts[2].substring(0, afterEnd);
			/* the surrounding text can be missing (terminals) or lag one compositor round
			 * trip behind: count what it does not cover as one byte per char, as before */
			int beforeBytes = utf8Length(before) + Math.max(0, beforeLength - parts[0].length());
			int afterBytes = utf8Length(after) + Math.max(0, afterLength - parts[2].length());
			return nativeDeleteSurroundingText(nativePtr, beforeBytes, afterBytes);
		}

		@Override
		public boolean setSelection(int start, int end) {
			return nativeSetSelection(nativePtr, start, end);
		}

		@Override
		public boolean sendKeyEvent(KeyEvent event) {
			System.out.println("softkeyboard preview: sendKeyEvent(" + event + ")");
			return nativeSendKeyEvent(nativePtr, event.getEventTime(), event.getKeyCode(), event.getAction());
		}

		/* these functions are noop on AOSP by default, so we just add a print for debugging purposes and still return false */
		@Override
		public boolean commitCompletion(CompletionInfo completionInfo) {
			System.out.println("softkeyboard preview: commitCompletion(\"" + completionInfo + "\")");
			return false;
		}

		@Override
		public boolean commitCorrection(CorrectionInfo correctionInfo) {
			System.out.println("softkeyboard preview: commitCorrection(\"" + correctionInfo + "\")");
			return false;
		}
	}

	private InputConnection input_connection = new ATLInputConnection();

	public InputMethodService() {}

	public void launch_keyboard(boolean is_layershell) {
		if (is_layershell)
			kb_dialog = new ATLKeyboardDialog(this);
		else
			kb_dialog = new Dialog(this);

		View decorview = kb_dialog.getWindow().getDecorView();
		decorview.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

		onCreate();

		AbstractInputMethodImpl impl = onCreateInputMethodInterface();
		impl.createSession(null);

		// to force portrait version:
		getResources().getConfiguration().orientation = Configuration.ORIENTATION_PORTRAIT;

		onConfigurationChanged(getResources().getConfiguration());

		onBindInput();
		onStartInput(new EditorInfo(), false);

		kb_box = new LinearLayout(this);
		kb_box.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		kb_box.setOrientation(LinearLayout.VERTICAL);

		candidates_view = onCreateCandidatesView();
		kb_view = onCreateInputView();

		if (candidates_view != null)
			kb_box.addView(candidates_view);
		kb_box.addView(kb_view);

		kb_dialog.setContentView(kb_box);
		kb_dialog.show();

		onConfigureWindow(kb_dialog.getWindow(), false, false);

		onComputeInsets(new Insets());

		onStartInputView(new EditorInfo(), false);
	}

	public void sendKeyChar(char c) {
		System.out.println("softkeyboard preview: sendKeyChar('" + c + "')");
	}

	public void setInputView(View view) {
		kb_view = view;
	}

	public void setCandidatesView(View view) {
		candidates_view = view;
	}

	public InputBinding getCurrentInputBinding() {
		return new InputBinding(input_connection, null, 0, 0);
	}

	public IBinder onBind(Intent intent) {
		return null;
	}

	public LayoutInflater getLayoutInflater() {
		return (LayoutInflater)getSystemService("layout_inflater");
	}

	public boolean onEvaluateInputViewShown() {
		return true;
	}

	public void setCandidatesViewShown(boolean shown) {
	}

	public void showStatusIcon(int resId) {
	}

	public void hideStatusIcon() {
	}

	public void updateInputViewShown() {
	}

	public void updateFullscreenMode() {
	}

	public boolean isFullscreenMode() {
		return false;
	}

	public int getMaxWidth() {
		return (kb_view != null && kb_view.getWidth() > 0) ? kb_view.getWidth() : Resources.getSystem().getDisplayMetrics().widthPixels;
	}

	public EditorInfo getCurrentInputEditorInfo() {
		return new EditorInfo();
	}

	public void requestHideSelf(int flags) {
	}

	public Dialog getWindow() {
		return kb_dialog;
	}

	public InputConnection getCurrentInputConnection() {
		return input_connection;
	}

	@Deprecated
	public boolean enableHardwareAcceleration() {
		return false;
	}

	public boolean isInputViewShown() {
		return kb_view != null && kb_dialog != null && kb_dialog.isShowing();
	}

	public void showWindow(boolean showInput) {
		if (kb_dialog != null && !kb_dialog.isShowing()) {
			kb_dialog.show();
			onWindowShown();
		}
	}

	public void hideWindow() {
		if (kb_dialog != null && kb_dialog.isShowing()) {
			kb_dialog.hide();
			onWindowHidden();
		}
	}

	public void onWindowShown() {
	}

	public void onWindowHidden() {
	}

	public boolean onShowInputRequested(int flags, boolean configChange) {
		return true;
	}

	public boolean onEvaluateFullscreenMode() {
		return false;
	}

	public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
	}

	public void onExtractedCursorMovement(int dx, int dy) {
	}

	public void onExtractedTextClicked() {
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		return false;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		return false;
	}

	public void onTrimMemory(int level) {
	}

	public void onFinishInputView(boolean finishingInput) {
	}

	public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
	}

	/* ATL runs one input method, there is nothing to switch to */
	public boolean shouldOfferSwitchingToNextInputMethod() {
		return false;
	}

	public boolean switchToNextInputMethod(boolean onlyCurrentIme) {
		return false;
	}

	public boolean switchToPreviousInputMethod() {
		return false;
	}

	public void switchInputMethod(String id) {
	}

	public final void switchInputMethod(String id, InputMethodSubtype subtype) {
		// only this input method exists in ATL, so only its subtype can change
		android.view.inputmethod.InputMethodManager.setCurrentSubtype(subtype);
		onCurrentInputMethodSubtypeChanged(subtype);
	}

	protected void dump(FileDescriptor fd, PrintWriter fout, String[] args) {
	}

	/* --- */

	public AbstractInputMethodImpl onCreateInputMethodInterface() {
		return new InputMethodImpl();
	}

	public View onCreateCandidatesView() {
		return null;
	}

	public View onCreateInputView() {
		return null;
	}

	public void onConfigurationChanged(Configuration configuration) {
	}

	public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
	}

	public void onComputeInsets(Insets insets) {
	}

	public void onStartInput(EditorInfo info, boolean restarting) {
	}

	public void onFinishInput() {
	}

	public void onStartInputView(EditorInfo info, boolean restarting) {
	}

	public void onBindInput() {
	}

	/* --- */

	public static final class Insets {
		public int contentTopInsets;
		public int visibleTopInsets;
		public final Region touchableRegion = new Region();
		public static final int TOUCHABLE_INSETS_FRAME = ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_FRAME;
		public static final int TOUCHABLE_INSETS_CONTENT = ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_CONTENT;
		public static final int TOUCHABLE_INSETS_VISIBLE = ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_VISIBLE;
		public static final int TOUCHABLE_INSETS_REGION = ViewTreeObserver.InternalInsetsInfo.TOUCHABLE_INSETS_REGION;
		public int touchableInsets;
	}

	public class InputMethodImpl extends AbstractInputMethodImpl {
	}
}
