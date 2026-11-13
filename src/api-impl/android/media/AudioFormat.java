package android.media;

public class AudioFormat {

	public static final int CHANNEL_INVALID = 0;

	int sampleRate = 44100;
	int channelMask;
	int encoding;

	public int getSampleRate() { return sampleRate; }
	public int getChannelMask() { return channelMask; }
	public int getEncoding() { return encoding; }
	public int getChannelCount() { return Integer.bitCount(channelMask); }

	public int getFrameSizeInBytes() {
		int bytes;
		switch (encoding) {
			case 3: // ENCODING_PCM_8BIT
				bytes = 1;
				break;
			case 4: // ENCODING_PCM_FLOAT
			case 22: // ENCODING_PCM_32BIT
				bytes = 4;
				break;
			case 21: // ENCODING_PCM_24BIT_PACKED
				bytes = 3;
				break;
			case 1: // ENCODING_DEFAULT
			case 2: // ENCODING_PCM_16BIT
				bytes = 2;
				break;
			default: // compressed formats have no frame size
				return 1;
		}
		return bytes * Math.max(getChannelCount(), 1);
	}

	public static class Builder {

		private AudioFormat audioFormat = new AudioFormat();

		public Builder setSampleRate(int sampleRate) {
			audioFormat.sampleRate = sampleRate;
			return this;
		}

		public Builder setChannelMask(int channelMask) {
			audioFormat.channelMask = channelMask;
			return this;
		}

		public Builder setEncoding(int encoding) {
			audioFormat.encoding = encoding;
			return this;
		}

		public AudioFormat build() {
			return audioFormat;
		}
	}
}
