package android.webkit;

import android.atl.ATLLoadedApp;
import android.atl.annotation.Export;
import android.net.Uri;
import android.util.Log;
import android.webkit.WebView;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TimeZone;

/* The cookie jar that an app's own HTTP code shares with its WebViews.
 * Cookies are kept here, so getCookie() works on any thread and before
 * WebKit is loaded, and saved in the app's data directory. Once WebKit is
 * loaded, every cookie set here is also set in WebKit's default network
 * session, and the cookies a page sets come back through
 * internalWebKitCookies(). */
public class CookieManager {
	private static final String TAG = "CookieManager";

	private static CookieManager instance;

	public static synchronized CookieManager getInstance() {
		// HACK: disable NewPipe's WebView based PoToken generator for now
		if (ATLLoadedApp.getPrimaryApplication().pkg.packageName.equals("org.schabi.newpipe")) {
			throw new RuntimeException("CookieManager not yet fully implemented");
		}
		try { // also handle NewPipe forks which can have a different packagename
			ATLLoadedApp.getPrimaryApplication().loadClass(
			    "org.schabi.newpipe.util.potoken.PoTokenWebView");
			throw new RuntimeException("CookieManager not yet fully implemented");
		} catch (ClassNotFoundException e) {
		}
		if (instance == null)
			instance = new CookieManager();
		return instance;
	}

	private static final class Cookie {
		String name;
		String value;
		String domain; // without a leading dot
		boolean hostOnly;
		String path;
		long expires = -1; // ms since the epoch, -1 for a session cookie
		boolean secure;
		boolean httpOnly;
		int sameSite; // 0 unset, 1 None, 2 Lax, 3 Strict

		boolean sameKey(Cookie other) {
			return name.equals(other.name) && domain.equals(other.domain) && path.equals(other.path);
		}

		boolean sameAs(Cookie o) {
			return sameKey(o) && value.equals(o.value) && hostOnly == o.hostOnly && expires == o.expires && secure == o.secure && httpOnly == o.httpOnly && sameSite == o.sameSite;
		}

		boolean expired(long now) {
			return expires != -1 && expires <= now;
		}
	}

	private final ArrayList<Cookie> cookies = new ArrayList<>();
	private File file;
	private boolean acceptCookie = true;
	private boolean acceptThirdParty;
	/* bumped by every change made here, so a list from WebKit that was
	 * requested before such a change does not undo it */
	private int generation;
	private boolean webkitAttached;

	private CookieManager() {
		try {
			File dir = new File(ATLLoadedApp.getPrimaryApplication().getApplication().getDataDir(), "app_webview");
			dir.mkdirs();
			file = new File(dir, "atl-cookies");
			load();
		} catch (Exception e) {
			Log.w(TAG, "cookies are not saved", e);
			file = null;
		}
	}

	/* ---- parsing and matching ---- */

	private static String host(Uri uri) {
		String host = uri.getHost();
		return host == null ? null : host.toLowerCase(Locale.ROOT);
	}

	private static Uri parseUrl(String url) {
		if (url == null)
			return null;
		Uri uri = Uri.parse(url);
		if (uri.getScheme() == null) // Android accepts "example.com"
			uri = Uri.parse("http://" + url);
		return host(uri) == null ? null : uri;
	}

	private static boolean domainMatches(String host, String domain) {
		return host.equals(domain) || (host.endsWith("." + domain) && !Character.isDigit(host.charAt(host.length() - 1)));
	}

	private static boolean pathMatches(String requestPath, String cookiePath) {
		if (requestPath.equals(cookiePath))
			return true;
		return requestPath.startsWith(cookiePath) && (cookiePath.endsWith("/") || requestPath.charAt(cookiePath.length()) == '/');
	}

	private static String defaultPath(Uri uri) {
		String path = uri.getPath();
		if (path == null || !path.startsWith("/"))
			return "/";
		int slash = path.lastIndexOf('/');
		return slash == 0 ? "/" : path.substring(0, slash);
	}

	private static final String[] DATE_FORMATS = {
	    "EEE, dd MMM yyyy HH:mm:ss zzz",
	    "EEE, dd-MMM-yyyy HH:mm:ss zzz",
	    "EEE, dd-MMM-yy HH:mm:ss zzz",
	    "EEE, dd MMM yy HH:mm:ss zzz",
	    "EEE MMM d HH:mm:ss yyyy",
	};

	private static long parseDate(String s) {
		for (String format : DATE_FORMATS) {
			SimpleDateFormat f = new SimpleDateFormat(format, Locale.US);
			f.setTimeZone(TimeZone.getTimeZone("GMT"));
			Date d = f.parse(s, new ParsePosition(0));
			if (d != null)
				return d.getTime();
		}
		return Long.MIN_VALUE;
	}

	/* one Set-Cookie value, as Android's setCookie() takes it */
	private static Cookie parse(Uri uri, String header, long now) {
		String[] parts = header.split(";");
		int eq = parts[0].indexOf('=');
		Cookie c = new Cookie();
		c.name = (eq < 0 ? "" : parts[0].substring(0, eq)).trim();
		c.value = (eq < 0 ? parts[0] : parts[0].substring(eq + 1)).trim();
		String host = host(uri);
		c.domain = host;
		c.hostOnly = true;
		c.path = defaultPath(uri);
		long maxAgeExpiry = Long.MIN_VALUE;
		for (int i = 1; i < parts.length; i++) {
			String attr = parts[i].trim();
			int aeq = attr.indexOf('=');
			String key = (aeq < 0 ? attr : attr.substring(0, aeq)).trim().toLowerCase(Locale.ROOT);
			String val = aeq < 0 ? "" : attr.substring(aeq + 1).trim();
			switch (key) {
				case "domain":
					String domain = (val.startsWith(".") ? val.substring(1) : val).toLowerCase(Locale.ROOT);
					if (domain.isEmpty())
						break;
					if (!domainMatches(host, domain))
						return null; // a cookie for another site
					c.domain = domain;
					c.hostOnly = false;
					break;
				case "path":
					if (val.startsWith("/"))
						c.path = val;
					break;
				case "expires":
					long t = parseDate(val);
					if (t != Long.MIN_VALUE)
						c.expires = t;
					break;
				case "max-age":
					try {
						long seconds = Long.parseLong(val);
						maxAgeExpiry = seconds <= 0 ? 0 : now + Math.min(seconds, Long.MAX_VALUE / 2000) * 1000;
					} catch (NumberFormatException e) {
					}
					break;
				case "secure":
					c.secure = true;
					break;
				case "httponly":
					c.httpOnly = true;
					break;
				case "samesite":
					c.sameSite = val.equalsIgnoreCase("strict") ? 3 : val.equalsIgnoreCase("lax") ? 2 : val.equalsIgnoreCase("none") ? 1 : 0;
					break;
			}
		}
		if (maxAgeExpiry != Long.MIN_VALUE) // Max-Age wins over Expires
			c.expires = maxAgeExpiry;
		return c;
	}

	/* ---- the jar ---- */

	/* replaces a cookie with the same name, domain and path; an expired one only deletes */
	private boolean store(Cookie c, long now) {
		boolean changed = false;
		for (Iterator<Cookie> it = cookies.iterator(); it.hasNext();) {
			Cookie old = it.next();
			if (old.sameKey(c)) {
				if (old.sameAs(c))
					return false;
				it.remove();
				changed = true;
			}
		}
		if (!c.expired(now)) {
			cookies.add(c);
			changed = true;
		}
		return changed;
	}

	private void dropExpired(long now) {
		for (Iterator<Cookie> it = cookies.iterator(); it.hasNext();)
			if (it.next().expired(now))
				it.remove();
	}

	public synchronized String getCookie(String url) {
		Uri uri = parseUrl(url);
		if (uri == null)
			return null;
		String host = host(uri);
		String path = uri.getPath();
		if (path == null || path.isEmpty())
			path = "/";
		boolean secure = "https".equalsIgnoreCase(uri.getScheme()) || "wss".equalsIgnoreCase(uri.getScheme());
		long now = System.currentTimeMillis();
		ArrayList<Cookie> matching = new ArrayList<>();
		for (Cookie c : cookies) {
			if (c.expired(now) || (c.secure && !secure))
				continue;
			if (c.hostOnly ? !host.equals(c.domain) : !domainMatches(host, c.domain))
				continue;
			if (pathMatches(path, c.path))
				matching.add(c);
		}
		if (matching.isEmpty())
			return null;
		// longer paths first, as browsers send them
		Collections.sort(matching, new Comparator<Cookie>() {
			@Override
			public int compare(Cookie a, Cookie b) {
				return b.path.length() - a.path.length();
			}
		});
		StringBuilder sb = new StringBuilder();
		for (Cookie c : matching) {
			if (sb.length() > 0)
				sb.append("; ");
			if (!c.name.isEmpty())
				sb.append(c.name).append('=');
			sb.append(c.value);
		}
		return sb.toString();
	}

	public String getCookie(String url, boolean privateBrowsing) {
		return getCookie(url);
	}

	public void setCookie(String url, String value) {
		setCookie(url, value, null);
	}

	public void setCookie(String url, String value, ValueCallback<Boolean> callback) {
		boolean ok = false;
		synchronized (this) {
			Uri uri = parseUrl(url);
			if (acceptCookie && uri != null && value != null) {
				long now = System.currentTimeMillis();
				Cookie c = parse(uri, value, now);
				if (c != null) {
					ok = true;
					if (store(c, now)) {
						generation++;
						save();
						if (webkitAttached) {
							if (c.expired(now))
								native_deleteCookie(c.name, c.domain, c.hostOnly, c.path);
							else
								pushToWebKit(c);
						}
					}
				}
			}
		}
		if (callback != null)
			callback.onReceiveValue(ok);
	}

	public synchronized boolean hasCookies() {
		dropExpired(System.currentTimeMillis());
		return !cookies.isEmpty();
	}

	public boolean hasCookies(boolean privateBrowsing) {
		return hasCookies();
	}

	private synchronized boolean remove(boolean sessionOnly) {
		boolean changed = false;
		for (Iterator<Cookie> it = cookies.iterator(); it.hasNext();) {
			Cookie c = it.next();
			if (!sessionOnly || c.expires == -1) {
				it.remove();
				changed = true;
				if (webkitAttached && sessionOnly)
					native_deleteCookie(c.name, c.domain, c.hostOnly, c.path);
			}
		}
		if (webkitAttached && !sessionOnly)
			native_deleteAll();
		if (changed) {
			generation++;
			save();
		}
		return changed;
	}

	public void removeAllCookies(ValueCallback<Boolean> callback) {
		boolean changed = remove(false);
		if (callback != null)
			callback.onReceiveValue(changed);
	}

	public void removeSessionCookies(ValueCallback<Boolean> callback) {
		boolean changed = remove(true);
		if (callback != null)
			callback.onReceiveValue(changed);
	}

	public synchronized void removeExpiredCookie() {
		dropExpired(System.currentTimeMillis());
	}

	public void removeAllCookie() {
		remove(false);
	}

	public void removeSessionCookie() {
		remove(true);
	}

	public synchronized void flush() {
		save();
	}

	public synchronized void setAcceptCookie(boolean accept) {
		acceptCookie = accept;
	}

	public synchronized boolean acceptCookie() {
		return acceptCookie;
	}

	public synchronized boolean acceptThirdPartyCookies(WebView webview) {
		return acceptThirdParty;
	}

	public synchronized void setAcceptThirdPartyCookies(WebView webView, boolean accept) {
		acceptThirdParty = accept;
	}

	public static boolean allowFileSchemeCookies() {
		return false;
	}

	public static void setAcceptFileSchemeCookies(boolean accept) {}

	/* ---- saving ---- */

	private static final int FILE_VERSION = 1;

	private void load() throws IOException {
		if (!file.exists())
			return;
		try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
			if (in.readInt() != FILE_VERSION)
				return;
			int count = in.readInt();
			long now = System.currentTimeMillis();
			for (int i = 0; i < count; i++) {
				Cookie c = new Cookie();
				c.name = in.readUTF();
				c.value = in.readUTF();
				c.domain = in.readUTF();
				c.hostOnly = in.readBoolean();
				c.path = in.readUTF();
				c.expires = in.readLong();
				c.secure = in.readBoolean();
				c.httpOnly = in.readBoolean();
				c.sameSite = in.readInt();
				if (!c.expired(now))
					cookies.add(c);
			}
		}
	}

	private void save() {
		if (file == null)
			return;
		dropExpired(System.currentTimeMillis());
		File tmp = new File(file.getPath() + ".tmp");
		try (DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp))) {
			out.writeInt(FILE_VERSION);
			out.writeInt(cookies.size());
			for (Cookie c : cookies) {
				out.writeUTF(c.name);
				out.writeUTF(c.value);
				out.writeUTF(c.domain);
				out.writeBoolean(c.hostOnly);
				out.writeUTF(c.path);
				out.writeLong(c.expires);
				out.writeBoolean(c.secure);
				out.writeBoolean(c.httpOnly);
				out.writeInt(c.sameSite);
			}
		} catch (IOException e) {
			Log.w(TAG, "could not save cookies", e);
			return;
		}
		if (!tmp.renameTo(file))
			Log.w(TAG, "could not save cookies to " + file);
	}

	/* ---- WebKit ---- */

	private void pushToWebKit(Cookie c) {
		native_addCookie(c.name, c.value, c.domain, c.hostOnly, c.path, c.expires, c.secure, c.httpOnly, c.sameSite);
	}

	/* called by WebView right after it loaded WebKit, before its first page */
	static void attachWebKit() {
		CookieManager manager;
		try {
			manager = getInstance();
		} catch (RuntimeException e) { // the NewPipe hack above
			return;
		}
		synchronized (manager) {
			if (manager.webkitAttached)
				return;
			manager.webkitAttached = true;
			native_attach();
			long now = System.currentTimeMillis();
			for (Cookie c : manager.cookies)
				if (!c.expired(now))
					manager.pushToWebKit(c);
			native_requestCookies(manager.generation);
		}
	}

	// to be used by native code
	@Export
	private static void internalWebKitChanged() {
		CookieManager manager = instance;
		if (manager == null)
			return;
		synchronized (manager) {
			native_requestCookies(manager.generation);
		}
	}

	/* WebKit's cookies; each entry is name, value, domain (with a leading
	 * dot unless host-only), path, expiry in ms or -1, flags ("s" secure,
	 * "h" http-only) and SameSite, separated by tabs */
	// to be used by native code
	@Export
	private static void internalWebKitCookies(int requestGeneration, String[] entries) {
		CookieManager manager = instance;
		if (manager == null)
			return;
		synchronized (manager) {
			ArrayList<Cookie> fromWebKit = new ArrayList<>();
			for (String entry : entries) {
				String[] f = entry.split("\t", -1);
				if (f.length < 7)
					continue;
				Cookie c = new Cookie();
				c.name = f[0];
				c.value = f[1];
				c.hostOnly = !f[2].startsWith(".");
				c.domain = (c.hostOnly ? f[2] : f[2].substring(1)).toLowerCase(Locale.ROOT);
				c.path = f[3];
				c.expires = Long.parseLong(f[4]);
				c.secure = f[5].contains("s");
				c.httpOnly = f[5].contains("h");
				c.sameSite = Integer.parseInt(f[6]);
				fromWebKit.add(c);
			}
			long now = System.currentTimeMillis();
			boolean changed = false;
			if (requestGeneration == manager.generation) {
				/* nothing changed here since the request: WebKit's list is the
				 * whole truth, including cookies a page deleted */
				for (Iterator<Cookie> it = manager.cookies.iterator(); it.hasNext();) {
					Cookie old = it.next();
					boolean kept = false;
					for (Cookie c : fromWebKit)
						kept |= c.sameKey(old);
					if (!kept) {
						it.remove();
						changed = true;
					}
				}
			}
			for (Cookie c : fromWebKit)
				changed |= manager.store(c, now);
			if (changed)
				manager.save();
		}
	}

	private static native void native_attach();
	private static native void native_addCookie(String name, String value, String domain, boolean hostOnly, String path, long expires, boolean secure, boolean httpOnly, int sameSite);
	private static native void native_deleteCookie(String name, String domain, boolean hostOnly, String path);
	private static native void native_deleteAll();
	private static native void native_requestCookies(int generation);
}
