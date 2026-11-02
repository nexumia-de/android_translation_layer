package android.bluetooth.le;

import android.os.Parcelable;

/* BlueZ has no scan modes or batching; the settings are kept but do not change the scan */
public final class ScanSettings implements Parcelable {
	public static final int SCAN_MODE_OPPORTUNISTIC = -1;
	public static final int SCAN_MODE_LOW_POWER = 0;
	public static final int SCAN_MODE_BALANCED = 1;
	public static final int SCAN_MODE_LOW_LATENCY = 2;
	public static final int CALLBACK_TYPE_ALL_MATCHES = 1;
	public static final int CALLBACK_TYPE_FIRST_MATCH = 2;
	public static final int CALLBACK_TYPE_MATCH_LOST = 4;
	public static final int MATCH_NUM_ONE_ADVERTISEMENT = 1;
	public static final int MATCH_NUM_FEW_ADVERTISEMENT = 2;
	public static final int MATCH_NUM_MAX_ADVERTISEMENT = 3;
	public static final int MATCH_MODE_AGGRESSIVE = 1;
	public static final int MATCH_MODE_STICKY = 2;
	public static final int PHY_LE_ALL_SUPPORTED = 255;

	private final int scanMode;
	private final int callbackType;
	private final long reportDelayMillis;
	private final boolean legacy;
	private final int phy;

	private ScanSettings(int scanMode, int callbackType, long reportDelayMillis, boolean legacy, int phy) {
		this.scanMode = scanMode;
		this.callbackType = callbackType;
		this.reportDelayMillis = reportDelayMillis;
		this.legacy = legacy;
		this.phy = phy;
	}

	public int getScanMode() {
		return scanMode;
	}

	public int getCallbackType() {
		return callbackType;
	}

	public long getReportDelayMillis() {
		return reportDelayMillis;
	}

	public boolean getLegacy() {
		return legacy;
	}

	public int getPhy() {
		return phy;
	}

	public static final class Builder {
		private int scanMode = SCAN_MODE_LOW_POWER;
		private int callbackType = CALLBACK_TYPE_ALL_MATCHES;
		private long reportDelayMillis = 0;
		private boolean legacy = true;
		private int phy = PHY_LE_ALL_SUPPORTED;

		public Builder setScanMode(int scanMode) {
			this.scanMode = scanMode;
			return this;
		}

		public Builder setCallbackType(int callbackType) {
			this.callbackType = callbackType;
			return this;
		}

		public Builder setReportDelay(long reportDelayMillis) {
			this.reportDelayMillis = reportDelayMillis;
			return this;
		}

		public Builder setNumOfMatches(int numOfMatches) {
			return this;
		}

		public Builder setMatchMode(int matchMode) {
			return this;
		}

		public Builder setLegacy(boolean legacy) {
			this.legacy = legacy;
			return this;
		}

		public Builder setPhy(int phy) {
			this.phy = phy;
			return this;
		}

		public ScanSettings build() {
			return new ScanSettings(scanMode, callbackType, reportDelayMillis, legacy, phy);
		}
	}
}
