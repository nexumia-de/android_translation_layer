package android.media;

import java.util.Collections;
import java.util.List;

public class AudioDeviceInfo {

	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_BUILTIN_SPEAKER = 2;

	public int getId() {
		return 0;
	}

	public int getType() {
		return TYPE_BUILTIN_SPEAKER;
	}

	public int[] getSampleRates() {
		return new int[0];
	}

	public int getSpeakerLayoutChannelMask() {
		return AudioFormat.CHANNEL_INVALID;
	}

	public List getAudioProfiles() {
		return Collections.emptyList();
	}

	public List getAudioDescriptors() {
		return Collections.emptyList();
	}
}
