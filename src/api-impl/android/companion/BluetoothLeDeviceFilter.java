package android.companion;

import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import java.util.regex.Pattern;

public final class BluetoothLeDeviceFilter implements DeviceFilter<ScanResult> {
	private BluetoothLeDeviceFilter() {}

	public static final class Builder {
		public Builder setNamePattern(Pattern regex) {
			return this;
		}

		public Builder setScanFilter(ScanFilter scanFilter) {
			return this;
		}

		public Builder setRawDataFilter(byte[] rawDataFilter, byte[] rawDataFilterMask) {
			return this;
		}

		public BluetoothLeDeviceFilter build() {
			return new BluetoothLeDeviceFilter();
		}
	}
}
