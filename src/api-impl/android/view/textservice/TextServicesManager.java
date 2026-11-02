package android.view.textservice;

import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/* There is no spell checker service: like Android without one, no session
 * is created and spell checking counts as disabled. */
public class TextServicesManager {

	public SpellCheckerSession newSpellCheckerSession(Bundle bundle, Locale locale, SpellCheckerSession.SpellCheckerSessionListener listener, boolean referToSpellCheckerLanguageSettings) {
		return null;
	}

	public boolean isSpellCheckerEnabled() {
		return false;
	}

	public SpellCheckerInfo getCurrentSpellCheckerInfo() {
		return null;
	}

	public List<SpellCheckerInfo> getEnabledSpellCheckerInfos() {
		return new ArrayList<SpellCheckerInfo>();
	}
}
