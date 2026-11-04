package android.graphics;

public class LightingColorFilter extends ColorFilter {
	private final int mMul;
	private final int mAdd;

	public LightingColorFilter(int mul, int add) {
		mMul = mul;
		mAdd = add;
	}

	public int getColorMultiply() {
		return mMul;
	}

	public int getColorAdd() {
		return mAdd;
	}

	/**
	 * @hide
	 */
	@Override
	public float[] toColorMatrix() {
		final float[] m = new float[20];
		m[0] = Color.red(mMul) / 255.f;
		m[6] = Color.green(mMul) / 255.f;
		m[12] = Color.blue(mMul) / 255.f;
		m[18] = 1;
		m[4] = Color.red(mAdd);
		m[9] = Color.green(mAdd);
		m[14] = Color.blue(mAdd);
		return m;
	}
}
