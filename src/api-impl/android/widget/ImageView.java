package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

public class ImageView extends View {

	private Bitmap bitmap = null;
	private ScaleType scaleType = ScaleType.FIT_CENTER;
	private Drawable drawable = null;
	private ColorFilter colorFilter = null;
	private ColorStateList tintList = null;
	private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
	private int level = 0;
	private boolean adjustViewBounds = false;
	private int maxWidth = Integer.MAX_VALUE;
	private int maxHeight = Integer.MAX_VALUE;

	public ImageView(Context context) {
		this(context, null);
	}

	public ImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public ImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		haveCustomMeasure = false;
		TypedArray a = context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.ImageView, defStyleAttr, defStyleRes);
		if (a.hasValue(com.android.internal.R.styleable.ImageView_tint)) {
			tintList = a.getColorStateList(com.android.internal.R.styleable.ImageView_tint);
			tintMode = PorterDuff.Mode.values()[a.getInt(com.android.internal.R.styleable.ImageView_tintMode, PorterDuff.Mode.SRC_IN.nativeInt)];
		}
		drawable = a.getDrawable(com.android.internal.R.styleable.ImageView_src);
		if (drawable != null) {
			drawable = applyImageFilters(drawable);
			drawable.setCallback(this);
			native_setDrawable(widget, drawable.paintable);
		}
		if (a.hasValue(com.android.internal.R.styleable.ImageView_scaleType))
			setScaleType(scaletype_from_int[a.getInt(com.android.internal.R.styleable.ImageView_scaleType, 3 /*CENTER*/)]);
		adjustViewBounds = a.getBoolean(com.android.internal.R.styleable.ImageView_adjustViewBounds, false);
		maxWidth = a.getDimensionPixelSize(com.android.internal.R.styleable.ImageView_maxWidth, Integer.MAX_VALUE);
		maxHeight = a.getDimensionPixelSize(com.android.internal.R.styleable.ImageView_maxHeight, Integer.MAX_VALUE);
		a.recycle();
	}

	/* as ImageView.onMeasure in AOSP: the image's own size, not GTK's height for width,
	 * which would scale every image up to the width it is given (as if adjustViewBounds) */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int w = 0;
		int h = 0;
		float desiredAspect = 0.0f;
		boolean resizeWidth = false;
		boolean resizeHeight = false;
		final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
		final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
		if (drawable != null || bitmap != null) {
			w = drawable != null ? drawable.getIntrinsicWidth() : bitmap.getWidth();
			h = drawable != null ? drawable.getIntrinsicHeight() : bitmap.getHeight();
			if (w <= 0)
				w = 1;
			if (h <= 0)
				h = 1;
			if (adjustViewBounds) {
				resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
				resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;
				desiredAspect = (float)w / (float)h;
			}
		}
		final int pleft = getPaddingLeft();
		final int pright = getPaddingRight();
		final int ptop = getPaddingTop();
		final int pbottom = getPaddingBottom();
		int widthSize;
		int heightSize;
		if (resizeWidth || resizeHeight) {
			widthSize = resolveAdjustedSize(w + pleft + pright, maxWidth, widthMeasureSpec);
			heightSize = resolveAdjustedSize(h + ptop + pbottom, maxHeight, heightMeasureSpec);
			final float actualAspect = (float)(widthSize - pleft - pright) / (heightSize - ptop - pbottom);
			if (Math.abs(actualAspect - desiredAspect) > 0.0000001) {
				boolean done = false;
				if (resizeWidth) {
					int newWidth = (int)(desiredAspect * (heightSize - ptop - pbottom)) + pleft + pright;
					if (!resizeHeight)
						widthSize = resolveAdjustedSize(newWidth, maxWidth, widthMeasureSpec);
					if (newWidth <= widthSize) {
						widthSize = newWidth;
						done = true;
					}
				}
				if (!done && resizeHeight) {
					int newHeight = (int)((widthSize - pleft - pright) / desiredAspect) + ptop + pbottom;
					if (!resizeWidth)
						heightSize = resolveAdjustedSize(newHeight, maxHeight, heightMeasureSpec);
					if (newHeight <= heightSize)
						heightSize = newHeight;
				}
			}
		} else {
			w = Math.max(w + pleft + pright, getSuggestedMinimumWidth());
			h = Math.max(h + ptop + pbottom, getSuggestedMinimumHeight());
			widthSize = resolveSizeAndState(w, widthMeasureSpec, 0);
			heightSize = resolveSizeAndState(h, heightMeasureSpec, 0);
		}
		setMeasuredDimension(widthSize, heightSize);
	}

	private static int resolveAdjustedSize(int desiredSize, int maxSize, int measureSpec) {
		int specSize = MeasureSpec.getSize(measureSpec);
		switch (MeasureSpec.getMode(measureSpec)) {
			case MeasureSpec.UNSPECIFIED:
				return Math.min(desiredSize, maxSize);
			case MeasureSpec.AT_MOST:
				return Math.min(Math.min(desiredSize, specSize), maxSize);
			default:
				return specSize;
		}
	}

	public void setImageResource(final int resid) {
		if (this.getResources().getString(resid).endsWith(".xml")) {
			setImageDrawable(getContext().getDrawable(resid));
			return;
		}
		if (drawable != null)
			drawable.setCallback(null);
		drawable = null;
		bitmap = BitmapFactory.decodeResource(this.getResources(), resid);
		native_setDrawable(widget, bitmap != null ? bitmap.getTexture() : 0);
		/* as on Android: the new image may have another size */
		requestLayout();
	}
	public void setAdjustViewBounds(boolean adjustViewBounds) {
		if (this.adjustViewBounds != adjustViewBounds) {
			this.adjustViewBounds = adjustViewBounds;
			requestLayout();
		}
	}

	public boolean getAdjustViewBounds() {
		return adjustViewBounds;
	}

	public void setScaleType(ScaleType scaleType) {
		this.scaleType = scaleType;
		native_setScaleType(widget, scaleType.nativeInt);
	}

	public ScaleType getScaleType() {
		return scaleType;
	}

	public Drawable getDrawable() {
		return drawable;
	}

	public void setImageDrawable(Drawable drawable) {
		if (drawable != null)
			drawable = applyImageFilters(drawable);
		if (this.drawable != null) {
			this.drawable.setCallback(null);
		}
		this.drawable = drawable;
		bitmap = null;
		if (drawable != null) {
			drawable.setCallback(this);
		}
		native_setDrawable(widget, drawable != null ? drawable.paintable : 0);
		if (drawable != null && level != 0) {
			drawable.setLevel(level);
		}
		/* as on Android: the new drawable may have another size */
		requestLayout();
	}

	public void setImageLevel(int level) {
		this.level = level;
		if (drawable != null) {
			drawable.setLevel(level);
		}
	}

	public void setImageMatrix(Matrix matrix) {}

	public void setImageURI(Uri uri) {
		Drawable d = null;
		if (uri != null) {
			try (java.io.InputStream is = getContext().getContentResolver().openInputStream(uri)) {
				d = Drawable.createFromStream(is, null);
			} catch (Exception e) {
				Log.w("ImageView", "Unable to open content: " + uri, e);
			}
		}
		setImageDrawable(d);
	}

	public void setImageBitmap(Bitmap bitmap) {
		if (drawable != null)
			drawable.setCallback(null);
		drawable = null;
		this.bitmap = bitmap;
		native_setDrawable(widget, bitmap != null ? bitmap.getTexture() : 0);
		requestLayout();
	}

	/**
	 * Options for scaling the bounds of an image to the bounds of this view.
	 */
	public enum ScaleType {
		/**
		 * Scale using the image matrix when drawing. The image matrix can be set using
		 * {@link ImageView#setImageMatrix(Matrix)}. From XML, use this syntax:
		 * <code>android:scaleType="matrix"</code>.
		 */
		MATRIX(0),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#FILL}.
		 * From XML, use this syntax: <code>android:scaleType="fitXY"</code>.
		 */
		FIT_XY(1),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#START}.
		 * From XML, use this syntax: <code>android:scaleType="fitStart"</code>.
		 */
		FIT_START(2),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#CENTER}.
		 * From XML, use this syntax:
		 * <code>android:scaleType="fitCenter"</code>.
		 */
		FIT_CENTER(3),
		/**
		 * Scale the image using {@link Matrix.ScaleToFit#END}.
		 * From XML, use this syntax: <code>android:scaleType="fitEnd"</code>.
		 */
		FIT_END(4),
		/**
		 * Center the image in the view, but perform no scaling.
		 * From XML, use this syntax: <code>android:scaleType="center"</code>.
		 */
		CENTER(5),
		/**
		 * Scale the image uniformly (maintain the image's aspect ratio) so
		 * that both dimensions (width and height) of the image will be equal
		 * to or larger than the corresponding dimension of the view
		 * (minus padding). The image is then centered in the view.
		 * From XML, use this syntax: <code>android:scaleType="centerCrop"</code>.
		 */
		CENTER_CROP(6),
		/**
		 * Scale the image uniformly (maintain the image's aspect ratio) so
		 * that both dimensions (width and height) of the image will be equal
		 * to or less than the corresponding dimension of the view
		 * (minus padding). The image is then centered in the view.
		 * From XML, use this syntax: <code>android:scaleType="centerInside"</code>.
		 */
		CENTER_INSIDE(7);

		ScaleType(int ni) {
			nativeInt = ni;
		}
		final int nativeInt;
	}
	private final ScaleType[] scaletype_from_int = {
		ScaleType.MATRIX,
		ScaleType.FIT_XY,
		ScaleType.FIT_START,
		ScaleType.FIT_CENTER,
		ScaleType.FIT_END,
		ScaleType.CENTER,
		ScaleType.CENTER_CROP,
		ScaleType.CENTER_INSIDE,
	};

	/* the image tint and color filter, applied to a mutated drawable as in AOSP */
	private Drawable applyImageFilters(Drawable drawable) {
		if (tintList == null && colorFilter == null)
			return drawable;
		drawable = drawable.mutate();
		if (tintList != null) {
			drawable.setTintList(tintList);
			drawable.setTintMode(tintMode);
		}
		if (colorFilter != null)
			drawable.setColorFilter(colorFilter);
		return drawable;
	}

	public final void setColorFilter(int color, PorterDuff.Mode mode) {
		setColorFilter(new PorterDuffColorFilter(color, mode));
	}

	public void setImageTintList(ColorStateList tint) {
		tintList = tint;
		if (drawable != null) {
			if (tint == null)
				drawable.setTintList(null);
			else
				setImageDrawable(drawable);
		}
	}

	public void setImageAlpha(int alpha) {}

	@Deprecated
	public void setAlpha(int alpha) {
		setImageAlpha(alpha);
	}

	public void setMaxWidth(int width) {
		maxWidth = width;
		requestLayout();
	}

	public void setMaxHeight(int height) {
		maxHeight = height;
		requestLayout();
	}

	public void setImageState(int[] state, boolean merge) {}

	public ColorStateList getImageTintList() {
		return tintList;
	}

	public PorterDuff.Mode getImageTintMode() {
		return tintMode;
	}

	public void setImageTintMode(PorterDuff.Mode tintMode) {
		this.tintMode = tintMode;
		if (drawable != null && tintList != null)
			drawable.setTintMode(tintMode);
	}

	public void setCropToPadding(boolean crop) {}

	public void setColorFilter(int color) {
		setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
	}

	public void setColorFilter(ColorFilter cf) {
		if (colorFilter != cf) {
			colorFilter = cf;
			if (drawable != null) {
				if (cf == null)
					drawable.setColorFilter(null);
				else
					setImageDrawable(drawable);
			}
		}
	}

	public ColorFilter getColorFilter() {
		return colorFilter;
	}

	public Matrix getImageMatrix() {
		return Matrix.IDENTITY_MATRIX;
	}

	public void clearColorFilter() {
		setColorFilter(null);
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	protected native void native_setDrawable(long widget, long paintable);
	protected native void native_setScaleType(long widget, int scale_type);
}
