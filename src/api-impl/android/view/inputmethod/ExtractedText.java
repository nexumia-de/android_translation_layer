package android.view.inputmethod;

/* Text an editor hands to an input method, as on Android. */
public class ExtractedText {
	public static final int FLAG_SINGLE_LINE = 0x0001;
	public static final int FLAG_SELECTING = 0x0002;

	public CharSequence text;
	public int startOffset;
	public int partialStartOffset;
	public int partialEndOffset;
	public int selectionStart;
	public int selectionEnd;
	public int flags;
	public CharSequence hint;
}
