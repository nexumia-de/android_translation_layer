package android.media;

import android.hardware.HardwareBuffer;

/* ATL produces no images (no ImageReader), but the class must exist:
 * Flutter's engine looks it up when it starts and aborts without it. */
public abstract class Image implements AutoCloseable {

	protected Image() {}

	public abstract int getFormat();

	public abstract int getWidth();

	public abstract int getHeight();

	public abstract long getTimestamp();

	public HardwareBuffer getHardwareBuffer() {
		return null;
	}

	@Override
	public abstract void close();
}
