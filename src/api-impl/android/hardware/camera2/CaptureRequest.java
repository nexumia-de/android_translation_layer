package android.hardware.camera2;

import android.view.Surface;

public final class CaptureRequest extends CameraMetadata<CaptureRequest.Key<?>> {
	public static final class Key<T> {
		private final String name;

		public Key(String name, Class<T> type) {
			this.name = name;
		}

		public String getName() {
			return name;
		}
	}

	public <T> T get(Key<T> key) {
		return null;
	}

	public Object getTag() {
		return null;
	}

	public static final class Builder {
		public <T> void set(Key<T> key, T value) {}

		public <T> T get(Key<T> key) {
			return null;
		}

		public void addTarget(Surface outputTarget) {}

		public void removeTarget(Surface outputTarget) {}

		public void setTag(Object tag) {}

		public CaptureRequest build() {
			return new CaptureRequest();
		}
	}
}
