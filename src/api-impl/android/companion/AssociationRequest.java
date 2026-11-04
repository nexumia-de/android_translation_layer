package android.companion;

import android.os.Parcelable;
import java.util.ArrayList;
import java.util.List;

public final class AssociationRequest implements Parcelable {
	private final List<DeviceFilter<?>> deviceFilters;
	private final boolean singleDevice;

	private AssociationRequest(Builder b) {
		deviceFilters = b.deviceFilters;
		singleDevice = b.singleDevice;
	}

	public boolean isSingleDevice() {
		return singleDevice;
	}

	public static final class Builder {
		private final List<DeviceFilter<?>> deviceFilters = new ArrayList<>();
		private boolean singleDevice;

		public Builder addDeviceFilter(DeviceFilter<?> deviceFilter) {
			if (deviceFilter != null)
				deviceFilters.add(deviceFilter);
			return this;
		}

		public Builder setSingleDevice(boolean singleDevice) {
			this.singleDevice = singleDevice;
			return this;
		}

		public Builder setDeviceProfile(String deviceProfile) {
			return this;
		}

		public Builder setDisplayName(CharSequence displayName) {
			return this;
		}

		public Builder setSelfManaged(boolean selfManaged) {
			return this;
		}

		public AssociationRequest build() {
			return new AssociationRequest(this);
		}
	}
}
