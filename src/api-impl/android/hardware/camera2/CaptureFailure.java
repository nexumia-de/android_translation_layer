package android.hardware.camera2;

public class CaptureFailure {
	public static final int REASON_ERROR = 0;
	public static final int REASON_FLUSHED = 1;

	CaptureFailure() {}

	public CaptureRequest getRequest() {
		return null;
	}

	public long getFrameNumber() {
		return 0;
	}

	public int getReason() {
		return REASON_ERROR;
	}

	public boolean wasImageCaptured() {
		return false;
	}

	public int getSequenceId() {
		return 0;
	}
}
