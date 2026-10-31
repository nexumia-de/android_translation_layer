package android.graphics;

public class ColorSpace {

	public static enum Named {
		SRGB,
		LINEAR_SRGB,
		EXTENDED_SRGB,
		LINEAR_EXTENDED_SRGB,
		BT709,
		BT2020,
		DCI_P3,
		DISPLAY_P3,
		NTSC_1953,
		SMPTE_C,
		ADOBE_RGB,
		PRO_PHOTO_RGB,
		ACES,
		ACESCG,
		CIE_XYZ,
		CIE_LAB,
		BT2020_HLG,
		BT2020_PQ,
		OK_LAB,
	}

	private static final ColorSpace[] spaces = new ColorSpace[Named.values().length];

	/* apps compare the result with ==, so there is one object per name */
	public static synchronized ColorSpace get(Named named) {
		if (spaces[named.ordinal()] == null)
			spaces[named.ordinal()] = new ColorSpace();
		return spaces[named.ordinal()];
	}
}
