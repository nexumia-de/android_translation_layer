package android.graphics;

public class ColorFilter {

	/**
	 * The filter as a 4x5 color matrix in the layout of ColorMatrix (translation in 0-255),
	 * applied to unpremultiplied colors, or null if it can't be expressed as one.
	 *
	 * @hide
	 */
	public float[] toColorMatrix() {
		return null;
	}

	/**
	 * Porter-Duff modes with a constant source color are linear in the unpremultiplied
	 * destination color where the destination is opaque. Modes that are not linear for
	 * translucent destinations use the opaque result; DARKEN, LIGHTEN and OVERLAY are not
	 * supported.
	 *
	 * @hide
	 */
	static float[] porterDuffMatrix(int color, PorterDuff.Mode mode) {
		if (mode == null)
			return null;
		final float sa = Color.alpha(color) / 255.f;
		final float[] c = {Color.red(color), Color.green(color), Color.blue(color)};
		float rgbScale; // destination color factor, the same for r, g and b
		float[] rgbOffset = new float[3];
		float alphaScale;
		float alphaOffset;
		switch (mode) {
			case CLEAR:
				rgbScale = 0;
				alphaScale = 0;
				alphaOffset = 0;
				break;
			case SRC:
				rgbScale = 0;
				rgbOffset = c;
				alphaScale = 0;
				alphaOffset = sa * 255;
				break;
			case DST:
				return null;
			case SRC_OVER:
			case SRC_ATOP:
				rgbScale = 1 - sa;
				for (int i = 0; i < 3; i++)
					rgbOffset[i] = c[i] * sa;
				alphaScale = mode == PorterDuff.Mode.SRC_ATOP ? 1 : 1 - sa;
				alphaOffset = mode == PorterDuff.Mode.SRC_ATOP ? 0 : sa * 255;
				break;
			case DST_OVER:
				rgbScale = 1;
				alphaScale = 1 - sa;
				alphaOffset = sa * 255;
				break;
			case SRC_IN:
				rgbScale = 0;
				rgbOffset = c;
				alphaScale = sa;
				alphaOffset = 0;
				break;
			case DST_IN:
				rgbScale = 1;
				alphaScale = sa;
				alphaOffset = 0;
				break;
			case SRC_OUT:
				rgbScale = 0;
				rgbOffset = c;
				alphaScale = -sa;
				alphaOffset = sa * 255;
				break;
			case DST_OUT:
				rgbScale = 1;
				alphaScale = 1 - sa;
				alphaOffset = 0;
				break;
			case DST_ATOP:
				rgbScale = 1;
				alphaScale = 0;
				alphaOffset = sa * 255;
				break;
			case XOR:
				rgbScale = 1;
				alphaScale = 1 - 2 * sa;
				alphaOffset = sa * 255;
				break;
			case MULTIPLY: {
				final float[] m = new float[20];
				for (int i = 0; i < 3; i++)
					m[i * 6] = c[i] / 255.f;
				m[18] = sa;
				return m;
			}
			case SCREEN: {
				final float[] m = new float[20];
				for (int i = 0; i < 3; i++) {
					m[i * 6] = 1 - c[i] / 255.f * sa;
					m[i * 5 + 4] = c[i] * sa;
				}
				m[18] = 1 - sa;
				m[19] = sa * 255;
				return m;
			}
			case ADD:
				rgbScale = 1;
				for (int i = 0; i < 3; i++)
					rgbOffset[i] = c[i] * sa;
				alphaScale = 1;
				alphaOffset = sa * 255;
				break;
			default:
				return null;
		}
		final float[] m = new float[20];
		for (int i = 0; i < 3; i++) {
			m[i * 6] = rgbScale;
			m[i * 5 + 4] = rgbOffset[i];
		}
		m[18] = alphaScale;
		m[19] = alphaOffset;
		return m;
	}
}
