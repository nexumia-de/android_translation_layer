package android.graphics.fonts;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/* a font file loaded into fontconfig, with the style the app gave it; like Typeface, only
 * bold (weight >= 600) and italic reach the text, variation settings are ignored */
public final class Font {
	private final Typeface typeface;
	private final FontStyle style;

	private Font(Typeface typeface, FontStyle style) {
		this.typeface = typeface;
		this.style = style;
	}

	public FontStyle getStyle() {
		return style;
	}

	/** @hide for Typeface.CustomFallbackBuilder */
	public Typeface getTypeface() {
		return typeface;
	}

	public static final class Builder {
		private Typeface typeface;
		private InputStream stream;
		private IOException error;
		private int weight = FontStyle.FONT_WEIGHT_NORMAL;
		private int slant = FontStyle.FONT_SLANT_UPRIGHT;

		public Builder(Resources res, int resId) {
			try {
				stream = res.openRawResource(resId);
			} catch (Resources.NotFoundException e) {
				error = new IOException(e);
			}
		}

		public Builder(AssetManager am, String path) {
			try {
				stream = am.open(path);
			} catch (IOException e) {
				error = e;
			}
		}

		public Builder(File file) {
			try {
				stream = new FileInputStream(file);
			} catch (IOException e) {
				error = e;
			}
		}

		public Builder(ParcelFileDescriptor fd) {
			stream = new FileInputStream(fd.getFileDescriptor());
		}

		public Builder(ByteBuffer buffer) {
			byte[] bytes = new byte[buffer.remaining()];
			buffer.duplicate().get(bytes);
			stream = new ByteArrayInputStream(bytes);
		}

		public Builder(Font font) {
			typeface = font.typeface;
			weight = font.style.getWeight();
			slant = font.style.getSlant();
		}

		public Builder setWeight(int weight) {
			this.weight = weight;
			return this;
		}

		public Builder setSlant(int slant) {
			this.slant = slant;
			return this;
		}

		public Builder setTtcIndex(int ttcIndex) {
			return this;
		}

		public Builder setFontVariationSettings(String variationSettings) {
			return this;
		}

		public Builder setFontVariationSettings(FontVariationAxis[] axes) {
			return this;
		}

		public Font build() throws IOException {
			if (error != null)
				throw error;
			if (typeface == null)
				typeface = Typeface.createFromResourceStream(stream);
			return new Font(typeface, new FontStyle(weight, slant));
		}
	}
}
