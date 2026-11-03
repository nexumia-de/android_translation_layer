package android.atl;

/* Runtime permissions without a portal (docs/hardening-roadmap.md, phase 4): inside an app's own
 * Flatpak the host's installer service asks the user and keeps the answers. */
public final class ATLPermissions {
	/* ATL's own permission: talking to Google's servers, asked with a warning, default denied */
	public static final String GOOGLE_SERVICES = "atl.google-services";

	private static final String[] DECIDED_BY_SERVICE = {
		"android.permission.READ_CONTACTS",
		"android.permission.WRITE_CONTACTS",
		"android.permission.READ_CALENDAR",
		"android.permission.WRITE_CALENDAR",
		"android.permission.READ_SMS",
		"android.permission.SEND_SMS",
		"android.permission.READ_CALL_LOG",
		"android.permission.READ_PHONE_STATE",
		"android.permission.ACCESS_BACKGROUND_LOCATION",
		"android.permission.RECORD_AUDIO",
		"android.permission.BLUETOOTH_CONNECT",
		"android.permission.BLUETOOTH_SCAN",
		"android.permission.BODY_SENSORS",
		"android.permission.ACTIVITY_RECOGNITION",
		GOOGLE_SERVICES,
	};

	private ATLPermissions() {}

	/* whether the service decides this permission for the running app */
	public static boolean decidedByService(String permission) {
		if (!ATLPaths.in_flatpak || permission == null)
			return false;
		for (String p : DECIDED_BY_SERVICE)
			if (p.equals(permission))
				return true;
		return false;
	}

	/* the stored answer without asking: true only for "yes"; fast enough for checkPermission */
	public static boolean check(String permission) {
		return "yes".equals(nativeCall("Check", permission));
	}

	/* asks the user unless already answered; blocks until the answer, so not on the main thread */
	public static boolean request(String permission) {
		return "yes".equals(nativeCall("Request", permission));
	}

	/* "yes", "no", "ask", or null when the service cannot be reached */
	private static native String nativeCall(String method, String permission);
}
