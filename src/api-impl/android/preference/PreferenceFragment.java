package android.preference;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

public abstract class PreferenceFragment extends Fragment {

	private ListView list;
	private PreferenceScreen preferenceScreen;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(com.android.internal.R.layout.preference_list_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		list = (ListView)view.findViewById(android.R.id.list);
		bindPreferences();
	}

	@Override
	public void onDestroyView() {
		list = null;
		super.onDestroyView();
	}

	public void addPreferencesFromResource(int preferencesResId) {
		preferenceScreen = new PreferenceManager(getActivity()).inflateFromResource(getActivity(), preferencesResId, preferenceScreen);
		bindPreferences();
	}

	public Preference findPreference(CharSequence key) {
		return preferenceScreen == null ? null : preferenceScreen.findPreference(key);
	}

	private void bindPreferences() {
		if (preferenceScreen != null && list != null) {
			preferenceScreen.bind(list);
		}
	}
}
