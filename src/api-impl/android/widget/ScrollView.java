package android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

public class ScrollView extends FrameLayout {
	private boolean fillViewport;

	public ScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.ScrollView);
		fillViewport = a.getBoolean(com.android.internal.R.styleable.ScrollView_fillViewport, false);
		a.recycle();
	}

	public ScrollView(Context context) {
		super(context);
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	@Override
	protected native void native_addView(long widget, long child, int index, ViewGroup.LayoutParams params);
	@Override
	protected native void native_removeView(long widget, long child);

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		return native_gtkEventPropagation();
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		return native_gtkEventPropagation();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = 0;
		int height = 0;
		if (getChildCount() > 0) {
			View child = getChildAt(0);
			ViewGroup.LayoutParams lp = child.getLayoutParams();
			int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, 0, lp.width);
			int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
			width = child.getMeasuredWidth();
			height = child.getMeasuredHeight();
		}
		setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));

		// like Android: with fillViewport a shorter child is stretched to the height of the scroll view
		if (!fillViewport || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED || getChildCount() == 0)
			return;
		View child = getChildAt(0);
		ViewGroup.LayoutParams lp = child.getLayoutParams();
		int widthPadding = getPaddingLeft() + getPaddingRight();
		int heightPadding = getPaddingTop() + getPaddingBottom();
		if (lp instanceof ViewGroup.MarginLayoutParams) { // API 23 and later
			ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams)lp;
			widthPadding += mlp.leftMargin + mlp.rightMargin;
			heightPadding += mlp.topMargin + mlp.bottomMargin;
		}
		int desiredHeight = getMeasuredHeight() - heightPadding;
		if (child.getMeasuredHeight() < desiredHeight) {
			int childWidthMeasureSpec = getChildMeasureSpec(widthMeasureSpec, widthPadding, lp.width);
			int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(desiredHeight, MeasureSpec.EXACTLY);
			child.measure(childWidthMeasureSpec, childHeightMeasureSpec);
		}
	}

	public boolean isFillViewport() {
		return fillViewport;
	}

	public void setFillViewport(boolean fillViewport) {
		if (this.fillViewport != fillViewport) {
			this.fillViewport = fillViewport;
			requestLayout();
		}
	}

	public boolean fullScroll(int direction) {
		return true;
	}
}
