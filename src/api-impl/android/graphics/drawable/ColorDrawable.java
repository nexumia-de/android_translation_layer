package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.util.AttributeSet;
import com.android.internal.R;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class ColorDrawable extends Drawable {

	private int color;
	private Paint paint;

	public ColorDrawable() {
		this.paint = new Paint();
	}

	public ColorDrawable(int color) {
		this.color = color;
		this.paint = new Paint();
		this.paint.setColor(color);
	}

	/**
	 * The color after the color filter or tint. A filter on a solid color is a color transform,
	 * so views can use the result as their native background color.
	 *
	 * @hide
	 */
	public int getFilteredColor() {
		ColorFilter filter = getEffectiveColorFilter();
		float[] m = filter != null ? filter.toColorMatrix() : null;
		if (m == null)
			return color;
		int[] in = {Color.red(color), Color.green(color), Color.blue(color), Color.alpha(color)};
		int[] out = new int[4];
		for (int row = 0; row < 4; row++) {
			float v = m[row * 5 + 4];
			for (int col = 0; col < 4; col++)
				v += m[row * 5 + col] * in[col];
			out[row] = Math.max(0, Math.min(255, Math.round(v)));
		}
		return Color.argb(out[3], out[0], out[1], out[2]);
	}

	/* <color android:color="..."/> as a drawable resource, e.g. the background of an adaptive icon */
	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme) throws XmlPullParserException, IOException {
		super.inflate(r, parser, attrs, theme);
		final TypedArray a = obtainAttributes(r, theme, attrs, R.styleable.ColorDrawable);
		ColorStateList csl = a.getColorStateList(R.styleable.ColorDrawable_color);
		if (csl != null)
			setColor(csl.getDefaultColor());
		a.recycle();
	}

	public int getColor() {
		return color;
	}

	@Override
	public void draw(Canvas canvas) {
		canvas.drawRect(getBounds(), paint);
	}

	public void setColor(int color) {
		this.color = color;
		paint.setColor(getFilteredColor());
		invalidateSelf();
	}

	@Override
	void applyColorFilter(ColorFilter filter) {
		paint.setColor(getFilteredColor());
	}

	/* a new drawable of the same color, with its own color filter and tint */
	@Override
	public ConstantState getConstantState() {
		return new ConstantState() {
			@Override
			public Drawable newDrawable() {
				return new ColorDrawable(color);
			}

			@Override
			public Drawable newDrawable(Resources res) {
				return new ColorDrawable(color);
			}

			@Override
			public int getChangingConfigurations() {
				return 0;
			}
		};
	}
}
