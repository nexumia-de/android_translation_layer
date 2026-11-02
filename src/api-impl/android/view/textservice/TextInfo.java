package android.view.textservice;

public final class TextInfo {
	private final CharSequence text;
	private final int cookie;
	private final int sequenceNumber;

	public TextInfo(String text) {
		this(text, 0, 0);
	}

	public TextInfo(String text, int cookie, int sequenceNumber) {
		this((CharSequence)text, 0, text == null ? 0 : text.length(), cookie, sequenceNumber);
	}

	public TextInfo(CharSequence charSequence, int start, int end, int cookie, int sequenceNumber) {
		this.text = charSequence.subSequence(start, end);
		this.cookie = cookie;
		this.sequenceNumber = sequenceNumber;
	}

	public String getText() {
		return text.toString();
	}

	public CharSequence getCharSequence() {
		return text;
	}

	public int getCookie() {
		return cookie;
	}

	public int getSequence() {
		return sequenceNumber;
	}
}
