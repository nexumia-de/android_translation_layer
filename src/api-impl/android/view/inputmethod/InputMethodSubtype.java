package android.view.inputmethod;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/* A subtype of an input method (language and mode), as on Android: a plain value
 * object built with InputMethodSubtypeBuilder or the deprecated constructors. */
public final class InputMethodSubtype implements Parcelable {
	private static final String EXTRA_VALUE_PAIR_SEPARATOR = ",";
	private static final String EXTRA_VALUE_KEY_VALUE_SEPARATOR = "=";
	private static final int SUBTYPE_ID_NONE = 0;

	private final boolean mIsAuxiliary;
	private final boolean mOverridesImplicitlyEnabledSubtype;
	private final boolean mIsAsciiCapable;
	private final int mSubtypeHashCode;
	private final int mSubtypeIconResId;
	private final int mSubtypeNameResId;
	private final CharSequence mSubtypeNameOverride;
	private final int mSubtypeId;
	private final String mSubtypeLocale;
	private final String mSubtypeLanguageTag;
	private final String mSubtypeMode;
	private final String mSubtypeExtraValue;
	private HashMap<String, String> mExtraValueHashMapCache;

	public static class InputMethodSubtypeBuilder {
		private boolean mIsAuxiliary = false;
		private boolean mOverridesImplicitlyEnabledSubtype = false;
		private boolean mIsAsciiCapable = false;
		private int mSubtypeIconResId = 0;
		private int mSubtypeNameResId = 0;
		private CharSequence mSubtypeNameOverride = "";
		private int mSubtypeId = SUBTYPE_ID_NONE;
		private String mSubtypeLocale = "";
		private String mSubtypeLanguageTag = "";
		private String mSubtypeMode = "";
		private String mSubtypeExtraValue = "";

		public InputMethodSubtypeBuilder setIsAuxiliary(boolean isAuxiliary) {
			mIsAuxiliary = isAuxiliary;
			return this;
		}
		public InputMethodSubtypeBuilder setOverridesImplicitlyEnabledSubtype(boolean value) {
			mOverridesImplicitlyEnabledSubtype = value;
			return this;
		}
		public InputMethodSubtypeBuilder setIsAsciiCapable(boolean isAsciiCapable) {
			mIsAsciiCapable = isAsciiCapable;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeIconResId(int subtypeIconResId) {
			mSubtypeIconResId = subtypeIconResId;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeNameResId(int subtypeNameResId) {
			mSubtypeNameResId = subtypeNameResId;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeNameOverride(CharSequence nameOverride) {
			mSubtypeNameOverride = nameOverride;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeId(int subtypeId) {
			mSubtypeId = subtypeId;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeLocale(String subtypeLocale) {
			mSubtypeLocale = subtypeLocale == null ? "" : subtypeLocale;
			return this;
		}
		public InputMethodSubtypeBuilder setLanguageTag(String languageTag) {
			mSubtypeLanguageTag = languageTag == null ? "" : languageTag;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeMode(String subtypeMode) {
			mSubtypeMode = subtypeMode == null ? "" : subtypeMode;
			return this;
		}
		public InputMethodSubtypeBuilder setSubtypeExtraValue(String subtypeExtraValue) {
			mSubtypeExtraValue = subtypeExtraValue == null ? "" : subtypeExtraValue;
			return this;
		}
		public InputMethodSubtype build() {
			return new InputMethodSubtype(this);
		}
	}

	/* Used by InputMethodManager when no input method is bound. */
	InputMethodSubtype() {
		this(new InputMethodSubtypeBuilder().setSubtypeMode("keyboard"));
	}

	@Deprecated
	public InputMethodSubtype(int nameId, int iconId, String locale, String mode, String extraValue,
	                          boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype) {
		this(nameId, iconId, locale, mode, extraValue, isAuxiliary, overridesImplicitlyEnabledSubtype, 0);
	}

	@Deprecated
	public InputMethodSubtype(int nameId, int iconId, String locale, String mode, String extraValue,
	                          boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype, int id) {
		this(new InputMethodSubtypeBuilder()
		         .setSubtypeNameResId(nameId)
		         .setSubtypeIconResId(iconId)
		         .setSubtypeLocale(locale)
		         .setSubtypeMode(mode)
		         .setSubtypeExtraValue(extraValue)
		         .setIsAuxiliary(isAuxiliary)
		         .setOverridesImplicitlyEnabledSubtype(overridesImplicitlyEnabledSubtype)
		         .setSubtypeId(id));
	}

	private InputMethodSubtype(InputMethodSubtypeBuilder b) {
		mSubtypeNameResId = b.mSubtypeNameResId;
		mSubtypeNameOverride = b.mSubtypeNameOverride;
		mSubtypeIconResId = b.mSubtypeIconResId;
		mSubtypeLocale = b.mSubtypeLocale;
		mSubtypeLanguageTag = b.mSubtypeLanguageTag;
		mSubtypeMode = b.mSubtypeMode;
		mSubtypeExtraValue = b.mSubtypeExtraValue;
		mIsAuxiliary = b.mIsAuxiliary;
		mOverridesImplicitlyEnabledSubtype = b.mOverridesImplicitlyEnabledSubtype;
		mSubtypeId = b.mSubtypeId;
		mIsAsciiCapable = b.mIsAsciiCapable;
		mSubtypeHashCode = mSubtypeId != SUBTYPE_ID_NONE ? mSubtypeId : hashCodeInternal(mSubtypeLocale, mSubtypeMode, mSubtypeExtraValue, mIsAuxiliary, mOverridesImplicitlyEnabledSubtype, mIsAsciiCapable);
	}

	public int getNameResId() {
		return mSubtypeNameResId;
	}

	public CharSequence getNameOverride() {
		return mSubtypeNameOverride;
	}

	public int getIconResId() {
		return mSubtypeIconResId;
	}

	@Deprecated
	public String getLocale() {
		return mSubtypeLocale;
	}

	public String getLanguageTag() {
		return mSubtypeLanguageTag;
	}

	public String getLocale() {
		return java.util.Locale.getDefault().toString();
	}

	public String getMode() {
		return mSubtypeMode;
	}

	public String getExtraValue() {
		return mSubtypeExtraValue;
	}

	public boolean isAuxiliary() {
		return mIsAuxiliary;
	}

	public boolean overridesImplicitlyEnabledSubtype() {
		return mOverridesImplicitlyEnabledSubtype;
	}

	public boolean isAsciiCapable() {
		return mIsAsciiCapable;
	}

	public CharSequence getDisplayName(Context context, String packageName, ApplicationInfo appInfo) {
		if (mSubtypeNameResId == 0)
			return TextUtils.isEmpty(mSubtypeNameOverride) ? mSubtypeLocale : mSubtypeNameOverride;
		String name = context.getString(mSubtypeNameResId);
		try {
			// the label may contain %s for the locale's display name
			return String.format(name, localeDisplayName());
		} catch (RuntimeException e) {
			return name;
		}
	}

	private String localeDisplayName() {
		Locale locale = !TextUtils.isEmpty(mSubtypeLanguageTag) ? Locale.forLanguageTag(mSubtypeLanguageTag) : new Locale(mSubtypeLocale);
		return locale.getDisplayName(locale);
	}

	private HashMap<String, String> getExtraValueHashMap() {
		synchronized (this) {
			if (mExtraValueHashMapCache == null) {
				mExtraValueHashMapCache = new HashMap<>();
				for (String pair : mSubtypeExtraValue.split(EXTRA_VALUE_PAIR_SEPARATOR)) {
					String[] pairs = pair.split(EXTRA_VALUE_KEY_VALUE_SEPARATOR);
					if (pairs.length == 1)
						mExtraValueHashMapCache.put(pairs[0], null);
					else if (pairs.length > 1)
						mExtraValueHashMapCache.put(pairs[0], pairs[1]);
				}
			}
			return mExtraValueHashMapCache;
		}
	}

	public boolean containsExtraValueKey(String key) {
		return getExtraValueHashMap().containsKey(key);
	}

	public String getExtraValueOf(String key) {
		return getExtraValueHashMap().get(key);
	}

	@Override
	public int hashCode() {
		return mSubtypeHashCode;
	}

	public final boolean hasSubtypeId() {
		return mSubtypeId != SUBTYPE_ID_NONE;
	}

	public final int getSubtypeId() {
		return mSubtypeId;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof InputMethodSubtype))
			return false;
		InputMethodSubtype s = (InputMethodSubtype)o;
		if (s.hasSubtypeId() || hasSubtypeId())
			return s.hashCode() == hashCode();
		return s.hashCode() == hashCode() && s.getLocale().equals(getLocale()) && s.getLanguageTag().equals(getLanguageTag()) && s.getMode().equals(getMode()) && s.getExtraValue().equals(getExtraValue()) && s.isAuxiliary() == isAuxiliary() && s.overridesImplicitlyEnabledSubtype() == overridesImplicitlyEnabledSubtype() && s.isAsciiCapable() == isAsciiCapable();
	}

	@Override
	public String toString() {
		return "InputMethodSubtype{locale=" + mSubtypeLocale + ", languageTag=" + mSubtypeLanguageTag + ", mode=" + mSubtypeMode + ", extraValue=" + mSubtypeExtraValue + "}";
	}

	private static int hashCodeInternal(String locale, String mode, String extraValue, boolean isAuxiliary, boolean overridesImplicitlyEnabledSubtype, boolean isAsciiCapable) {
		return Arrays.hashCode(new Object[] {locale, mode, extraValue, isAuxiliary, overridesImplicitlyEnabledSubtype, isAsciiCapable});
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mSubtypeNameResId);
		dest.writeInt(mSubtypeIconResId);
		dest.writeString(mSubtypeLocale);
		dest.writeString(mSubtypeLanguageTag);
		dest.writeString(mSubtypeMode);
		dest.writeString(mSubtypeExtraValue);
		dest.writeInt(mIsAuxiliary ? 1 : 0);
		dest.writeInt(mOverridesImplicitlyEnabledSubtype ? 1 : 0);
		dest.writeInt(mSubtypeId);
		dest.writeInt(mIsAsciiCapable ? 1 : 0);
	}

	public static final Parcelable.Creator<InputMethodSubtype> CREATOR = new Parcelable.Creator<InputMethodSubtype>() {
		@Override
		public InputMethodSubtype createFromParcel(Parcel source) {
			return new InputMethodSubtypeBuilder()
			    .setSubtypeNameResId(source.readInt())
			    .setSubtypeIconResId(source.readInt())
			    .setSubtypeLocale(source.readString())
			    .setLanguageTag(source.readString())
			    .setSubtypeMode(source.readString())
			    .setSubtypeExtraValue(source.readString())
			    .setIsAuxiliary(source.readInt() == 1)
			    .setOverridesImplicitlyEnabledSubtype(source.readInt() == 1)
			    .setSubtypeId(source.readInt())
			    .setIsAsciiCapable(source.readInt() == 1)
			    .build();
		}

		@Override
		public InputMethodSubtype[] newArray(int size) {
			return new InputMethodSubtype[size];
		}
	};
}
