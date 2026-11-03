package android.telephony;

import java.util.ArrayList;
import java.util.List;

public class TelephonyManager {
	private static Boolean hasModem;

	/* whether the device has a cellular modem, as ModemManager reports it; ATL_HAS_TELEPHONY=1 or 0 overrides.
	 * Asked once per process: a modem that ModemManager finds later (e.g. an app started right after boot) shows only after a restart of the app */
	public static synchronized boolean hasModem() {
		if (hasModem == null) {
			String override = System.getenv("ATL_HAS_TELEPHONY");
			hasModem = override != null ? override.equals("1") : nativeHasModem();
		}
		return hasModem;
	}

	private static native boolean nativeHasModem();

	public static TelephonyManager getDefault() {
		return new TelephonyManager();
	}

	// FIXME: can we return null instead of ""?
	public String getNetworkOperator() {
		return "";
	}

	public String getNetworkOperatorName() {
		return "";
	}

	public String getSimOperator() {
		return "";
	}

	public String getSubscriberId() {
		return "";
	}

	public int getPhoneType() {
		return 0; // PHONE_TYPE_NONE
	}

	public String getNetworkCountryIso() { return ""; }
	public String getSimCountryIso() { return ""; }

	public List getNeighboringCellInfo() {
		return new ArrayList(0);
	}

	public CellLocation getCellLocation() {
		return new CellLocation();
	}

	public boolean isNetworkRoaming() {
		return false;
	}

	public void listen(PhoneStateListener listener, int events) {}

	public int getNetworkType() {
		return 0; // NETWORK_TYPE_UNKNOWN
	}

	public String getLine1Number() {
		return "";
	}

	public int getSimState() {
		return 0; // SIM_STATE_UNKNOWN
	}

	public String getSimOperatorName() {
		return "";
	}

	public int getCallState() {
		return 0; // CALL_STATE_IDLE
	}

	public String getDeviceId() {
		return "";
	}
}
