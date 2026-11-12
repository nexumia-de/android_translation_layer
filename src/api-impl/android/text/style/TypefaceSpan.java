package android.text.style;

public class TypefaceSpan extends MetricAffectingSpan {
	private String family;

	public TypefaceSpan(String family) {
		this.family = family;
	}

	public String getFamily() {
		return family;
	}
}
