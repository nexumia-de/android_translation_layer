package android.webkit;

import android.atl.annotation.Export;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.ViewGroup;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WebView extends ViewGroup {

	private WebViewClient webViewClient;
	private final WebSettings settings = new WebSettings();
	private int contentHeight;

	public WebView(Context context) {
		this(context, null);
	}

	public WebView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public WebView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	public WebSettings getSettings() {
		return settings;
	}

	private WebChromeClient webChromeClient;
	private boolean ownLoadPending;
	private final HashMap<String, Object> javascriptInterfaces = new HashMap<>();

	public void setDownloadListener(DownloadListener downloadListener) {}

	public void setScrollBarStyle(int scrollBarStyle) {}

	public void setWebViewClient(WebViewClient webViewClient) {
		this.webViewClient = webViewClient;
		interceptRequests = webViewClient != null && overridesInterceptRequest(webViewClient.getClass());
	}

	/* WebKitGTK cannot intercept http and https. When the client wants to (Capacitor and
	 * WebViewAssetLoader serve the app from https://localhost/ that way), the page is loaded
	 * through the own scheme atl-https:// instead, which WebKit hands to internalInterceptRequest. */
	private static final String INTERCEPT_PREFIX = "atl-";
	private static final ExecutorService interceptExecutor = Executors.newCachedThreadPool();
	private boolean interceptRequests;
	private String mainFrameUrl;
	private int progress;

	private static boolean overridesInterceptRequest(Class<?> clazz) {
		for (; clazz != null && clazz != WebViewClient.class; clazz = clazz.getSuperclass()) {
			for (Method method : clazz.getDeclaredMethods()) {
				if (method.getName().equals("shouldInterceptRequest"))
					return true;
			}
		}
		return false;
	}

	private String toWebKitUrl(String url) {
		if (interceptRequests && url != null && (url.startsWith("http://") || url.startsWith("https://")))
			return INTERCEPT_PREFIX + url;
		return url;
	}

	private static String toAndroidUrl(String url) {
		if (url != null && (url.startsWith("atl-http://") || url.startsWith("atl-https://")))
			return url.substring(INTERCEPT_PREFIX.length());
		return url;
	}

	private static class Request implements WebResourceRequest {
		private final Uri url;
		private final String method;
		private final Map<String, String> headers = new HashMap<>();
		private final boolean mainFrame;

		Request(String url, String method, String[] headers, boolean mainFrame) {
			this.url = Uri.parse(url);
			this.method = method != null ? method : "GET";
			for (int i = 0; headers != null && i + 1 < headers.length; i += 2)
				this.headers.put(headers[i], headers[i + 1]);
			this.mainFrame = mainFrame;
		}

		public Uri getUrl() { return url; }
		public boolean isForMainFrame() { return mainFrame; }
		public boolean isRedirect() { return false; }
		public boolean hasGesture() { return false; }
		public String getMethod() { return method; }
		public Map<String, String> getRequestHeaders() { return headers; }
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[65536];
		int n;
		try {
			while ((n = in.read(buffer)) > 0)
				out.write(buffer, 0, n);
		} finally {
			in.close();
		}
		return out.toByteArray();
	}

	/* the client returned nothing: load the resource from the network, as Android does */
	private static WebResourceResponse fetch(Request request) throws IOException {
		HttpURLConnection connection = (HttpURLConnection)new URL(request.getUrl().toString()).openConnection();
		connection.setRequestMethod(request.getMethod());
		for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet())
			connection.setRequestProperty(header.getKey(), header.getValue());
		int status = connection.getResponseCode();
		InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
		Map<String, String> headers = new HashMap<>();
		for (Map.Entry<String, List<String>> header : connection.getHeaderFields().entrySet()) {
			if (header.getKey() != null && !header.getValue().isEmpty())
				headers.put(header.getKey(), header.getValue().get(0));
		}
		String mimeType = connection.getContentType();
		if (mimeType != null && mimeType.contains(";"))
			mimeType = mimeType.substring(0, mimeType.indexOf(';')).trim();
		return new WebResourceResponse(mimeType, connection.getContentEncoding(), status, connection.getResponseMessage(), headers, in);
	}

	// to be used by native code; the client runs on a worker thread, as on Android
	@Export
	void internalInterceptRequest(final long request, String url, String method, String[] headers) {
		final WebViewClient client = webViewClient;
		final String androidUrl = toAndroidUrl(url);
		final Request androidRequest = new Request(androidUrl, method, headers, androidUrl.equals(mainFrameUrl));
		final Handler handler = new Handler(Looper.getMainLooper());
		interceptExecutor.execute(new Runnable() {
			@Override
			public void run() {
				int status = 404;
				String reason = "Not Found";
				String mimeType = null;
				List<String> responseHeaders = new ArrayList<>();
				byte[] body = new byte[0];
				try {
					WebResourceResponse response = client != null ? client.shouldInterceptRequest(WebView.this, androidRequest) : null;
					if (response == null)
						response = fetch(androidRequest);
					if (response.getData() != null) {
						status = response.getStatusCode();
						reason = response.getReasonPhrase();
						body = readAll(response.getData());
					}
					mimeType = response.getMimeType();
					if (mimeType != null && response.getEncoding() != null && mimeType.startsWith("text/"))
						mimeType += "; charset=" + response.getEncoding();
					if (response.getResponseHeaders() != null) {
						for (Map.Entry<String, String> header : response.getResponseHeaders().entrySet()) {
							responseHeaders.add(header.getKey());
							responseHeaders.add(header.getValue());
						}
					}
				} catch (IOException | RuntimeException e) {
					e.printStackTrace();
				}
				final int finalStatus = status;
				final String finalReason = reason;
				final String finalMimeType = mimeType;
				final String[] finalHeaders = responseHeaders.toArray(new String[0]);
				final byte[] finalBody = body;
				handler.post(new Runnable() {
					@Override
					public void run() {
						native_finishInterceptedRequest(request, finalStatus, finalReason, finalMimeType, finalHeaders, finalBody);
					}
				});
			}
		});
	}

	public int getProgress() {
		return progress;
	}

	public WebBackForwardList saveState(Bundle outState) {
		return null;
	}

	public WebBackForwardList restoreState(Bundle inState) {
		return null;
	}

	public void pauseTimers() {}

	// to be used by native code
	@Export
	void internalLoadChanged(int loadState, String url) {
		url = toAndroidUrl(url);
		if (loadState == /*WEBKIT_LOAD_STARTED*/ 0) {
			if (webViewClient != null)
				webViewClient.onPageStarted(this, url, null);
			// Android reports some progress right away, too
			progress = 10;
			if (webChromeClient != null)
				webChromeClient.onProgressChanged(this, 10);
		} else if (loadState == /*WEBKIT_LOAD_FINISHED*/ 3) {
			progress = 100;
			if (webChromeClient != null)
				webChromeClient.onProgressChanged(this, 100);
			if (webViewClient != null)
				webViewClient.onPageFinished(this, url);
		}
	}

	@Export
	boolean internalShouldOverrideUrlLoading(String url, boolean mainFrame) {
		url = toAndroidUrl(url);
		if (mainFrame)
			mainFrameUrl = url;
		// on Android the app's own loadUrl/loadData does not reach the client
		if (ownLoadPending || url.startsWith("about:")) {
			ownLoadPending = false;
			return false;
		}
		if (webViewClient != null)
			return webViewClient.shouldOverrideUrlLoading(this, new Request(url, "GET", null, mainFrame));
		else
			return false;
	}

	// to be used by native code
	@Export
	void internalContentHeightChanged(int height) {
		if (height != contentHeight) {
			contentHeight = height;
			requestLayout();
		}
	}

	// wrap_content means the height of the page, as on Android
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec), resolveSize(contentHeight, heightMeasureSpec));
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		if (w != oldw)
			native_updateContentHeight(widget);
	}

	public void setVerticalScrollBarEnabled(boolean enabled) {}
	public void setVerticalScrollbarOverlay(boolean overlay) {}
	public void setInitialScale(int scaleInPercent) {}

	public void addJavascriptInterface(Object object, String name) {
		// HACK: directly call onRenderingDone for OctoDroid, as the javascript interface is not implemented yet
		if (object.getClass().getName().startsWith("com.gh4a") && "NativeClient".equals(name)) {
			try {
				object.getClass().getMethod("onRenderingDone").invoke(object);
			} catch (ReflectiveOperationException e) {
				e.printStackTrace();
			}
		}
		javascriptInterfaces.put(name, object);
	}

	public void setWebChromeClient(WebChromeClient webChromeClient) {
		this.webChromeClient = webChromeClient;
	}

	public void removeAllViews() {}

	public void destroy() {}

	public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
		if ("base64".equals(encoding)) {
			data = new String(Base64.decode(data, 0));
			encoding = "UTF-8";
		}
		if (mimeType != null && mimeType.contains(";")) {
			mimeType = mimeType.substring(0, mimeType.indexOf(";"));
		}
		// webkit doesn't allow overwriting the file:// uri scheme. So we replace it with the android-asset:// scheme
		data = data.replace("file:///android_asset/", "android-asset:///assets/");
		ownLoadPending = true;
		updateBridgeScript();
		native_loadDataWithBaseURL(widget, toWebKitUrl(baseUrl), data, mimeType, encoding, settings.getJavaScriptEnabled());
	}

	public void loadUrl(String url) {
		if (url.startsWith("javascript:")) {
			evaluateJavascript(url.substring("javascript:".length()), null);
			return;
		}
		// webkit doesn't allow overwriting the file:// uri scheme. So we replace it with the android-asset:// scheme
		url = url.replace("file:///android_asset/", "android-asset:///assets/");
		ownLoadPending = true;
		mainFrameUrl = url;
		updateBridgeScript();
		native_loadUrl(widget, toWebKitUrl(url), settings.getJavaScriptEnabled());
	}

	public void stopLoading() {}

	public boolean canGoBack() {
		return native_canGoBackOrForward(widget, -1);
	}

	public void goBack() {
		native_goBackOrForward(widget, -1);
	}

	public boolean canGoForward() {
		return native_canGoBackOrForward(widget, 1);
	}

	public void goForward() {
		native_goBackOrForward(widget, 1);
	}

	public boolean canGoBackOrForward(int steps) {
		return native_canGoBackOrForward(widget, steps);
	}

	public void goBackOrForward(int steps) {
		native_goBackOrForward(widget, steps);
	}

	public void reload() {
		native_reload(widget);
	}

	public String getUrl() {
		return toAndroidUrl(native_getUrl(widget));
	}

	// to be used by native code
	@Export
	AssetManager internalGetAssetManager() {
		return getContext().getResources().getAssets();
	}

	public void onPause() {}

	public void onResume() {}

	public boolean isPaused() {
		return false;
	}

	public void resumeTimers() {}

	public void loadData(String data, String mimeType, String encoding) {
		loadDataWithBaseURL("about:blank", data, mimeType, encoding, "about:blank");
	}

	public void evaluateJavascript(String script, ValueCallback<String> resultCallback) {
		native_evaluateJavascript(widget, script, resultCallback, settings.getJavaScriptEnabled());
	}

	public static void setWebContentsDebuggingEnabled(boolean enabled) {}

	public void clearFormData() {}

	public void clearHistory() {}

	public void clearMatches() {}

	public void clearSslPreferences() {}

	public void clearCache(boolean includeDiskFiles) {}

	// directly accessed by androidx WebViewGlueCommunicator to get ClassLoader
	private static Object getFactory() {
		return new Object();
	}

	public void removeJavascriptInterface(String name) {
		javascriptInterfaces.remove(name);
	}

	/* Android's interfaces answer synchronously, WebKit's script messages only asynchronously,
	 * so each call is a synchronous request to the atl-bridge scheme, see internalBridgeCall */
	private void updateBridgeScript() {
		if (javascriptInterfaces.isEmpty())
			return;
		StringBuilder script = new StringBuilder("(function(){function call(n,m,a){var x=new XMLHttpRequest();"
		    + "x.open('GET','atl-bridge:///'+encodeURIComponent(n)+'/'+encodeURIComponent(m)+'?'+encodeURIComponent(JSON.stringify(Array.prototype.slice.call(a))),false);"
		    + "x.send();return JSON.parse(x.responseText)}"
		    + "function add(n,m){var o=window[n]=window[n]||{};o[m]=function(){return call(n,m,arguments)}}");
		for (Map.Entry<String, Object> entry : javascriptInterfaces.entrySet()) {
			for (Method method : entry.getValue().getClass().getMethods()) {
				if (method.getDeclaringClass() != Object.class)
					script.append("add(").append(JSONObject.quote(entry.getKey())).append(",").append(JSONObject.quote(method.getName())).append(");");
			}
		}
		native_setUserScript(widget, script.append("})()").toString(), settings.getJavaScriptEnabled());
	}

	private static Object convertBridgeArgument(JSONArray args, int index, Class<?> type) throws JSONException {
		if (args.isNull(index))
			return type.isPrimitive() ? (type == boolean.class ? (Object)Boolean.FALSE : (Object)Integer.valueOf(0)) : null;
		if (type == boolean.class || type == Boolean.class)
			return args.getBoolean(index);
		if (type == int.class || type == Integer.class)
			return args.getInt(index);
		if (type == long.class || type == Long.class)
			return args.getLong(index);
		if (type == float.class || type == Float.class)
			return (float)args.getDouble(index);
		if (type == double.class || type == Double.class)
			return args.getDouble(index);
		return args.get(index).toString();
	}

	// to be used by native code
	@Export
	String internalBridgeCall(String uri) {
		try {
			Uri parsed = Uri.parse(uri);
			Object object = javascriptInterfaces.get(parsed.getPathSegments().get(0));
			String methodName = parsed.getPathSegments().get(1);
			JSONArray args = new JSONArray(parsed.getQuery());
			if (object == null)
				return "null";
			for (Method method : object.getClass().getMethods()) {
				Class<?>[] types = method.getParameterTypes();
				if (!method.getName().equals(methodName) || types.length != args.length())
					continue;
				Object[] values = new Object[types.length];
				for (int i = 0; i < types.length; i++)
					values[i] = convertBridgeArgument(args, i, types[i]);
				method.setAccessible(true);
				Object result = method.invoke(object, values);
				return result == null ? "null" : result instanceof String ? JSONObject.quote((String)result) : result.toString();
			}
		} catch (ReflectiveOperationException | JSONException | RuntimeException e) {
			e.printStackTrace();
		}
		return "null";
	}

	// to be used by native code
	@Export
	void internalEvaluateDone(ValueCallback<String> callback, String result) {
		callback.onReceiveValue(result != null ? result : "null");
	}

	@Override
	protected native long native_constructor(Context context, AttributeSet attrs);
	private native void native_loadDataWithBaseURL(long widget, String baseUrl, String data, String mimeType, String encoding, boolean javaScriptEnabled);
	private native void native_loadUrl(long widget, String url, boolean javaScriptEnabled);
	private native void native_updateContentHeight(long widget);
	private native void native_setUserScript(long widget, String script, boolean javaScriptEnabled);
	private native void native_evaluateJavascript(long widget, String script, ValueCallback callback, boolean javaScriptEnabled);
	private native boolean native_canGoBackOrForward(long widget, int steps);
	private native void native_goBackOrForward(long widget, int steps);
	private native void native_reload(long widget);
	private native String native_getUrl(long widget);
	private static native void native_finishInterceptedRequest(long request, int status, String reason, String mimeType, String[] headers, byte[] body);
}
