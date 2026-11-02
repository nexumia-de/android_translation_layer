package android.bluetooth.le;

import android.os.ParcelUuid;
import android.util.SparseArray;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * Advertising data. BlueZ hands out the parsed fields, not the raw
 * advertisement, so getBytes() returns the fields encoded again as AD
 * structures.
 */
public final class ScanRecord {
	private static final UUID BASE_UUID = UUID.fromString("00000000-0000-1000-8000-00805f9b34fb");

	private final int advertiseFlags;
	private final List<ParcelUuid> serviceUuids;
	private final SparseArray<byte[]> manufacturerSpecificData;
	private final Map<ParcelUuid, byte[]> serviceData;
	private final int txPowerLevel;
	private final String deviceName;
	private final byte[] bytes;

	private ScanRecord(List<ParcelUuid> serviceUuids, SparseArray<byte[]> manufacturerData, Map<ParcelUuid, byte[]> serviceData,
	                   int advertiseFlags, int txPowerLevel, String deviceName) {
		this.serviceUuids = serviceUuids;
		this.manufacturerSpecificData = manufacturerData;
		this.serviceData = serviceData;
		this.advertiseFlags = advertiseFlags;
		this.txPowerLevel = txPowerLevel;
		this.deviceName = deviceName;
		this.bytes = encode();
	}

	@SuppressWarnings("unchecked")
	static ScanRecord fromBlueZ(Map<String, Object> props) {
		List<ParcelUuid> uuids = null;
		if (props.get("UUIDs") instanceof String[]) {
			uuids = new ArrayList<>();
			for (String uuid : (String[])props.get("UUIDs"))
				uuids.add(ParcelUuid.fromString(uuid));
		}
		SparseArray<byte[]> manufacturerData = new SparseArray<>();
		if (props.get("ManufacturerData") instanceof Map)
			for (Map.Entry<Object, Object> e : ((Map<Object, Object>)props.get("ManufacturerData")).entrySet())
				if (e.getKey() instanceof Integer && e.getValue() instanceof byte[])
					manufacturerData.put((Integer)e.getKey(), (byte[])e.getValue());
		Map<ParcelUuid, byte[]> serviceData = new HashMap<>();
		if (props.get("ServiceData") instanceof Map)
			for (Map.Entry<Object, Object> e : ((Map<Object, Object>)props.get("ServiceData")).entrySet())
				if (e.getKey() instanceof String && e.getValue() instanceof byte[])
					serviceData.put(ParcelUuid.fromString((String)e.getKey()), (byte[])e.getValue());
		int flags = -1;
		if (props.get("AdvertisingFlags") instanceof byte[] && ((byte[])props.get("AdvertisingFlags")).length > 0)
			flags = ((byte[])props.get("AdvertisingFlags"))[0] & 0xFF;
		Object txPower = props.get("TxPower");
		Object name = props.get("Name");
		return new ScanRecord(uuids, manufacturerData, serviceData, flags, txPower instanceof Integer ? (Integer)txPower : Integer.MIN_VALUE,
		                      name instanceof String ? (String)name : null);
	}

	private static boolean isShort(UUID uuid) {
		return (uuid.getMostSignificantBits() & 0xFFFF0000FFFFFFFFL) == BASE_UUID.getMostSignificantBits() && uuid.getLeastSignificantBits() == BASE_UUID.getLeastSignificantBits() && (uuid.getMostSignificantBits() >>> 32) <= 0xFFFF;
	}

	private static void writeStructure(ByteArrayOutputStream out, int type, byte[] data) {
		if (data.length + 1 > 255)
			return;
		out.write(data.length + 1);
		out.write(type);
		out.write(data, 0, data.length);
	}

	private static byte[] uuidBytes(UUID uuid) {
		if (isShort(uuid)) {
			int value = (int)(uuid.getMostSignificantBits() >>> 32);
			return new byte[] {(byte)value, (byte)(value >> 8)};
		}
		byte[] b = new byte[16];
		long lsb = uuid.getLeastSignificantBits(), msb = uuid.getMostSignificantBits();
		for (int i = 0; i < 8; i++) {
			b[i] = (byte)(lsb >>> (8 * i));
			b[8 + i] = (byte)(msb >>> (8 * i));
		}
		return b;
	}

	private byte[] encode() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (advertiseFlags >= 0)
			writeStructure(out, 0x01, new byte[] {(byte)advertiseFlags});
		if (serviceUuids != null) {
			ByteArrayOutputStream short16 = new ByteArrayOutputStream(), long128 = new ByteArrayOutputStream();
			for (ParcelUuid uuid : serviceUuids) {
				byte[] b = uuidBytes(uuid.getUuid());
				(b.length == 2 ? short16 : long128).write(b, 0, b.length);
			}
			if (short16.size() > 0)
				writeStructure(out, 0x03, short16.toByteArray());
			if (long128.size() > 0)
				writeStructure(out, 0x07, long128.toByteArray());
		}
		if (deviceName != null)
			writeStructure(out, 0x09, deviceName.getBytes());
		if (txPowerLevel != Integer.MIN_VALUE)
			writeStructure(out, 0x0A, new byte[] {(byte)txPowerLevel});
		for (Map.Entry<ParcelUuid, byte[]> e : serviceData.entrySet()) {
			byte[] uuid = uuidBytes(e.getKey().getUuid());
			byte[] data = new byte[uuid.length + e.getValue().length];
			System.arraycopy(uuid, 0, data, 0, uuid.length);
			System.arraycopy(e.getValue(), 0, data, uuid.length, e.getValue().length);
			writeStructure(out, uuid.length == 2 ? 0x16 : 0x21, data);
		}
		for (int i = 0; i < manufacturerSpecificData.size(); i++) {
			int id = manufacturerSpecificData.keyAt(i);
			byte[] value = manufacturerSpecificData.valueAt(i);
			byte[] data = new byte[2 + value.length];
			data[0] = (byte)id;
			data[1] = (byte)(id >> 8);
			System.arraycopy(value, 0, data, 2, value.length);
			writeStructure(out, 0xFF, data);
		}
		return out.toByteArray();
	}

	public int getAdvertiseFlags() {
		return advertiseFlags;
	}

	public List<ParcelUuid> getServiceUuids() {
		return serviceUuids;
	}

	public List<ParcelUuid> getServiceSolicitationUuids() {
		return new ArrayList<>();
	}

	public SparseArray<byte[]> getManufacturerSpecificData() {
		return manufacturerSpecificData;
	}

	public byte[] getManufacturerSpecificData(int manufacturerId) {
		return manufacturerSpecificData.get(manufacturerId);
	}

	public Map<ParcelUuid, byte[]> getServiceData() {
		return serviceData;
	}

	public byte[] getServiceData(ParcelUuid serviceDataUuid) {
		return serviceDataUuid == null ? null : serviceData.get(serviceDataUuid);
	}

	public int getTxPowerLevel() {
		return txPowerLevel;
	}

	public String getDeviceName() {
		return deviceName;
	}

	public byte[] getBytes() {
		return bytes;
	}

	@Override
	public String toString() {
		return "ScanRecord{name=" + deviceName + ", uuids=" + serviceUuids + "}";
	}
}
