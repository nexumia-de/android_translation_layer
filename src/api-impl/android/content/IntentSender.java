package android.content;

import android.app.PendingIntent;
import android.os.Handler;

public class IntentSender {
	private final PendingIntent pendingIntent;

	public IntentSender(PendingIntent pendingIntent) {
		this.pendingIntent = pendingIntent;
	}

	public void sendIntent(Context context, int code, Intent intent, OnFinished onFinished, Handler handler) throws SendIntentException {
		sendIntent(context, code, intent, onFinished, handler, null);
	}

	public void sendIntent(Context context, int code, Intent intent, OnFinished onFinished, Handler handler, String requiredPermission) throws SendIntentException {
		pendingIntent.send(context, code, intent);
		if (onFinished != null)
			onFinished.onSendFinished(this, intent, code, null, null);
	}

	public String getCreatorPackage() {
		return pendingIntent.getCreatorPackage();
	}

	public interface OnFinished {
		void onSendFinished(IntentSender intentSender, Intent intent, int resultCode, String resultData, android.os.Bundle resultExtras);
	}

	public static class SendIntentException extends Exception {
		public SendIntentException() {}

		public SendIntentException(String name) {
			super(name);
		}

		public SendIntentException(Exception cause) {
			super(cause);
		}
	}
}
