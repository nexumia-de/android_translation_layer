package android.atl;

public final class ATLToggles {
	public static final boolean ATL_GHAX = queryBooleanToggle("ATL_GHAX", true);
	/**
	 * Report the Google Play services packages (com.google.android.gms, .gsf, com.android.vending)
	 * as installed and signed with Google's certificate: the built-in gstub.jar when none is
	 * installed, an installed microG with Google's signature added. Off by default: apps then see
	 * no Google Play services, like on a phone without them, and take their own fallbacks. Set it
	 * per app: --install keeps it in the launcher.
	 */
	public static final boolean ATL_GMS_STUB = queryBooleanToggle("ATL_GMS_STUB", false);

	/** ATL's permission for an app to contact Google's servers (FCM push and the like). */
	public static final String GOOGLE_SERVICES_PERMISSION = "atl.google-services";
	/**
	 * Grant GOOGLE_SERVICES_PERMISSION to this app. Stand-in until the per-app consent dialog
	 * exists; off by default.
	 */
	public static final boolean ATL_GOOGLE_SERVICES = queryBooleanToggle("ATL_GOOGLE_SERVICES", false);

	private ATLToggles() {}

	private static boolean queryBooleanToggle(String name, boolean def) {
		String env = System.getenv(name);
		return env == null || env.isEmpty() ? def : "1".equals(env) || "true".equals(env);
	}
}
