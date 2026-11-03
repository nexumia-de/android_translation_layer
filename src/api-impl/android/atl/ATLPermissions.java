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

	/* Background location (owner, 2026-11-03): the location portal's dialog already covers use while
	 * the app has a window or a foreground service ("while in use"), so asking for
	 * ACCESS_BACKGROUND_LOCATION shows nothing and reports granted unless the user said no or
	 * foreground location is missing (the service answers "no" then). The question comes only at
	 * real background use, in locationAllowedNow. */
	public static boolean backgroundLocationReported() {
		String answer = nativeCall("Check", BACKGROUND_LOCATION);
		return "yes".equals(answer) || "ask".equals(answer);
	}

	/* Whether the app may get location updates now: always while it has a window or a foreground
	 * service, otherwise only with the user's answer to the background question, asked here at
	 * the first real background use (default no). Blocks while the dialog is open, so not on the
	 * main thread. The one place to change the policy. */
	public static boolean locationAllowedNow(boolean hasWindowOrForegroundService) {
		if (hasWindowOrForegroundService)
			return true;
		return !ATLPaths.in_flatpak || request(BACKGROUND_LOCATION);
	}

	/* the legacy review's answers (permission -> "yes"/"no"), null for apps that ask at runtime */
	private static java.util.Map<String, String> legacy_answers;

	/* Legacy permission review (owner, 2026-11-03; Android 10+, GrapheneOS): an app made for
	 * Android before 6 (target SDK or SDK_INT below 23) never calls requestPermissions, so before its
	 * code runs the service shows one review of its dangerous permissions, once, each off by
	 * default. Blocks until it is answered. */
	public static void legacyReviewIfNeeded(int effective_sdk) {
		if (!ATLPaths.in_flatpak || effective_sdk >= 23)
			return;
		String answers = nativeReview();
		if (answers == null)
			return;
		java.util.Map<String, String> map = new java.util.HashMap<>();
		for (String pair : answers.split(";")) {
			int eq = pair.indexOf('=');
			if (eq > 0)
				map.put(pair.substring(0, eq), pair.substring(eq + 1));
		}
		legacy_answers = map;
	}

	/* for a legacy app: the review's answer as checkSelfPermission reports it, or null */
	public static Boolean legacyAnswer(String permission) {
		if (legacy_answers == null || !legacy_answers.containsKey(permission))
			return null;
		return "yes".equals(legacy_answers.get(permission));
	}

	/* "permission=yes;permission=no;...", or null when the service cannot be reached */
	private static native String nativeReview();

	/* "yes", "no", "ask", or null when the service cannot be reached */
	private static native String nativeCall(String method, String permission);
}
