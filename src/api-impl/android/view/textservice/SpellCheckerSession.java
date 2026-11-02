package android.view.textservice;

/* ATL has no spell checker service, so TextServicesManager never creates a
 * session; the class and its listener exist for apps that implement it. */
public class SpellCheckerSession {

	public interface SpellCheckerSessionListener {
		public void onGetSuggestions(SuggestionsInfo[] results);

		public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results);
	}

	private SpellCheckerSession() {}

	public boolean isSessionDisconnected() {
		return true;
	}

	public SpellCheckerInfo getSpellChecker() {
		return null;
	}

	public void cancel() {}

	public void close() {}

	public void getSentenceSuggestions(TextInfo[] textInfos, int suggestionsLimit) {}

	public void getSuggestions(TextInfo textInfo, int suggestionsLimit) {}

	public void getSuggestions(TextInfo[] textInfos, int suggestionsLimit, boolean sequentialWords) {}
}
