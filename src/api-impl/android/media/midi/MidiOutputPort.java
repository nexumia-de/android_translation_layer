package android.media.midi;

import java.io.Closeable;
import java.io.IOException;

public final class MidiOutputPort extends MidiSender implements Closeable {

	private final int portNumber;

	MidiOutputPort(int portNumber) {
		this.portNumber = portNumber;
	}

	public final int getPortNumber() {
		return portNumber;
	}

	@Override
	public void onConnect(MidiReceiver receiver) {}

	@Override
	public void onDisconnect(MidiReceiver receiver) {}

	@Override
	public void close() throws IOException {}
}
