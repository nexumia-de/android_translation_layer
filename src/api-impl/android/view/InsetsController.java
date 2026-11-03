package android.view;

import android.view.WindowInsets.Type.InsetsType;

/* remembers what the app asked for; Phosh draws the system bars itself */
public class InsetsController implements WindowInsetsController {
	private int appearance;
	private int behavior = BEHAVIOR_DEFAULT;

	public void setSystemBarsAppearance(int appearance, int mask) {
		this.appearance = (this.appearance & ~mask) | (appearance & mask);
	}
	public int getSystemBarsAppearance() {
		return appearance;
	}
	public void setSystemBarsBehavior(int behavior) {
		this.behavior = behavior;
	}
	public int getSystemBarsBehavior() {
		return behavior;
	}
	public void show(@InsetsType int types) {}
	public void hide(@InsetsType int types) {}
}
