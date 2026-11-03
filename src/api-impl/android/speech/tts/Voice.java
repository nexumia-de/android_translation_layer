package android.speech.tts;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class Voice {
	public static final int QUALITY_VERY_LOW = 100;
	public static final int QUALITY_LOW = 200;
	public static final int QUALITY_NORMAL = 300;
	public static final int QUALITY_HIGH = 400;
	public static final int QUALITY_VERY_HIGH = 500;
	public static final int LATENCY_VERY_LOW = 100;
	public static final int LATENCY_LOW = 200;
	public static final int LATENCY_NORMAL = 300;
	public static final int LATENCY_HIGH = 400;
	public static final int LATENCY_VERY_HIGH = 500;

	private final String name;
	private final Locale locale;
	private final int quality;
	private final int latency;
	private final boolean requiresNetworkConnection;
	private final Set<String> features;

	public Voice(String name, Locale locale, int quality, int latency, boolean requiresNetworkConnection, Set<String> features) {
		this.name = name;
		this.locale = locale;
		this.quality = quality;
		this.latency = latency;
		this.requiresNetworkConnection = requiresNetworkConnection;
		this.features = features != null ? features : Collections.<String>emptySet();
	}

	public String getName() { return name; }
	public Locale getLocale() { return locale; }
	public int getQuality() { return quality; }
	public int getLatency() { return latency; }
	public boolean isNetworkConnectionRequired() { return requiresNetworkConnection; }
	public Set<String> getFeatures() { return features; }

	@Override
	public String toString() {
		return "Voice[" + name + " " + locale + "]";
	}
}
