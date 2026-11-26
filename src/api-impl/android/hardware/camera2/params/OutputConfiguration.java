package android.hardware.camera2.params;

import android.view.Surface;

public final class OutputConfiguration {
	private final Surface surface;

	public OutputConfiguration(Surface surface) {
		this.surface = surface;
	}

	public Surface getSurface() {
		return surface;
	}
}
