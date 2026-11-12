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

	public void setRepeatCount(int count) {}

	public void setRepeatMode(int mode) {}

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
				applyTransformation(1, new Transformation());
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
