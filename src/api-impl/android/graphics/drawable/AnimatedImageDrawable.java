package android.graphics.drawable;

import android.graphics.Canvas;

/**
 * Never created: ImageDecoder decodes animated images to a BitmapDrawable of the first frame.
 */
public class AnimatedImageDrawable extends Drawable implements Animatable {

	@Override
	public void draw(Canvas canvas) {}

	@Override
	public void start() {}

	@Override
	public void stop() {}

	@Override
	public boolean isRunning() { return false; }
}
