package android.bluetooth.le;

import android.bluetooth.BluetoothDevice;
import android.os.Parcelable;
import java.util.Map;

public final class ScanResult implements Parcelable {
	public static final int DATA_COMPLETE = 0;
	public static final int PHY_UNUSED = 0;
	public static final int SID_NOT_PRESENT = 255;
	public static final int TX_POWER_NOT_PRESENT = 127;
	public static final int PERIODIC_INTERVAL_NOT_PRESENT = 0;

	private final BluetoothDevice device;
	private final ScanRecord scanRecord;
	private final int rssi;
	private final long timestampNanos;
	private final int txPower;

	public ScanResult(BluetoothDevice device, ScanRecord scanRecord, int rssi, long timestampNanos) {
		this(device, scanRecord, rssi, timestampNanos, TX_POWER_NOT_PRESENT);
	}

	private ScanResult(BluetoothDevice device, ScanRecord scanRecord, int rssi, long timestampNanos, int txPower) {
		this.device = device;
		this.scanRecord = scanRecord;
		this.rssi = rssi;
		this.timestampNanos = timestampNanos;
		this.txPower = txPower;
	}

	/** ATL internal: a result from the properties of a BlueZ org.bluez.Device1 */
	public static ScanResult fromBlueZ(BluetoothDevice device, Map<String, Object> props, long timestampNanos) {
		Object rssi = props.get("RSSI");
		Object txPower = props.get("TxPower");
		return new ScanResult(device, ScanRecord.fromBlueZ(props), rssi instanceof Integer ? (Integer)rssi : -127, timestampNanos,
		                      txPower instanceof Integer ? (Integer)txPower : TX_POWER_NOT_PRESENT);
	}

	public BluetoothDevice getDevice() {
		return device;
	}

	public ScanRecord getScanRecord() {
		return scanRecord;
	}

	public int getRssi() {
		return rssi;
	}

	public long getTimestampNanos() {
		return timestampNanos;
	}

	public boolean isLegacy() {
		return true;
	}

	public boolean isConnectable() {
		return true;
	}

	public int getDataStatus() {
		return DATA_COMPLETE;
	}

	public int getPrimaryPhy() {
		return 1;
	}

	public int getSecondaryPhy() {
		return PHY_UNUSED;
	}

	public int getAdvertisingSid() {
		return SID_NOT_PRESENT;
	}

	public int getTxPower() {
		return txPower;
	}

	public int getPeriodicAdvertisingInterval() {
		return PERIODIC_INTERVAL_NOT_PRESENT;
	}

	@Override
	public String toString() {
		return "ScanResult{device=" + device + ", rssi=" + rssi + ", scanRecord=" + scanRecord + "}";
	}
}
