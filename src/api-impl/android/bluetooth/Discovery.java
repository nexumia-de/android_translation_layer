package android.bluetooth;

import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Slog;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ATL internal, not Android API: BlueZ discovery shared by classic discovery (startDiscovery) and LE scans.
 *
 * BlueZ runs one discovery session per D-Bus client, so this process has at
 * most one. LE scans set the transport filter to "le"; classic discovery
 * (which Android ends after about 12 s) sets "auto", which finds both.
 */
public final class Discovery {
	private static final String TAG = "BluetoothDiscovery";
	private static final long CLASSIC_DURATION_MS = 12000;

	private static final class LeScan {
		final List<ScanFilter> filters;
		final ScanSettings settings;

		LeScan(List<ScanFilter> filters, ScanSettings settings) {
			this.filters = filters;
			this.settings = settings;
		}
	}

	private static final Map<ScanCallback, LeScan> leScans = new HashMap<>();
	private static boolean classic;
	private static boolean running;
	private static String runningFilter;
	private static Handler handler;
	private static boolean listening;
	/* connection attempts that need discovery off: many controllers abort an LE connection while scanning */
	private static int pauses;
	private static boolean stopRetryRunning;
	private static final Runnable stopClassicLater = new Runnable() {
		public void run() {
			stopClassic(BluetoothAdapter.getDefaultAdapter());
		}
	};

	private static final BlueZ.Listener listener = new BlueZ.Listener() {
		@Override
		public void onPropertiesChanged(String path, String iface, Map<String, Object> changed) {
			if (BlueZ.DEVICE.equals(iface) && (changed.containsKey("RSSI") || changed.containsKey("ManufacturerData") || changed.containsKey("ServiceData")))
				deliverLe(path);
		}

		@Override
		public void onInterfacesAdded(String path, Map<String, Map<String, Object>> ifaces) {
			if (ifaces.containsKey(BlueZ.DEVICE))
				deliverLe(path);
		}
	};

	private static synchronized Handler handler() {
		if (handler == null)
			handler = new Handler(Looper.getMainLooper());
		return handler;
	}

	static synchronized boolean classicActive() {
		return classic;
	}

	static boolean startClassic(BluetoothAdapter adapter) {
		synchronized (Discovery.class) {
			if (adapter == null || !adapter.isEnabled())
				return false;
			classic = true;
			if (!update(adapter)) {
				classic = false;
				return false;
			}
		}
		BlueZ.broadcast(new Intent(BluetoothAdapter.ACTION_DISCOVERY_STARTED));
		/* devices BlueZ saw shortly before count as found, as Android's inquiry would find them again */
		for (String path : BlueZ.findObjects(adapter.path, BlueZ.DEVICE))
			if (BlueZ.getProperty(path, BlueZ.DEVICE, "RSSI") != null)
				sendFound(path);
		handler().removeCallbacks(stopClassicLater);
		handler().postDelayed(stopClassicLater, CLASSIC_DURATION_MS);
		return true;
	}

	static void stopClassic(BluetoothAdapter adapter) {
		synchronized (Discovery.class) {
			if (!classic)
				return;
			classic = false;
			update(adapter);
		}
		handler().removeCallbacks(stopClassicLater);
		BlueZ.broadcast(new Intent(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));
	}

	public static boolean startLe(BluetoothAdapter adapter, List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
		synchronized (Discovery.class) {
			leScans.put(callback, new LeScan(filters, settings));
			if (!update(adapter)) {
				leScans.remove(callback);
				return false;
			}
		}
		/* report devices BlueZ already knows from a running scan */
		for (String path : BlueZ.findObjects(adapter.path, BlueZ.DEVICE))
			if (BlueZ.getProperty(path, BlueZ.DEVICE, "RSSI") != null)
				deliverLe(path, callback);
		return true;
	}

	public static void stopLe(BluetoothAdapter adapter, ScanCallback callback) {
		synchronized (Discovery.class) {
			if (leScans.remove(callback) != null)
				update(adapter);
		}
	}

	/* starts;
			}
		}, changes or stops BlueZ's discovery to match what is requested; called with the class lock held */
	private static boolean update(BluetoothAdapter adapter) {
		if (adapter == null)
			return false;
		if (!listening) {
			BlueZ.addListener(listener);
			listening = true;
		}
		boolean wanted = (classic || !leScans.isEmpty()) && pauses == 0;
		String filter = classic ? "auto" : "le";
		if (wanted && (!running || !filter.equals(runningFilter))) {
			Map<String, Object> filterArgs = new HashMap<>();
			filterArgs.put("Transport", filter);
			filterArgs.put("DuplicateData", Boolean.TRUE);
			try {
				BlueZ.call(adapter.path, BlueZ.ADAPTER, "SetDiscoveryFilter", "a{sv}", filterArgs);
				runningFilter = filter;
			} catch (IOException e) {
				Slog.w(TAG, "SetDiscoveryFilter: " + e.getMessage());
			}
			if (!running) {
				try {
					BlueZ.call(adapter.path, BlueZ.ADAPTER, "StartDiscovery", "");
				} catch (IOException e) {
					/* InProgress: this client's session is starting or already runs; it still has to be stopped later */
					if (e.getMessage() == null || !e.getMessage().contains("InProgress")) {
						Slog.w(TAG, "StartDiscovery: " + e.getMessage());
						return false;
					}
				}
				running = true;
			}
		} else if (!wanted && running) {
			running = false;
			runningFilter = null;
			if (!stopDiscovery(adapter))
				retryStop(adapter);
		}
		return true;
	}

	/* false if BlueZ is still busy starting or changing the discovery (InProgress) and the stop has to be retried */
	private static boolean stopDiscovery(BluetoothAdapter adapter) {
		try {
			BlueZ.call(adapter.path, BlueZ.ADAPTER, "StopDiscovery", "");
		} catch (IOException e) {
			if (e.getMessage() != null && e.getMessage().contains("InProgress"))
				return false;
			Slog.w(TAG, "StopDiscovery: " + e.getMessage());
		}
		return true;
	}

	/* BlueZ refuses StopDiscovery while a StartDiscovery or filter change of this client is pending;
	 * without a retry its discovery keeps running although nobody wants it */
	private static void retryStop(final BluetoothAdapter adapter) {
		if (stopRetryRunning)
			return;
		stopRetryRunning = true;
		new Thread(new Runnable() {
			public void run() {
				for (int i = 0; i < 50; i++) {
					SystemClock.sleep(100);
					synchronized (Discovery.class) {
						if (running) {
							stopRetryRunning = false;
							return;
						}
						if (stopDiscovery(adapter)) {
							stopRetryRunning = false;
							return;
						}
					}
				}
				synchronized (Discovery.class) {
					stopRetryRunning = false;
				}
				Slog.w(TAG, "StopDiscovery: still in progress after 5 s");
			}
		}, "BluetoothDiscovery stop").start();
	}

	/* stops the discovery of this process until resume(); calls nest */
	static void pause(BluetoothAdapter adapter) {
		synchronized (Discovery.class) {
			pauses++;
			update(adapter);
		}
	}

	static void resume(BluetoothAdapter adapter) {
		synchronized (Discovery.class) {
			if (pauses > 0)
				pauses--;
			update(adapter);
		}
	}

	static void sendFound(String path) {
		BluetoothDevice device = BluetoothDevice.fromPath(path);
		if (device == null)
			return;
		Intent intent = new Intent(BluetoothDevice.ACTION_FOUND);
		intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
		Object rssi = BlueZ.getProperty(path, BlueZ.DEVICE, "RSSI");
		intent.putExtra(BluetoothDevice.EXTRA_RSSI, (short)(rssi instanceof Integer ? (Integer)rssi : Short.MIN_VALUE));
		intent.putExtra(BluetoothDevice.EXTRA_NAME, device.getName());
		intent.putExtra(BluetoothDevice.EXTRA_CLASS, device.getBluetoothClass());
		intent.putExtra(BluetoothDevice.EXTRA_UUID, device.getUuids());
		BlueZ.broadcast(intent);
	}

	private static void deliverLe(String path) {
		Map<ScanCallback, LeScan> scans;
		synchronized (Discovery.class) {
			if (leScans.isEmpty())
				return;
			scans = new HashMap<>(leScans);
		}
		for (ScanCallback callback : scans.keySet())
			deliverLe(path, callback);
	}

	private static void deliverLe(String path, ScanCallback callback) {
		LeScan scan;
		synchronized (Discovery.class) {
			scan = leScans.get(callback);
		}
		if (scan == null)
			return;
		Map<String, Object> props = BlueZ.getProperties(path, BlueZ.DEVICE);
		BluetoothDevice device = BluetoothDevice.fromPath(path);
		if (props == null || device == null || props.get("RSSI") == null)
			return;
		/* classic-only devices are not LE scan results */
		if (props.get("Class") != null && props.get("AdvertisingFlags") == null && props.get("ManufacturerData") == null && props.get("ServiceData") == null && !"random".equals(props.get("AddressType")))
			return;
		ScanResult result = ScanResult.fromBlueZ(device, props, SystemClock.elapsedRealtimeNanos());
		if (scan.filters != null && !scan.filters.isEmpty()) {
			boolean match = false;
			for (ScanFilter filter : scan.filters)
				if (filter == null || filter.matches(result)) {
					match = true;
					break;
				}
			if (!match)
				return;
		}
		handler().post(new Runnable() {
			public void run() {
			synchronized (Discovery.class) {
				if (!leScans.containsKey(callback))
					return;
			}
			callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
		}
		});
	}
}
