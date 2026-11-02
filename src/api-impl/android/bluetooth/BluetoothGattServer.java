package android.bluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/* never handed out: BluetoothManager.openGattServer returns null until a GATT server on BlueZ's GattManager1 exists */
public final class BluetoothGattServer implements BluetoothProfile {
	private BluetoothGattServer() {}

	public void close() {}

	public boolean connect(BluetoothDevice device, boolean autoConnect) {
		return false;
	}

	public void cancelConnection(BluetoothDevice device) {}

	public boolean sendResponse(BluetoothDevice device, int requestId, int status, int offset, byte[] value) {
		return false;
	}

	public boolean notifyCharacteristicChanged(BluetoothDevice device, BluetoothGattCharacteristic characteristic, boolean confirm) {
		return false;
	}

	public int notifyCharacteristicChanged(BluetoothDevice device, BluetoothGattCharacteristic characteristic, boolean confirm, byte[] value) {
		return BluetoothStatusCodes.ERROR_UNKNOWN;
	}

	public boolean addService(BluetoothGattService service) {
		return false;
	}

	public boolean removeService(BluetoothGattService service) {
		return false;
	}

	public void clearServices() {}

	public List<BluetoothGattService> getServices() {
		return new ArrayList<>();
	}

	public BluetoothGattService getService(UUID uuid) {
		return null;
	}

	public List<BluetoothDevice> getConnectedDevices() {
		throw new UnsupportedOperationException("Use BluetoothManager#getConnectedDevices instead.");
	}

	public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
		throw new UnsupportedOperationException("Use BluetoothManager#getDevicesMatchingConnectionStates instead.");
	}

	public int getConnectionState(BluetoothDevice device) {
		throw new UnsupportedOperationException("Use BluetoothManager#getConnectionState instead.");
	}
}
