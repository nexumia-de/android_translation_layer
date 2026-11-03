package android.view.inputmethod;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;

/* An installed input method: its service and what its android.view.im XML
 * declares (settings activity, subtypes), as on Android. */
public final class InputMethodInfo {
	public static final String SERVICE_META_DATA = "android.view.im";
	private static final String NS = "http://schemas.android.com/apk/res/android";

	private final ResolveInfo mService;
	private final String mId;
	private final String mSettingsActivityName;
	private final int mIsDefaultResId;
	private final boolean mSupportsSwitchingToNextInputMethod;
	private final List<InputMethodSubtype> mSubtypes = new ArrayList<>();

	public InputMethodInfo(Context context, ResolveInfo service) {
		mService = service;
		ServiceInfo si = service.serviceInfo;
		mId = new ComponentName(si.packageName, si.name).flattenToShortString();
		String settings = null;
		int isDefault = 0;
		boolean switching = false;
		XmlResourceParser parser = null;
		try {
			parser = si.loadXmlMetaData(context.getPackageManager(), SERVICE_META_DATA);
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (parser != null) {
			try {
				int type;
				while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
					if (type != XmlPullParser.START_TAG)
						continue;
					if ("input-method".equals(parser.getName())) {
						settings = parser.getAttributeValue(NS, "settingsActivity");
						isDefault = parser.getAttributeResourceValue(NS, "isDefault", 0);
						switching = parser.getAttributeBooleanValue(NS, "supportsSwitchingToNextInputMethod", false);
					} else if ("subtype".equals(parser.getName())) {
						mSubtypes.add(new InputMethodSubtype.InputMethodSubtypeBuilder()
						                  .setSubtypeNameResId(parser.getAttributeResourceValue(NS, "label", 0))
						                  .setSubtypeIconResId(parser.getAttributeResourceValue(NS, "icon", 0))
						                  .setLanguageTag(parser.getAttributeValue(NS, "languageTag"))
						                  .setSubtypeLocale(parser.getAttributeValue(NS, "imeSubtypeLocale"))
						                  .setSubtypeMode(parser.getAttributeValue(NS, "imeSubtypeMode"))
						                  .setSubtypeExtraValue(parser.getAttributeValue(NS, "imeSubtypeExtraValue"))
						                  .setIsAuxiliary(parser.getAttributeBooleanValue(NS, "isAuxiliary", false))
						                  .setOverridesImplicitlyEnabledSubtype(parser.getAttributeBooleanValue(NS, "overridesImplicitlyEnabledSubtype", false))
						                  .setSubtypeId(parser.getAttributeIntValue(NS, "subtypeId", 0))
						                  .setIsAsciiCapable(parser.getAttributeBooleanValue(NS, "isAsciiCapable", false))
						                  .build());
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				parser.close();
			}
		}
		mSettingsActivityName = settings;
		mIsDefaultResId = isDefault;
		mSupportsSwitchingToNextInputMethod = switching;
	}

	public String getId() {
		return mId;
	}

	public String getPackageName() {
		return mService.serviceInfo.packageName;
	}

	public String getServiceName() {
		return mService.serviceInfo.name;
	}

	public ServiceInfo getServiceInfo() {
		return mService.serviceInfo;
	}

	public ComponentName getComponent() {
		return new ComponentName(getPackageName(), getServiceName());
	}

	public CharSequence loadLabel(PackageManager pm) {
		return mService.serviceInfo.loadLabel(pm);
	}

	public Drawable loadIcon(PackageManager pm) {
		return mService.serviceInfo.loadIcon(pm);
	}

	public String getSettingsActivity() {
		return mSettingsActivityName;
	}

	public int getSubtypeCount() {
		return mSubtypes.size();
	}

	public InputMethodSubtype getSubtypeAt(int index) {
		return mSubtypes.get(index);
	}

	public int getIsDefaultResourceId() {
		return mIsDefaultResId;
	}

	public boolean supportsSwitchingToNextInputMethod() {
		return mSupportsSwitchingToNextInputMethod;
	}

	List<InputMethodSubtype> getSubtypes() {
		return mSubtypes;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof InputMethodInfo && mId.equals(((InputMethodInfo)o).mId);
	}

	@Override
	public int hashCode() {
		return mId.hashCode();
	}

	@Override
	public String toString() {
		return "InputMethodInfo{" + mId + ", settings: " + mSettingsActivityName + "}";
	}
}
