package android.media.midi;

import java.io.Closeable;
import java.io.IOException;

public final class MidiInputPort extends MidiReceiver implements Closeable {

	private final int portNumber;

	MidiInputPort(int portNumber) {
		this.portNumber = portNumber;
	}

	public final int getPortNumber() {
		return portNumber;
	}

	@Override
	public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {}

	@Override
	public void close() throws IOException {}
}
