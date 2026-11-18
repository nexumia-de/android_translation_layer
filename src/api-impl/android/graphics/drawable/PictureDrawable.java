package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Picture;
import android.graphics.Rect;

public class PictureDrawable extends Drawable {

	private Picture picture;

	public PictureDrawable(Picture picture) {
		this.picture = picture;
	}

	public Picture getPicture() {
		return picture;
	}

	public void setPicture(Picture picture) {
		this.picture = picture;
	}

	@Override
	public void draw(Canvas canvas) {
		if (picture == null || picture.getWidth() <= 0 || picture.getHeight() <= 0)
			return;
		Rect bounds = getBounds();
		canvas.save();
		canvas.clipRect(bounds);
		canvas.translate(bounds.left, bounds.top);
		canvas.scale((float)bounds.width() / picture.getWidth(), (float)bounds.height() / picture.getHeight());
		canvas.drawPicture(picture);
		canvas.restore();
	}

	@Override
	public int getIntrinsicWidth() {
		return picture != null ? picture.getWidth() : -1;
	}

	@Override
	public int getIntrinsicHeight() {
		return picture != null ? picture.getHeight() : -1;
	}

	@Override
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	@Override
	public void setAlpha(int alpha) {}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {}
}
