package android.bluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothGattService {
	public static final int SERVICE_TYPE_PRIMARY = 0;
	public static final int SERVICE_TYPE_SECONDARY = 1;

	protected UUID mUuid;
	protected int mInstanceId;
	protected int mServiceType;
	protected List<BluetoothGattCharacteristic> mCharacteristics = new ArrayList<>();
	protected List<BluetoothGattService> mIncludedServices = new ArrayList<>();
	/* BlueZ object path, for services of a remote device */
	String path;

	public BluetoothGattService(UUID uuid, int serviceType) {
		mUuid = uuid;
		mServiceType = serviceType;
	}

	BluetoothGattService(UUID uuid, int instanceId, int serviceType, String path) {
		this(uuid, serviceType);
		mInstanceId = instanceId;
		this.path = path;
	}

	public boolean addService(BluetoothGattService service) {
		mIncludedServices.add(service);
		return true;
	}

	public boolean addCharacteristic(BluetoothGattCharacteristic characteristic) {
		mCharacteristics.add(characteristic);
		characteristic.setService(this);
		return true;
	}

	public UUID getUuid() {
		return mUuid;
	}

	public int getInstanceId() {
		return mInstanceId;
	}

	public int getType() {
		return mServiceType;
	}

	public List<BluetoothGattService> getIncludedServices() {
		return mIncludedServices;
	}

	public List<BluetoothGattCharacteristic> getCharacteristics() {
		return mCharacteristics;
	}

	public BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
		for (BluetoothGattCharacteristic characteristic : mCharacteristics)
			if (uuid.equals(characteristic.getUuid()))
				return characteristic;
		return null;
	}
}
