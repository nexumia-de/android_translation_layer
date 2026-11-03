package android.view.textservice;

public final class SuggestionsInfo {
	public static final int RESULT_ATTR_IN_THE_DICTIONARY = 0x0001;
	public static final int RESULT_ATTR_LOOKS_LIKE_TYPO = 0x0002;
	public static final int RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS = 0x0004;
	public static final int RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR = 0x0008;
	public static final int RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS = 0x0010;

	private final int suggestionsAttributes;
	private final String[] suggestions;
	private int cookie;
	private int sequence;

	public SuggestionsInfo(int suggestionsAttributes, String[] suggestions) {
		this(suggestionsAttributes, suggestions, 0, 0);
	}

	public SuggestionsInfo(int suggestionsAttributes, String[] suggestions, int cookie, int sequence) {
		this.suggestionsAttributes = suggestionsAttributes;
		this.suggestions = suggestions == null ? new String[0] : suggestions;
		this.cookie = cookie;
		this.sequence = sequence;
	}

	public void setCookieAndSequence(int cookie, int sequence) {
		this.cookie = cookie;
		this.sequence = sequence;
	}

	public int getCookie() {
		return cookie;
	}

	public int getSequence() {
		return sequence;
	}

	public int getSuggestionsAttributes() {
		return suggestionsAttributes;
	}

	public int getSuggestionsCount() {
		return suggestions.length;
	}

	public String getSuggestionAt(int i) {
		return suggestions[i];
	}
}
