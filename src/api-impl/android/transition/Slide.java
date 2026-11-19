package android.transition;

import android.view.Gravity;

public class Slide extends Visibility {

	private int slideEdge = Gravity.BOTTOM;

	public Slide() {}

	public Slide(int slideEdge) {
		setSlideEdge(slideEdge);
	}

	public void setSlideEdge(int slideEdge) {
		this.slideEdge = slideEdge;
	}

	public int getSlideEdge() {
		return slideEdge;
	}
}
