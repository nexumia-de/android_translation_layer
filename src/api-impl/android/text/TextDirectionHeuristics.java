package android.text;

public class TextDirectionHeuristics {

	public static final TextDirectionHeuristic LTR = new TextDirectionHeuristic() {
		@Override
		public boolean isRtl(CharSequence text, int start, int end) {
			return false;
		}
	};

	public static final TextDirectionHeuristic RTL = new TextDirectionHeuristic() {
		@Override
		public boolean isRtl(CharSequence text, int start, int end) {
			return true;
		}
	};

	public static final TextDirectionHeuristic FIRSTSTRONG_LTR = new TextDirectionHeuristic() {
		@Override
		public boolean isRtl(CharSequence text, int start, int end) {
			return false;
		}
	};

	public static final TextDirectionHeuristic FIRSTSTRONG_RTL = new TextDirectionHeuristic() {
		@Override
		public boolean isRtl(CharSequence text, int start, int end) {
			return true;
		}
	};

	/* ATL lays out left to right only (like LTR above), so these never report RTL */
	public static final TextDirectionHeuristic ANYRTL_LTR = LTR;

	public static final TextDirectionHeuristic LOCALE = LTR;
}
