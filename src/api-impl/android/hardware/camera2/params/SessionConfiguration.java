package android.hardware.camera2.params;

import android.hardware.camera2.CameraCaptureSession;
import java.util.List;
import java.util.concurrent.Executor;

public final class SessionConfiguration {
	public static final int SESSION_REGULAR = 0;
	public static final int SESSION_HIGH_SPEED = 1;

	private final int sessionType;
	private final List<OutputConfiguration> outputs;
	private final Executor executor;
	private final CameraCaptureSession.StateCallback callback;

	public SessionConfiguration(int sessionType, List<OutputConfiguration> outputs, Executor executor, CameraCaptureSession.StateCallback callback) {
		this.sessionType = sessionType;
		this.outputs = outputs;
		this.executor = executor;
		this.callback = callback;
	}

	public int getSessionType() {
		return sessionType;
	}

	public List<OutputConfiguration> getOutputConfigurations() {
		return outputs;
	}

	public Executor getExecutor() {
		return executor;
	}

	public CameraCaptureSession.StateCallback getStateCallback() {
		return callback;
	}
}
