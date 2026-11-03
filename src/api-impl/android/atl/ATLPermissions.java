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

	public static final String BACKGROUND_LOCATION = "android.permission.ACCESS_BACKGROUND_LOCATION";

	/* Android 11's rule: background location asked together with foreground location is denied
	 * without a dialog; the app has to ask for it later, on its own, once it has foreground
	 * location (the service checks that too) */
	public static boolean deniedWithoutAsking(String permission, String[] requestedTogether) {
		if (!BACKGROUND_LOCATION.equals(permission))
			return false;
		for (String p : requestedTogether)
			if ("android.permission.ACCESS_FINE_LOCATION".equals(p) || "android.permission.ACCESS_COARSE_LOCATION".equals(p))
				return true;
		return false;
	}

	/* Whether the app may get location updates now. The owner's decision is open; this is the
	 * recommendation (2026-11-04): always while the app has a window or a foreground service,
	 * otherwise only with the user's background location answer, default no. The one place to
	 * change it. */
	public static boolean locationAllowedNow(boolean hasWindowOrForegroundService) {
		if (hasWindowOrForegroundService)
			return true;
		return !ATLPaths.in_flatpak || check(BACKGROUND_LOCATION);
	}

	/* "yes", "no", "ask", or null when the service cannot be reached */
	private static native String nativeCall(String method, String permission);
}
