package android.bluetooth;

import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Slog;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The local Bluetooth adapter, backed by BlueZ's first org.bluez.Adapter1.
 */
public final class BluetoothAdapter {
	private static final String TAG = "BluetoothAdapter";

	public static final String ACTION_STATE_CHANGED = "android.bluetooth.adapter.action.STATE_CHANGED";
	public static final String ACTION_DISCOVERY_STARTED = "android.bluetooth.adapter.action.DISCOVERY_STARTED";
	public static final String ACTION_DISCOVERY_FINISHED = "android.bluetooth.adapter.action.DISCOVERY_FINISHED";
	public static final String ACTION_REQUEST_ENABLE = "android.bluetooth.adapter.action.REQUEST_ENABLE";
	public static final String ACTION_REQUEST_DISCOVERABLE = "android.bluetooth.adapter.action.REQUEST_DISCOVERABLE";
	public static final String ACTION_CONNECTION_STATE_CHANGED = "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED";
	public static final String ACTION_LOCAL_NAME_CHANGED = "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED";
	public static final String ACTION_SCAN_MODE_CHANGED = "android.bluetooth.adapter.action.SCAN_MODE_CHANGED";
	public static final String EXTRA_STATE = "android.bluetooth.adapter.extra.STATE";
	public static final String EXTRA_PREVIOUS_STATE = "android.bluetooth.adapter.extra.PREVIOUS_STATE";
	public static final String EXTRA_CONNECTION_STATE = "android.bluetooth.adapter.extra.CONNECTION_STATE";
	public static final String EXTRA_PREVIOUS_CONNECTION_STATE = "android.bluetooth.adapter.extra.PREVIOUS_CONNECTION_STATE";
	public static final String EXTRA_LOCAL_NAME = "android.bluetooth.adapter.extra.LOCAL_NAME";
	public static final String EXTRA_SCAN_MODE = "android.bluetooth.adapter.extra.SCAN_MODE";
	public static final String EXTRA_DISCOVERABLE_DURATION = "android.bluetooth.adapter.extra.DISCOVERABLE_DURATION";

	public static final int ERROR = Integer.MIN_VALUE;
	public static final int STATE_OFF = 10;
	public static final int STATE_TURNING_ON = 11;
	public static final int STATE_ON = 12;
	public static final int STATE_TURNING_OFF = 13;
	public static final int STATE_DISCONNECTED = 0;
	public static final int STATE_CONNECTING = 1;
	public static final int STATE_CONNECTED = 2;
	public static final int STATE_DISCONNECTING = 3;
	public static final int SCAN_MODE_NONE = 20;
	public static final int SCAN_MODE_CONNECTABLE = 21;
	public static final int SCAN_MODE_CONNECTABLE_DISCOVERABLE = 23;

	public interface LeScanCallback {
		void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord);
	}

	private static BluetoothAdapter defaultAdapter;

	final String path;
	private BluetoothLeScanner leScanner;
	private final Map<LeScanCallback, ScanCallback> leScanCallbacks = new HashMap<>();

	private BluetoothAdapter(String path) {
		this.path = path;
	}

	/* null when there is no system bus, no BlueZ or no adapter, like a device without Bluetooth */
	public static synchronized BluetoothAdapter getDefaultAdapter() {
		if (defaultAdapter == null) {
			if (!BlueZ.init())
				return null;
			String path = BlueZ.adapterPath();
			if (path == null)
				return null;
			defaultAdapter = new BluetoothAdapter(path);
		}
		return defaultAdapter;
	}

	public static boolean checkBluetoothAddress(String address) {
		return address != null && address.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}");
	}

	public boolean isEnabled() {
		return BlueZ.getBoolean(BlueZ.getProperty(path, BlueZ.ADAPTER, "Powered"));
	}

	public int getState() {
		return isEnabled() ? STATE_ON : STATE_OFF;
	}

	public boolean enable() {
		try {
			BlueZ.setProperty(path, BlueZ.ADAPTER, "Powered", Boolean.TRUE);
			return true;
		} catch (IOException e) {
			Slog.w(TAG, "enable: " + e.getMessage());
			return false;
		}
	}

	public boolean disable() {
		try {
			BlueZ.setProperty(path, BlueZ.ADAPTER, "Powered", Boolean.FALSE);
			return true;
		} catch (IOException e) {
			Slog.w(TAG, "disable: " + e.getMessage());
			return false;
		}
	}

	public String getAddress() {
		Object address = BlueZ.getProperty(path, BlueZ.ADAPTER, "Address");
		return address instanceof String ? (String)address : "02:00:00:00:00:00";
	}

	public String getName() {
		Object alias = BlueZ.getProperty(path, BlueZ.ADAPTER, "Alias");
		return alias instanceof String ? (String)alias : (String)BlueZ.getProperty(path, BlueZ.ADAPTER, "Name");
	}

	public boolean setName(String name) {
		try {
			BlueZ.setProperty(path, BlueZ.ADAPTER, "Alias", name);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public int getScanMode() {
		if (!isEnabled())
			return SCAN_MODE_NONE;
		return BlueZ.getBoolean(BlueZ.getProperty(path, BlueZ.ADAPTER, "Discoverable")) ? SCAN_MODE_CONNECTABLE_DISCOVERABLE : SCAN_MODE_CONNECTABLE;
	}

	public Set<BluetoothDevice> getBondedDevices() {
		Set<BluetoothDevice> devices = new HashSet<>();
		for (String devicePath : BlueZ.findObjects(path, BlueZ.DEVICE)) {
			BluetoothDevice device = BluetoothDevice.fromPath(devicePath);
			if (device != null && device.getBondState() == BluetoothDevice.BOND_BONDED)
				devices.add(device);
		}
		return devices;
	}

	public BluetoothDevice getRemoteDevice(String address) {
		if (!checkBluetoothAddress(address))
			throw new IllegalArgumentException(address + " is not a valid Bluetooth address");
		return new BluetoothDevice(address);
	}

	public BluetoothDevice getRemoteDevice(byte[] address) {
		if (address == null || address.length != 6)
			throw new IllegalArgumentException("Bluetooth address must have 6 bytes");
		return getRemoteDevice(String.format("%02X:%02X:%02X:%02X:%02X:%02X", address[0], address[1], address[2], address[3], address[4], address[5]));
	}

	public BluetoothDevice getRemoteLeDevice(String address, int addressType) {
		return getRemoteDevice(address);
	}

	public boolean startDiscovery() {
		return Discovery.startClassic(this);
	}

	public boolean cancelDiscovery() {
		Discovery.stopClassic(this);
		return true;
	}

	public boolean isDiscovering() {
		return BlueZ.getBoolean(BlueZ.getProperty(path, BlueZ.ADAPTER, "Discovering"));
	}

	public synchronized BluetoothLeScanner getBluetoothLeScanner() {
		if (!isEnabled())
			return null;
		if (leScanner == null)
			leScanner = new BluetoothLeScanner(this);
		return leScanner;
	}

	public boolean isLe2MPhySupported() {
		return false;
	}

	public boolean isLeCodedPhySupported() {
		return false;
	}

	public boolean isLeExtendedAdvertisingSupported() {
		return false;
	}

	public boolean isMultipleAdvertisementSupported() {
		return false;
	}

	public boolean isOffloadedFilteringSupported() {
		return false;
	}

	public boolean isOffloadedScanBatchingSupported() {
		return false;
	}

	public int getLeMaximumAdvertisingDataLength() {
		return 31;
	}

	@Deprecated
	public boolean startLeScan(LeScanCallback callback) {
		return startLeScan(null, callback);
	}

	@Deprecated
	public boolean startLeScan(UUID[] serviceUuids, LeScanCallback callback) {
		BluetoothLeScanner scanner = getBluetoothLeScanner();
		if (scanner == null || callback == null)
			return false;
		ScanCallback scanCallback = new ScanCallback() {
			@Override
			public void onScanResult(int callbackType, ScanResult result) {
				if (serviceUuids != null) {
					java.util.List<android.os.ParcelUuid> uuids = result.getScanRecord() == null ? null : result.getScanRecord().getServiceUuids();
					if (uuids == null)
						return;
					for (UUID uuid : serviceUuids)
						if (!uuids.contains(new android.os.ParcelUuid(uuid)))
							return;
				}
				callback.onLeScan(result.getDevice(), result.getRssi(), result.getScanRecord() == null ? null : result.getScanRecord().getBytes());
			}
		};
		synchronized (leScanCallbacks) {
			leScanCallbacks.put(callback, scanCallback);
		}
		scanner.startScan(scanCallback);
		return true;
	}

	@Deprecated
	public void stopLeScan(LeScanCallback callback) {
		ScanCallback scanCallback;
		synchronized (leScanCallbacks) {
			scanCallback = leScanCallbacks.remove(callback);
		}
		BluetoothLeScanner scanner = getBluetoothLeScanner();
		if (scanner != null && scanCallback != null)
			scanner.stopScan(scanCallback);
	}

	/* A2DP, headset and the other profiles belong to the desktop's audio stack */
	public boolean getProfileProxy(Context context, BluetoothProfile.ServiceListener listener, int profile) {
		Slog.i(TAG, "getProfileProxy(" + profile + "): profiles are not available");
		return false;
	}

	public void closeProfileProxy(int profile, BluetoothProfile proxy) {}

	public int getProfileConnectionState(int profile) {
		return BluetoothProfile.STATE_DISCONNECTED;
	}

	public BluetoothServerSocket listenUsingRfcommWithServiceRecord(String name, UUID uuid) throws IOException {
		throw new IOException("RFCOMM server sockets are not implemented");
	}

	public BluetoothServerSocket listenUsingInsecureRfcommWithServiceRecord(String name, UUID uuid) throws IOException {
		throw new IOException("RFCOMM server sockets are not implemented");
	}

	@Override
	public String toString() {
		return getAddress();
	}
}
