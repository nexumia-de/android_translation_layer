package android.hardware.camera2;

import android.os.Handler;
import java.util.concurrent.Executor;

/* ATL has no camera backend yet: there are no cameras, so nothing can be opened */
public class CameraManager {
	public String[] getCameraIdList() throws CameraAccessException {
		return new String[] {};
	}

	public CameraCharacteristics getCameraCharacteristics(String cameraId) throws CameraAccessException {
		throw new IllegalArgumentException("no camera with id " + cameraId);
	}

	public void openCamera(String cameraId, CameraDevice.StateCallback callback, Handler handler) throws CameraAccessException {
		throw new IllegalArgumentException("no camera with id " + cameraId);
	}

	public void openCamera(String cameraId, Executor executor, CameraDevice.StateCallback callback) throws CameraAccessException {
		throw new IllegalArgumentException("no camera with id " + cameraId);
	}

	/* the camera list never changes, so there is nothing to report */
	public void registerAvailabilityCallback(AvailabilityCallback callback, Handler handler) {}

	public void registerAvailabilityCallback(Executor executor, AvailabilityCallback callback) {}

	public void unregisterAvailabilityCallback(AvailabilityCallback callback) {}

	public void registerTorchCallback(TorchCallback callback, Handler handler) {}

	public void registerTorchCallback(Executor executor, TorchCallback callback) {}

	public void unregisterTorchCallback(TorchCallback callback) {}

	public void setTorchMode(String cameraId, boolean enabled) throws CameraAccessException {
		throw new IllegalArgumentException("no camera with id " + cameraId);
	}

	public static abstract class AvailabilityCallback {
		public void onCameraAvailable(String cameraId) {}

		public void onCameraUnavailable(String cameraId) {}

		public void onCameraAccessPrioritiesChanged() {}
	}

	public static abstract class TorchCallback {
		public void onTorchModeUnavailable(String cameraId) {}

		public void onTorchModeChanged(String cameraId, boolean enabled) {}
	}
}
