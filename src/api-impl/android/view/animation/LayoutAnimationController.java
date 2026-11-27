package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

/* kept for apps that set one on a ViewGroup; ATL does not animate the children, they appear at once */
public class LayoutAnimationController {
	public static final int ORDER_NORMAL = 0;
	public static final int ORDER_REVERSE = 1;
	public static final int ORDER_RANDOM = 2;

	private Animation animation;
	private float delay;
	private int order = ORDER_NORMAL;
	private Interpolator interpolator;

	public LayoutAnimationController(Context context, AttributeSet attrs) {
		delay = 0.5f;
	}

	public LayoutAnimationController(Animation animation) {
		this(animation, 0.5f);
	}

	public LayoutAnimationController(Animation animation, float delay) {
		this.animation = animation;
		this.delay = delay;
	}

	public int getOrder() { return order; }
	public void setOrder(int order) { this.order = order; }
	public void setAnimation(Context context, int resourceID) { animation = AnimationUtils.loadAnimation(context, resourceID); }
	public void setAnimation(Animation animation) { this.animation = animation; }
	public Animation getAnimation() { return animation; }
	public void setInterpolator(Context context, int resourceID) {}
	public void setInterpolator(Interpolator interpolator) { this.interpolator = interpolator; }
	public Interpolator getInterpolator() { return interpolator; }
	public float getDelay() { return delay; }
	public void setDelay(float delay) { this.delay = delay; }
	public boolean willOverlap() { return delay < 1.0f; }
	public void start() {}
	public final Animation getAnimationForView(View view) { return null; }
	public boolean isDone() { return true; }

	public static class AnimationParameters {
		public int count;
		public int index;
	}
}
