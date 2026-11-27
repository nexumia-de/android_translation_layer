package android.view.accessibility;

import android.atl.ATLPaths;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;

/* The caption preferences Android keeps in Settings.Secure (accessibility_captioning_*): on or
 * off, text size and one of Android's style presets. ATL's caption settings screen
 * (android.atl.ATLCaptioningSettings, opened by Settings.ACTION_CAPTIONING_SETTINGS) changes
 * them; they are stored in the app data directory, as ATL has no system settings. */
public class CaptioningManager {
	private static final String TAG = "CaptioningManager";

	/* Android's Settings.Secure keys */
	static final String KEY_ENABLED = "accessibility_captioning_enabled";
	static final String KEY_PRESET = "accessibility_captioning_preset";
	static final String KEY_FONT_SCALE = "accessibility_captioning_font_scale";

	private static Properties settings;
	private static final ArrayList<CaptioningChangeListener> listeners = new ArrayList<>();

	private static File settingsFile() {
		return new File(ATLPaths.app_data_dir_base, "captioning.properties");
	}

	private static synchronized Properties settings() {
		if (settings == null) {
			settings = new Properties();
			try (FileInputStream in = new FileInputStream(settingsFile())) {
				settings.load(in);
			} catch (IOException e) {
				/* not set yet: Android's defaults */
			}
		}
		return settings;
	}

	public boolean isEnabled() {
		return isEnabledSetting();
	}

	public float getFontScale() {
		return getFontScaleSetting();
	}

	public Locale getLocale() {
		return null;
	}

	public CaptionStyle getUserStyle() {
		return CaptionStyle.PRESETS[getPresetSetting()];
	}

	public boolean isSystemAudioCaptioningEnabled() {
		return false;
	}

	public boolean isSystemAudioCaptioningUiEnabled() {
		return false;
	}

	/** @hide */
	public static boolean isEnabledSetting() {
		return "1".equals(settings().getProperty(KEY_ENABLED, "0"));
	}

	/** @hide */
	public static float getFontScaleSetting() {
		try {
			return Float.parseFloat(settings().getProperty(KEY_FONT_SCALE, "1.0"));
		} catch (NumberFormatException e) {
			return 1.0f;
		}
	}

	/** @hide */
	public static int getPresetSetting() {
		try {
			int preset = Integer.parseInt(settings().getProperty(KEY_PRESET, "0"));
			return preset >= 0 && preset < CaptionStyle.PRESETS.length ? preset : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** @hide: called by ATL's caption settings screen; tells the listeners, as Android's settings observer does */
	public static void putSettings(final boolean enabled, final float fontScale, final int preset) {
		final boolean enabledChanged = enabled != isEnabledSetting();
		final boolean scaleChanged = fontScale != getFontScaleSetting();
		final boolean styleChanged = preset != getPresetSetting();
		synchronized (CaptioningManager.class) {
			Properties s = settings();
			s.setProperty(KEY_ENABLED, enabled ? "1" : "0");
			s.setProperty(KEY_FONT_SCALE, Float.toString(fontScale));
			s.setProperty(KEY_PRESET, Integer.toString(preset));
			File file = settingsFile();
			file.getParentFile().mkdirs();
			try (FileOutputStream out = new FileOutputStream(file)) {
				s.store(out, null);
			} catch (IOException e) {
				Slog.w(TAG, "cannot save the caption settings: " + e);
			}
		}
		final CaptionStyle style = CaptionStyle.PRESETS[preset];
		final ArrayList<CaptioningChangeListener> copy;
		synchronized (listeners) {
			copy = new ArrayList<>(listeners);
		}
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				for (CaptioningChangeListener l : copy) {
					if (enabledChanged)
						l.onEnabledChanged(enabled);
					if (scaleChanged)
						l.onFontScaleChanged(fontScale);
					if (styleChanged)
						l.onUserStyleChanged(style);
				}
			}
		});
	}

	public static final class CaptionStyle {
		public static final int COLOR_UNSPECIFIED = 0x00FFFFFF;
		public static final int EDGE_TYPE_UNSPECIFIED = -1;
		public static final int EDGE_TYPE_NONE = 0;
		public static final int EDGE_TYPE_OUTLINE = 1;
		public static final int EDGE_TYPE_DROP_SHADOW = 2;
		public static final int EDGE_TYPE_RAISED = 3;
		public static final int EDGE_TYPE_DEPRESSED = 4;

		/* Android's presets, in the order of accessibility_captioning_preset */
		private static final CaptionStyle WHITE_ON_BLACK = new CaptionStyle(Color.WHITE, Color.BLACK, EDGE_TYPE_NONE, Color.BLACK, COLOR_UNSPECIFIED);
		private static final CaptionStyle BLACK_ON_WHITE = new CaptionStyle(Color.BLACK, Color.WHITE, EDGE_TYPE_NONE, Color.BLACK, COLOR_UNSPECIFIED);
		private static final CaptionStyle YELLOW_ON_BLACK = new CaptionStyle(Color.YELLOW, Color.BLACK, EDGE_TYPE_NONE, Color.BLACK, COLOR_UNSPECIFIED);
		private static final CaptionStyle YELLOW_ON_BLUE = new CaptionStyle(Color.YELLOW, Color.BLUE, EDGE_TYPE_NONE, Color.BLACK, COLOR_UNSPECIFIED);

		/** @hide */
		public static final CaptionStyle[] PRESETS = {WHITE_ON_BLACK, BLACK_ON_WHITE, YELLOW_ON_BLACK, YELLOW_ON_BLUE};

		public static final CaptionStyle DEFAULT = WHITE_ON_BLACK;

		public final int foregroundColor;
		public final int backgroundColor;
		public final int edgeType;
		public final int edgeColor;
		public final int windowColor;

		private CaptionStyle(int foregroundColor, int backgroundColor, int edgeType, int edgeColor, int windowColor) {
			this.foregroundColor = foregroundColor;
			this.backgroundColor = backgroundColor;
			this.edgeType = edgeType;
			this.edgeColor = edgeColor;
			this.windowColor = windowColor;
		}

		public boolean hasForegroundColor() {
			return foregroundColor != COLOR_UNSPECIFIED;
		}

		public boolean hasBackgroundColor() {
			return backgroundColor != COLOR_UNSPECIFIED;
		}

		public boolean hasEdgeType() {
			return edgeType != EDGE_TYPE_UNSPECIFIED;
		}

		public boolean hasEdgeColor() {
			return edgeColor != COLOR_UNSPECIFIED;
		}

		public boolean hasWindowColor() {
			return windowColor != COLOR_UNSPECIFIED;
		}

		public Typeface getTypeface() {
			return null;
		}
	}

	public static abstract class CaptioningChangeListener {
		public void onEnabledChanged(boolean enabled) {}
		public void onUserStyleChanged(CaptionStyle userStyle) {}
		public void onLocaleChanged(Locale locale) {}
		public void onFontScaleChanged(float fontScale) {}
		public void onSystemAudioCaptioningChanged(boolean enabled) {}
		public void onSystemAudioCaptioningUiChanged(boolean enabled) {}
	}

	public void addCaptioningChangeListener(CaptioningChangeListener listener) {
		synchronized (listeners) {
			if (!listeners.contains(listener))
				listeners.add(listener);
		}
	}

	public void removeCaptioningChangeListener(CaptioningChangeListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}
}
