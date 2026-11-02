package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

/** The Class of Device of a classic Bluetooth device. */
public final class BluetoothClass implements Parcelable {
	private final int mClass;

	public static final class Service {
		public static final int LIMITED_DISCOVERABILITY = 0x002000;
		public static final int LE_AUDIO = 0x004000;
		public static final int POSITIONING = 0x010000;
		public static final int NETWORKING = 0x020000;
		public static final int RENDER = 0x040000;
		public static final int CAPTURE = 0x080000;
		public static final int OBJECT_TRANSFER = 0x100000;
		public static final int AUDIO = 0x200000;
		public static final int TELEPHONY = 0x400000;
		public static final int INFORMATION = 0x800000;
	}

	public static class Device {
		public static final int AUDIO_VIDEO_HEADPHONES = 0x0418;
		public static final int AUDIO_VIDEO_WEARABLE_HEADSET = 0x0404;
		public static final int AUDIO_VIDEO_HANDSFREE = 0x0408;
		public static final int AUDIO_VIDEO_LOUDSPEAKER = 0x0414;
		public static final int PHONE_SMART = 0x020C;
		public static final int WEARABLE_WRIST_WATCH = 0x0704;
		public static final int WEARABLE_PAGER = 0x0708;
		public static final int WEARABLE_JACKET = 0x070C;
		public static final int WEARABLE_HELMET = 0x0710;
		public static final int WEARABLE_GLASSES = 0x0714;
		public static final int HEALTH_PULSE_RATE = 0x0918;
		public static final int HEALTH_WEIGHING = 0x0910;

		public static class Major {
			public static final int MISC = 0x0000;
			public static final int COMPUTER = 0x0100;
			public static final int PHONE = 0x0200;
			public static final int NETWORKING = 0x0300;
			public static final int AUDIO_VIDEO = 0x0400;
			public static final int PERIPHERAL = 0x0500;
			public static final int IMAGING = 0x0600;
			public static final int WEARABLE = 0x0700;
			public static final int TOY = 0x0800;
			public static final int HEALTH = 0x0900;
			public static final int UNCATEGORIZED = 0x1F00;
		}
	}

	BluetoothClass(int cls) {
		mClass = cls;
	}

	public boolean hasService(int service) {
		return (mClass & 0xFFE000 & service) != 0;
	}

	public int getMajorDeviceClass() {
		return mClass & 0x1F00;
	}

	public int getDeviceClass() {
		return mClass & 0x1FFC;
	}

	@Override
	public boolean equals(Object o) {
		return o instanceof BluetoothClass && mClass == ((BluetoothClass)o).mClass;
	}

	@Override
	public int hashCode() {
		return mClass;
	}

	@Override
	public String toString() {
		return Integer.toHexString(mClass);
	}

	public void writeToParcel(Parcel out, int flags) {
		out.writeInt(mClass);
	}

	public static final Parcelable.Creator<BluetoothClass> CREATOR = new Parcelable.Creator<BluetoothClass>() {
		public BluetoothClass createFromParcel(Parcel in) {
			return new BluetoothClass(in.readInt());
		}

		public BluetoothClass[] newArray(int size) {
			return new BluetoothClass[size];
		}
	};
}
