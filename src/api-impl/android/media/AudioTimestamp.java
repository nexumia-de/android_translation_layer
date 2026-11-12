package android.media;

public class AudioTimestamp {

	public static final int TIMEBASE_MONOTONIC = 0;
	public static final int TIMEBASE_BOOTTIME = 1;

	public long framePosition;
	public long nanoTime;
}
