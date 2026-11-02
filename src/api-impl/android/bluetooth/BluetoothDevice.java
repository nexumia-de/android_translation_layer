package android.bluetooth;

import android.content.Context;
import android.os.Handler;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.util.Slog;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * A remote Bluetooth device, identified by its address. Its state is read
 * from BlueZ's org.bluez.Device1 object of that address.
 */
public final class BluetoothDevice implements Parcelable {
	private static final String TAG = "BluetoothDevice";

	public static final String ACTION_FOUND = "android.bluetooth.device.action.FOUND";
	public static final String ACTION_CLASS_CHANGED = "android.bluetooth.device.action.CLASS_CHANGED";
	public static final String ACTION_ACL_CONNECTED = "android.bluetooth.device.action.ACL_CONNECTED";
	public static final String ACTION_ACL_DISCONNECT_REQUESTED = "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED";
	public static final String ACTION_ACL_DISCONNECTED = "android.bluetooth.device.action.ACL_DISCONNECTED";
	public static final String ACTION_NAME_CHANGED = "android.bluetooth.device.action.NAME_CHANGED";
	public static final String ACTION_ALIAS_CHANGED = "android.bluetooth.action.ALIAS_CHANGED";
	public static final String ACTION_BOND_STATE_CHANGED = "android.bluetooth.device.action.BOND_STATE_CHANGED";
	public static final String ACTION_PAIRING_REQUEST = "android.bluetooth.device.action.PAIRING_REQUEST";
	public static final String ACTION_UUID = "android.bluetooth.device.action.UUID";
	public static final String EXTRA_DEVICE = "android.bluetooth.device.extra.DEVICE";
	public static final String EXTRA_NAME = "android.bluetooth.device.extra.NAME";
	public static final String EXTRA_RSSI = "android.bluetooth.device.extra.RSSI";
	public static final String EXTRA_CLASS = "android.bluetooth.device.extra.CLASS";
	public static final String EXTRA_BOND_STATE = "android.bluetooth.device.extra.BOND_STATE";
	public static final String EXTRA_PREVIOUS_BOND_STATE = "android.bluetooth.device.extra.PREVIOUS_BOND_STATE";
	public static final String EXTRA_PAIRING_VARIANT = "android.bluetooth.device.extra.PAIRING_VARIANT";
	public static final String EXTRA_PAIRING_KEY = "android.bluetooth.device.extra.PAIRING_KEY";
	public static final String EXTRA_UUID = "android.bluetooth.device.extra.UUID";
	public static final String EXTRA_TRANSPORT = "android.bluetooth.device.extra.TRANSPORT";

	public static final int ERROR = Integer.MIN_VALUE;
	public static final int BOND_NONE = 10;
	public static final int BOND_BONDING = 11;
	public static final int BOND_BONDED = 12;
	public static final int DEVICE_TYPE_UNKNOWN = 0;
	public static final int DEVICE_TYPE_CLASSIC = 1;
	public static final int DEVICE_TYPE_LE = 2;
	public static final int DEVICE_TYPE_DUAL = 3;
	public static final int TRANSPORT_AUTO = 0;
	public static final int TRANSPORT_BREDR = 1;
	public static final int TRANSPORT_LE = 2;
	public static final int PHY_LE_1M = 1;
	public static final int PHY_LE_2M = 2;
	public static final int PHY_LE_CODED = 3;
	public static final int PHY_LE_1M_MASK = 1;
	public static final int PHY_LE_2M_MASK = 2;
	public static final int PHY_LE_CODED_MASK = 4;
	public static final int PHY_OPTION_NO_PREFERRED = 0;
	public static final int PHY_OPTION_S2 = 1;
	public static final int PHY_OPTION_S8 = 2;
	public static final int PAIRING_VARIANT_PIN = 0;
	public static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
	public static final int ADDRESS_TYPE_PUBLIC = 0;
	public static final int ADDRESS_TYPE_RANDOM = 1;
	public static final int ADDRESS_TYPE_UNKNOWN = 0xFFFF;

	private final String address;
	/* set while createBond's Pair call runs */
	private static final java.util.Set<String> bonding = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

	BluetoothDevice(String address) {
		this.address = address.toUpperCase();
	}

	/* the device of a BlueZ object path, or null if it is not a device path */
	static BluetoothDevice fromPath(String path) {
		int index = path.lastIndexOf("/dev_");
		if (index < 0 || path.length() != index + 5 + 17)
			return null;
		return new BluetoothDevice(path.substring(index + 5).replace('_', ':'));
	}

	String path() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		return adapter == null ? null : BlueZ.devicePath(adapter.path, address);
	}

	private Object property(String name) {
		String path = path();
		return path == null ? null : BlueZ.getProperty(path, BlueZ.DEVICE, name);
	}

	/* true if BlueZ has an object for this device */
	boolean known() {
		String path = path();
		return path != null && BlueZ.getProperties(path, BlueZ.DEVICE) != null;
	}

	public String getAddress() {
		return address;
	}

	public String getName() {
		Object name = property("Name");
		return name instanceof String ? (String)name : null;
	}

	public String getAlias() {
		Object alias = property("Alias");
		return alias instanceof String ? (String)alias : getName();
	}

	public int setAlias(String alias) {
		try {
			BlueZ.setProperty(path(), BlueZ.DEVICE, "Alias", alias);
			return BluetoothStatusCodes.SUCCESS;
		} catch (IOException e) {
			return BluetoothStatusCodes.ERROR_UNKNOWN;
		}
	}

	public int getAddressType() {
		Object type = property("AddressType");
		if ("random".equals(type))
			return ADDRESS_TYPE_RANDOM;
		return "public".equals(type) ? ADDRESS_TYPE_PUBLIC : ADDRESS_TYPE_UNKNOWN;
	}

	/* BlueZ does not say which transports a device has, so guess from what it reported */
	public int getType() {
		String path = path();
		Map<String, Object> props = path == null ? null : BlueZ.getProperties(path, BlueZ.DEVICE);
		if (props == null)
			return DEVICE_TYPE_UNKNOWN;
		boolean le = "random".equals(props.get("AddressType")) || props.containsKey("AdvertisingFlags") || props.containsKey("Appearance") || BlueZ.findObjects(path, BlueZ.GATT_SERVICE).size() > 0;
		boolean classic = props.containsKey("Class");
		if (le && classic)
			return DEVICE_TYPE_DUAL;
		if (classic)
			return DEVICE_TYPE_CLASSIC;
		if (le)
			return DEVICE_TYPE_LE;
		/* a known public address without anything else: assume LE, which most gadgets are */
		return DEVICE_TYPE_LE;
	}

	public BluetoothClass getBluetoothClass() {
		Object cls = property("Class");
		return cls instanceof Integer ? new BluetoothClass((Integer)cls) : null;
	}

	public int getBondState() {
		if (bonding.contains(address))
			return BOND_BONDING;
		return BlueZ.getBoolean(property("Paired")) || BlueZ.getBoolean(property("Bonded")) ? BOND_BONDED : BOND_NONE;
	}

	public boolean isConnected() {
		return BlueZ.getBoolean(property("Connected"));
	}

	public ParcelUuid[] getUuids() {
		return BlueZ.toParcelUuids(property("UUIDs"));
	}

	/* BlueZ resolves the services itself on connection; report what it has */
	public boolean fetchUuidsWithSdp() {
		ParcelUuid[] uuids = getUuids();
		android.content.Intent intent = new android.content.Intent(ACTION_UUID);
		intent.putExtra(EXTRA_DEVICE, this);
		intent.putExtra(EXTRA_UUID, uuids);
		BlueZ.broadcast(intent);
		return true;
	}

	public boolean fetchUuidsWithSdp(int transport) {
		return fetchUuidsWithSdp();
	}

	/*
	 * Pairs through BlueZ. Without an agent of this process BlueZ uses its
	 * default agent, if the desktop has one, or "just works" pairing.
	 */
	public boolean createBond() {
		return createBond(TRANSPORT_AUTO);
	}

	public boolean createBond(int transport) {
		if (getBondState() != BOND_NONE)
			return false;
		final String path = path();
		if (path == null)
			return false;
		bonding.add(address);
		sendBondState(BOND_BONDING, BOND_NONE);
		new Thread(new Runnable() {
			public void run() {
			int result = BOND_NONE;
			try {
				ensureKnown(TRANSPORT_AUTO);
				BlueZ.call(120000, path, BlueZ.DEVICE, "Pair", "");
				result = BOND_BONDED;
			} catch (IOException e) {
				Slog.w(TAG, "createBond " + address + ": " + e.getMessage());
				if (e.getMessage() != null && e.getMessage().contains("AlreadyExists"))
					result = BOND_BONDED;
			}
			bonding.remove(address);
			/* a successful Pair also changes "Paired", whose broadcast says BONDED; this one covers failures */
			if (result != BOND_BONDED)
				sendBondState(result, BOND_BONDING);
		}
		}, "BluetoothDevice.createBond").start();
		return true;
	}

	private void sendBondState(int state, int previous) {
		android.content.Intent intent = new android.content.Intent(ACTION_BOND_STATE_CHANGED);
		intent.putExtra(EXTRA_DEVICE, this);
		intent.putExtra(EXTRA_BOND_STATE, state);
		intent.putExtra(EXTRA_PREVIOUS_BOND_STATE, previous);
		BlueZ.broadcast(intent);
	}

	public boolean cancelBondProcess() {
		try {
			BlueZ.call(path(), BlueZ.DEVICE, "CancelPairing", "");
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	/* hidden API, which apps call through reflection to forget a device */
	public boolean removeBond() {
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null || !known())
			return false;
		try {
			BlueZ.call(adapter.path, BlueZ.ADAPTER, "RemoveDevice", "o", path());
			return true;
		} catch (IOException e) {
			Slog.w(TAG, "removeBond " + address + ": " + e.getMessage());
			return false;
		}
	}

	public boolean setPairingConfirmation(boolean confirm) {
		return false;
	}

	public boolean setPin(byte[] pin) {
		return false;
	}

	/*
	 * Makes BlueZ create its object for this address, which Connect needs:
	 * first with ConnectDevice (only on bluetoothd with experimental
	 * interfaces), then by scanning for up to 15 s.
	 */
	void ensureKnown(int transport) throws IOException {
		if (known())
			return;
		BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null)
			throw new IOException("no Bluetooth adapter");
		android.bluetooth.le.ScanCallback callback = new android.bluetooth.le.ScanCallback() {};
		boolean scanning = Discovery.startLe(adapter, null, null, callback);
		try {
			for (int i = 0; i < 150 && !known(); i++) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					break;
				}
			}
		} finally {
			if (scanning)
				Discovery.stopLe(adapter, callback);
		}
		if (!known())
			throw new IOException("device " + address + " not found");
	}

	public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback) {
		return connectGatt(context, autoConnect, callback, TRANSPORT_AUTO, PHY_LE_1M_MASK, null);
	}

	public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport) {
		return connectGatt(context, autoConnect, callback, transport, PHY_LE_1M_MASK, null);
	}

	public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport, int phy) {
		return connectGatt(context, autoConnect, callback, transport, phy, null);
	}

	public BluetoothGatt connectGatt(Context context, boolean autoConnect, BluetoothGattCallback callback, int transport, int phy, Handler handler) {
		if (callback == null)
			throw new NullPointerException("callback is null");
		if (BluetoothAdapter.getDefaultAdapter() == null)
			return null;
		BluetoothGatt gatt = new BluetoothGatt(this, callback, autoConnect, transport, handler);
		gatt.connect();
		return gatt;
	}

	public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid) throws IOException {
		return new BluetoothSocket(this, uuid, true);
	}

	public BluetoothSocket createInsecureRfcommSocketToServiceRecord(UUID uuid) throws IOException {
		return new BluetoothSocket(this, uuid, false);
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BluetoothDevice && address.equals(((BluetoothDevice)o).address);
	}

	@Override
	public int hashCode() {
		return address.hashCode();
	}

	@Override
	public String toString() {
		return address;
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeString(address);
	}

	public static final Parcelable.Creator<BluetoothDevice> CREATOR = new Parcelable.Creator<BluetoothDevice>() {
		public BluetoothDevice createFromParcel(Parcel in) {
			return new BluetoothDevice(in.readString());
		}

		public BluetoothDevice[] newArray(int size) {
			return new BluetoothDevice[size];
		}
	};
}
