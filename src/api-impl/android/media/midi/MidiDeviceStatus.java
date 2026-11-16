package android.media.midi;

public final class MidiDeviceStatus {

	private final MidiDeviceInfo deviceInfo;

	MidiDeviceStatus(MidiDeviceInfo deviceInfo) {
		this.deviceInfo = deviceInfo;
	}

	public MidiDeviceInfo getDeviceInfo() {
		return deviceInfo;
	}

	public boolean isInputPortOpen(int portNumber) {
		return false;
	}

	public int getOutputPortOpenCount(int portNumber) {
		return 0;
	}
}
