package android.bluetooth;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Slog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * A GATT client connection, mapped onto BlueZ's org.bluez.Device1 and its
 * GattService1, GattCharacteristic1 and GattDescriptor1 objects.
 *
 * Operations run one after another on an own thread, as BlueZ's D-Bus calls
 * block; callbacks run on the Handler given to connectGatt, or on a second
 * own thread (Android uses a binder thread there). Notifications arrive as
 * changes of the characteristic's "Value" after StartNotify, which BlueZ
 * does when the app enables notifications; BlueZ itself writes the Client
 * Characteristic Configuration descriptor, so writing it is only recorded.
 */
public final class BluetoothGatt implements BluetoothProfile {
	private static final String TAG = "BluetoothGatt";

	public static final int GATT_SUCCESS = 0;
	public static final int GATT_READ_NOT_PERMITTED = 0x2;
	public static final int GATT_WRITE_NOT_PERMITTED = 0x3;
	public static final int GATT_INSUFFICIENT_AUTHENTICATION = 0x5;
	public static final int GATT_REQUEST_NOT_SUPPORTED = 0x6;
	public static final int GATT_INVALID_OFFSET = 0x7;
	public static final int GATT_INSUFFICIENT_AUTHORIZATION = 0x8;
	public static final int GATT_INVALID_ATTRIBUTE_LENGTH = 0xd;
	public static final int GATT_INSUFFICIENT_ENCRYPTION = 0xf;
	public static final int GATT_CONNECTION_CONGESTED = 0x8f;
	public static final int GATT_CONNECTION_TIMEOUT = 0x93;
	public static final int GATT_FAILURE = 0x101;
	/* what Android reports for most failed connections */
	static final int GATT_ERROR = 133;
	static final int GATT_CONN_TERMINATE_PEER_USER = 0x13;

	public static final int CONNECTION_PRIORITY_BALANCED = 0;
	public static final int CONNECTION_PRIORITY_HIGH = 1;
	public static final int CONNECTION_PRIORITY_LOW_POWER = 2;
	public static final int CONNECTION_PRIORITY_DCK = 3;

	private static final int DEFAULT_MTU = 23;
	private static final long SERVICES_TIMEOUT_MS = 30000;
	private static final long AUTO_CONNECT_RETRY_MS = 5000;

	private final BluetoothDevice device;
	private final BluetoothGattCallback callback;
	private final boolean autoConnect;
	private final int transport;
	private final ExecutorService ops;
	private final ExecutorService callbackThread;
	private final Executor callbacks;
	private final BlueZ.Listener listener;

	private volatile int state = STATE_DISCONNECTED;
	private volatile boolean closed;
	private volatile boolean disconnectRequested;
	/* this object made BlueZ connect, so close() takes the link down again */
	private volatile boolean connectedByUs;

	private final List<BluetoothGattService> services = Collections.synchronizedList(new ArrayList<>());
	private final Map<String, BluetoothGattCharacteristic> characteristicsByPath = new ConcurrentHashMap<>();
	private final Set<String> notifying = ConcurrentHashMap.newKeySet();
	private final Map<String, Integer> notifyErrors = new ConcurrentHashMap<>();

	/* the echo of a ReadValue as a "Value" change must not look like a notification */
	private volatile String readingPath;
	private volatile byte[] lastReadValue;
	private volatile long lastReadEnd;

	BluetoothGatt(BluetoothDevice device, BluetoothGattCallback callback, boolean autoConnect, int transport, final Handler handler) {
		this.device = device;
		this.callback = callback;
		this.autoConnect = autoConnect;
		this.transport = transport;
		String name = "BluetoothGatt " + device.getAddress();
		this.ops = Executors.newSingleThreadExecutor(namedThreads(name));
		if (handler != null) {
			this.callbackThread = null;
			this.callbacks = new Executor() {
				public void execute(Runnable r) {
					handler.post(r);
				}
			};
		} else {
			this.callbackThread = Executors.newSingleThreadExecutor(namedThreads(name + " callbacks"));
			this.callbacks = callbackThread;
		}
		this.listener = new BlueZ.Listener() {
			@Override
			public void onPropertiesChanged(String path, String iface, Map<String, Object> changed) {
				onBlueZChange(path, iface, changed);
			}
		};
		BlueZ.addListener(listener);
	}

	private static final java.util.Comparator<BluetoothGattService> BY_SERVICE_HANDLE = new java.util.Comparator<BluetoothGattService>() {
		public int compare(BluetoothGattService a, BluetoothGattService b) {
			return a.getInstanceId() - b.getInstanceId();
		}
	};
	private static final java.util.Comparator<BluetoothGattCharacteristic> BY_CHARACTERISTIC_HANDLE = new java.util.Comparator<BluetoothGattCharacteristic>() {
		public int compare(BluetoothGattCharacteristic a, BluetoothGattCharacteristic b) {
			return a.getInstanceId() - b.getInstanceId();
		}
	};

	private static java.util.concurrent.ThreadFactory namedThreads(final String name) {
		return new java.util.concurrent.ThreadFactory() {
			public Thread newThread(Runnable r) {
				return new Thread(r, name);
			}
		};
	}

	private String devicePath() {
		return device.path();
	}

	private void submit(Runnable op) {
		try {
			ops.execute(new Runnable() {
			public void run() {
				try {
					op.run();
				} catch (RuntimeException e) {
					Slog.e(TAG, "operation failed", e);
				}
			}
		});
		} catch (RejectedExecutionException e) {
			Slog.w(TAG, "operation after close() ignored");
		}
	}

	private void dispatch(Runnable r) {
		if (closed)
			return;
		try {
			callbacks.execute(new Runnable() {
			public void run() {
				if (!closed)
					r.run();
			}
		});
		} catch (RejectedExecutionException e) {
		}
	}

	/* maps a BlueZ error to an ATT status as Android reports it */
	static int statusOf(IOException e) {
		String message = e.getMessage() == null ? "" : e.getMessage();
		int index = message.indexOf("ATT error: 0x");
		if (index >= 0) {
			try {
				return Integer.parseInt(message.substring(index + 13, Math.min(message.length(), index + 15)), 16);
			} catch (NumberFormatException ignored) {
			}
		}
		if (message.contains("Read not permitted"))
			return GATT_READ_NOT_PERMITTED;
		if (message.contains("Write not permitted"))
			return GATT_WRITE_NOT_PERMITTED;
		if (message.contains("NotAuthorized"))
			return GATT_INSUFFICIENT_AUTHORIZATION;
		if (message.contains("NotSupported"))
			return GATT_REQUEST_NOT_SUPPORTED;
		if (message.contains("InvalidOffset"))
			return GATT_INVALID_OFFSET;
		if (message.contains("InvalidValueLength"))
			return GATT_INVALID_ATTRIBUTE_LENGTH;
		return GATT_FAILURE;
	}

	private static boolean isAlreadyConnected(IOException e) {
		return e.getMessage() != null && (e.getMessage().contains("AlreadyConnected") || e.getMessage().contains("already connected"));
	}

	public BluetoothDevice getDevice() {
		return device;
	}

	public boolean connect() {
		if (closed)
			return false;
		disconnectRequested = false;
		state = STATE_CONNECTING;
		submit(new Runnable() {
			public void run() {
				doConnect();
			}
		});
		return true;
	}

	private void doConnect() {
		while (!closed && !disconnectRequested) {
			try {
				device.ensureKnown(transport);
				if (!device.isConnected()) {
					/* a running discovery makes BlueZ abort the LE connection (le-connection-abort-by-local),
					 * so it pauses until the services are resolved, as Android's stack does around connects */
					BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
					Discovery.pause(adapter);
					/* the device may ask for pairing after connecting (a lost bond) */
					PairingAgent.acquire();
					boolean ok = false;
					try {
						BlueZ.call(60000, devicePath(), BlueZ.DEVICE, "Connect", "");
						ok = true;
					} finally {
						resumeDiscoveryWhenResolved(adapter, ok);
						PairingAgent.release();
					}
					connectedByUs = true;
				}
				connected();
				return;
			} catch (IOException e) {
				if (isAlreadyConnected(e)) {
					connected();
					return;
				}
				Slog.w(TAG, "connect " + device.getAddress() + ": " + e.getMessage());
				if (!autoConnect || closed || disconnectRequested) {
					state = STATE_DISCONNECTED;
					dispatch(new Runnable() {
			public void run() {
				callback.onConnectionStateChange(BluetoothGatt.this, GATT_ERROR, STATE_DISCONNECTED);
			}
		});
					return;
				}
			}
			SystemClock.sleep(AUTO_CONNECT_RETRY_MS);
		}
	}

	private void resumeDiscoveryWhenResolved(final BluetoothAdapter adapter, boolean connected) {
		if (!connected) {
			Discovery.resume(adapter);
			return;
		}
		final String path = devicePath();
		new Thread(new Runnable() {
			public void run() {
			for (int i = 0; i < 100 && !closed && !BlueZ.getBoolean(BlueZ.getProperty(path, BlueZ.DEVICE, "ServicesResolved")); i++)
				SystemClock.sleep(100);
			Discovery.resume(adapter);
		}
		}, "BluetoothGatt resume discovery").start();
	}

	private void connected() {
		if (closed || disconnectRequested)
			return;
		state = STATE_CONNECTED;
		dispatch(new Runnable() {
			public void run() {
				callback.onConnectionStateChange(BluetoothGatt.this, GATT_SUCCESS, STATE_CONNECTED);
			}
		});
	}

	public void disconnect() {
		if (closed)
			return;
		disconnectRequested = true;
		final boolean wasDisconnected = state == STATE_DISCONNECTED;
		/* not on the operation thread, which may wait in Connect; Disconnect cancels a pending connection */
		new Thread(new Runnable() {
			public void run() {
			stopAllNotifications();
			try {
				BlueZ.call(devicePath(), BlueZ.DEVICE, "Disconnect", "");
			} catch (IOException e) {
				Slog.w(TAG, "disconnect " + device.getAddress() + ": " + e.getMessage());
			}
			connectedByUs = false;
			state = STATE_DISCONNECTED;
			services.clear();
			if (!wasDisconnected)
				dispatch(new Runnable() {
			public void run() {
				callback.onConnectionStateChange(BluetoothGatt.this, GATT_SUCCESS, STATE_DISCONNECTED);
			}
		});
		}
		}, "BluetoothGatt.disconnect").start();
	}

	public void close() {
		if (closed)
			return;
		closed = true;
		BlueZ.removeListener(listener);
		final boolean takeDown = connectedByUs && !disconnectRequested && state != STATE_DISCONNECTED;
		final String path = devicePath();
		ops.shutdownNow();
		if (callbackThread != null)
			callbackThread.shutdown();
		new Thread(new Runnable() {
			public void run() {
			stopAllNotifications();
			if (takeDown) {
				try {
					BlueZ.call(path, BlueZ.DEVICE, "Disconnect", "");
				} catch (IOException e) {
				}
			}
		}
		}, "BluetoothGatt.close").start();
		state = STATE_DISCONNECTED;
	}

	private void stopAllNotifications() {
		for (String path : new ArrayList<>(notifying)) {
			try {
				BlueZ.call(path, BlueZ.GATT_CHARACTERISTIC, "StopNotify", "");
			} catch (IOException e) {
			}
			notifying.remove(path);
		}
	}

	/* hidden API some apps call through reflection to drop cached services */
	public boolean refresh() {
		services.clear();
		characteristicsByPath.clear();
		return true;
	}

	public boolean discoverServices() {
		if (state != STATE_CONNECTED)
			return false;
		submit(new Runnable() {
			public void run() {
			long deadline = SystemClock.uptimeMillis() + SERVICES_TIMEOUT_MS;
			while (!BlueZ.getBoolean(BlueZ.getProperty(devicePath(), BlueZ.DEVICE, "ServicesResolved"))) {
				if (closed || state != STATE_CONNECTED)
					return;
				if (SystemClock.uptimeMillis() > deadline) {
					Slog.w(TAG, "discoverServices " + device.getAddress() + ": BlueZ did not resolve the services");
					dispatch(new Runnable() {
			public void run() {
				callback.onServicesDiscovered(BluetoothGatt.this, GATT_FAILURE);
			}
		});
					return;
				}
				SystemClock.sleep(100);
			}
			loadServices();
			dispatch(new Runnable() {
			public void run() {
				callback.onServicesDiscovered(BluetoothGatt.this, GATT_SUCCESS);
			}
		});
		}
		});
		return true;
	}

	private static int handleOf(Map<String, Object> props, String path) {
		Object handle = props.get("Handle");
		if (handle instanceof Integer && (Integer)handle != 0)
			return (Integer)handle;
		/* the path ends in service001a, char001b or desc001d */
		try {
			return Integer.parseInt(path.substring(path.length() - 4), 16);
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private void loadServices() {
		List<BluetoothGattService> result = new ArrayList<>();
		Map<String, BluetoothGattCharacteristic> byPath = new HashMap<>();
		for (String servicePath : BlueZ.findObjects(devicePath(), BlueZ.GATT_SERVICE)) {
			Map<String, Object> sp = BlueZ.getProperties(servicePath, BlueZ.GATT_SERVICE);
			if (sp == null || !(sp.get("UUID") instanceof String))
				continue;
			int type = BlueZ.getBoolean(sp.get("Primary")) || sp.get("Primary") == null ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY;
			BluetoothGattService service = new BluetoothGattService(UUID.fromString((String)sp.get("UUID")), handleOf(sp, servicePath), type, servicePath);
			for (String charPath : BlueZ.findObjects(servicePath, BlueZ.GATT_CHARACTERISTIC)) {
				Map<String, Object> cp = BlueZ.getProperties(charPath, BlueZ.GATT_CHARACTERISTIC);
				if (cp == null || !(cp.get("UUID") instanceof String))
					continue;
				int properties = BluetoothGattCharacteristic.propertiesFromFlags((String[])cp.get("Flags"));
				BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(UUID.fromString((String)cp.get("UUID")), handleOf(cp, charPath), properties, charPath);
				boolean hasCccd = false;
				for (String descPath : BlueZ.findObjects(charPath, BlueZ.GATT_DESCRIPTOR)) {
					Map<String, Object> dp = BlueZ.getProperties(descPath, BlueZ.GATT_DESCRIPTOR);
					if (dp == null || !(dp.get("UUID") instanceof String))
						continue;
					UUID uuid = UUID.fromString((String)dp.get("UUID"));
					hasCccd |= BluetoothGattDescriptor.CCCD.equals(uuid);
					characteristic.addDescriptor(new BluetoothGattDescriptor(uuid, handleOf(dp, descPath), descPath));
				}
				/* apps look up the CCCD to enable notifications, even where BlueZ does not export it */
				if (!hasCccd && (properties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0)
					characteristic.addDescriptor(new BluetoothGattDescriptor(BluetoothGattDescriptor.CCCD, characteristic.getInstanceId() + 1, null));
				service.addCharacteristic(characteristic);
				byPath.put(charPath, characteristic);
			}
			Collections.sort(service.getCharacteristics(), BY_CHARACTERISTIC_HANDLE);
			result.add(service);
		}
		Collections.sort(result, BY_SERVICE_HANDLE);
		synchronized (services) {
			services.clear();
			services.addAll(result);
		}
		characteristicsByPath.clear();
		characteristicsByPath.putAll(byPath);
	}

	public List<BluetoothGattService> getServices() {
		synchronized (services) {
			return new ArrayList<>(services);
		}
	}

	public BluetoothGattService getService(UUID uuid) {
		synchronized (services) {
			for (BluetoothGattService service : services)
				if (service.getUuid().equals(uuid))
					return service;
		}
		return null;
	}

	public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (state != STATE_CONNECTED || characteristic == null || characteristic.path == null || (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
			return false;
		submit(new Runnable() {
			public void run() {
			byte[] value = new byte[0];
			int status = GATT_SUCCESS;
			readingPath = characteristic.path;
			try {
				Object result = BlueZ.call(characteristic.path, BlueZ.GATT_CHARACTERISTIC, "ReadValue", "a{sv}", new HashMap<String, Object>());
				if (result instanceof byte[])
					value = (byte[])result;
			} catch (IOException e) {
				status = statusOf(e);
				Slog.w(TAG, "readCharacteristic " + characteristic.getUuid() + ": " + e.getMessage());
			}
			lastReadValue = value;
			lastReadEnd = SystemClock.uptimeMillis();
			readingPath = null;
			final byte[] v = value;
			final int s = status;
			dispatch(new Runnable() {
			public void run() {
				if (s == GATT_SUCCESS)
					characteristic.setValue(v);
				callback.onCharacteristicRead(BluetoothGatt.this, characteristic, v, s);
			}
		});
		}
		});
		return true;
	}

	@Deprecated
	public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (characteristic == null || characteristic.getValue() == null)
			return false;
		return writeCharacteristic(characteristic, characteristic.getValue(), characteristic.getWriteType()) == BluetoothStatusCodes.SUCCESS;
	}

	public int writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value, int writeType) {
		if (state != STATE_CONNECTED)
			return BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND;
		if (characteristic == null || characteristic.path == null || value == null)
			return BluetoothStatusCodes.ERROR_UNKNOWN;
		if ((characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE | BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE)) == 0)
			return BluetoothStatusCodes.ERROR_GATT_WRITE_NOT_ALLOWED;
		final byte[] data = value.clone();
		submit(new Runnable() {
			public void run() {
			int status = GATT_SUCCESS;
			Map<String, Object> options = new HashMap<>();
			options.put("type", writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE ? "command" : "request");
			try {
				BlueZ.call(characteristic.path, BlueZ.GATT_CHARACTERISTIC, "WriteValue", "aya{sv}", data, options);
			} catch (IOException e) {
				status = statusOf(e);
				Slog.w(TAG, "writeCharacteristic " + characteristic.getUuid() + ": " + e.getMessage());
			}
			final int s = status;
			dispatch(new Runnable() {
			public void run() {
				callback.onCharacteristicWrite(BluetoothGatt.this, characteristic, s);
			}
		});
		}
		});
		return BluetoothStatusCodes.SUCCESS;
	}

	public boolean readDescriptor(BluetoothGattDescriptor descriptor) {
		if (state != STATE_CONNECTED || descriptor == null)
			return false;
		submit(new Runnable() {
			public void run() {
			byte[] value = new byte[0];
			int status = GATT_SUCCESS;
			if (descriptor.path == null) {
				BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
				boolean on = characteristic != null && notifying.contains(characteristic.path);
				value = on ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE.clone() : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE.clone();
			} else {
				try {
					Object result = BlueZ.call(descriptor.path, BlueZ.GATT_DESCRIPTOR, "ReadValue", "a{sv}", new HashMap<String, Object>());
					if (result instanceof byte[])
						value = (byte[])result;
				} catch (IOException e) {
					status = statusOf(e);
					Slog.w(TAG, "readDescriptor " + descriptor.getUuid() + ": " + e.getMessage());
				}
			}
			final byte[] v = value;
			final int s = status;
			dispatch(new Runnable() {
			public void run() {
				if (s == GATT_SUCCESS)
					descriptor.setValue(v);
				callback.onDescriptorRead(BluetoothGatt.this, descriptor, s, v);
			}
		});
		}
		});
		return true;
	}

	@Deprecated
	public boolean writeDescriptor(BluetoothGattDescriptor descriptor) {
		if (descriptor == null || descriptor.getValue() == null)
			return false;
		return writeDescriptor(descriptor, descriptor.getValue()) == BluetoothStatusCodes.SUCCESS;
	}

	public int writeDescriptor(BluetoothGattDescriptor descriptor, byte[] value) {
		if (state != STATE_CONNECTED)
			return BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND;
		if (descriptor == null || value == null)
			return BluetoothStatusCodes.ERROR_UNKNOWN;
		final byte[] data = value.clone();
		submit(new Runnable() {
			public void run() {
			int status = GATT_SUCCESS;
			BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
			if (BluetoothGattDescriptor.CCCD.equals(descriptor.getUuid()) && characteristic != null && characteristic.path != null) {
				/* BlueZ refuses writes to the CCCD and writes it itself in StartNotify */
				boolean enable = data.length > 0 && (data[0] & 0x03) != 0;
				status = enable ? startNotify(characteristic) : stopNotify(characteristic);
			} else if (descriptor.path == null) {
				status = GATT_WRITE_NOT_PERMITTED;
			} else {
				try {
					BlueZ.call(descriptor.path, BlueZ.GATT_DESCRIPTOR, "WriteValue", "aya{sv}", data, new HashMap<String, Object>());
				} catch (IOException e) {
					status = statusOf(e);
					Slog.w(TAG, "writeDescriptor " + descriptor.getUuid() + ": " + e.getMessage());
				}
			}
			final int s = status;
			dispatch(new Runnable() {
			public void run() {
				callback.onDescriptorWrite(BluetoothGatt.this, descriptor, s);
			}
		});
		}
		});
		return BluetoothStatusCodes.SUCCESS;
	}

	private int startNotify(BluetoothGattCharacteristic characteristic) {
		if (notifying.contains(characteristic.path))
			return GATT_SUCCESS;
		Integer error = notifyErrors.remove(characteristic.path);
		if (error != null)
			return error;
		try {
			BlueZ.call(characteristic.path, BlueZ.GATT_CHARACTERISTIC, "StartNotify", "");
			notifying.add(characteristic.path);
			return GATT_SUCCESS;
		} catch (IOException e) {
			if (e.getMessage() != null && e.getMessage().contains("InProgress")) {
				notifying.add(characteristic.path);
				return GATT_SUCCESS;
			}
			Slog.w(TAG, "StartNotify " + characteristic.getUuid() + ": " + e.getMessage());
			return statusOf(e);
		}
	}

	private int stopNotify(BluetoothGattCharacteristic characteristic) {
		if (!notifying.remove(characteristic.path))
			return GATT_SUCCESS;
		try {
			BlueZ.call(characteristic.path, BlueZ.GATT_CHARACTERISTIC, "StopNotify", "");
		} catch (IOException e) {
			Slog.w(TAG, "StopNotify " + characteristic.getUuid() + ": " + e.getMessage());
		}
		return GATT_SUCCESS;
	}

	/* on Android only a local switch; here it starts BlueZ's notification session, in order with the other operations */
	public boolean setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
		if (characteristic == null || characteristic.path == null || state != STATE_CONNECTED)
			return false;
		if ((characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) == 0)
			return true;
		submit(new Runnable() {
			public void run() {
			if (enable) {
				notifyErrors.remove(characteristic.path);
				int status = startNotify(characteristic);
				if (status != GATT_SUCCESS)
					notifyErrors.put(characteristic.path, status);
			} else {
				stopNotify(characteristic);
			}
		}
		});
		return true;
	}

	/* BlueZ negotiates the ATT MTU itself when connecting; report what it got */
	public boolean requestMtu(int mtu) {
		if (state != STATE_CONNECTED)
			return false;
		submit(new Runnable() {
			public void run() {
			int result = DEFAULT_MTU;
			for (String path : characteristicsByPath.keySet()) {
				Object value = BlueZ.getProperty(path, BlueZ.GATT_CHARACTERISTIC, "MTU");
				if (value instanceof Integer) {
					result = (Integer)value;
					break;
				}
			}
			final int m = result;
			dispatch(new Runnable() {
			public void run() {
				callback.onMtuChanged(BluetoothGatt.this, m, GATT_SUCCESS);
			}
		});
		}
		});
		return true;
	}

	public boolean requestConnectionPriority(int connectionPriority) {
		return state == STATE_CONNECTED;
	}

	public boolean readRemoteRssi() {
		return false;
	}

	public void setPreferredPhy(int txPhy, int rxPhy, int phyOptions) {
		dispatch(new Runnable() {
			public void run() {
				callback.onPhyUpdate(BluetoothGatt.this, BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M, GATT_SUCCESS);
			}
		});
	}

	public void readPhy() {
		dispatch(new Runnable() {
			public void run() {
				callback.onPhyRead(BluetoothGatt.this, BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M, GATT_SUCCESS);
			}
		});
	}

	public boolean beginReliableWrite() {
		return false;
	}

	public boolean executeReliableWrite() {
		return false;
	}

	public void abortReliableWrite() {}

	@Deprecated
	public void abortReliableWrite(BluetoothDevice mDevice) {}

	private void onBlueZChange(String path, String iface, Map<String, Object> changed) {
		if (closed)
			return;
		if (BlueZ.DEVICE.equals(iface) && path.equals(devicePath())) {
			if (changed.containsKey("Connected") && !BlueZ.getBoolean(changed.get("Connected")) && state == STATE_CONNECTED && !disconnectRequested) {
				state = STATE_DISCONNECTED;
				notifying.clear();
				services.clear();
				connectedByUs = false;
				dispatch(new Runnable() {
			public void run() {
				callback.onConnectionStateChange(BluetoothGatt.this, GATT_CONN_TERMINATE_PEER_USER, STATE_DISCONNECTED);
			}
		});
				if (autoConnect)
					connect();
			}
			return;
		}
		if (!BlueZ.GATT_CHARACTERISTIC.equals(iface) || !(changed.get("Value") instanceof byte[]))
			return;
		if (!notifying.contains(path))
			return;
		BluetoothGattCharacteristic characteristic = characteristicsByPath.get(path);
		if (characteristic == null)
			return;
		byte[] value = (byte[])changed.get("Value");
		if (path.equals(readingPath))
			return;
		byte[] readValue = lastReadValue;
		if (readValue != null && SystemClock.uptimeMillis() - lastReadEnd < 500 && Arrays.equals(readValue, value)) {
			lastReadValue = null;
			return;
		}
		dispatch(new Runnable() {
			public void run() {
			characteristic.setValue(value);
			callback.onCharacteristicChanged(BluetoothGatt.this, characteristic, value);
		}
		});
	}

	public int getConnectionState(BluetoothDevice device) {
		throw new UnsupportedOperationException("Use BluetoothManager#getConnectionState instead.");
	}

	public List<BluetoothDevice> getConnectedDevices() {
		throw new UnsupportedOperationException("Use BluetoothManager#getConnectedDevices instead.");
	}

	public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
		throw new UnsupportedOperationException("Use BluetoothManager#getDevicesMatchingConnectionStates instead.");
	}
}
