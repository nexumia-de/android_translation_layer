package android.media;

public final class PlaybackParams {

	public static final int AUDIO_FALLBACK_MODE_DEFAULT = 0;
	public static final int AUDIO_FALLBACK_MODE_MUTE = 1;
	public static final int AUDIO_FALLBACK_MODE_FAIL = 2;

	private float speed = 1.0f;
	private float pitch = 1.0f;
	private int audioFallbackMode = AUDIO_FALLBACK_MODE_DEFAULT;

	public PlaybackParams() {}

	public PlaybackParams allowDefaults() {
		return this;
	}

	public PlaybackParams setSpeed(float speed) {
		this.speed = speed;
		return this;
	}

	public float getSpeed() {
		return speed;
	}

	public PlaybackParams setPitch(float pitch) {
		if (pitch < 0.f)
			throw new IllegalArgumentException("pitch must not be negative");
		this.pitch = pitch;
		return this;
	}

	public float getPitch() {
		return pitch;
	}

	public PlaybackParams setAudioFallbackMode(int audioFallbackMode) {
		this.audioFallbackMode = audioFallbackMode;
		return this;
	}

	public int getAudioFallbackMode() {
		return audioFallbackMode;
	}
}
