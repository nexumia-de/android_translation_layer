package android.bluetooth;

public final class BluetoothStatusCodes {
	public static final int SUCCESS = 0;
	public static final int ERROR_BLUETOOTH_NOT_ENABLED = 2;
	public static final int ERROR_BLUETOOTH_NOT_ALLOWED = 3;
	public static final int ERROR_DEVICE_NOT_BONDED = 4;
	public static final int ERROR_MISSING_BLUETOOTH_CONNECT_PERMISSION = 6;
	public static final int ERROR_PROFILE_SERVICE_NOT_BOUND = 9;
	public static final int FEATURE_SUPPORTED = 10;
	public static final int FEATURE_NOT_SUPPORTED = 11;
	public static final int ERROR_GATT_WRITE_NOT_ALLOWED = 200;
	public static final int ERROR_GATT_WRITE_REQUEST_BUSY = 201;
	public static final int ERROR_UNKNOWN = Integer.MAX_VALUE;
}
