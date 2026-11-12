package android.text.style;

public class ForegroundColorSpan extends CharacterStyle {
	private int color;

	public ForegroundColorSpan(int color) {
		this.color = color;
	}

	public int getForegroundColor() {
		return color;
	}
}
