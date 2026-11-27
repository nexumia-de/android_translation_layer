package android.webkit;

import android.graphics.Bitmap;

public class WebViewClient {
	public void onPageStarted(WebView view, String url, Bitmap favicon) {}
	public void onPageFinished(WebView view, String url) {}
	public boolean shouldOverrideUrlLoading(WebView view, String url) {
		return false; // continue loading the URL
	}
	public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
		return shouldOverrideUrlLoading(view, request.getUrl().toString());
	}
}
