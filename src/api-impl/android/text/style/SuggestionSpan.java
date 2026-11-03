package android.text.style;

import android.content.Context;
import android.text.TextPaint;
import java.util.Locale;

/* Alternatives for a word, shown by the editor's suggestion popup on Android;
 * ATL keeps them but draws nothing. */
public class SuggestionSpan extends CharacterStyle {
	public static final int FLAG_EASY_CORRECT = 0x0001;
	public static final int FLAG_MISSPELLED = 0x0002;
	public static final int FLAG_AUTO_CORRECTION = 0x0004;
	public static final int FLAG_GRAMMAR_ERROR = 0x0008;
	public static final int SUGGESTIONS_MAX_SIZE = 5;

	private final String[] mSuggestions;
	private final int mFlags;
	private final Locale mLocale;

	public SuggestionSpan(Context context, String[] suggestions, int flags) {
		this(context, null, suggestions, flags, null);
	}

	public SuggestionSpan(Locale locale, String[] suggestions, int flags) {
		this(null, locale, suggestions, flags, null);
	}

	public SuggestionSpan(Context context, Locale locale, String[] suggestions, int flags, Class<?> notificationTargetClass) {
		int n = Math.min(SUGGESTIONS_MAX_SIZE, suggestions.length);
		mSuggestions = java.util.Arrays.copyOf(suggestions, n);
		mFlags = flags;
		mLocale = locale;
	}

	public String[] getSuggestions() {
		return mSuggestions;
	}

	public int getFlags() {
		return mFlags;
	}

	public Locale getLocaleObject() {
		return mLocale;
	}

	public void updateDrawState(TextPaint tp) {
	}
}
