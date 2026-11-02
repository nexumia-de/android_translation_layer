package android.speech.tts;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Speaks through speech-dispatcher's spd-say, which uses the voices the
 * user configured for the desktop (espeak-ng, piper, ...). Without spd-say
 * in PATH, initialization fails with ERROR as on a device without engine.
 */
public class TextToSpeech {
	private static final String TAG = "TextToSpeech";

	public static final int SUCCESS = 0;
	public static final int ERROR = -1;
	public static final int STOPPED = -2;
	public static final int QUEUE_FLUSH = 0;
	public static final int QUEUE_ADD = 1;
	public static final int LANG_COUNTRY_VAR_AVAILABLE = 2;
	public static final int LANG_COUNTRY_AVAILABLE = 1;
	public static final int LANG_AVAILABLE = 0;
	public static final int LANG_MISSING_DATA = -1;
	public static final int LANG_NOT_SUPPORTED = -2;
	public static final int ERROR_SYNTHESIS = -3;
	public static final int ERROR_SERVICE = -4;
	public static final int ERROR_OUTPUT = -5;
	public static final int ERROR_NOT_INSTALLED_YET = -9;
	public static final String ACTION_TTS_QUEUE_PROCESSING_COMPLETED = "android.speech.tts.TTS_QUEUE_PROCESSING_COMPLETED";

	private static final String ENGINE = "org.freedesktop.speechd";

	public class Engine {
		public static final String KEY_PARAM_UTTERANCE_ID = "utteranceId";
		public static final String KEY_PARAM_STREAM = "streamType";
		public static final String KEY_PARAM_VOLUME = "volume";
		public static final String KEY_PARAM_PAN = "pan";
		public static final String KEY_PARAM_SESSION_ID = "sessionId";
		public static final String KEY_FEATURE_NETWORK_SYNTHESIS = "networkTts";
		public static final String KEY_FEATURE_EMBEDDED_SYNTHESIS = "embeddedTts";
		public static final String ACTION_CHECK_TTS_DATA = "android.speech.tts.engine.CHECK_TTS_DATA";
		public static final String ACTION_INSTALL_TTS_DATA = "android.speech.tts.engine.INSTALL_TTS_DATA";
		public static final String ACTION_GET_SAMPLE_TEXT = "android.speech.tts.engine.GET_SAMPLE_TEXT";
		public static final String EXTRA_AVAILABLE_VOICES = "availableVoices";
		public static final String EXTRA_UNAVAILABLE_VOICES = "unavailableVoices";
		public static final int CHECK_VOICE_DATA_PASS = 1;
		public static final int CHECK_VOICE_DATA_FAIL = 0;
		public static final int DEFAULT_STREAM = 3;
	}

	public static class EngineInfo {
		public String name;
		public String label;
		public int icon;
		public int priority;
		public boolean system;
	}

	public static interface OnInitListener {
		abstract void onInit(int status);
	}

	public static interface OnUtteranceCompletedListener {
		public abstract void onUtteranceCompleted(String utteranceId);
	}

	private static class Utterance {
		String text;
		String id;
	}

	private final String spdSay;
	private final LinkedList<Utterance> queue = new LinkedList<>();
	private Thread worker;
	private Process current;
	private Utterance currentUtterance;
	private boolean shutdown;
	private Locale language = Locale.getDefault();
	private int rate = 0;
	private int pitch = 0;
	private UtteranceProgressListener progressListener;
	private OnUtteranceCompletedListener completedListener;
	private static Set<String> voiceLanguages;

	public TextToSpeech(Context context, OnInitListener listener) {
		this(context, listener, null);
	}

	public TextToSpeech(Context context, final OnInitListener listener, String engine) {
		spdSay = findInPath("spd-say");
		if (spdSay == null)
			Log.w(TAG, "spd-say not found, text to speech is unavailable; install speech-dispatcher");
		if (listener != null) {
			new Handler(Looper.getMainLooper()).post(new Runnable() {
				@Override
				public void run() {
					listener.onInit(spdSay != null ? SUCCESS : ERROR);
				}
			});
		}
	}

	private static String findInPath(String name) {
		String path = System.getenv("PATH");
		if (path == null)
			path = "/usr/local/bin:/usr/bin:/bin";
		for (String dir : path.split(":")) {
			File f = new File(dir, name);
			if (f.canExecute())
				return f.getPath();
		}
		return null;
	}

	/* languages of the voices speech-dispatcher offers, as lower case "xx" and "xx-yy" */
	private Set<String> getVoiceLanguages() {
		synchronized (TextToSpeech.class) {
			if (voiceLanguages != null)
				return voiceLanguages;
			voiceLanguages = new HashSet<>();
			if (spdSay == null)
				return voiceLanguages;
			try {
				Process p = new ProcessBuilder(spdSay, "-L").redirectErrorStream(true).start();
				BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line = r.readLine(); // header: NAME LANGUAGE VARIANT
				while ((line = r.readLine()) != null) {
					String[] fields = line.trim().split("\\s+");
					if (fields.length < 2)
						continue;
					String lang = fields[fields.length - 2].toLowerCase(Locale.ROOT).replace('_', '-');
					voiceLanguages.add(lang);
					int dash = lang.indexOf('-');
					voiceLanguages.add(dash > 0 ? lang.substring(0, dash) : lang);
				}
				p.waitFor();
			} catch (Exception e) {
				Log.w(TAG, "cannot list speech-dispatcher voices: " + e);
			}
			return voiceLanguages;
		}
	}

	public int isLanguageAvailable(Locale loc) {
		if (spdSay == null || loc == null)
			return LANG_NOT_SUPPORTED;
		Set<String> langs = getVoiceLanguages();
		String l = loc.getLanguage().toLowerCase(Locale.ROOT);
		String c = loc.getCountry().toLowerCase(Locale.ROOT);
		/* an empty list means spd-say -L gave nothing usable; let the module decide */
		if (langs.isEmpty())
			return LANG_AVAILABLE;
		if (!c.isEmpty() && langs.contains(l + "-" + c))
			return LANG_COUNTRY_AVAILABLE;
		return langs.contains(l) ? LANG_AVAILABLE : LANG_NOT_SUPPORTED;
	}

	public int setLanguage(Locale loc) {
		int result = isLanguageAvailable(loc);
		if (result >= LANG_AVAILABLE)
			language = loc;
		return result;
	}

	public Locale getLanguage() {
		return language;
	}

	public Voice getVoice() {
		return spdSay == null ? null : new Voice(language.toLanguageTag(), language, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, null);
	}

	public Voice getDefaultVoice() {
		Locale l = Locale.getDefault();
		return spdSay == null ? null : new Voice(l.toLanguageTag(), l, Voice.QUALITY_NORMAL, Voice.LATENCY_NORMAL, false, null);
	}

	public int setVoice(Voice voice) {
		return voice != null && setLanguage(voice.getLocale()) >= LANG_AVAILABLE ? SUCCESS : ERROR;
	}

	/* speech-dispatcher plays through its own audio output */
	public int setAudioAttributes(android.media.AudioAttributes audioAttributes) {
		return SUCCESS;
	}

	public Locale getDefaultLanguage() {
		return Locale.getDefault();
	}

	public Set<Locale> getAvailableLanguages() {
		Set<Locale> locales = new HashSet<>();
		for (String lang : getVoiceLanguages())
			locales.add(Locale.forLanguageTag(lang));
		return locales;
	}

	/* Android's rate and pitch are factors around 1.0; speech-dispatcher's run from -100 to 100 */
	private static int toSpdScale(float factor) {
		if (factor <= 0)
			return 0;
		int v = Math.round((float)(50 * Math.log(factor) / Math.log(2)));
		return Math.max(-100, Math.min(100, v));
	}

	public int setSpeechRate(float speechRate) {
		rate = toSpdScale(speechRate);
		return SUCCESS;
	}

	public int setPitch(float pitch) {
		this.pitch = toSpdScale(pitch);
		return SUCCESS;
	}

	public String getDefaultEngine() {
		return ENGINE;
	}

	public List<EngineInfo> getEngines() {
		List<EngineInfo> engines = new ArrayList<>();
		if (spdSay != null) {
			EngineInfo info = new EngineInfo();
			info.name = ENGINE;
			info.label = "Speech Dispatcher";
			info.system = true;
			engines.add(info);
		}
		return engines;
	}

	public int setEngineByPackageName(String enginePackageName) {
		return spdSay != null ? SUCCESS : ERROR;
	}

	public int speak(CharSequence text, int queueMode, Bundle params, String utteranceId) {
		return enqueue(text == null ? "" : text.toString(), queueMode, utteranceId);
	}

	@Deprecated
	public int speak(String text, int queueMode, HashMap<String, String> params) {
		return enqueue(text, queueMode, params == null ? null : params.get(Engine.KEY_PARAM_UTTERANCE_ID));
	}

	public int playSilentUtterance(long durationInMs, int queueMode, String utteranceId) {
		return enqueue(null, queueMode, utteranceId);
	}

	private int enqueue(String text, int queueMode, String utteranceId) {
		if (spdSay == null)
			return ERROR;
		synchronized (queue) {
			if (shutdown)
				return ERROR;
			if (queueMode == QUEUE_FLUSH)
				stopLocked(true);
			Utterance u = new Utterance();
			u.text = text;
			u.id = utteranceId;
			queue.add(u);
			if (worker == null) {
				worker = new Thread(new Runnable() {
					@Override
					public void run() {
						workerLoop();
					}
				}, "TextToSpeech");
				worker.setDaemon(true);
				worker.start();
			}
			queue.notifyAll();
		}
		return SUCCESS;
	}

	private void workerLoop() {
		while (true) {
			Utterance u;
			Process p = null;
			synchronized (queue) {
				while (queue.isEmpty() && !shutdown) {
					try {
						queue.wait();
					} catch (InterruptedException e) {
					}
				}
				if (shutdown)
					return;
				u = queue.removeFirst();
				currentUtterance = u;
			}
			UtteranceProgressListener l = progressListener;
			if (l != null && u.id != null)
				l.onStart(u.id);
			boolean ok = true;
			if (u.text != null && !u.text.isEmpty()) {
				try {
					synchronized (queue) {
						if (currentUtterance == u) {
							p = new ProcessBuilder(spdSay, "-w", "-l", language.toLanguageTag(),
							                       "-r", Integer.toString(rate), "-p", Integer.toString(pitch), "--", u.text)
							        .redirectErrorStream(true).start();
							current = p;
						}
					}
					if (p != null) {
						p.getOutputStream().close();
						ok = p.waitFor() == 0;
					}
				} catch (Exception e) {
					Log.w(TAG, "spd-say failed: " + e);
					ok = false;
				}
			}
			boolean interrupted;
			synchronized (queue) {
				interrupted = currentUtterance != u;
				if (!interrupted)
					currentUtterance = null;
				current = null;
			}
			if (u.id == null)
				continue;
			l = progressListener;
			if (interrupted) {
				if (l != null)
					l.onStop(u.id, true);
			} else if (ok) {
				if (l != null)
					l.onDone(u.id);
				OnUtteranceCompletedListener c = completedListener;
				if (c != null)
					c.onUtteranceCompleted(u.id);
			} else if (l != null) {
				l.onError(u.id, ERROR_OUTPUT);
			}
		}
	}

	/* called with the queue lock held */
	private void stopLocked(boolean notify) {
		UtteranceProgressListener l = progressListener;
		for (Utterance u : queue) {
			if (notify && l != null && u.id != null)
				l.onStop(u.id, false);
		}
		queue.clear();
		currentUtterance = null;
		if (current != null)
			current.destroy();
	}

	public int stop() {
		synchronized (queue) {
			stopLocked(true);
		}
		return SUCCESS;
	}

	public boolean isSpeaking() {
		synchronized (queue) {
			return currentUtterance != null || !queue.isEmpty();
		}
	}

	public void shutdown() {
		synchronized (queue) {
			stopLocked(false);
			shutdown = true;
			queue.notifyAll();
		}
	}

	public int setOnUtteranceProgressListener(UtteranceProgressListener listener) {
		progressListener = listener;
		return SUCCESS;
	}

	@Deprecated
	public int setOnUtteranceCompletedListener(OnUtteranceCompletedListener listener) {
		completedListener = listener;
		return SUCCESS;
	}

	public static int getMaxSpeechInputLength() {
		return 4000;
	}
}
