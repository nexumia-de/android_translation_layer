package android.graphics;

import android.content.res.AssetManager;
import android.graphics.fonts.FontVariationAxis;
import java.io.IOException;
import java.nio.ByteBuffer;

public class FontFamily {

	Typeface typeface;

	public void addFontWeightStyle(String dummy, int dummy2, boolean dummy3) {}

	public boolean addFontFromAssetManager(AssetManager mgr, String path, int cookie, boolean isAsset, int dummy5, int dummy6, int dummy7, FontVariationAxis[] dummy8) {
		try {
			typeface = Typeface.fromStream(isAsset ? mgr.open(path) : mgr.openNonAsset(cookie, path));
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean addFontFromBuffer(ByteBuffer buffer, int dummy2, FontVariationAxis[] dummy3, int dummy4, int dummy5) {
		try {
			byte[] font = new byte[buffer.remaining()];
			buffer.duplicate().get(font);
			typeface = Typeface.fromBytes(font);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean freeze() { return true; }

	public void abortCreation() {}
}
