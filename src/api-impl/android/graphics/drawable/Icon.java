package android.graphics.drawable;

import android.content.Context;
import android.graphics.Bitmap;

public class Icon {

	public static Icon createWithResource(String packageName, int resourceId) {
		return new Icon();
	}

	public static Icon createWithBitmap(Bitmap bitmap) {
		return new Icon();
	}

	public Drawable loadDrawable(Context context) {
		return new Drawable();
	}
}
