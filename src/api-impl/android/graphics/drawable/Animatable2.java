package android.graphics.drawable;

public interface Animatable2 extends Animatable {

	public void registerAnimationCallback(AnimationCallback callback);

	public boolean unregisterAnimationCallback(AnimationCallback callback);

	public void clearAnimationCallbacks();

	public abstract static class AnimationCallback {

		public void onAnimationStart(Drawable drawable) {}

		public void onAnimationEnd(Drawable drawable) {}
	}
}
