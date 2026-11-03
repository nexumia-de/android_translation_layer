package android.text;

/* A Layout of text that changes, such as an Editable. Layout lays out a copy
 * of the text, so the copy is redone before a query when the text changed.
 * Flutter's InputConnectionAdaptor keeps one to move the cursor by line. */
public class DynamicLayout extends Layout {

	private final CharSequence base;
	private final TextPaint dynamicPaint;
	private int dynamicWidth;
	private String laidOut;

	public DynamicLayout(CharSequence base, TextPaint paint, int width, Alignment align, float spacingmult, float spacingadd, boolean includepad) {
		this(base, base, paint, width, align, spacingmult, spacingadd, includepad);
	}

	public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint, int width, Alignment align, float spacingmult, float spacingadd, boolean includepad) {
		this(base, display, paint, width, align, spacingmult, spacingadd, includepad, null, 0);
	}

	public DynamicLayout(CharSequence base, CharSequence display, TextPaint paint, int width, Alignment align, float spacingmult, float spacingadd, boolean includepad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
		super(display, paint, width, align, spacingmult, spacingadd);
		this.base = display;
		this.dynamicPaint = paint;
		this.dynamicWidth = width;
		this.laidOut = display != null ? display.toString() : "";
	}

	private void sync() {
		String now = base != null ? base.toString() : "";
		if (!now.equals(laidOut)) {
			long old = layout;
			layout = native_constructor(now, dynamicPaint.paint, dynamicWidth);
			native_free(old);
			setAlignment(getAlignment());
			laidOut = now;
		}
	}

	@Override
	public CharSequence getText() { return base; }

	@Override
	public int getLineCount() { sync(); return super.getLineCount(); }

	@Override
	public int getLineForOffset(int offset) { sync(); return super.getLineForOffset(offset); }

	@Override
	public int getLineForVertical(int y) { sync(); return super.getLineForVertical(y); }

	@Override
	public int getOffsetForHorizontal(int line, float x) { sync(); return super.getOffsetForHorizontal(line, x); }

	@Override
	public float getPrimaryHorizontal(int offset) { sync(); return super.getPrimaryHorizontal(offset); }

	@Override
	public int getLineStart(int line) { sync(); return super.getLineStart(line); }

	@Override
	public int getLineEnd(int line) { sync(); return super.getLineEnd(line); }

	@Override
	public int getLineTop(int line) { sync(); return super.getLineTop(line); }

	@Override
	public int getLineBottom(int line) { sync(); return super.getLineBottom(line); }

	@Override
	public int getHeight() { sync(); return super.getHeight(); }
}
