package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class Fragment {

	Activity activity;
	View view;
	int containerViewId;

	public static Fragment instantiate(Context context, String fname, Bundle args) {
		try {
			Class<? extends Fragment> clazz = context.get_atl_loaded_app().loadClass(fname).asSubclass(Fragment.class);
			Fragment f = clazz.getDeclaredConstructor().newInstance();
			return f;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Unable to instantiate fragment " + fname + ": make sure class name exists, is public, and has an empty constructor", e);
		}
	}

	public void onAttach(Activity activity) {
		this.activity = activity;
	}

	public void onDetach() {
		activity = null;
	}

	public void onCreate(Bundle savedInstanceState) {
	}

	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return null;
	}

	public void onViewCreated(View view, Bundle savedInstanceState) {
	}

	public void onActivityCreated(Bundle savedInstanceState) {
	}

	public void onStart() {
	}

	public void onResume() {
	}

	public void onPause() {
	}

	public void onStop() {
	}

	public void onDestroyView() {
	}

	public void onDestroy() {
	}

	public Activity getActivity() {
		return activity;
	}
}
