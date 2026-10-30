package android.webkit;

import android.atl.annotation.Export;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
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

	private boolean ownLoadPending;
	private final HashMap<String, Object> javascriptInterfaces = new HashMap<>();

	public void setDownloadListener(DownloadListener downloadListener) {}

	public void setScrollBarStyle(int scrollBarStyle) {}

	public void setWebViewClient(WebViewClient webViewClient) {
		this.webViewClient = webViewClient;
	}

	// to be used by native code
	@Export
	void internalLoadChanged(int loadState, String url) {
		if (loadState == /*WEBKIT_LOAD_STARTED*/ 0 && webViewClient != null) {
			webViewClient.onPageStarted(this, url);
		} else if (loadState == /*WEBKIT_LOAD_FINISHED*/ 3 && webViewClient != null) {
			webViewClient.onPageFinished(this, url);
		}
	}

	@Export
	boolean internalShouldOverrideUrlLoading(String url) {
		// on Android the app's own loadUrl/loadData does not reach the client
		if (ownLoadPending || url.startsWith("about:")) {
			ownLoadPending = false;
			return false;
		}
		if (webViewClient != null)
			return webViewClient.shouldOverrideUrlLoading(this, url);
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

	public void setWebChromeClient(WebChromeClient dummy) {}

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
		native_loadDataWithBaseURL(widget, baseUrl, data, mimeType, encoding, settings.getJavaScriptEnabled());
	}

	public void loadUrl(String url) {
		if (url.startsWith("javascript:")) {
			evaluateJavascript(url.substring("javascript:".length()), null);
			return;
		}
		// webkit doesn't allow overwriting the file:// uri scheme. So we replace it with the android-asset:// scheme
		url = url.replace("file:///android_asset/", "android-asset:///assets/");
		ownLoadPending = true;
		updateBridgeScript();
		native_loadUrl(widget, url, settings.getJavaScriptEnabled());
	}

	public void stopLoading() {}

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
}
