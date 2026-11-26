package android.view.inputmethod;

import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;

/* passes every call on to the wrapped connection, as in AOSP */
public class InputConnectionWrapper implements InputConnection {
	private InputConnection target;
	private final boolean mutable;

	public InputConnectionWrapper(InputConnection target, boolean mutable) {
		this.target = target;
		this.mutable = mutable;
	}

	public void setTarget(InputConnection target) {
		if (this.target != null && !mutable) {
			throw new SecurityException("not mutable");
		}
		this.target = target;
	}

	public CharSequence getTextBeforeCursor(int n, int flags) {
		return target.getTextBeforeCursor(n, flags);
	}

	public CharSequence getTextAfterCursor(int n, int flags) {
		return target.getTextAfterCursor(n, flags);
	}

	public CharSequence getSelectedText(int flags) {
		return target.getSelectedText(flags);
	}

	public int getCursorCapsMode(int reqModes) {
		return target.getCursorCapsMode(reqModes);
	}

	public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
		return target.getExtractedText(request, flags);
	}

	public boolean deleteSurroundingText(int beforeLength, int afterLength) {
		return target.deleteSurroundingText(beforeLength, afterLength);
	}

	public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
		return target.deleteSurroundingTextInCodePoints(beforeLength, afterLength);
	}

	public boolean setComposingText(CharSequence text, int newCursorPosition) {
		return target.setComposingText(text, newCursorPosition);
	}

	public boolean setComposingRegion(int start, int end) {
		return target.setComposingRegion(start, end);
	}

	public boolean finishComposingText() {
		return target.finishComposingText();
	}

	public boolean commitText(CharSequence text, int newCursorPosition) {
		return target.commitText(text, newCursorPosition);
	}

	public boolean requestCursorUpdates(int cursorUpdateMode) {
		return target.requestCursorUpdates(cursorUpdateMode);
	}

	public boolean commitCompletion(CompletionInfo text) {
		return target.commitCompletion(text);
	}

	public boolean commitCorrection(CorrectionInfo correctionInfo) {
		return target.commitCorrection(correctionInfo);
	}

	public boolean setSelection(int start, int end) {
		return target.setSelection(start, end);
	}

	public boolean performEditorAction(int editorAction) {
		return target.performEditorAction(editorAction);
	}

	public boolean performContextMenuAction(int id) {
		return target.performContextMenuAction(id);
	}

	public boolean beginBatchEdit() {
		return target.beginBatchEdit();
	}

	public boolean endBatchEdit() {
		return target.endBatchEdit();
	}

	public boolean sendKeyEvent(KeyEvent event) {
		return target.sendKeyEvent(event);
	}

	public boolean clearMetaKeyStates(int states) {
		return target.clearMetaKeyStates(states);
	}

	public boolean reportFullscreenMode(boolean enabled) {
		return target.reportFullscreenMode(enabled);
	}

	public boolean performPrivateCommand(String action, Bundle data) {
		return target.performPrivateCommand(action, data);
	}

	public Handler getHandler() {
		return target.getHandler();
	}

	public void closeConnection() {
		target.closeConnection();
	}
}
