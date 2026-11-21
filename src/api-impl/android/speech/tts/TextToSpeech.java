package android.speech.tts;

import android.atl.annotation.Export;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.HashMap;
import java.util.Locale;

public class TextToSpeech {
	public static final int ERROR = -1;
	public static final int SUCCESS = 0;

	public static final int LANG_NOT_SUPPORTED = -2;
	public static final int LANG_COUNTRY_VAR_AVAILABLE = 2;

	public static final String KEY_PARAM_UTTERANCE_ID = "utteranceId";

	Voice default_voice;
	Voice current_voice;

	long spd_connection;

	public TextToSpeech(Context context, OnInitListener listener) {
		default_voice = new Voice(context.getResources().getConfiguration().locale);
		current_voice = default_voice;

		spd_connection = native_initializer(context.getPackageName());

		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				listener.onInit(spd_connection != 0 ? SUCCESS : ERROR);
			}
		});
	}

	public int setOnUtteranceCompletedListener(OnUtteranceCompletedListener listener) {
		native_setOnUtteranceCompletedListener(spd_connection, listener);
		return SUCCESS;
	}

	public int setOnUtteranceProgressListener(UtteranceProgressListener listener) {
		return SUCCESS;
	}

	public void shutdown() {
	}

	public int stop() {
		return SUCCESS;
	}

	public Voice getDefaultVoice() {
		return default_voice;
	}

	public Voice getVoice() {
		return current_voice;
	}

	public int isLanguageAvailable(Locale locale) {
		return LANG_COUNTRY_VAR_AVAILABLE;
	}

	public int setLanguage(Locale locale) {
		current_voice = new Voice(locale);
		boolean ret = native_setLanguage(spd_connection, locale.getLanguage());
		return ret ? LANG_COUNTRY_VAR_AVAILABLE : LANG_NOT_SUPPORTED;
	}

	public int speak(String text, int queueMode, HashMap<String, String> params) {
		String utterance_id = params.get(KEY_PARAM_UTTERANCE_ID);
		boolean ret = native_speak(spd_connection, text, utterance_id);
		return ret ? SUCCESS : ERROR;
	}

	public static interface OnInitListener {
		abstract void onInit(int status);
	}

	public static interface OnUtteranceCompletedListener {
		@Export
		public abstract void onUtteranceCompleted(String utteranceId);
	}

	public static native long native_initializer(String package_name);
	public static native void native_setOnUtteranceCompletedListener(long spd_connection, OnUtteranceCompletedListener listener);
	public static native boolean native_speak(long spd_connection, String text, String utterance_id);
	public static native boolean native_setLanguage(long spd_connection, String lang);
}
