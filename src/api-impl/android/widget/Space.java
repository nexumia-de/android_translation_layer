package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

/* as on Android: draws nothing and, unlike View, takes only its minimum size under an AT_MOST spec */
public class Space extends View {

	public Space(Context context) {
		super(context);
		if (getVisibility() == VISIBLE)
			setVisibility(INVISIBLE);
	}

	public Space(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public Space(Context context, AttributeSet attributeSet, int defStyleAttr) {
		this(context, attributeSet, defStyleAttr, 0);
	}

	public Space(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
		if (getVisibility() == VISIBLE)
			setVisibility(INVISIBLE);
	}

	@Override
	public void draw(Canvas canvas) {}

	private static int getDefaultSize2(int size, int measureSpec) {
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);
		switch (specMode) {
			case MeasureSpec.AT_MOST:
				return Math.min(size, specSize);
			case MeasureSpec.EXACTLY:
				return specSize;
			default:
				return size;
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(getDefaultSize2(getSuggestedMinimumWidth(), widthMeasureSpec),
		                     getDefaultSize2(getSuggestedMinimumHeight(), heightMeasureSpec));
	}
}
