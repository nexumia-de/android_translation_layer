package android.companion;

import android.bluetooth.BluetoothDevice;
import android.os.ParcelUuid;
import java.util.regex.Pattern;

public final class BluetoothDeviceFilter implements DeviceFilter<BluetoothDevice> {
	private BluetoothDeviceFilter() {}

	public static final class Builder {
		public Builder setAddress(String address) {
			return this;
		}

		public Builder setNamePattern(Pattern regex) {
			return this;
		}

		public Builder addServiceUuid(ParcelUuid serviceUuid, ParcelUuid serviceUuidMask) {
			return this;
		}

		public BluetoothDeviceFilter build() {
			return new BluetoothDeviceFilter();
		}
	}
}
