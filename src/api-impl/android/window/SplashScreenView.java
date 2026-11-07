package android.window;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * The view of the system's splash screen, handed to an app's exit animation
 * listener. ATL shows no splash screen, so none is ever created; androidx
 * core-splashscreen still checks added views against this class.
 */
public final class SplashScreenView extends FrameLayout {
	private SplashScreenView(Context context) {
		super(context);
	}

	public View getIconView() {
		return null;
	}

	public void remove() {
		if (getParent() instanceof ViewGroup)
			((ViewGroup)getParent()).removeView(this);
	}
}
