package android.graphics;

import android.atl.ATLPaths;
import android.content.res.AssetManager;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class Typeface {

	// Style
	public static final int NORMAL = 0;
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int BOLD_ITALIC = 3;

	/**
	 * The default NORMAL typeface object
	 */
	public static final Typeface DEFAULT = new Typeface(null, NORMAL);
	/**
	 * The default BOLD typeface object. Note: this may be not actually be
	 * bold, depending on what fonts are installed. Call getStyle() to know
	 * for sure.
	 */
	public static final Typeface DEFAULT_BOLD = new Typeface(null, BOLD);
	/**
	 * The NORMAL style of the default sans serif typeface.
	 */
	public static final Typeface SANS_SERIF = new Typeface(null, NORMAL);
	/**
	 * The NORMAL style of the default serif typeface.
	 */
	public static final Typeface SERIF = new Typeface("serif", NORMAL);
	/**
	 * The NORMAL style of the default monospace typeface.
	 */
	public static final Typeface MONOSPACE = new Typeface("monospace", NORMAL);

	public long native_instance = 0; // directly accessed by androidx

	final String family; // fontconfig family, null for the default font
	private final int style;

	public Typeface() {
		this(null, NORMAL);
	}

	private Typeface(String family, int style) {
		this.family = family;
		this.style = style;
	}

	private static native String nativeAddFontFile(String path, boolean onMainThread);

	/* fontconfig needs a file, and the bytes of an app's font are often not one */
	static Typeface fromStream(InputStream stream) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[16384];
		int count;
		while ((count = stream.read(buffer)) > 0)
			bytes.write(buffer, 0, count);
		stream.close();
		return fromBytes(bytes.toByteArray());
	}

	static Typeface fromBytes(byte[] font) throws IOException {
		File dir = new File(ATLPaths.app_data_dir_base, "_fonts_");
		dir.mkdirs();
		File file = new File(dir, font.length + "-" + Integer.toHexString(Arrays.hashCode(font)) + ".font");
		if (!file.isFile()) {
			File part = File.createTempFile("font", null, dir);
			try (FileOutputStream out = new FileOutputStream(part)) {
				out.write(font);
			}
			part.renameTo(file);
		}
		return fromFile(file.getPath());
	}

	private static Typeface fromFile(String path) {
		String family = nativeAddFontFile(path, Looper.myLooper() == Looper.getMainLooper());
		return family != null ? new Typeface(family, NORMAL) : DEFAULT;
	}

	public static Typeface createFromAsset(AssetManager mgr, String path) {
		try {
			return fromStream(mgr.open(path));
		} catch (IOException e) {
			e.printStackTrace();
			return DEFAULT;
		}
	}

	public static Typeface create(String family_name, int style) {
		boolean known = "serif".equals(family_name) || "monospace".equals(family_name);
		return new Typeface(known ? family_name : null, style);
	}

	public static Typeface create(Typeface typeface, int style) {
		if (typeface == null)
			typeface = DEFAULT;
		return typeface.style == style ? typeface : new Typeface(typeface.family, style);
	}

	/* only the bold flag of the weight is kept, like Android's isBold() (weight >= 600) */
	public static Typeface create(Typeface family, int weight, boolean italic) {
		return create(family, (weight >= 600 ? BOLD : NORMAL) | (italic ? ITALIC : NORMAL));
	}

	public int getWeight() {
		return isBold() ? 700 : 400;
	}

	public static Typeface createFromFile(String path) {
		return fromFile(path);
	}

	public static Typeface createFromFile(File file) {
		return fromFile(file.getPath());
	}

	public String getFamily() {
		return family;
	}

	public int getStyle() {
		return style;
	}

	public final boolean isBold() {
		return (style & BOLD) != 0;
	}

	public final boolean isItalic() {
		return (style & ITALIC) != 0;
	}

	public static Typeface defaultFromStyle(int style) {
		return create((String)null, style);
	}

	public static Typeface createFromFamiliesWithDefault(FontFamily[] families) {
		for (FontFamily family : families) {
			if (family.typeface != null)
				return family.typeface;
		}
		return DEFAULT;
	}

	public static Typeface createFromFamiliesWithDefault(FontFamily[] families, int dummy1, int dummy2) {
		return createFromFamiliesWithDefault(families);
	}

	public static class Builder {
		private final Typeface typeface;

		public Builder(AssetManager mgr, String path) {
			typeface = createFromAsset(mgr, path);
		}

		public Builder setFontVariationSettings(String settings) {
			return this;
		}

		public Typeface build() {
			return typeface;
		}
	}
}
