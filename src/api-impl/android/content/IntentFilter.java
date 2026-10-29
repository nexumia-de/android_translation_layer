package android.content;

import android.net.Uri;
import android.os.PatternMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class IntentFilter {

	private List<String> actions = new ArrayList<>();
	private Set<String> categories = new HashSet<>();
	private List<String> dataSchemes = new ArrayList<>();
	private List<String> dataAuthorities = new ArrayList<>();
	private List<PatternMatcher> dataPaths = new ArrayList<>();

	public IntentFilter() {}
	public IntentFilter(String action) {
		addAction(action);
	}

	public void addAction(String action) {
		actions.add(action);
	}
	public int countActions() {
		return actions.size();
	}

	public final boolean matchAction(String action) {
		return actions.contains(action);
	}

	public void addCategory(String category) {
		categories.add(category);
	}
	public int countCategories() {
		return categories.size();
	}

	public final boolean hasCategory(String category) {
		return categories.contains(category);
	}

	public String getAction(int index) {
		return actions.get(index);
	}

	public void setPriority(int priority) {}

	public void addDataScheme(String dataScheme) {
		dataSchemes.add(dataScheme);
	}

	public void addDataAuthority(String host, String port) {
		dataAuthorities.add(host);
	}

	public void addDataPath(String path, int type) {
		dataPaths.add(new PatternMatcher(path, type));
	}

	/* the host and path of a URI against <data android:host> and android:path*; a host may start with a "*" wildcard */
	private boolean matchAuthorityAndPath(Uri data) {
		if (data == null || dataAuthorities.isEmpty())
			return true;
		String host = data.getHost();
		boolean hostMatches = false;
		for (String authority : dataAuthorities) {
			if (host != null && (authority.equals(host) || (authority.startsWith("*") && host.endsWith(authority.substring(1))))) {
				hostMatches = true;
				break;
			}
		}
		if (!hostMatches)
			return false;
		if (dataPaths.isEmpty())
			return true;
		for (PatternMatcher path : dataPaths) {
			if (path.match(data.getPath()))
				return true;
		}
		return false;
	}

	public final void addDataSchemeSpecificPart(String ssp, int type) {
		/* FIXME */
	}

	public boolean hasDataScheme(String dataScheme) {
		return dataSchemes.contains(dataScheme);
	}

	public int countDataSchemes() {
		return dataSchemes.size();
	}

	public String getDataScheme(int index) {
		return dataSchemes.get(index);
	}

	public final Iterator<String> actionsIterator() {
		return actions.iterator();
	}

	public boolean hasAction(String action) {
		return actions.contains(action);
	}

	public int match(String action, String type, String scheme, Uri data, Set<String> categories, String logTag) {
		int ret = 0;
		if (!matchAction(action)) {
			ret = -3 /*NO_MATCH_ACTION*/;
		}
		if (scheme == null) {
			ret = 0x00100000 /*MATCH_CATEGORY_EMPTY*/ | 0x00008000 /*MATCH_ADJUSTMENT_NORMAL*/;
		} else if (hasDataScheme(scheme) && matchAuthorityAndPath(data)) {
			if (data == null || dataAuthorities.isEmpty())
				ret = 0x00200000 /*MATCH_CATEGORY_SCHEME*/;
			else if (dataPaths.isEmpty())
				ret = 0x00300000 /*MATCH_CATEGORY_HOST*/;
			else
				ret = 0x00500000 /*MATCH_CATEGORY_PATH*/;
			ret |= 0x00008000 /*MATCH_ADJUSTMENT_NORMAL*/;
		} else {
			ret = -2 /*NO_MATCH_DATA*/;
		}
		return ret;
	}

	public final Iterator<String> schemesIterator() {
		return dataSchemes.iterator();
	}

	public final Iterator authoritiesIterator() {
		return null;
	}

	public final int countDataAuthorities() {
		return dataAuthorities.size();
	}

	public final int countDataPaths() {
		return dataPaths.size();
	}
}
