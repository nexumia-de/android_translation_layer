package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class BitmapDrawable extends Drawable {

	private Bitmap bitmap;
	/* the screen's density, as in AOSP: the size is the bitmap's scaled from its density to this */
	private int targetDensity = DisplayMetrics.DENSITY_DEFAULT;

	public BitmapDrawable() {
	}

	public BitmapDrawable(Resources res, Bitmap bitmap) {
		this.bitmap = bitmap;
		if (res != null)
			targetDensity = res.getDisplayMetrics().densityDpi;
		if (bitmap != null) {
			setFilteredPaintable(bitmap.getTexture());
			updateScale();
		}
	}

	/* the paintable reports the scaled size too (ImageView's GtkPicture), but keeps all pixels */
	private void updateScale() {
		if (bitmap == null || bitmap.getWidth() == 0 || bitmap.getHeight() == 0)
			return;
		native_setScale(paintable, getIntrinsicWidth() / (float)bitmap.getWidth(), getIntrinsicHeight() / (float)bitmap.getHeight());
	}

	public BitmapDrawable(Bitmap bitmap) {
		this(null, bitmap);
	}

	/* a new drawable for the same bitmap, with its own color filter and tint */
	@Override
	public ConstantState getConstantState() {
		return new ConstantState() {
			@Override
			public Drawable newDrawable() {
				BitmapDrawable d = new BitmapDrawable(null, bitmap);
				d.setTargetDensity(targetDensity);
				return d;
			}

			@Override
			public Drawable newDrawable(Resources res) {
				return res != null ? new BitmapDrawable(res, bitmap) : newDrawable();
			}

			@Override
			public int getChangingConfigurations() {
				return 0;
			}
		};
	}

	public Bitmap getBitmap() {
		return bitmap;
	}

	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme)
	    throws XmlPullParserException, IOException {
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.BitmapDrawable);
		if (a.hasValue(R.styleable.BitmapDrawable_src)) {
			BitmapDrawable dr = null;
			try {
				// TODO: getDrawable() might pick up another <bitmap> XML,
				// but we should reject anything other than image files.
				dr = (BitmapDrawable)a.getDrawable(R.styleable.BitmapDrawable_src);
			} catch (java.lang.Exception e) {
				e.printStackTrace();
			}
			if (dr != null)
				bitmap = dr.bitmap;
		}
		a.recycle();
		if (bitmap == null)
			throw new XmlPullParserException("<bitmap> needs a valid `src' attribute");
		if (r != null)
			targetDensity = r.getDisplayMetrics().densityDpi;
		setFilteredPaintable(bitmap.getTexture());
		updateScale();
	}

	@Override
	public int getIntrinsicWidth() {
		if (bitmap == null)
			return 0;
		return bitmap.getScaledWidth(targetDensity);
	}

	@Override
	public int getIntrinsicHeight() {
		if (bitmap == null)
			return 0;
		return bitmap.getScaledHeight(targetDensity);
	}

	public void setTileModeX(Shader.TileMode mode) {}

	public Paint getPaint() {
		return new Paint();
	}

	public void setGravity(int g) {}

	public void setTargetDensity(android.graphics.Canvas canvas) {
		setTargetDensity(canvas.getDensity());
	}

	public void setTargetDensity(DisplayMetrics metrics) {
		setTargetDensity(metrics.densityDpi);
	}

	public void setTargetDensity(int density) {
		if (density == 0)
			density = DisplayMetrics.DENSITY_DEFAULT;
		if (targetDensity != density) {
			targetDensity = density;
			updateScale();
			invalidateSelf();
		}
	}

	private static native void native_setScale(long paintable, float scaleX, float scaleY);
}
