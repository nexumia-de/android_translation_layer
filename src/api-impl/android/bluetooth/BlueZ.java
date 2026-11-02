package android.bluetooth;

import android.atl.ATLLoadedApp;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Slog;
import android.atl.annotation.Export;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * BlueZ on the D-Bus system bus, the backend of android.bluetooth.
 *
 * Keeps a copy of BlueZ's object tree (GetManagedObjects, then kept current
 * from its signals), forwards changes to listeners and sends the system
 * broadcasts Android sends for adapter and device changes.
 */
final class BlueZ {
	private static final String TAG = "BlueZ";

	static final String SERVICE = "org.bluez";
	static final String ADAPTER = "org.bluez.Adapter1";
	static final String DEVICE = "org.bluez.Device1";
	static final String GATT_SERVICE = "org.bluez.GattService1";
	static final String GATT_CHARACTERISTIC = "org.bluez.GattCharacteristic1";
	static final String GATT_DESCRIPTOR = "org.bluez.GattDescriptor1";
	static final String PROPERTIES = "org.freedesktop.DBus.Properties";
	static final String OBJECT_MANAGER = "org.freedesktop.DBus.ObjectManager";

	static final int DEFAULT_TIMEOUT = 25000;

	interface Listener {
		/* called on the signal thread; must not block */
		void onPropertiesChanged(String path, String iface, Map<String, Object> changed);

		default void onInterfacesAdded(String path, Map<String, Map<String, Object>> ifaces) {}

		default void onInterfacesRemoved(String path, String[] ifaces) {}
	}

	private static native boolean nativeInit();

	static native Object nativeCall(String destination, String path, String iface, String method, String signature,
	                                Object[] args, int timeoutMs) throws IOException;

	private static final Map<String, Map<String, Map<String, Object>>> objects = new HashMap<>();
	private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
	private static Boolean available;
	private static Handler mainHandler;

	/* true if the system bus and BlueZ are reachable; loads the object tree on the first call */
	static synchronized boolean init() {
		if (available != null)
			return available;
		available = false;
		try {
			if (!nativeInit())
				return false;
			loadObjects();
			available = true;
			Slog.i(TAG, "connected to BlueZ, adapter " + adapterPath());
		} catch (IOException e) {
			Slog.w(TAG, "BlueZ is not available: " + e.getMessage());
		} catch (UnsatisfiedLinkError e) {
			Slog.w(TAG, "BlueZ bridge missing: " + e);
		}
		return available;
	}

	@SuppressWarnings("unchecked")
	private static void loadObjects() throws IOException {
		Object tree = nativeCall(SERVICE, "/", OBJECT_MANAGER, "GetManagedObjects", "", new Object[0], DEFAULT_TIMEOUT);
		synchronized (objects) {
			objects.clear();
			if (tree instanceof Map)
				objects.putAll((Map<String, Map<String, Map<String, Object>>>)tree);
		}
	}

	static Object call(String path, String iface, String method, String signature, Object... args) throws IOException {
		return call(DEFAULT_TIMEOUT, path, iface, method, signature, args);
	}

	static Object call(int timeoutMs, String path, String iface, String method, String signature, Object... args) throws IOException {
		if (!init())
			throw new IOException("BlueZ is not available");
		return nativeCall(SERVICE, path, iface, method, signature, args, timeoutMs);
	}

	static void setProperty(String path, String iface, String name, Object value) throws IOException {
		call(path, PROPERTIES, "Set", "ssv", iface, name, value);
	}

	/* a property from the cached tree, or null */
	static Object getProperty(String path, String iface, String name) {
		if (!init())
			return null;
		synchronized (objects) {
			Map<String, Map<String, Object>> object = objects.get(path);
			Map<String, Object> props = object == null ? null : object.get(iface);
			return props == null ? null : props.get(name);
		}
	}

	/* a copy of the properties of one interface of an object, or null */
	static Map<String, Object> getProperties(String path, String iface) {
		if (!init())
			return null;
		synchronized (objects) {
			Map<String, Map<String, Object>> object = objects.get(path);
			Map<String, Object> props = object == null ? null : object.get(iface);
			return props == null ? null : new HashMap<>(props);
		}
	}

	/* paths of all objects with the interface, below prefix (null for all) */
	static List<String> findObjects(String prefix, String iface) {
		List<String> result = new ArrayList<>();
		if (!init())
			return result;
		synchronized (objects) {
			for (Map.Entry<String, Map<String, Map<String, Object>>> e : objects.entrySet()) {
				if ((prefix == null || e.getKey().startsWith(prefix + "/")) && e.getValue().containsKey(iface))
					result.add(e.getKey());
			}
		}
		result.sort(null);
		return result;
	}

	static String adapterPath() {
		List<String> adapters = findObjects(null, ADAPTER);
		return adapters.isEmpty() ? null : adapters.get(0);
	}

	static String devicePath(String adapter, String address) {
		return adapter + "/dev_" + address.toUpperCase().replace(':', '_');
	}

	static void addListener(Listener listener) {
		listeners.add(listener);
	}

	static void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	static boolean getBoolean(Object value) {
		return value instanceof Boolean && (Boolean)value;
	}

	static ParcelUuid[] toParcelUuids(Object uuids) {
		if (!(uuids instanceof String[]))
			return null;
		String[] strings = (String[])uuids;
		ParcelUuid[] result = new ParcelUuid[strings.length];
		for (int i = 0; i < strings.length; i++)
			result[i] = ParcelUuid.fromString(strings[i]);
		return result;
	}

	/* delivers a system broadcast on the main thread, as Android does */
	static void broadcast(Intent intent) {
		synchronized (BlueZ.class) {
			if (mainHandler == null)
				mainHandler = new Handler(Looper.getMainLooper());
		}
		mainHandler.post(new Runnable() {
			public void run() {
			ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
			if (app != null)
				app.getApplication().sendBroadcast(intent);
		}
		});
	}

	@SuppressWarnings("unchecked")
	@Export
	private static void onSignal(String path, String iface, String member, Object args) {
		Object[] a = args instanceof Object[] ? (Object[])args : new Object[0];
		try {
			if (PROPERTIES.equals(iface) && "PropertiesChanged".equals(member) && a.length >= 2) {
				String changedIface = (String)a[0];
				Map<String, Object> changed = (Map<String, Object>)a[1];
				Map<String, Object> old;
				synchronized (objects) {
					Map<String, Map<String, Object>> object = objects.get(path);
					if (object == null)
						objects.put(path, object = new HashMap<>());
					Map<String, Object> props = object.get(changedIface);
					if (props == null)
						object.put(changedIface, props = new HashMap<>());
					old = new HashMap<>(props);
					props.putAll(changed);
					if (a.length >= 3 && a[2] instanceof String[])
						for (String name : (String[])a[2])
							props.remove(name);
				}
				systemBroadcasts(path, changedIface, old, changed);
				for (Listener l : listeners)
					l.onPropertiesChanged(path, changedIface, changed);
			} else if (OBJECT_MANAGER.equals(iface) && "InterfacesAdded".equals(member) && a.length >= 2) {
				String objectPath = (String)a[0];
				Map<String, Map<String, Object>> ifaces = (Map<String, Map<String, Object>>)a[1];
				synchronized (objects) {
					Map<String, Map<String, Object>> object = objects.get(objectPath);
					if (object == null)
						objects.put(objectPath, object = new HashMap<>());
					object.putAll(ifaces);
				}
				if (ifaces.containsKey(DEVICE) && Discovery.classicActive())
					Discovery.sendFound(objectPath);
				for (Listener l : listeners)
					l.onInterfacesAdded(objectPath, ifaces);
			} else if (OBJECT_MANAGER.equals(iface) && "InterfacesRemoved".equals(member) && a.length >= 2) {
				String objectPath = (String)a[0];
				String[] ifaces = (String[])a[1];
				synchronized (objects) {
					Map<String, Map<String, Object>> object = objects.get(objectPath);
					if (object != null) {
						for (String name : ifaces)
							object.remove(name);
						if (object.isEmpty())
							objects.remove(objectPath);
					}
				}
				for (Listener l : listeners)
					l.onInterfacesRemoved(objectPath, ifaces);
			}
		} catch (RuntimeException e) {
			Slog.w(TAG, "signal " + member + " on " + path + " failed", e);
		}
	}

	private static void systemBroadcasts(String path, String iface, Map<String, Object> old, Map<String, Object> changed) {
		if (ADAPTER.equals(iface)) {
			if (changed.containsKey("Powered")) {
				boolean was = getBoolean(old.get("Powered"));
				boolean is = getBoolean(changed.get("Powered"));
				if (was != is) {
					Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
					intent.putExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, was ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF);
					intent.putExtra(BluetoothAdapter.EXTRA_STATE, is ? BluetoothAdapter.STATE_ON : BluetoothAdapter.STATE_OFF);
					broadcast(intent);
				}
			}
			return;
		}
		if (!DEVICE.equals(iface))
			return;
		BluetoothDevice device = BluetoothDevice.fromPath(path);
		if (device == null)
			return;
		if ((changed.containsKey("Paired") || changed.containsKey("Bonded")) && !BluetoothDevice.isBonding(device.getAddress())) {
			boolean was = getBoolean(old.get("Paired")) || getBoolean(old.get("Bonded"));
			boolean is = device.getBondState() == BluetoothDevice.BOND_BONDED;
			if (was != is) {
				Intent intent = new Intent(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
				intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
				intent.putExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, was ? BluetoothDevice.BOND_BONDED : BluetoothDevice.BOND_NONE);
				intent.putExtra(BluetoothDevice.EXTRA_BOND_STATE, is ? BluetoothDevice.BOND_BONDED : BluetoothDevice.BOND_NONE);
				broadcast(intent);
			}
		}
		if (changed.containsKey("Connected") && getBoolean(old.get("Connected")) != getBoolean(changed.get("Connected"))) {
			Intent intent = new Intent(getBoolean(changed.get("Connected")) ? BluetoothDevice.ACTION_ACL_CONNECTED : BluetoothDevice.ACTION_ACL_DISCONNECTED);
			intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
			broadcast(intent);
		}
		if (changed.containsKey("Alias") || changed.containsKey("Name")) {
			Intent intent = new Intent(BluetoothDevice.ACTION_NAME_CHANGED);
			intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
			intent.putExtra(BluetoothDevice.EXTRA_NAME, device.getName());
			broadcast(intent);
		}
		if (changed.containsKey("UUIDs")) {
			Intent intent = new Intent(BluetoothDevice.ACTION_UUID);
			intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
			intent.putExtra(BluetoothDevice.EXTRA_UUID, toParcelUuids(changed.get("UUIDs")));
			broadcast(intent);
		}
		if (Discovery.classicActive() && (changed.containsKey("RSSI") || changed.containsKey("Name")))
			Discovery.sendFound(path);
	}
}
