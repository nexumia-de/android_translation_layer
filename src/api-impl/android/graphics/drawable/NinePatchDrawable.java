package android.graphics.drawable;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;

public class NinePatchDrawable extends Drawable {

	/* the native nine-patch paintable, shared by drawables from the same constant state */
	private long ninePatch;

	public NinePatchDrawable(Resources res, Bitmap bitmap, byte[] data, Rect padding, String name) {
		if (bitmap != null && data != null)
			setNinePatch(nativeCreate(data, bitmap.getTexture()));
	}

	NinePatchDrawable(String path) {
		this(path, 1);
	}

	/* scale: the screen's density over the image's, as for bitmaps */
	NinePatchDrawable(String path, float scale) {
		setNinePatch(nativeCreate(path));
		if (ninePatch != 0 && scale > 0 && scale != 1)
			native_setScale(ninePatch, scale);
	}

	@Override
	public int getIntrinsicWidth() {
		return ninePatch != 0 ? native_getIntrinsicWidth(ninePatch) : -1;
	}

	@Override
	public int getIntrinsicHeight() {
		return ninePatch != 0 ? native_getIntrinsicHeight(ninePatch) : -1;
	}

	@Override
	public int getMinimumWidth() {
		return Math.max(getIntrinsicWidth(), 0);
	}

	@Override
	public int getMinimumHeight() {
		return Math.max(getIntrinsicHeight(), 0);
	}

	@Override
	public boolean getPadding(Rect padding) {
		if (ninePatch == 0)
			return super.getPadding(padding);
		int[] p = new int[4];
		boolean any = native_getPadding(ninePatch, p);
		padding.set(p[0], p[1], p[2], p[3]);
		return any;
	}

	private NinePatchDrawable(long ninePatch) {
		native_ref(ninePatch);
		this.ninePatch = ninePatch;
		setFilteredPaintable(ninePatch);
	}

	private void setNinePatch(long ninePatch) {
		this.ninePatch = ninePatch;
		setFilteredPaintable(ninePatch);
	}

	/* a new drawable for the same nine-patch, with its own color filter and tint */
	@Override
	public ConstantState getConstantState() {
		if (ninePatch == 0)
			return super.getConstantState();
		return new ConstantState() {
			@Override
			public Drawable newDrawable() {
				return new NinePatchDrawable(ninePatch);
			}

			@Override
			public Drawable newDrawable(Resources res) {
				return new NinePatchDrawable(ninePatch);
			}

			@Override
			public int getChangingConfigurations() {
				return 0;
			}
		};
	}

	@SuppressWarnings("removal")
	@Override
	protected void finalize() throws Throwable {
		try {
			if (ninePatch != 0)
				native_unref(ninePatch);
			ninePatch = 0;
		} finally {
			super.finalize();
		}
	}

	private native long nativeCreate(byte[] data, long texture);
	private native long nativeCreate(String path);
	private static native void native_setScale(long ninePatch, float scale);
	private static native int native_getIntrinsicWidth(long ninePatch);
	private static native int native_getIntrinsicHeight(long ninePatch);
	private static native boolean native_getPadding(long ninePatch, int[] out);
}
