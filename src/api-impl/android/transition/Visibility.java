package android.transition;

public abstract class Visibility extends Transition {
	public static final int MODE_IN = 0x1;
	public static final int MODE_OUT = 0x2;

	private int mode = MODE_IN | MODE_OUT;

	public void setMode(int mode) {
		this.mode = mode;
	}

	public int getMode() {
		return mode;
	}
}
