package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;

public class DrawableContainer extends Drawable {

	private DrawableContainerState state;
	private int curIndex = -1;

	/* passed on to the current child, as in AOSP */
	private boolean hasColorFilter;
	private boolean hasTintList;
	private ColorStateList tintList;
	private boolean hasTintMode;
	private PorterDuff.Mode tintMode;

	/* redraws the container when its current child changes, e.g. its tint on a state change */
	private final Callback childCallback = new Callback() {
		@Override
		public void invalidateDrawable(Drawable who) {
			if (curIndex != -1 && who == state.drawables[curIndex])
				invalidateSelf();
		}

		@Override
		public void scheduleDrawable(Drawable who, Runnable what, long when) {
			if (curIndex != -1 && who == state.drawables[curIndex])
				scheduleSelf(what, when);
		}

		@Override
		public void unscheduleDrawable(Drawable who, Runnable what) {
			unscheduleSelf(what);
		}
	};

	protected native long native_constructor();
	protected native void native_selectChild(long container, long child);

	public DrawableContainer() {
		setPaintable(native_constructor());
	}

	public boolean selectDrawable(int idx) {
		if (idx >= 0 && idx < state.childCount && idx != curIndex && state.drawables[idx] != null) {
			curIndex = idx;
			Drawable d = state.drawables[idx];
			if (hasColorFilter)
				d.setColorFilter(getColorFilter());
			if (hasTintList)
				d.setTintList(tintList);
			if (hasTintMode)
				d.setTintMode(tintMode);
			d.setState(getState());
			d.setLevel(getLevel());
			d.setBounds(getBounds());
			d.setCallback(childCallback);
			native_selectChild(paintable, d.paintable);
			invalidateSelf();
			return true;
		}
		return false;
	}

	protected void setConstantState(DrawableContainerState state) {
		this.state = state;
	}

	@Override
	public ConstantState getConstantState() {
		return state;
	}

	public static class DrawableContainerState extends ConstantState {

		private Drawable drawables[] = new Drawable[10];
		private int childCount = 0;
		private DrawableContainer owner;

		public DrawableContainerState(DrawableContainerState orig, DrawableContainer owner, Resources res) {
			this.owner = owner;
			if (orig != null) {
				/* new children, so that a color filter or tint of the copy stays its own */
				drawables = new Drawable[orig.drawables.length];
				childCount = orig.childCount;
				for (int i = 0; i < childCount; i++) {
					Drawable d = orig.drawables[i];
					ConstantState cs = d != null ? d.getConstantState() : null;
					drawables[i] = cs != null ? cs.newDrawable(res) : d;
				}
			}
		}

		public int getCapacity() {
			return drawables.length;
		}

		public int getChildCount() {
			return childCount;
		}

		public Drawable[] getChildren() {
			return drawables;
		}

		public Drawable getChild(int idx) {
			return drawables[idx];
		}

		public int addChild(Drawable dr) {
			if (childCount >= drawables.length) {
				growArray(drawables.length, drawables.length * 2);
			}
			drawables[childCount] = dr;
			return childCount++;
		}

		public void growArray(int oldSize, int newSize) {
			Drawable[] newDrawables = new Drawable[newSize];
			System.arraycopy(drawables, 0, newDrawables, 0, oldSize);
			drawables = newDrawables;
		}

		@Override
		public Drawable newDrawable(Resources res) {
			return owner;
		}

		@Override
		public Drawable newDrawable() {
			return owner;
		}

		@Override
		public int getChangingConfigurations() {
			return owner.getChangingConfigurations();
		}
	}

	@Override
	public void draw(Canvas canvas) {
		if (curIndex != -1)
			state.drawables[curIndex].draw(canvas);
	}

	@Override
	public int getIntrinsicHeight() {
		return curIndex != -1 ? state.drawables[curIndex].getIntrinsicHeight() : -1;
	}

	@Override
	public int getIntrinsicWidth() {
		return curIndex != -1 ? state.drawables[curIndex].getIntrinsicWidth() : -1;
	}

	@Override
	public void setBounds(int left, int top, int right, int bottom) {
		super.setBounds(left, top, right, bottom);
		if (curIndex != -1)
			state.drawables[curIndex].setBounds(left, top, right, bottom);
	}

	@Override
	protected boolean onStateChange(int[] stateSet) {
		return curIndex != -1 && state.drawables[curIndex].setState(stateSet);
	}

	@Override
	protected boolean onLevelChange(int level) {
		return curIndex != -1 && state.drawables[curIndex].setLevel(level);
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		super.setColorFilter(colorFilter);
		hasColorFilter = true;
		if (curIndex != -1)
			state.drawables[curIndex].setColorFilter(colorFilter);
	}

	@Override
	public void setTintList(ColorStateList tint) {
		hasTintList = true;
		tintList = tint;
		if (curIndex != -1)
			state.drawables[curIndex].setTintList(tint);
	}

	@Override
	public void setTintMode(PorterDuff.Mode mode) {
		hasTintMode = true;
		tintMode = mode;
		if (curIndex != -1)
			state.drawables[curIndex].setTintMode(mode);
	}

	@Override
	public boolean isStateful() {
		if (state == null)
			return false;
		for (int i = 0; i < state.childCount; i++) {
			if (state.drawables[i] != null && state.drawables[i].isStateful())
				return true;
		}
		return false;
	}

	@Override
	public Drawable getCurrent() {
		return curIndex != -1 ? state.drawables[curIndex] : this;
	}

	public void setEnterFadeDuration(int duration) {}

	public void setExitFadeDuration(int duration) {}
}
