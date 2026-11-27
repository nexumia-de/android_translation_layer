package android.widget;

import android.atl.annotation.Export;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;

public class SeekBar extends AbsSeekBar {

	public SeekBar(Context context) {
		super(context);
	}

	public SeekBar(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
		initColors(context);
	}

	/* AppCompatSeekBar calls this one */
	public SeekBar(Context context, AttributeSet attributeSet, int defStyleAttr) {
		super(context, attributeSet, defStyleAttr);
		initColors(context);
	}

	private void initColors(Context context) {
		/* the colours AppCompat and the platform tint the track and thumb with */
		TypedArray a = context.obtainStyledAttributes(new int[] {com.android.internal.R.attr.colorControlActivated, com.android.internal.R.attr.colorControlNormal});
		progress_color = thumb_color = a.getColor(0, 0);
		/* the unfilled track: colorControlNormal at the alpha of AOSP's scrubber track */
		int normal = a.getColor(1, 0);
		track_color = (normal & 0xffffff) | (((normal >>> 24) * 0x42 / 0xff) << 24);
		a.recycle();
		if (progress_color != 0)
			native_setColors(widget, progress_color, thumb_color, track_color);
	}

	private int progress_color, thumb_color, track_color;
	private native void native_setColors(long widget, int progress, int thumb, int track);

	@Override
	public void setThumbTintList(ColorStateList tint) {
		if (tint == null)
			return;
		thumb_color = tint.getDefaultColor();
		native_setColors(widget, progress_color, thumb_color, track_color);
	}

	@Override
	public void setProgressTintList(ColorStateList tint) {
		if (tint == null)
			return;
		progress_color = tint.getDefaultColor();
		native_setColors(widget, progress_color, thumb_color, track_color);
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	@Override
	protected native void native_setProgress(long widget, float fraction);
	protected native int native_getProgress(long widget);
	protected native void native_setMax(long widget, int max);
	@Override
	public void native_setIndeterminate(boolean indeterminate) {}

	@Override
	public void setMax(int max) {
		this.max = max;
		native_setMax(widget, max);
	}
	/* as on Android, a change from code reaches the listener too, with fromUser false; changes by
	 * the user come from GTK's change-value (android_widget_SeekBar.c) */
	@Override
	public void setProgress(int progress) {
		progress = Math.max(0, Math.min(progress, max));
		boolean changed = progress != getProgress();
		this.progress = progress;
		native_setProgress(widget, progress);
		if (changed && changeListener != null)
			changeListener.onProgressChanged(this, progress, false);
	}

	private OnSeekBarChangeListener changeListener;

	public void setOnSeekBarChangeListener(final OnSeekBarChangeListener l) {
		changeListener = l;
		native_setOnSeekBarChangeListener(l);
	}

	private native void native_setOnSeekBarChangeListener(final OnSeekBarChangeListener l);

	@Override
	public int getProgress() {
		return native_getProgress(widget);
	}

	public static interface OnSeekBarChangeListener {
		@Export
		public void onStartTrackingTouch(SeekBar seekBar);

		@Export
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);

		@Export
		public void onStopTrackingTouch(SeekBar seekBar);
	}
}
