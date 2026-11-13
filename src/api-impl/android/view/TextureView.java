package android.view;

import android.atl.annotation.Export;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;

/*
 * Uses the widget of SurfaceView: what the app renders with EGL ends up as a
 * texture in the GTK scene, which is what a TextureView is on Android too.
 */
public class TextureView extends View {

	private SurfaceTextureListener surfaceTextureListener;
	private SurfaceTexture surfaceTexture;

	public TextureView(Context context) {
		super(context);
	}

	public TextureView(Context context, AttributeSet attributeSet) {
		super(context, attributeSet);
	}

	public TextureView(Context context, AttributeSet attributeSet, int defStyleAttr) {
		super(context, attributeSet, defStyleAttr);
	}

	public TextureView(Context context, AttributeSet attributeSet, int defStyleAttr, int defStyleRes) {
		super(context, attributeSet, defStyleAttr, defStyleRes);
	}

	public interface SurfaceTextureListener {
		void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height);
		void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height);
		boolean onSurfaceTextureDestroyed(SurfaceTexture surface);
		void onSurfaceTextureUpdated(SurfaceTexture surface);
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);

	/* the widget got a size */
	@Export
	private void surfaceChanged(int width, int height) {
		if (width <= 0 || height <= 0)
			return;
		if (surfaceTexture == null) {
			surfaceTexture = new SurfaceTexture(false);
			surfaceTexture.widget = widget;
			if (surfaceTextureListener != null)
				surfaceTextureListener.onSurfaceTextureAvailable(surfaceTexture, width, height);
		} else if (surfaceTextureListener != null) {
			surfaceTextureListener.onSurfaceTextureSizeChanged(surfaceTexture, width, height);
		}
	}

	/* the widget left the window */
	@Export
	private void surfaceDestroyed() {
		if (surfaceTexture == null)
			return;
		SurfaceTexture destroyed = surfaceTexture;
		surfaceTexture = null;
		if (surfaceTextureListener == null || surfaceTextureListener.onSurfaceTextureDestroyed(destroyed))
			destroyed.release();
	}

	public void setSurfaceTextureListener(SurfaceTextureListener surfaceTextureListener) {
		this.surfaceTextureListener = surfaceTextureListener;
	}

	public SurfaceTextureListener getSurfaceTextureListener() {
		return surfaceTextureListener;
	}

	public SurfaceTexture getSurfaceTexture() {
		return surfaceTexture;
	}

	public boolean isAvailable() {
		return surfaceTexture != null;
	}

	/* the texture is always blended with its alpha channel */
	public void setOpaque(boolean opaque) {}

	public boolean isOpaque() {
		return false;
	}

	public void setTransform(Matrix transform) {}

	public Bitmap getBitmap() {
		return getBitmap(getWidth(), getHeight());
	}

	/* reading the texture back is not implemented: an empty bitmap */
	public Bitmap getBitmap(int width, int height) {
		if (!isAvailable() || width <= 0 || height <= 0)
			return null;
		return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
	}

	public Bitmap getBitmap(Bitmap bitmap) {
		return bitmap;
	}
}
