package android.graphics.drawable;

import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/* A drawable rotated by an angle between fromDegrees and toDegrees chosen by
 * its level, around a pivot (default: the centre), as on Android. */
public class RotateDrawable extends DrawableWrapper {
	private static final String NS = "http://schemas.android.com/apk/res/android";
	private static final int MAX_LEVEL = 10000;

	private float mFromDegrees = 0;
	private float mToDegrees = 360;
	private float mPivotX = 0.5f;
	private float mPivotY = 0.5f;
	private boolean mPivotXRel = true;
	private boolean mPivotYRel = true;

	public RotateDrawable() {
		super(new RotateState(null, null), null);
	}

	@Override
	public void inflate(Resources r, XmlPullParser parser, AttributeSet attrs, Theme theme) throws XmlPullParserException, IOException {
		mFromDegrees = attrs.getAttributeFloatValue(NS, "fromDegrees", mFromDegrees);
		mToDegrees = attrs.getAttributeFloatValue(NS, "toDegrees", mToDegrees);
		String px = attrs.getAttributeValue(NS, "pivotX");
		if (px != null) {
			mPivotXRel = px.endsWith("%");
			mPivotX = mPivotXRel ? Float.parseFloat(px.substring(0, px.length() - 1)) / 100 : Float.parseFloat(px);
		}
		String py = attrs.getAttributeValue(NS, "pivotY");
		if (py != null) {
			mPivotYRel = py.endsWith("%");
			mPivotY = mPivotYRel ? Float.parseFloat(py.substring(0, py.length() - 1)) / 100 : Float.parseFloat(py);
		}
		super.inflate(r, parser, attrs, theme);
	}

	public float getCurrentDegrees() {
		return mFromDegrees + (mToDegrees - mFromDegrees) * getLevel() / MAX_LEVEL;
	}

	public void setFromDegrees(float fromDegrees) {
		mFromDegrees = fromDegrees;
		invalidateSelf();
	}

	public float getFromDegrees() {
		return mFromDegrees;
	}

	public void setToDegrees(float toDegrees) {
		mToDegrees = toDegrees;
		invalidateSelf();
	}

	public float getToDegrees() {
		return mToDegrees;
	}

	@Override
	public void draw(Canvas canvas) {
		Drawable d = getDrawable();
		if (d == null)
			return;
		Rect bounds = d.getBounds();
		float px = mPivotXRel ? bounds.width() * mPivotX : mPivotX;
		float py = mPivotYRel ? bounds.height() * mPivotY : mPivotY;
		int saveCount = canvas.save();
		canvas.rotate(getCurrentDegrees(), px + bounds.left, py + bounds.top);
		d.draw(canvas);
		canvas.restoreToCount(saveCount);
	}

	static final class RotateState extends DrawableWrapper.DrawableWrapperState {
		RotateState(DrawableWrapperState orig, Resources res) {
			super(orig, res);
		}

		@Override
		public Drawable newDrawable(Resources res) {
			throw new UnsupportedOperationException("RotateDrawable: newDrawable");
		}
	}
}
