package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public abstract class AbsSeekBar extends ProgressBar {

	public AbsSeekBar(Context context) {
		super(context);
	}

	public AbsSeekBar(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	public AbsSeekBar(Context context, AttributeSet attributeSet, int defStyleAttr) {
		super(context, attributeSet, defStyleAttr);
	}

	public Drawable getThumb() {
		return new Drawable();
	}

	public void setKeyProgressIncrement(int keyProgressIncrement) {}

	public int getKeyProgressIncrement() { return 0; }

	private int thumbOffset;

	/* kept for apps that read it back; the GTK scale positions its own slider */
	public int getThumbOffset() { return thumbOffset; }

	public void setThumbOffset(int thumbOffset) {
		this.thumbOffset = thumbOffset;
	}

	public void setThumbTintList(ColorStateList tint) {}

	public void setThumb(Drawable thumb) {}
}
