package android.text.style;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.ParcelableSpan;

public interface LeadingMarginSpan extends ParagraphStyle {

	public int getLeadingMargin(boolean first);

	public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
	                              CharSequence text, int start, int end, boolean first, Layout layout);

	public static class Standard implements LeadingMarginSpan, ParcelableSpan {
		private final int first;
		private final int rest;

		public Standard(int indent) {
			this(indent, indent);
		}

		public Standard(int first_indent, int rest_indent) {
			first = first_indent;
			rest = rest_indent;
		}

		@Override
		public int getLeadingMargin(boolean first) {
			return first ? this.first : rest;
		}

		@Override
		public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
		                              CharSequence text, int start, int end, boolean first, Layout layout) {}
	}
}
