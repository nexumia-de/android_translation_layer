package android.os;

import android.content.Context;

// there is only one vibrator, the one Context.getSystemService("vibrator") returns
public class VibratorManager {

	private Vibrator getVibrator() {
		return (Context.vibrator != null) ? Context.vibrator : (Context.vibrator = new Vibrator());
	}

	public Vibrator getDefaultVibrator() {
		return getVibrator();
	}

	public Vibrator getVibrator(int vibratorId) {
		return getVibrator();
	}

	public int[] getVibratorIds() {
		return new int[] {0};
	}

	public void vibrate(CombinedVibration vibration) {
		getVibrator().vibrate(vibration.effect);
	}

	public void cancel() {
		getVibrator().cancel();
	}
}
