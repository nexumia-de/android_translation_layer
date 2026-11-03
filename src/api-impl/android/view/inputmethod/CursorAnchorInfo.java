package android.view.inputmethod;

import android.graphics.Matrix;

/* Where the cursor and the composing text are on screen, as an editor tells
 * the input method. The input methods ATL talks to do not use it. */
public class CursorAnchorInfo {
	public static final int FLAG_HAS_VISIBLE_REGION = 0x01;
	public static final int FLAG_HAS_INVISIBLE_REGION = 0x02;
	public static final int FLAG_IS_RTL = 0x04;

	private int selectionStart = -1;
	private int selectionEnd = -1;
	private int composingTextStart = -1;
	private CharSequence composingText;

	public int getSelectionStart() { return selectionStart; }
	public int getSelectionEnd() { return selectionEnd; }
	public int getComposingTextStart() { return composingTextStart; }
	public CharSequence getComposingText() { return composingText; }

	public static class Builder {
		private CursorAnchorInfo info = new CursorAnchorInfo();

		public Builder setSelectionRange(int newStart, int newEnd) {
			info.selectionStart = newStart;
			info.selectionEnd = newEnd;
			return this;
		}

		public Builder setComposingText(int composingTextStart, CharSequence composingText) {
			info.composingTextStart = composingTextStart;
			info.composingText = composingText;
			return this;
		}

		public Builder setInsertionMarkerLocation(float horizontalPosition, float lineTop, float lineBaseline, float lineBottom, int flags) {
			return this;
		}

		public Builder addCharacterBounds(int index, float left, float top, float right, float bottom, int flags) {
			return this;
		}

		public Builder setMatrix(Matrix matrix) {
			return this;
		}

		public CursorAnchorInfo build() {
			CursorAnchorInfo built = info;
			info = new CursorAnchorInfo();
			return built;
		}

		public void reset() {
			info = new CursorAnchorInfo();
		}
	}
}
