package android.text.style;

public class AbsoluteSizeSpan extends MetricAffectingSpan {
	private int size;
	private boolean dip;

	public AbsoluteSizeSpan() {}
	public AbsoluteSizeSpan(int size) {
		this(size, false);
	}
	public AbsoluteSizeSpan(int size, boolean dip) {
		this.size = size;
		this.dip = dip;
	}

	public int getSize() {
		return size;
	}

	public boolean getDip() {
		return dip;
	}
}
