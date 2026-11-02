package android.hardware;

/* No buffers are allocated; the class exists for apps and engines (Flutter)
 * that look it up and only use it behind feature checks. */
public final class HardwareBuffer implements AutoCloseable {

	public static final int RGBA_8888 = 1;
	public static final long USAGE_CPU_READ_RARELY = 2;
	public static final long USAGE_GPU_SAMPLED_IMAGE = 1 << 8;
	public static final long USAGE_GPU_COLOR_OUTPUT = 1 << 9;

	private HardwareBuffer() {}

	public static HardwareBuffer create(int width, int height, int format, int layers, long usage) {
		throw new IllegalArgumentException("HardwareBuffer is not supported");
	}

	public static boolean isSupported(int width, int height, int format, int layers, long usage) {
		return false;
	}

	public boolean isClosed() {
		return true;
	}

	@Override
	public void close() {}
}
