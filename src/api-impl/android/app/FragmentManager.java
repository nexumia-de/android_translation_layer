package android.app;

public class FragmentManager {

	private Activity activity;

	public FragmentManager(Activity activity) {
		this.activity = activity;
	}

	// androidx's ProcessLifecycleOwner finds its ReportFragment this way below API 29
	public Fragment findFragmentByTag(String tag) {
		if (tag == null)
			return null;
		for (Fragment fragment : activity.fragments) {
			if (tag.equals(fragment.tag))
				return fragment;
		}
		return null;
	}

	public FragmentTransaction beginTransaction() {
		return new FragmentTransaction(activity);
	}

	public boolean executePendingTransactions() {
		return false;
	}
}
