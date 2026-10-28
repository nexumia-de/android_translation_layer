package android.os;

public class CombinedVibration {
	VibrationEffect effect;

	private CombinedVibration(VibrationEffect effect) {
		this.effect = effect;
	}

	public static CombinedVibration createParallel(VibrationEffect effect) {
		return new CombinedVibration(effect);
	}
}
