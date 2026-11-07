package android.text.style;

import android.graphics.drawable.Drawable;

public abstract class DynamicDrawableSpan extends ReplacementSpan {
	public static final int ALIGN_BOTTOM = 0;
	public static final int ALIGN_BASELINE = 1;
	public static final int ALIGN_CENTER = 2;

	public abstract Drawable getDrawable();
}
