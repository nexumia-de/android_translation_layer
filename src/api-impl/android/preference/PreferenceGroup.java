package android.preference;

import android.content.Context;
import android.util.AttributeSet;

public class PreferenceGroup extends Preference {

	public PreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public PreferenceGroup(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, 0);
	}

	public boolean addPreference(Preference preference) {
		return false;
	}

	public Preference findPreference(CharSequence key) {
		return null;
	}
	
}
