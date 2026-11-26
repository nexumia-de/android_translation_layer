package android.net.wifi;

/* ATL reads no Wi-Fi details, so this reports what Android reports to an app without
 * location permission: an unknown network with the documented "invalid" values */
public class WifiInfo {
	public static final String LINK_SPEED_UNITS = "Mbps";
	public static final int LINK_SPEED_UNKNOWN = -1;
	public static final String FREQUENCY_UNITS = "MHz";

	/* WifiManager.UNKNOWN_SSID */
	private static final String UNKNOWN_SSID = "<unknown ssid>";

	public String getMacAddress() {
		return "";
	}

	public String getBSSID() {
		return "";
	}

	public String getSSID() {
		return UNKNOWN_SSID;
	}

	public boolean getHiddenSSID() {
		return false;
	}

	public int getIpAddress() {
		return -1;
	}

	public int getNetworkId() {
		return -1;
	}

	/* WifiInfo.INVALID_RSSI */
	public int getRssi() {
		return -127;
	}

	public int getLinkSpeed() {
		return LINK_SPEED_UNKNOWN;
	}

	public int getTxLinkSpeedMbps() {
		return LINK_SPEED_UNKNOWN;
	}

	public int getRxLinkSpeedMbps() {
		return LINK_SPEED_UNKNOWN;
	}

	public int getMaxSupportedTxLinkSpeedMbps() {
		return LINK_SPEED_UNKNOWN;
	}

	public int getMaxSupportedRxLinkSpeedMbps() {
		return LINK_SPEED_UNKNOWN;
	}

	public int getFrequency() {
		return -1;
	}

	/* ScanResult.WIFI_STANDARD_UNKNOWN */
	public int getWifiStandard() {
		return 0;
	}

	/* WifiInfo.SECURITY_TYPE_UNKNOWN */
	public int getCurrentSecurityType() {
		return -1;
	}

	public String getPasspointFqdn() {
		return null;
	}

	public String getPasspointProviderFriendlyName() {
		return null;
	}

	public int describeContents() {
		return 0;
	}
}
