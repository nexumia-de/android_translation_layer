package android.widget;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.util.AttributeSet;

/* plain object as in AOSP (not a View); no glow is drawn */
public class EdgeEffect {
	public static final BlendMode DEFAULT_BLEND_MODE = BlendMode.SRC_ATOP;

	private int color;
	private BlendMode blendMode = DEFAULT_BLEND_MODE;

	public EdgeEffect(Context context) {}

	public EdgeEffect(Context context, AttributeSet attributeSet) {}

	public void setSize(int width, int height) {}
	public void onPull(float deltaDistance) {}
	public void onPull(float deltaDistance, float displacement) {}
	public float onPullDistance(float deltaDistance, float displacement) { return 0; }
	public boolean isFinished() { return true; }
	public void onRelease() {}
	public void onAbsorb(int velocity) {}
	public int getColor() { return color; }
	public void setColor(int color) { this.color = color; }
	public BlendMode getBlendMode() { return blendMode; }
	public void setBlendMode(BlendMode blendMode) { this.blendMode = blendMode; }
	public float getDistance() { return 0; }
	public int getMaxHeight() { return 0; }
	public void finish() {}

	public boolean draw(Canvas canvas) {
		return false;
	}
}
