package android.webkit;

public class CookieManager {

	public static CookieManager getInstance() {
		return new CookieManager();
	}

	public void removeAllCookies(ValueCallback callback) {}

	public void removeSessionCookies(ValueCallback callback) {}

	public void removeExpiredCookie() {}

	public void removeAllCookie() {}

	public void removeSessionCookie() {}

	public void flush() {}

	public String getCookie(String url) {
		return "";
	}

	public void setCookie(String url, String value) {}

	public void setAcceptCookie(boolean accept) {}

	public boolean acceptThirdPartyCookies(WebView webview) {
		return false;
	}

	public void setAcceptThirdPartyCookies(WebView webView, boolean accept) {}

	public static void setAcceptFileSchemeCookies(boolean accept) {}
}
