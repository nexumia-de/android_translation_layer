package android.graphics;

public class ColorMatrixColorFilter extends ColorFilter {
	private final ColorMatrix mMatrix = new ColorMatrix();

	public ColorMatrixColorFilter(ColorMatrix matrix) {
		super();
		if (matrix != null)
			mMatrix.set(matrix);
	}

	public ColorMatrixColorFilter(float[] array) {
		super();
		if (array.length < 20)
			throw new ArrayIndexOutOfBoundsException();
		mMatrix.set(array);
	}

	public void getColorMatrix(ColorMatrix colorMatrix) {
		colorMatrix.set(mMatrix);
	}

	/**
	 * @hide
	 */
	@Override
	public float[] toColorMatrix() {
		return mMatrix.getArray().clone();
	}
}
