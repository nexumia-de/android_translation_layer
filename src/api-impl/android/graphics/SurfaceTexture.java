package android.graphics;

import android.os.Handler;

public class SurfaceTexture {

	/* the TextureView's widget, 0 for a SurfaceTexture the app made itself (which has no consumer in ATL) */
	public long widget;
	private boolean released;

	public static interface OnFrameAvailableListener {
		void onFrameAvailable(SurfaceTexture surfaceTexture);
	}

	public SurfaceTexture(int texName) {}

	public SurfaceTexture(boolean singleBufferMode) {}

	public void setOnFrameAvailableListener(OnFrameAvailableListener listener) {}

	public void setOnFrameAvailableListener(OnFrameAvailableListener listener, Handler handler) {}

	public void setDefaultBufferSize(int width, int height) {}

	public void updateTexImage() {}

	public void getTransformMatrix(float[] mtx) {
		android.opengl.Matrix.setIdentityM(mtx, 0);
	}

	public long getTimestamp() {
		return System.nanoTime();
	}

	public void release() {
		released = true;
	}

	public boolean isReleased() {
		return released;
	}
}
