package android.text.style;

public class BackgroundColorSpan extends CharacterStyle {
	private int color;

	public BackgroundColorSpan(int color) {
		this.color = color;
	}

	public int getBackgroundColor() {
		return color;
	}
}
