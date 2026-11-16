package android.media.midi;

import android.os.Handler;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * There are no MIDI devices: the lists are empty, callbacks are never called and opening a device fails.
 */
public final class MidiManager {

	public static final int TRANSPORT_MIDI_BYTE_STREAM = 1;
	public static final int TRANSPORT_UNIVERSAL_MIDI_PACKETS = 2;

	public static class DeviceCallback {
		public void onDeviceAdded(MidiDeviceInfo device) {}

		public void onDeviceRemoved(MidiDeviceInfo device) {}

		public void onDeviceStatusChanged(MidiDeviceStatus status) {}
	}

	public interface OnDeviceOpenedListener {
		void onDeviceOpened(MidiDevice device);
	}

	public void registerDeviceCallback(DeviceCallback callback, Handler handler) {}

	public void registerDeviceCallback(int transport, Executor executor, DeviceCallback callback) {}

	public void unregisterDeviceCallback(DeviceCallback callback) {}

	public MidiDeviceInfo[] getDevices() {
		return new MidiDeviceInfo[0];
	}

	public Set<MidiDeviceInfo> getDevicesForTransport(int transport) {
		return Collections.emptySet();
	}

	public void openDevice(MidiDeviceInfo deviceInfo, final OnDeviceOpenedListener listener, Handler handler) {
		Runnable failed = new Runnable() {
			@Override
			public void run() {
				listener.onDeviceOpened(null);
			}
		};
		if (handler != null)
			handler.post(failed);
		else
			failed.run();
	}
}
