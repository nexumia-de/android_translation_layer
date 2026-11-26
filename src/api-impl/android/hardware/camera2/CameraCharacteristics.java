package android.hardware.camera2;

public final class CameraCharacteristics extends CameraMetadata<CameraCharacteristics.Key<?>> {
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
}
