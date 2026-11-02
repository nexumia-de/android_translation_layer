package android.bluetooth;

import java.util.UUID;

public class BluetoothGattDescriptor {
	public static final byte[] ENABLE_NOTIFICATION_VALUE = {0x01, 0x00};
	public static final byte[] ENABLE_INDICATION_VALUE = {0x02, 0x00};
	public static final byte[] DISABLE_NOTIFICATION_VALUE = {0x00, 0x00};

	public static final int PERMISSION_READ = 0x01;
	public static final int PERMISSION_READ_ENCRYPTED = 0x02;
	public static final int PERMISSION_READ_ENCRYPTED_MITM = 0x04;
	public static final int PERMISSION_WRITE = 0x10;
	public static final int PERMISSION_WRITE_ENCRYPTED = 0x20;
	public static final int PERMISSION_WRITE_ENCRYPTED_MITM = 0x40;
	public static final int PERMISSION_WRITE_SIGNED = 0x80;
	public static final int PERMISSION_WRITE_SIGNED_MITM = 0x100;

	/* the Client Characteristic Configuration descriptor, which BlueZ writes itself in StartNotify */
	static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

	protected UUID mUuid;
	protected int mInstance;
	protected int mPermissions;
	protected BluetoothGattCharacteristic mCharacteristic;
	protected byte[] mValue;
	/* BlueZ object path; null for a CCCD BlueZ does not export */
	String path;

	public BluetoothGattDescriptor(UUID uuid, int permissions) {
		mUuid = uuid;
		mPermissions = permissions;
	}

	BluetoothGattDescriptor(UUID uuid, int instance, String path) {
		this(uuid, 0);
		mInstance = instance;
		this.path = path;
	}

	public BluetoothGattCharacteristic getCharacteristic() {
		return mCharacteristic;
	}

	void setCharacteristic(BluetoothGattCharacteristic characteristic) {
		mCharacteristic = characteristic;
	}

	public UUID getUuid() {
		return mUuid;
	}

	public int getInstanceId() {
		return mInstance;
	}

	public int getPermissions() {
		return mPermissions;
	}

	@Deprecated
	public byte[] getValue() {
		return mValue;
	}

	@Deprecated
	public boolean setValue(byte[] value) {
		mValue = value;
		return true;
	}
}
