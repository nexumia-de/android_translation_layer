package android.view.animation;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;

public class AnimationUtils {
	public static Animation loadAnimation(Context context, int dummy) { return new Animation(); }

	public static long currentAnimationTimeMillis() {
		return System.currentTimeMillis();
	}

	/* PathInterpolator still runs linear, but callers must never get null */
	public static Interpolator loadInterpolator(Context context, int id) {
		try (XmlResourceParser parser = context.getResources().getXml(id)) {
			int type;
			while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {}
			AttributeSet attrs = Xml.asAttributeSet(parser);
			final String ns = "http://schemas.android.com/apk/res/android";
			switch (parser.getName()) {
				case "accelerateInterpolator":
					return new AccelerateInterpolator(attrs.getAttributeFloatValue(ns, "factor", 1.0f));
				case "decelerateInterpolator":
					return new DecelerateInterpolator(attrs.getAttributeFloatValue(ns, "factor", 1.0f));
				case "accelerateDecelerateInterpolator":
					return new AccelerateDecelerateInterpolator();
				case "pathInterpolator":
					return new PathInterpolator(attrs.getAttributeFloatValue(ns, "controlX1", 0f),
					                            attrs.getAttributeFloatValue(ns, "controlY1", 0f),
					                            attrs.getAttributeFloatValue(ns, "controlX2", 1f),
					                            attrs.getAttributeFloatValue(ns, "controlY2", 1f));
				default:
					return new LinearInterpolator();
			}
		} catch (Exception e) {
			return new LinearInterpolator();
		}
	}
}
