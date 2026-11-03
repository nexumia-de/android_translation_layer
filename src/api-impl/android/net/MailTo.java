package android.net;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MailTo {
	public static final String MAILTO_SCHEME = "mailto:";

	private final HashMap<String, String> headers = new HashMap<>();

	private MailTo() {}

	public static boolean isMailTo(String url) {
		return url != null && url.toLowerCase(Locale.ROOT).startsWith(MAILTO_SCHEME);
	}

	public static boolean isMailTo(Uri uri) {
		return uri != null && "mailto".equalsIgnoreCase(uri.getScheme());
	}

	public static MailTo parse(Uri uri) throws ParseException {
		return parse(uri.toString());
	}

	public static MailTo parse(String url) throws ParseException {
		if (url == null)
			throw new NullPointerException();
		if (!isMailTo(url))
			throw new ParseException("Not a mailto scheme");
		String rest = url.substring(MAILTO_SCHEME.length());
		MailTo m = new MailTo();
		int q = rest.indexOf('?');
		String to = Uri.decode(q >= 0 ? rest.substring(0, q) : rest);
		if (q >= 0) {
			for (String pair : rest.substring(q + 1).split("&")) {
				if (pair.isEmpty())
					continue;
				int eq = pair.indexOf('=');
				String key = Uri.decode(eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase(Locale.ROOT);
				m.headers.put(key, eq >= 0 ? Uri.decode(pair.substring(eq + 1)) : "");
			}
		}
		String headerTo = m.headers.get("to");
		if (headerTo != null && !headerTo.isEmpty())
			to = to.isEmpty() ? headerTo : to + ", " + headerTo;
		m.headers.put("to", to);
		return m;
	}

	public String getTo() { return headers.get("to"); }
	public String getCc() { return headers.get("cc"); }
	public String getSubject() { return headers.get("subject"); }
	public String getBody() { return headers.get("body"); }
	public Map<String, String> getHeaders() { return headers; }
}
