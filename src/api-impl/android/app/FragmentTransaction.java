package android.app;

import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

public class FragmentTransaction {

	static final int OP_ADD = 1;
	static final int OP_REPLACE = 2;

	static class Op {
		int cmd;
		int containerViewId;
		Fragment fragment;
	}

	private final Activity activity;
	private final List<Op> ops = new ArrayList<>();

	FragmentTransaction(Activity activity) {
		this.activity = activity;
	}

	public FragmentTransaction add(Fragment fragment, String tag) {
		Op op = new Op();
		op.cmd = OP_ADD;
		op.fragment = fragment;
		ops.add(op);
		return this;
	}

	public FragmentTransaction replace(int containerViewId, Fragment fragment) {
		Op op = new Op();
		op.cmd = OP_REPLACE;
		op.containerViewId = containerViewId;
		op.fragment = fragment;
		ops.add(op);
		return this;
	}

	public int commit() {
		for (Op op : ops) {
			if (op.cmd == OP_REPLACE) {
				removeFragmentsInContainer(op.containerViewId);
				addFragment(op.containerViewId, op.fragment);
			} else if (op.cmd == OP_ADD) {
				addFragment(op.containerViewId, op.fragment);
			}
		}
		ops.clear();
		return 0;
	}

	private void removeFragmentsInContainer(int containerViewId) {
		for (int i = activity.fragments.size() - 1; i >= 0; i--) {
			Fragment f = activity.fragments.get(i);
			if (f.containerViewId == containerViewId) {
				if (f.view != null && f.view.getParent() instanceof ViewGroup)
					((ViewGroup)f.view.getParent()).removeView(f.view);
				f.onDestroyView();
				f.onDestroy();
				f.onDetach();
				activity.fragments.remove(i);
			}
		}
	}

	private void addFragment(int containerViewId, Fragment fragment) {
		fragment.activity = activity;
		fragment.containerViewId = containerViewId;
		if (!activity.fragments.contains(fragment))
			activity.fragments.add(fragment);
		fragment.onAttach(activity);
		fragment.onCreate(null);
		ViewGroup container = containerViewId != 0 ? (ViewGroup)activity.findViewById(containerViewId) : null;
		fragment.view = fragment.onCreateView(activity.getLayoutInflater(), container, null);
		if (fragment.view != null && container != null) {
			container.removeAllViews();
			container.addView(fragment.view);
		}
		fragment.onViewCreated(fragment.view, null);
		fragment.onActivityCreated(null);
		fragment.onStart();
		fragment.onResume();
	}
}
