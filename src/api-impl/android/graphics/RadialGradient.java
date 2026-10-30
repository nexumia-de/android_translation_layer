package android.graphics;

public class RadialGradient extends Shader {
	final float x, y, radius;
	final int[] colors;
	final float[] positions;
	final TileMode tile;

	public RadialGradient(float x, float y, float radius, int[] colors, float[] positions, TileMode tile) {
		this.x = x;
		this.y = y;
		this.radius = radius;
		this.colors = colors.clone();
		this.positions = gradientOffsets(colors.length, positions);
		this.tile = tile;
	}

	public RadialGradient(float x, float y, float radius, int color0, int color1, TileMode tile) {
		this(x, y, radius, new int[] {color0, color1}, null, tile);
	}

	@Override
	public int getGradient(float[] geometry) {
		float[] center = {x, y};
		float r = radius;
		Matrix matrix = new Matrix();
		if (getLocalMatrix(matrix)) {
			matrix.mapPoints(center);
			r = matrix.mapRadius(radius);
		}
		geometry[0] = center[0];
		geometry[1] = center[1];
		geometry[2] = r;
		return GRADIENT_RADIAL;
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
