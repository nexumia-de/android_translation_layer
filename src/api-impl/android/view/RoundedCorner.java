package android.view;

import android.graphics.Point;

/**
 * A rounded corner of the display. ATL knows no display corners, so
 * Display and WindowInsets return null for every position, as Android
 * does for a display without rounded corners.
 */
public final class RoundedCorner {
	public static final int POSITION_TOP_LEFT = 0;
	public static final int POSITION_TOP_RIGHT = 1;
	public static final int POSITION_BOTTOM_RIGHT = 2;
	public static final int POSITION_BOTTOM_LEFT = 3;

	private final int position;
	private final int radius;
	private final Point center;

	public RoundedCorner(int position, int radius, int centerX, int centerY) {
		this.position = position;
		this.radius = radius;
		this.center = new Point(centerX, centerY);
	}

	public int getPosition() {
		return position;
	}

	public int getRadius() {
		return radius;
	}

	public Point getCenter() {
		return new Point(center);
	}
}
