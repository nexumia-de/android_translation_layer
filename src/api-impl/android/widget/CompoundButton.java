package android.widget;

import android.atl.annotation.Export;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import com.android.internal.R;

public abstract class CompoundButton extends Button implements Checkable {
	Drawable button_drawable = null;
	private ColorStateList button_tint;
	private boolean draws_itself;
	public Drawable mButtonDrawable; // directly accessed by androidx

	private static final int[] CHECKED_STATE_SET = {R.attr.state_checked};

	public CompoundButton(Context context) {
		this(context, null);
	}

	public CompoundButton(Context context, AttributeSet attributeSet) {
		this(context, attributeSet, 0);
	}

	public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		TypedArray typedArray = context.obtainStyledAttributes(
		    attrs, R.styleable.CompoundButton, defStyleAttr, defStyleRes);
		if (typedArray.hasValue(R.styleable.CompoundButton_checked)) {
			this.setChecked(typedArray.getBoolean(R.styleable.CompoundButton_checked, false));
		}
		/* ATL's own switch stands in for the button only if the class does not draw itself:
		 * AndroidX's SwitchCompat (MaterialSwitch) draws track and thumb in onDraw, and GTK's switch
		 * showed below them */
		if (!(this instanceof CheckBox || this instanceof RadioButton || this instanceof Switch) && drawsItself()) {
			draws_itself = true;
			native_hideButton(widget);
		}
		if (typedArray.hasValue(R.styleable.CompoundButton_buttonTint)) {
			setButtonTintList(typedArray.getColorStateList(R.styleable.CompoundButton_buttonTint));
		}
		typedArray.recycle();
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	@Override
	public native void native_setText(long widget, String text);

	public static interface OnCheckedChangeListener {
		@Export
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
	}

	public native void setOnCheckedChangeListener(OnCheckedChangeListener listener);

	@Override
	public native void setChecked(boolean checked);

	@Override
	public native boolean isChecked();

	@Override
	/* Copyright (C) 2007 The Android Open Source Project */
	protected int[] onCreateDrawableState(int extraSpace) {
		final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
		if (isChecked()) {
			mergeDrawableStates(drawableState, CHECKED_STATE_SET);
		}
		return drawableState;
	}

	@Override
	/* Copyright (C) 2007 The Android Open Source Project */
	protected void drawableStateChanged() {
		super.drawableStateChanged();

		final Drawable buttonDrawable = button_drawable;
		if (buttonDrawable != null && buttonDrawable.isStateful()
		    && buttonDrawable.setState(getDrawableState())) {
			invalidateDrawable(buttonDrawable);
		}
	}

	public void toggle() {
		setChecked(!isChecked());
	}

	// following methods are overridden to prevent calling incompatible methods from superclasses
	@Override
	public void setOnClickListener(final OnClickListener l) {}
	@Override
	public void setTextColor(int color) {}
	@Override
	public void setTextSize(float size) {}
	@Override
	public CharSequence getText() {
		return label;
	}

	/* Button's getText reads a GtkButton, which a CompoundButton is not */
	private CharSequence label = "";

	@Override
	public void setText(CharSequence text) {
		label = text == null ? "" : text;
		native_setText(widget, label.toString());
	}

	/* as TextView.onMeasure in AOSP, which adds the compound padding: SwitchCompat reserves the
	 * room for its track there */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (!draws_itself)
			return;
		int width = getMeasuredWidth() + getCompoundPaddingLeft() + getCompoundPaddingRight();
		setMeasuredDimension(resolveSizeAndState(width, widthMeasureSpec, 0), getMeasuredHeightAndState());
	}

	/* the check box or radio button takes the tint's colours for checked and unchecked; Material's
	 * check boxes were GTK's accent colour instead of the app's */
	public void setButtonTintList(ColorStateList list) {
		button_tint = list;
		if (button_drawable != null)
			button_drawable.setTintList(list);
		applyTintColors(list);
	}

	private void applyTintColors(ColorStateList list) {
		if (list == null)
			return;
		int checked = list.getColorForState(new int[] {R.attr.state_checked, R.attr.state_enabled}, list.getDefaultColor());
		int unchecked = list.getColorForState(new int[] {R.attr.state_enabled}, list.getDefaultColor());
		native_setButtonTint(widget, checked, unchecked);
	}
	private native void native_setButtonTint(long widget, int checked, int unchecked);
	private native void native_setButtonDrawable(long widget, long paintable);

	private boolean drawsItself() {
		for (Class<?> c = getClass(); c != CompoundButton.class; c = c.getSuperclass()) {
			try {
				c.getDeclaredMethod("onDraw", android.graphics.Canvas.class);
				return true;
			} catch (NoSuchMethodException e) {
			}
		}
		return false;
	}
	private native void native_hideButton(long widget);

	/* A check box or radio button shows GTK's indicator for its usual state list drawables (checked
	 * or not). A drawable without states is an icon the app shows instead (CoMaps' travel modes are
	 * radio buttons with icons): it replaces the indicator, tinted like on Android */
	public void setButtonDrawable(Drawable drawable) {
		/* MaterialCheckBox keeps its tint to itself (its setButtonTintList does not call this one)
		 * and sets a newly tinted drawable instead: its getButtonTintList has the colours */
		if (getButtonTintList() != button_tint)
			applyTintColors(getButtonTintList());
		if (drawable != null && (this instanceof CheckBox || this instanceof RadioButton)
		    && !(drawable instanceof android.graphics.drawable.DrawableContainer)
		    && !(drawable instanceof android.graphics.drawable.LayerDrawable)) {
			if (drawable != button_drawable) {
				drawable = drawable.mutate();
				if (button_tint != null)
					drawable.setTintList(button_tint);
				drawable.setCallback(this);
				if (drawable.isStateful())
					drawable.setState(getDrawableState());
			}
			native_setButtonDrawable(widget, drawable.paintable);
		} else if (button_drawable != null) {
			native_setButtonDrawable(widget, 0);
		}
		button_drawable = drawable;
	}

	public Drawable getButtonDrawable() {
		return button_drawable;
	}

	public ColorStateList getButtonTintList() {
		return button_tint;
	}

	@Override
	public void setCompoundDrawables(Drawable left, Drawable top, Drawable right, Drawable bottom) {}

	public PorterDuff.Mode getButtonTintMode() {
		return null;
	}
}
