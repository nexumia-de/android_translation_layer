package android.view.textservice;

public final class SentenceSuggestionsInfo {
	private final SuggestionsInfo[] suggestionsInfos;
	private final int[] offsets;
	private final int[] lengths;

	public SentenceSuggestionsInfo(SuggestionsInfo[] suggestionsInfos, int[] offsets, int[] lengths) {
		this.suggestionsInfos = suggestionsInfos;
		this.offsets = offsets;
		this.lengths = lengths;
	}

	public int getSuggestionsCount() {
		return suggestionsInfos.length;
	}

	public SuggestionsInfo getSuggestionsInfoAt(int i) {
		return suggestionsInfos[i];
	}

	public int getOffsetAt(int i) {
		return offsets[i];
	}

	public int getLengthAt(int i) {
		return lengths[i];
	}
}
