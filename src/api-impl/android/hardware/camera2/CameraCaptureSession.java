package android.hardware.camera2;

import android.os.Handler;
import android.view.Surface;
import java.util.List;

/* never handed out: ATL has no camera */
public abstract class CameraCaptureSession implements AutoCloseable {
	public abstract CameraDevice getDevice();

	public abstract int capture(CaptureRequest request, CaptureCallback listener, Handler handler) throws CameraAccessException;

	public abstract int setRepeatingRequest(CaptureRequest request, CaptureCallback listener, Handler handler) throws CameraAccessException;

	public abstract void stopRepeating() throws CameraAccessException;

	public abstract void abortCaptures() throws CameraAccessException;

	@Override
	public abstract void close();

	public static abstract class StateCallback {
		public abstract void onConfigured(CameraCaptureSession session);

		public abstract void onConfigureFailed(CameraCaptureSession session);

		public void onReady(CameraCaptureSession session) {}

		public void onActive(CameraCaptureSession session) {}

		public void onCaptureQueueEmpty(CameraCaptureSession session) {}

		public void onClosed(CameraCaptureSession session) {}

		public void onSurfacePrepared(CameraCaptureSession session, Surface surface) {}
	}

	public static abstract class CaptureCallback {
		public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {}

		public void onReadoutStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {}

		public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {}

		public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {}

		public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {}

		public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {}

		public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {}

		public void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber) {}
	}
}
