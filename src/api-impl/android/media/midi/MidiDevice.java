package android.media.midi;

import java.io.Closeable;
import java.io.IOException;

public final class MidiDevice implements Closeable {

	private final MidiDeviceInfo info;

	MidiDevice(MidiDeviceInfo info) {
		this.info = info;
	}

	public MidiDeviceInfo getInfo() {
		return info;
	}

	public MidiInputPort openInputPort(int portNumber) {
		return null;
	}

	public MidiOutputPort openOutputPort(int portNumber) {
		return null;
	}

	@Override
	public void close() throws IOException {}
}
