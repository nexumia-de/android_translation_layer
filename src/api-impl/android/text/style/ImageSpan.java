package android.text.style;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

public class ImageSpan extends DynamicDrawableSpan {

	private Drawable drawable;

	public ImageSpan(Context context, int resId) {
		this(context, resId, ALIGN_BOTTOM);
	}

	public ImageSpan(Context context, int resId, int verticalAlignment) {
		drawable = context.getDrawable(resId);
		if (drawable != null)
			drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
	}

	public ImageSpan(Drawable d) {
		drawable = d;
	}

	public ImageSpan(Drawable d, String source) {
		drawable = d;
	}

	public ImageSpan(Drawable d, int verticalAlignment) {
		drawable = d;
	}

	public Drawable getDrawable() {
		return drawable;
	}

	@Override
	public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
		return drawable.getIntrinsicWidth();
	}
}
