package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;
import java.io.IOException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

public class PreferenceManager {

	private Context context;
	private long id_counter = 0;

	private SharedPreferences sharedPreferences;

	PreferenceManager(Context context) {
		this.context = context;
		this.sharedPreferences = getDefaultSharedPreferences(context);
	}

	public static SharedPreferences getDefaultSharedPreferences(Context context) {
		return context.getSharedPreferences(context.getPackageName() + "_preferences", 0);
	}

	public static void setDefaultValues(Context context, int i, boolean b) {
		System.out.println("android.preference.PrefereceManager.setDefaultValues: STUB");
	}

	public static void setDefaultValues(Context context, String s, int i, int i2, boolean b) {
		System.out.println("android.preference.PrefereceManager.setDefaultValues: STUB");
	}

	public SharedPreferences getSharedPreferences() {
		return sharedPreferences;
	}

	public Context getContext() {
		return context;
	}

	long getNextId() {
		synchronized (this) {
			return id_counter++;
		}
	}

	public PreferenceScreen inflateFromResource(Context context, int resId, PreferenceScreen rootPreferences) {
		try (XmlResourceParser parser = context.getResources().getXml(resId)) {
			AttributeSet attrs = Xml.asAttributeSet(parser);
			int type;
			while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
			}
			if (type != XmlPullParser.START_TAG)
				return rootPreferences;
			Preference root = createItemFromTag(parser.getName(), attrs);
			if (!(root instanceof PreferenceScreen))
				throw new RuntimeException("XML document must start with <PreferenceScreen> tag");
			PreferenceScreen screen = (PreferenceScreen)root;
			screen.onAttachedToHierarchy(this);
			rInflate(parser, screen, attrs);
			return screen;
		} catch (XmlPullParserException | IOException e) {
			throw new RuntimeException("Error inflating preference XML", e);
		}
	}

	private void rInflate(XmlPullParser parser, Preference parent, AttributeSet attrs) throws XmlPullParserException, IOException {
		final int depth = parser.getDepth();
		int type;
		while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
			if (type != XmlPullParser.START_TAG)
				continue;
			String name = parser.getName();
			if ("intent".equals(name)) {
				Intent intent = Intent.parseIntent(context.getResources(), parser, attrs);
				parent.setIntent(intent);
				skipCurrentTag(parser);
			} else if ("extra".equals(name)) {
				/* ignore extras for now; could be parsed into intent extras if needed */
				skipCurrentTag(parser);
			} else if (parent instanceof PreferenceGroup) {
				Preference item = createItemFromTag(name, attrs);
				((PreferenceGroup)parent).addPreference(item);
				rInflate(parser, item, attrs);
			}
		}
	}

	private Preference createItemFromTag(String name, AttributeSet attrs) {
		try {
			String className = name;
			if (className.indexOf('.') == -1)
				className = "android.preference." + className;
			Class<?> clazz = Class.forName(className, false, context.getClassLoader());
			return (Preference)clazz.getConstructor(Context.class, AttributeSet.class).newInstance(context, attrs);
		} catch (Exception e) {
			throw new RuntimeException("Error inflating class " + name, e);
		}
	}

	private static void skipCurrentTag(XmlPullParser parser) throws XmlPullParserException, IOException {
		int outerDepth = parser.getDepth();
		int type;
		while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
		       && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
		}
	}
}
