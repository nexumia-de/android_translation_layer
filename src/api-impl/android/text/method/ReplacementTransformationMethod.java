package android.text.method;

import android.graphics.Rect;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.view.View;

/**
 * Replaces each character of getOriginal() with the one at the same index
 * of getReplacement(). Unlike Android, the result is a copy of the text at
 * the time of the call, not a live view of it.
 */
public abstract class ReplacementTransformationMethod implements TransformationMethod {
	protected abstract char[] getOriginal();

	protected abstract char[] getReplacement();

	public CharSequence getTransformation(CharSequence source, View v) {
		char[] original = getOriginal();
		char[] replacement = getReplacement();
		boolean found = false;
		for (int i = 0; i < source.length() && !found; i++)
			for (char c : original)
				if (source.charAt(i) == c)
					found = true;
		if (!found)
			return source;
		if (!(source instanceof Spanned)) {
			char[] chars = source.toString().toCharArray();
			for (int i = 0; i < chars.length; i++)
				for (int j = 0; j < original.length; j++)
					if (chars[i] == original[j])
						chars[i] = replacement[j];
			return new String(chars);
		}
		SpannableStringBuilder result = new SpannableStringBuilder(source);
		for (int i = 0; i < result.length(); i++)
			for (int j = 0; j < original.length; j++)
				if (result.charAt(i) == original[j])
					result.replace(i, i + 1, String.valueOf(replacement[j]));
		return result;
	}

	public void onFocusChanged(View view, CharSequence sourceText, boolean focused, int direction, Rect previouslyFocusedRect) {}
}
