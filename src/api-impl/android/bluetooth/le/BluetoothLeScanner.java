package android.bluetooth.le;

import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import java.util.List;

/* LE scanning through BlueZ discovery with the "le" transport filter; filters are applied here */
public final class BluetoothLeScanner {
	private final BluetoothAdapter adapter;

	public BluetoothLeScanner(BluetoothAdapter adapter) {
		this.adapter = adapter;
	}

	public void startScan(ScanCallback callback) {
		startScan(null, null, callback);
	}

	public void startScan(List<ScanFilter> filters, ScanSettings settings, ScanCallback callback) {
		if (callback == null)
			throw new IllegalArgumentException("callback is null");
		if (!android.bluetooth.Discovery.startLe(adapter, filters, settings, callback))
			callback.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
	}

	public int startScan(List<ScanFilter> filters, ScanSettings settings, PendingIntent callbackIntent) {
		return ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED;
	}

	public void stopScan(ScanCallback callback) {
		android.bluetooth.Discovery.stopLe(adapter, callback);
	}

	public void stopScan(PendingIntent callbackIntent) {}

	public void flushPendingScanResults(ScanCallback callback) {}
}
