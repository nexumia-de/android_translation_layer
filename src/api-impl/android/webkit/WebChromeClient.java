package android.webkit;

import android.content.Intent;
import android.net.Uri;
import android.os.Message;
import android.view.View;

public class WebChromeClient {
	public interface CustomViewCallback {
		void onCustomViewHidden();
	}

	public static abstract class FileChooserParams {
		public static final int MODE_OPEN = 0;
		public static final int MODE_OPEN_MULTIPLE = 1;
		public static final int MODE_OPEN_FOLDER = 2;
		public static final int MODE_SAVE = 3;

		public static Uri[] parseResult(int resultCode, Intent data) {
			if (resultCode != -1 /*Activity.RESULT_OK*/ || data == null || data.getData() == null)
				return null;
			return new Uri[] {data.getData()};
		}

		public abstract int getMode();
		public abstract String[] getAcceptTypes();
		public abstract boolean isCaptureEnabled();
		public abstract CharSequence getTitle();
		public abstract String getFilenameHint();
		public abstract Intent createIntent();
	}

	public void onProgressChanged(WebView view, int newProgress) {}

	public void onReceivedTitle(WebView view, String title) {}

	public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
		return false;
	}

	public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
		return false;
	}

	public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
		return false;
	}

	public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
		return false;
	}

	public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
		return false;
	}

	public void onPermissionRequest(PermissionRequest request) {
		request.deny();
	}

	public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {}

	public void onGeolocationPermissionsHidePrompt() {}

	public void onShowCustomView(View view, CustomViewCallback callback) {}

	public void onHideCustomView() {}

	public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
		return false;
	}

	public void onCloseWindow(WebView window) {}

	public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
		return false;
	}
}
