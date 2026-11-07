package android.view;

import android.graphics.Rect;

public final class WindowMetrics {
	private final Rect bounds;
	private final WindowInsets windowInsets;
	private final float density;

	public WindowMetrics(Rect bounds, WindowInsets windowInsets) {
		this(bounds, windowInsets, android.content.res.Resources.getSystem().getDisplayMetrics().density);
	}

	public WindowMetrics(Rect bounds, WindowInsets windowInsets, float density) {
		this.bounds = new Rect(bounds);
		this.windowInsets = windowInsets;
		this.density = density;
	}

	public Rect getBounds() {
		return new Rect(bounds);
	}

	public WindowInsets getWindowInsets() {
		return windowInsets;
	}

	public float getDensity() {
		return density;
	}
}
