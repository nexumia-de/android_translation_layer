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

	public static Composition startComposition() {
		return new Composition();
	}

	/* a primitive is a short pulse here; scale needs amplitude control */
	public static final class Composition {
		public static final int PRIMITIVE_NOOP = 0;
		public static final int PRIMITIVE_CLICK = 1;
		public static final int PRIMITIVE_THUD = 2;
		public static final int PRIMITIVE_SPIN = 3;
		public static final int PRIMITIVE_QUICK_RISE = 4;
		public static final int PRIMITIVE_SLOW_RISE = 5;
		public static final int PRIMITIVE_QUICK_FALL = 6;
		public static final int PRIMITIVE_TICK = 7;
		public static final int PRIMITIVE_LOW_TICK = 8;

		private final java.util.ArrayList<Long> timings = new java.util.ArrayList<>();

		public Composition addPrimitive(int primitiveId) {
			return addPrimitive(primitiveId, 1f, 0);
		}

		public Composition addPrimitive(int primitiveId, float scale) {
			return addPrimitive(primitiveId, scale, 0);
		}

		public Composition addPrimitive(int primitiveId, float scale, int delay) {
			long length;
			switch (primitiveId) {
				case PRIMITIVE_NOOP:
					length = 0;
					break;
				case PRIMITIVE_TICK:
				case PRIMITIVE_LOW_TICK:
					length = 10;
					break;
				case PRIMITIVE_CLICK:
					length = 20;
					break;
				default:
					length = 50;
					break;
			}
			timings.add((long)delay);
			timings.add(length);
			return this;
		}

		public VibrationEffect compose() {
			long[] result = new long[timings.size()];
			for (int i = 0; i < result.length; i++)
				result[i] = timings.get(i);
			return new VibrationEffect(result, -1);
		}
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
