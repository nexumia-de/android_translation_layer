package android.hardware.camera2;

public class CaptureResult extends CameraMetadata<CaptureResult.Key<?>> {
	public static final class Key<T> {
		private final String name;

		public Key(String name, Class<T> type) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	CaptureResult() {}

	public <T> T get(Key<T> key) {
		return null;
	}

	public CaptureRequest getRequest() {
		return null;
	}

	public long getFrameNumber() {
		return 0;
	}

	public int getSequenceId() {
		return 0;
	}
}
