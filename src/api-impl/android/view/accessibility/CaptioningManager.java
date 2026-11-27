package android.view.accessibility;

import android.graphics.Color;
import android.graphics.Typeface;
import java.util.Locale;

/* captions are not configurable here; the values are Android's defaults with captioning off */
public class CaptioningManager {

	public boolean isEnabled() {
		return false;
	}

	public float getFontScale() {
		return 1.0f;
	}

	public Locale getLocale() {
		return null;
	}

	public CaptionStyle getUserStyle() {
		return CaptionStyle.DEFAULT;
	}

	public boolean isSystemAudioCaptioningEnabled() {
		return false;
	}

	public boolean isSystemAudioCaptioningUiEnabled() {
		return false;
	}

	public static final class CaptionStyle {
		public static final int COLOR_UNSPECIFIED = 0x00FFFFFF;
		public static final int EDGE_TYPE_UNSPECIFIED = -1;
		public static final int EDGE_TYPE_NONE = 0;
		public static final int EDGE_TYPE_OUTLINE = 1;
		public static final int EDGE_TYPE_DROP_SHADOW = 2;
		public static final int EDGE_TYPE_RAISED = 3;
		public static final int EDGE_TYPE_DEPRESSED = 4;

		public static final CaptionStyle DEFAULT = new CaptionStyle(Color.WHITE, Color.BLACK, EDGE_TYPE_NONE, Color.BLACK, COLOR_UNSPECIFIED);

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

	/* the settings never change, so listeners are never called */
	public void addCaptioningChangeListener(CaptioningChangeListener listener) {}

	public void removeCaptioningChangeListener(CaptioningChangeListener listener) {}
}
