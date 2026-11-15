package android.view.animation;

import android.os.Handler;

public class Animation {

	public interface AnimationListener {
		public void onAnimationEnd(Animation animation);
		public void onAnimationRepeat(Animation animation);
		public void onAnimationStart(Animation animation);
	}

	private AnimationListener listener;
	private long duration;
	private boolean started;
	private boolean ended;

	public void setDuration(long durationMillis) {
		duration = durationMillis;
	}

	public long getDuration() {
		return duration;
	}

	public void setInterpolator(Interpolator i) {}

	public void cancel() {}

	public void setFillBefore(boolean dummy) {}
	public void setFillAfter(boolean dummy) {}

	public void setStartOffset(long offset) {}

	public void setAnimationListener(AnimationListener l) {
		this.listener = l;
	}

	public static final int INFINITE = -1;
	public static final int RESTART = 1;
	public static final int REVERSE = 2;

	private int repeatCount;
	private int repeatMode = RESTART;

	public void setRepeatCount(int count) {
		repeatCount = count < 0 ? INFINITE : count;
	}

	public int getRepeatCount() {
		return repeatCount;
	}

	public void setRepeatMode(int mode) {
		repeatMode = mode;
	}

	public int getRepeatMode() {
		return repeatMode;
	}

	public void initialize(int width, int height, int parentWidth, int parentHeight) {}

	public void applyTransformation(float interpolatedTime, Transformation t) {}

	public void reset() {
		started = false;
		ended = false;
	}

	public void start() {
		new Handler().post(new Runnable() {
			@Override
			public void run() {
				initialize(0, 0, 0, 0);
				started = true;
				if (listener != null)
					listener.onAnimationStart(Animation.this);
				/* the animation jumps to its end, but apps rely on the repeats in between,
				 * e.g. to swap a text while it is faded out (DB Navigator) */
				for (int i = 0; listener != null && i < (repeatCount == INFINITE ? 1 : repeatCount); i++)
					listener.onAnimationRepeat(Animation.this);
				// an odd number of reversed repeats ends where it started
				applyTransformation(repeatMode == REVERSE && repeatCount % 2 != 0 ? 0 : 1, new Transformation());
				if (repeatCount == INFINITE)
					return; // never ends, as on Android
				ended = true;
				if (listener != null)
					listener.onAnimationEnd(Animation.this);
			}
		});
	}

	public boolean hasStarted() {
		return started;
	}

	public boolean hasEnded() {
		return ended;
	}

	public void setStartTime(long startTime) {}
}
