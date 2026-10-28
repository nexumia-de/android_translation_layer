package android.os;

public class VibrationEffect {
	public static final int DEFAULT_AMPLITUDE = -1;

	public static final int EFFECT_CLICK = 0;
	public static final int EFFECT_DOUBLE_CLICK = 1;
	public static final int EFFECT_TICK = 2;
	public static final int EFFECT_HEAVY_CLICK = 5;

	// same format as Vibrator.vibrate(long[], int): alternating off and on durations, starting with off
	long[] timings;
	int repeat;

	private VibrationEffect(long[] timings, int repeat) {
		this.timings = timings;
		this.repeat = repeat;
	}

	public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
		return new VibrationEffect(new long[] {0, milliseconds}, -1);
	}

	public static VibrationEffect createWaveform(long[] timings, int repeat) {
		return new VibrationEffect(timings, repeat);
	}

	public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
		return new VibrationEffect(timings, repeat);
	}

	public static VibrationEffect createPredefined(int effectId) {
		switch (effectId) {
			case EFFECT_DOUBLE_CLICK:
				return new VibrationEffect(new long[] {0, 20, 100, 20}, -1);
			case EFFECT_TICK:
				return new VibrationEffect(new long[] {0, 10}, -1);
			case EFFECT_HEAVY_CLICK:
				return new VibrationEffect(new long[] {0, 30}, -1);
			case EFFECT_CLICK:
			default:
				return new VibrationEffect(new long[] {0, 20}, -1);
		}
	}
}
