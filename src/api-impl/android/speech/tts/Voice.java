package android.speech.tts;

import java.util.Locale;

public class Voice {
	private Locale locale;

	Voice(Locale locale) {
		this.locale = locale;
	}

	public Locale getLocale() {
		return this.locale;
	}
}
