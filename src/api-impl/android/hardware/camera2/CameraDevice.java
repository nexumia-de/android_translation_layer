package android.hardware.camera2;

import android.hardware.camera2.params.SessionConfiguration;
import android.os.Handler;
import android.view.Surface;
import java.util.List;

/* never handed out: ATL has no camera */
public abstract class CameraDevice implements AutoCloseable {
	public static final int TEMPLATE_PREVIEW = 1;
	public static final int TEMPLATE_STILL_CAPTURE = 2;
	public static final int TEMPLATE_RECORD = 3;
	public static final int TEMPLATE_VIDEO_SNAPSHOT = 4;
	public static final int TEMPLATE_ZERO_SHUTTER_LAG = 5;
	public static final int TEMPLATE_MANUAL = 6;

	public abstract String getId();

	public abstract CaptureRequest.Builder createCaptureRequest(int templateType) throws CameraAccessException;

	public abstract void createCaptureSession(List<Surface> outputs, CameraCaptureSession.StateCallback callback, Handler handler) throws CameraAccessException;

	public void createCaptureSession(SessionConfiguration config) throws CameraAccessException {
		throw new UnsupportedOperationException();
	}

	@Override
	public abstract void close();

	public static abstract class StateCallback {
		public static final int ERROR_CAMERA_IN_USE = 1;
		public static final int ERROR_MAX_CAMERAS_IN_USE = 2;
		public static final int ERROR_CAMERA_DISABLED = 3;
		public static final int ERROR_CAMERA_DEVICE = 4;
		public static final int ERROR_CAMERA_SERVICE = 5;

		public abstract void onOpened(CameraDevice camera);

		public void onClosed(CameraDevice camera) {}

		public abstract void onDisconnected(CameraDevice camera);

		public abstract void onError(CameraDevice camera, int error);
	}
}
