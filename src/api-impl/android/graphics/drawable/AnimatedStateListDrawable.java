package android.graphics.drawable;

/* A state list whose transitions are not animated: it switches to the new state's drawable at once.
 * Loaded for <animated-selector> (Material's switch thumb and check box) */
public class AnimatedStateListDrawable extends StateListDrawable {

	public AnimatedStateListDrawable() {
		super();
	}

	public void addState(int[] stateSet, Drawable drawable, int id) {
		addState(stateSet, drawable);
	}

	public <T extends Drawable & Animatable> void addTransition(int fromId, int toId, T transition, boolean reversible) {}
}
