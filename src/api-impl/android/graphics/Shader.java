package android.graphics;

public class Shader {

	public enum TileMode {
		CLAMP,
		MIRROR,
		REPEAT
	}

	private Matrix localMatrix;

	protected void init(long ni) {
	}

	public void setLocalMatrix(Matrix matrix) {
		localMatrix = (matrix == null || matrix.isIdentity()) ? null : new Matrix(matrix);
	}

	public boolean getLocalMatrix(Matrix matrix) {
		if (localMatrix == null)
			return false;
		matrix.set(localMatrix);
		return true;
	}

	protected void copyLocalMatrix(Shader dest) {
		dest.setLocalMatrix(localMatrix);
	}

	public static final int GRADIENT_NONE = 0;
	public static final int GRADIENT_LINEAR = 1;
	public static final int GRADIENT_RADIAL = 2;

	/* for GskCanvas: the gradient type, and in geometry the points (x0, y0, x1, y1 or x, y, radius)
	 * after the local matrix */
	public int getGradient(float[] geometry) {
		return GRADIENT_NONE;
	}

	public int[] getGradientColors() {
		return null;
	}

	public float[] getGradientOffsets() {
		return null;
	}

	public boolean isGradientRepeating() {
		return false;
	}

	/* stop colors and offsets for a gradient, offsets spread evenly if none are given */
	static float[] gradientOffsets(int count, float[] positions) {
		if (positions != null)
			return positions;
		float[] offsets = new float[count];
		for (int i = 0; i < count; i++)
			offsets[i] = count > 1 ? (float)i / (count - 1) : 0;
		return offsets;
	}

	/* only sRGB color longs, which keep the ARGB int in their upper half */
	static int[] gradientColors(long[] colors) {
		int[] argb = new int[colors.length];
		for (int i = 0; i < colors.length; i++)
			argb[i] = (int)(colors[i] >>> 32);
		return argb;
	}
}
