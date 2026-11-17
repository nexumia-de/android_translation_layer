package android.speech;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/* There is no speech recognition service: isRecognitionAvailable() says so, and a
 * recognizer an app creates anyway answers startListening() with ERROR_CLIENT. */
public class SpeechRecognizer {
	public static final String RESULTS_RECOGNITION = "results_recognition";
	public static final String CONFIDENCE_SCORES = "confidence_scores";

	public static final int ERROR_NETWORK_TIMEOUT = 1;
	public static final int ERROR_NETWORK = 2;
	public static final int ERROR_AUDIO = 3;
	public static final int ERROR_SERVER = 4;
	public static final int ERROR_CLIENT = 5;
	public static final int ERROR_SPEECH_TIMEOUT = 6;
	public static final int ERROR_NO_MATCH = 7;
	public static final int ERROR_RECOGNIZER_BUSY = 8;
	public static final int ERROR_INSUFFICIENT_PERMISSIONS = 9;
	public static final int ERROR_TOO_MANY_REQUESTS = 10;
	public static final int ERROR_SERVER_DISCONNECTED = 11;
	public static final int ERROR_LANGUAGE_NOT_SUPPORTED = 12;
	public static final int ERROR_LANGUAGE_UNAVAILABLE = 13;
	public static final int ERROR_CANNOT_CHECK_SUPPORT = 14;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private RecognitionListener listener;

	private SpeechRecognizer() {}

	public static boolean isRecognitionAvailable(Context context) {
		return false;
	}

	public static boolean isOnDeviceRecognitionAvailable(Context context) {
		return false;
	}

	public static SpeechRecognizer createSpeechRecognizer(Context context) {
		return new SpeechRecognizer();
	}

	public static SpeechRecognizer createSpeechRecognizer(Context context, ComponentName serviceComponent) {
		return new SpeechRecognizer();
	}

	public static SpeechRecognizer createOnDeviceSpeechRecognizer(Context context) {
		return new SpeechRecognizer();
	}

	public void setRecognitionListener(RecognitionListener listener) {
		this.listener = listener;
	}

	public void startListening(Intent recognizerIntent) {
		final RecognitionListener listener = this.listener;
		if (listener == null)
			return;
		handler.post(new Runnable() {
			@Override
			public void run() {
				listener.onError(ERROR_CLIENT);
			}
		});
	}

	public void stopListening() {}

	public void cancel() {}

	public void destroy() {
		listener = null;
	}
}
