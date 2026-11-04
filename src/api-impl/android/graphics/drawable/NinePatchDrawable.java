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
		setNinePatch(nativeCreate(path));
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
}
