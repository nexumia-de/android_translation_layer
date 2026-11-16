package android.media.midi;

import java.io.IOException;

public abstract class MidiReceiver {

	private final int maxMessageSize;

	public MidiReceiver() {
		this(Integer.MAX_VALUE);
	}

	public MidiReceiver(int maxMessageSize) {
		this.maxMessageSize = maxMessageSize;
	}

	public abstract void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException;

	public void flush() throws IOException {
		onFlush();
	}

	public void onFlush() throws IOException {}

	public final int getMaxMessageSize() {
		return maxMessageSize;
	}

	public void send(byte[] msg, int offset, int count) throws IOException {
		send(msg, offset, count, 0);
	}

	public void send(byte[] msg, int offset, int count, long timestamp) throws IOException {
		onSend(msg, offset, count, timestamp);
	}
}
