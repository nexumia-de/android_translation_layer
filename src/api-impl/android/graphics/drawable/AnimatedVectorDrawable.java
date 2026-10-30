package android.graphics.drawable;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;

public class AnimatedVectorDrawable extends Drawable implements Animatable2 {

	/* the animation is not drawn, but apps wait for its end (e.g. splash screens) */
	private static final int ANIMATION_MS = 300;

	private final ArrayList<AnimationCallback> callbacks = new ArrayList<>();
	private boolean running;

	public void registerAnimationCallback(AnimationCallback callback) {
		if (callback != null)
			callbacks.add(callback);
	}

	public boolean unregisterAnimationCallback(AnimationCallback callback) {
		return callbacks.remove(callback);
	}

	public void clearAnimationCallbacks() {
		callbacks.clear();
	}

	public void start() {
		if (running)
			return;
		running = true;
		for (AnimationCallback callback : new ArrayList<>(callbacks))
			callback.onAnimationStart(this);
		new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
			@Override
			public void run() {
				stop();
			}
		}, ANIMATION_MS);
	}

	public void stop() {
		if (!running)
			return;
		running = false;
		for (AnimationCallback callback : new ArrayList<>(callbacks))
			callback.onAnimationEnd(this);
	}

	public boolean isRunning() {
		return running;
	}
}
