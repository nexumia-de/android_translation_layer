package android.transition;

import java.util.ArrayList;

public class TransitionSet extends Transition {
	public static final int ORDERING_TOGETHER = 0;
	public static final int ORDERING_SEQUENTIAL = 1;

	private ArrayList<Transition> transitions = new ArrayList<>();
	private int ordering = ORDERING_TOGETHER;

	public TransitionSet addTransition(Transition transition) {
		transitions.add(transition);
		return this;
	}

	public TransitionSet removeTransition(Transition transition) {
		transitions.remove(transition);
		return this;
	}

	public int getTransitionCount() {
		return transitions.size();
	}

	public Transition getTransitionAt(int index) {
		if (index < 0 || index >= transitions.size())
			return null;
		return transitions.get(index);
	}

	public TransitionSet setOrdering(int ordering) {
		this.ordering = ordering;
		return this;
	}

	public int getOrdering() {
		return ordering;
	}
}
