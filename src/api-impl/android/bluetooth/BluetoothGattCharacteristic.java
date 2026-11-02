package android.bluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothGattCharacteristic {
	public static final int PROPERTY_BROADCAST = 0x01;
	public static final int PROPERTY_READ = 0x02;
	public static final int PROPERTY_WRITE_NO_RESPONSE = 0x04;
	public static final int PROPERTY_WRITE = 0x08;
	public static final int PROPERTY_NOTIFY = 0x10;
	public static final int PROPERTY_INDICATE = 0x20;
	public static final int PROPERTY_SIGNED_WRITE = 0x40;
	public static final int PROPERTY_EXTENDED_PROPS = 0x80;

	public static final int PERMISSION_READ = 0x01;
	public static final int PERMISSION_READ_ENCRYPTED = 0x02;
	public static final int PERMISSION_READ_ENCRYPTED_MITM = 0x04;
	public static final int PERMISSION_WRITE = 0x10;
	public static final int PERMISSION_WRITE_ENCRYPTED = 0x20;
	public static final int PERMISSION_WRITE_ENCRYPTED_MITM = 0x40;
	public static final int PERMISSION_WRITE_SIGNED = 0x80;
	public static final int PERMISSION_WRITE_SIGNED_MITM = 0x100;

	public static final int WRITE_TYPE_DEFAULT = 0x02;
	public static final int WRITE_TYPE_NO_RESPONSE = 0x01;
	public static final int WRITE_TYPE_SIGNED = 0x04;

	public static final int FORMAT_UINT8 = 0x11;
	public static final int FORMAT_UINT16 = 0x12;
	public static final int FORMAT_UINT32 = 0x14;
	public static final int FORMAT_SINT8 = 0x21;
	public static final int FORMAT_SINT16 = 0x22;
	public static final int FORMAT_SINT32 = 0x24;
	public static final int FORMAT_SFLOAT = 0x32;
	public static final int FORMAT_FLOAT = 0x34;

	protected UUID mUuid;
	protected int mInstance;
	protected int mProperties;
	protected int mPermissions;
	protected int mWriteType;
	protected BluetoothGattService mService;
	protected byte[] mValue;
	protected List<BluetoothGattDescriptor> mDescriptors = new ArrayList<>();
	/* BlueZ object path, for characteristics of a remote device */
	String path;

	public BluetoothGattCharacteristic(UUID uuid, int properties, int permissions) {
		mUuid = uuid;
		mProperties = properties;
		mPermissions = permissions;
		mWriteType = (properties & PROPERTY_WRITE_NO_RESPONSE) != 0 ? WRITE_TYPE_NO_RESPONSE : WRITE_TYPE_DEFAULT;
	}

	BluetoothGattCharacteristic(UUID uuid, int instance, int properties, String path) {
		this(uuid, properties, 0);
		mInstance = instance;
		this.path = path;
	}

	/* BlueZ's "Flags" of org.bluez.GattCharacteristic1 as Android properties */
	static int propertiesFromFlags(String[] flags) {
		int properties = 0;
		if (flags == null)
			return 0;
		for (String flag : flags) {
			switch (flag) {
				case "broadcast": properties |= PROPERTY_BROADCAST; break;
				case "read": properties |= PROPERTY_READ; break;
				case "write-without-response": properties |= PROPERTY_WRITE_NO_RESPONSE; break;
				case "write": properties |= PROPERTY_WRITE; break;
				case "notify": properties |= PROPERTY_NOTIFY; break;
				case "indicate": properties |= PROPERTY_INDICATE; break;
				case "authenticated-signed-writes": properties |= PROPERTY_SIGNED_WRITE; break;
				case "extended-properties": properties |= PROPERTY_EXTENDED_PROPS; break;
			}
		}
		return properties;
	}

	public boolean addDescriptor(BluetoothGattDescriptor descriptor) {
		mDescriptors.add(descriptor);
		descriptor.setCharacteristic(this);
		return true;
	}

	public BluetoothGattService getService() {
		return mService;
	}

	void setService(BluetoothGattService service) {
		mService = service;
	}

	public UUID getUuid() {
		return mUuid;
	}

	public int getInstanceId() {
		return mInstance;
	}

	public int getProperties() {
		return mProperties;
	}

	public int getPermissions() {
		return mPermissions;
	}

	public int getWriteType() {
		return mWriteType;
	}

	public void setWriteType(int writeType) {
		mWriteType = writeType;
	}

	public void setKeySize(int keySize) {}

	public List<BluetoothGattDescriptor> getDescriptors() {
		return mDescriptors;
	}

	public BluetoothGattDescriptor getDescriptor(UUID uuid) {
		for (BluetoothGattDescriptor descriptor : mDescriptors)
			if (uuid.equals(descriptor.getUuid()))
				return descriptor;
		return null;
	}

	@Deprecated
	public byte[] getValue() {
		return mValue;
	}

	@Deprecated
	public Integer getIntValue(int formatType, int offset) {
		if (mValue == null || offset + getTypeLen(formatType) > mValue.length)
			return null;
		switch (formatType) {
			case FORMAT_UINT8: return unsignedByteToInt(mValue[offset]);
			case FORMAT_UINT16: return unsignedBytesToInt(mValue[offset], mValue[offset + 1]);
			case FORMAT_UINT32: return unsignedBytesToInt(mValue[offset], mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]);
			case FORMAT_SINT8: return unsignedToSigned(unsignedByteToInt(mValue[offset]), 8);
			case FORMAT_SINT16: return unsignedToSigned(unsignedBytesToInt(mValue[offset], mValue[offset + 1]), 16);
			case FORMAT_SINT32: return unsignedToSigned(unsignedBytesToInt(mValue[offset], mValue[offset + 1], mValue[offset + 2], mValue[offset + 3]), 32);
		}
		return null;
	}

	@Deprecated
	public Float getFloatValue(int formatType, int offset) {
		if (mValue == null || offset + getTypeLen(formatType) > mValue.length)
			return null;
		switch (formatType) {
			case FORMAT_SFLOAT: {
				int mantissa = unsignedToSigned(unsignedByteToInt(mValue[offset]) + ((unsignedByteToInt(mValue[offset + 1]) & 0x0F) << 8), 12);
				int exponent = unsignedToSigned(unsignedByteToInt(mValue[offset + 1]) >> 4, 4);
				return (float)(mantissa * Math.pow(10, exponent));
			}
			case FORMAT_FLOAT: {
				int mantissa = unsignedToSigned(unsignedByteToInt(mValue[offset]) + (unsignedByteToInt(mValue[offset + 1]) << 8) + (unsignedByteToInt(mValue[offset + 2]) << 16), 24);
				return (float)(mantissa * Math.pow(10, mValue[offset + 3]));
			}
		}
		return null;
	}

	@Deprecated
	public String getStringValue(int offset) {
		if (mValue == null || offset > mValue.length)
			return null;
		return new String(mValue, offset, mValue.length - offset);
	}

	@Deprecated
	public boolean setValue(byte[] value) {
		mValue = value;
		return true;
	}

	@Deprecated
	public boolean setValue(int value, int formatType, int offset) {
		int len = offset + getTypeLen(formatType);
		if (mValue == null)
			mValue = new byte[len];
		if (len > mValue.length)
			return false;
		switch (formatType) {
			case FORMAT_SINT8:
				value = intToSignedBits(value, 8);
			case FORMAT_UINT8:
				mValue[offset] = (byte)(value & 0xFF);
				break;
			case FORMAT_SINT16:
				value = intToSignedBits(value, 16);
			case FORMAT_UINT16:
				mValue[offset++] = (byte)(value & 0xFF);
				mValue[offset] = (byte)((value >> 8) & 0xFF);
				break;
			case FORMAT_SINT32:
				value = intToSignedBits(value, 32);
			case FORMAT_UINT32:
				mValue[offset++] = (byte)(value & 0xFF);
				mValue[offset++] = (byte)((value >> 8) & 0xFF);
				mValue[offset++] = (byte)((value >> 16) & 0xFF);
				mValue[offset] = (byte)((value >> 24) & 0xFF);
				break;
			default:
				return false;
		}
		return true;
	}

	@Deprecated
	public boolean setValue(int mantissa, int exponent, int formatType, int offset) {
		int len = offset + getTypeLen(formatType);
		if (mValue == null)
			mValue = new byte[len];
		if (len > mValue.length)
			return false;
		switch (formatType) {
			case FORMAT_SFLOAT:
				mantissa = intToSignedBits(mantissa, 12);
				exponent = intToSignedBits(exponent, 4);
				mValue[offset++] = (byte)(mantissa & 0xFF);
				mValue[offset] = (byte)((mantissa >> 8) & 0x0F);
				mValue[offset] += (byte)((exponent & 0x0F) << 4);
				break;
			case FORMAT_FLOAT:
				mantissa = intToSignedBits(mantissa, 24);
				exponent = intToSignedBits(exponent, 8);
				mValue[offset++] = (byte)(mantissa & 0xFF);
				mValue[offset++] = (byte)((mantissa >> 8) & 0xFF);
				mValue[offset++] = (byte)((mantissa >> 16) & 0xFF);
				mValue[offset] += (byte)(exponent & 0xFF);
				break;
			default:
				return false;
		}
		return true;
	}

	@Deprecated
	public boolean setValue(String value) {
		mValue = value.getBytes();
		return true;
	}

	private static int getTypeLen(int formatType) {
		return formatType & 0xF;
	}

	private static int unsignedByteToInt(byte b) {
		return b & 0xFF;
	}

	private static int unsignedBytesToInt(byte b0, byte b1) {
		return unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8);
	}

	private static int unsignedBytesToInt(byte b0, byte b1, byte b2, byte b3) {
		return unsignedByteToInt(b0) + (unsignedByteToInt(b1) << 8) + (unsignedByteToInt(b2) << 16) + (unsignedByteToInt(b3) << 24);
	}

	private static int unsignedToSigned(int unsigned, int size) {
		if ((unsigned & (1 << (size - 1))) != 0)
			unsigned = -1 * ((1 << (size - 1)) - (unsigned & ((1 << (size - 1)) - 1)));
		return unsigned;
	}

	private static int intToSignedBits(int i, int size) {
		if (i < 0)
			i = (1 << (size - 1)) + (i & ((1 << (size - 1)) - 1));
		return i;
	}
}
