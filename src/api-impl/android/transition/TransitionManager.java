package android.transition;

import android.view.ViewGroup;

/* layout changes are applied at once, without animation */
public class TransitionManager {

	public static void beginDelayedTransition(ViewGroup sceneRoot) {}

	public static void beginDelayedTransition(ViewGroup sceneRoot, Transition transition) {}

	public static void endTransitions(ViewGroup sceneRoot) {}
}
