package android.graphics;

public class LinearGradient extends Shader {
	final float x0, y0, x1, y1;
	final int[] colors;
	final float[] positions;
	final TileMode tile;

	public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] positions, TileMode tile) {
		this.x0 = x0;
		this.y0 = y0;
		this.x1 = x1;
		this.y1 = y1;
		this.colors = colors.clone();
		this.positions = gradientOffsets(colors.length, positions);
		this.tile = tile;
	}

	public LinearGradient(float x0, float y0, float x1, float y1, long[] colors, float[] positions, TileMode tile) {
		this(x0, y0, x1, y1, gradientColors(colors), positions, tile);
	}

	public LinearGradient(float x0, float y0, float x1, float y1, int color0, int color1, TileMode tile) {
		this(x0, y0, x1, y1, new int[] {color0, color1}, null, tile);
	}

	@Override
	public int getGradient(float[] geometry) {
		float[] points = {x0, y0, x1, y1};
		Matrix matrix = new Matrix();
		if (getLocalMatrix(matrix))
			matrix.mapPoints(points);
		System.arraycopy(points, 0, geometry, 0, 4);
		return GRADIENT_LINEAR;
	}

	@Override
	public int[] getGradientColors() {
		return colors;
	}

	@Override
	public float[] getGradientOffsets() {
		return positions;
	}

	@Override
	public boolean isGradientRepeating() {
		return tile == TileMode.REPEAT;
	}
}
