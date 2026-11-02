package android.bluetooth.le;

import android.bluetooth.BluetoothAdapter;
import android.os.ParcelUuid;
import android.os.Parcelable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScanFilter implements Parcelable {
	private final String deviceName;
	private final String deviceAddress;
	private final ParcelUuid serviceUuid;
	private final ParcelUuid serviceUuidMask;
	private final ParcelUuid serviceDataUuid;
	private final byte[] serviceData;
	private final byte[] serviceDataMask;
	private final int manufacturerId;
	private final byte[] manufacturerData;
	private final byte[] manufacturerDataMask;

	private ScanFilter(Builder b) {
		deviceName = b.deviceName;
		deviceAddress = b.deviceAddress;
		serviceUuid = b.serviceUuid;
		serviceUuidMask = b.serviceUuidMask;
		serviceDataUuid = b.serviceDataUuid;
		serviceData = b.serviceData;
		serviceDataMask = b.serviceDataMask;
		manufacturerId = b.manufacturerId;
		manufacturerData = b.manufacturerData;
		manufacturerDataMask = b.manufacturerDataMask;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public String getDeviceAddress() {
		return deviceAddress;
	}

	public ParcelUuid getServiceUuid() {
		return serviceUuid;
	}

	public ParcelUuid getServiceUuidMask() {
		return serviceUuidMask;
	}

	public ParcelUuid getServiceDataUuid() {
		return serviceDataUuid;
	}

	public byte[] getServiceData() {
		return serviceData;
	}

	public byte[] getServiceDataMask() {
		return serviceDataMask;
	}

	public int getManufacturerId() {
		return manufacturerId;
	}

	public byte[] getManufacturerData() {
		return manufacturerData;
	}

	public byte[] getManufacturerDataMask() {
		return manufacturerDataMask;
	}

	private static boolean matchesData(byte[] wanted, byte[] mask, byte[] data) {
		if (wanted == null)
			return true;
		if (data == null || data.length < wanted.length)
			return false;
		for (int i = 0; i < wanted.length; i++) {
			int m = mask == null ? 0xFF : mask[i];
			if ((wanted[i] & m) != (data[i] & m))
				return false;
		}
		return true;
	}

	private static boolean matchesUuid(UUID wanted, UUID mask, UUID uuid) {
		if (mask == null)
			return wanted.equals(uuid);
		return ((wanted.getMostSignificantBits() ^ uuid.getMostSignificantBits()) & mask.getMostSignificantBits()) == 0 && ((wanted.getLeastSignificantBits() ^ uuid.getLeastSignificantBits()) & mask.getLeastSignificantBits()) == 0;
	}

	public boolean matches(ScanResult result) {
		if (result == null)
			return false;
		if (deviceAddress != null && !deviceAddress.equalsIgnoreCase(result.getDevice().getAddress()))
			return false;
		ScanRecord record = result.getScanRecord();
		if (record == null)
			return deviceName == null && serviceUuid == null && manufacturerData == null && serviceData == null;
		if (deviceName != null && !deviceName.equals(record.getDeviceName()))
			return false;
		if (serviceUuid != null) {
			List<ParcelUuid> uuids = record.getServiceUuids();
			boolean found = false;
			if (uuids != null)
				for (ParcelUuid uuid : uuids)
					if (matchesUuid(serviceUuid.getUuid(), serviceUuidMask == null ? null : serviceUuidMask.getUuid(), uuid.getUuid())) {
						found = true;
						break;
					}
			if (!found)
				return false;
		}
		if (serviceDataUuid != null && !matchesData(serviceData, serviceDataMask, record.getServiceData(serviceDataUuid)))
			return false;
		if (manufacturerId >= 0 && !matchesData(manufacturerData == null ? new byte[0] : manufacturerData, manufacturerDataMask, record.getManufacturerSpecificData(manufacturerId)))
			return false;
		return true;
	}

	public static final class Builder {
		private String deviceName;
		private String deviceAddress;
		private ParcelUuid serviceUuid;
		private ParcelUuid serviceUuidMask;
		private ParcelUuid serviceDataUuid;
		private byte[] serviceData;
		private byte[] serviceDataMask;
		private int manufacturerId = -1;
		private byte[] manufacturerData;
		private byte[] manufacturerDataMask;

		public Builder setDeviceName(String deviceName) {
			this.deviceName = deviceName;
			return this;
		}

		public Builder setDeviceAddress(String deviceAddress) {
			if (deviceAddress != null && !BluetoothAdapter.checkBluetoothAddress(deviceAddress))
				throw new IllegalArgumentException("invalid device address " + deviceAddress);
			this.deviceAddress = deviceAddress;
			return this;
		}

		public Builder setServiceUuid(ParcelUuid serviceUuid) {
			this.serviceUuid = serviceUuid;
			this.serviceUuidMask = null;
			return this;
		}

		public Builder setServiceUuid(ParcelUuid serviceUuid, ParcelUuid uuidMask) {
			this.serviceUuid = serviceUuid;
			this.serviceUuidMask = uuidMask;
			return this;
		}

		public Builder setServiceData(ParcelUuid serviceDataUuid, byte[] serviceData) {
			return setServiceData(serviceDataUuid, serviceData, null);
		}

		public Builder setServiceData(ParcelUuid serviceDataUuid, byte[] serviceData, byte[] serviceDataMask) {
			this.serviceDataUuid = serviceDataUuid;
			this.serviceData = serviceData;
			this.serviceDataMask = serviceDataMask;
			return this;
		}

		public Builder setManufacturerData(int manufacturerId, byte[] manufacturerData) {
			return setManufacturerData(manufacturerId, manufacturerData, null);
		}

		public Builder setManufacturerData(int manufacturerId, byte[] manufacturerData, byte[] manufacturerDataMask) {
			this.manufacturerId = manufacturerId;
			this.manufacturerData = manufacturerData;
			this.manufacturerDataMask = manufacturerDataMask;
			return this;
		}

		public ScanFilter build() {
			return new ScanFilter(this);
		}
	}
}
