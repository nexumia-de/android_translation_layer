package android.hardware.camera2;

import java.util.Collections;
import java.util.List;

/* ATL has no camera (CameraManager lists none), so no metadata is ever filled in */
public abstract class CameraMetadata<TKey> {
	public static final int LENS_FACING_FRONT = 0;
	public static final int LENS_FACING_BACK = 1;
	public static final int LENS_FACING_EXTERNAL = 2;

	protected CameraMetadata() {}

	public List<TKey> getKeys() {
		return Collections.emptyList();
	}
}
