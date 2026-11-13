package android.view;

import android.graphics.SurfaceTexture;

public class Surface {
	public long widget;

	public Surface() {}

	public Surface(SurfaceTexture surfaceTexture) {
		widget = surfaceTexture.widget;
	}

	public boolean isValid() {
		return widget != 0;
	}

	public void release() {
		widget = 0;
	}

	public void setFrameRate(float frameRate, int compatibility) {}

	public void setFrameRate(float frameRate, int compatibility, int changeFrameRateStrategy) {}
}
