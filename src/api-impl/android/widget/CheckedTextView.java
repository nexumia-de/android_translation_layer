package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

public class CheckedTextView extends TextView implements Checkable {

	public CheckedTextView(Context context) {
		super(context);
	}

	public CheckedTextView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	private boolean checked;

	public void setChecked(boolean checked) {
		if (this.checked != checked) {
			this.checked = checked;
			refreshDrawableState();
		}
	}

	public boolean isChecked() {
		return checked;
	}

	private static final int[] CHECKED_STATE_SET = {com.android.internal.R.attr.state_checked};

	/* Copyright (C) 2007 The Android Open Source Project */
	@Override
	protected int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked()) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}

	public void toggle() {
		setChecked(!checked);
	}

	public void setCheckMarkDrawable(Drawable d) {}

	public Drawable getCheckMarkDrawable() {
		return null;
	}
}
