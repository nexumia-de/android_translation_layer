package android.text.method;

/**
 * Shows line breaks as spaces and hides carriage returns (U+FEFF), for a
 * single line TextView.
 */
public class SingleLineTransformationMethod extends ReplacementTransformationMethod {
	private static final char[] ORIGINAL = new char[] {'\n', '\r'};
	private static final char[] REPLACEMENT = new char[] {' ', '﻿'};

	private static SingleLineTransformationMethod sInstance;

	protected char[] getOriginal() {
		return ORIGINAL;
	}

	protected char[] getReplacement() {
		return REPLACEMENT;
	}

	public static SingleLineTransformationMethod getInstance() {
		if (sInstance == null)
			sInstance = new SingleLineTransformationMethod();
		return sInstance;
	}
}
