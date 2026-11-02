package android.bluetooth;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public final class BluetoothManager {

	public BluetoothAdapter getAdapter() {
		return BluetoothAdapter.getDefaultAdapter();
	}

	/* GATT connections go through BlueZ's shared link, so "connected" means BlueZ's Device1.Connected */
	public int getConnectionState(BluetoothDevice device, int profile) {
		return device != null && device.isConnected() ? BluetoothProfile.STATE_CONNECTED : BluetoothProfile.STATE_DISCONNECTED;
	}

	public List<BluetoothDevice> getConnectedDevices(int profile) {
		List<BluetoothDevice> devices = new ArrayList<>();
		BluetoothAdapter adapter = getAdapter();
		if (adapter == null)
			return devices;
		for (String path : BlueZ.findObjects(adapter.path, BlueZ.DEVICE)) {
			BluetoothDevice device = BluetoothDevice.fromPath(path);
			if (device != null && device.isConnected())
				devices.add(device);
		}
		return devices;
	}

	public List<BluetoothDevice> getDevicesMatchingConnectionStates(int profile, int[] states) {
		List<BluetoothDevice> devices = new ArrayList<>();
		BluetoothAdapter adapter = getAdapter();
		if (adapter == null)
			return devices;
		for (String path : BlueZ.findObjects(adapter.path, BlueZ.DEVICE)) {
			BluetoothDevice device = BluetoothDevice.fromPath(path);
			int state = getConnectionState(device, profile);
			for (int s : states)
				if (s == state)
					devices.add(device);
		}
		return devices;
	}

	/* a GATT server needs BlueZ's GattManager1 with exported objects; not implemented */
	public BluetoothGattServer openGattServer(Context context, BluetoothGattServerCallback callback) {
		android.util.Slog.w("BluetoothManager", "openGattServer: GATT server is not implemented");
		return null;
	}

	public BluetoothGattServer openGattServer(Context context, BluetoothGattServerCallback callback, int transport) {
		return openGattServer(context, callback);
	}
}
