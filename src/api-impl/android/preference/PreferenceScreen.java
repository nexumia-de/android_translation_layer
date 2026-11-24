package android.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ListView;

public class PreferenceScreen extends PreferenceGroup {

	public PreferenceScreen(Context context, AttributeSet attrs) {
		super(context, attrs, com.android.internal.R.attr.preferenceScreenStyle);
	}

	public void bind(ListView listView) {
	}
}
