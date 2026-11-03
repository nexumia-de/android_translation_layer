package android.view.inputmethod;

import android.os.Bundle;
import android.os.LocaleList;

public class EditorInfo {
	public int actionId = 0;
	public CharSequence actionLabel = null;
	public Bundle extras = null;
	public int fieldId = 0;
	public String fieldName = null;
	public CharSequence hintText = null;
	public int imeOptions = 0x0;
	public int initialCapsMode = 0;
	public int initialSelStart = -1;
	public int initialSelEnd = -1;
	public int inputType = /*0x0*/ 0x00000001; /* TYPE_NULL */ /* TYPE_CLASS_TEXT */
	public CharSequence label = null;
	public String packageName = "com.example.FIXME";
	public String privateImeOptions = null;
	public String[] contentMimeTypes = null;
	public LocaleList hintLocales = null;

	/* the input method asks the connection for the text around the cursor */
	public void setInitialSurroundingText(CharSequence sourceText) {}

	public void setInitialSurroundingSubText(CharSequence subText, int subTextStart) {}

	public void setStylusHandwritingEnabled(boolean enabled) {}
}
