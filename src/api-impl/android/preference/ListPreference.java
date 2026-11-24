package android.preference;

import android.content.Context;
import android.util.AttributeSet;

public class ListPreference extends Preference {

	public ListPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public String getValue() { return null; }

	public CharSequence[] getEntryValues() {
		CharSequence[] array = new CharSequence[0];
		return array;
	}

	public void setSummary(CharSequence sequence) {}
}
