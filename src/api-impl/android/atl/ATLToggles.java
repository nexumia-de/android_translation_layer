package android.atl;

public final class ATLToggles {
	public static final boolean ATL_GHAX = queryBooleanToggle("ATL_GHAX", true);
	/* Apps that ran with upstream's fake GMS and show Google's "won't run without Google Play
	 * services" error without it (DB Navigator, 2026-11-03), see docs/gms-compat.md. */
	private static final java.util.Set<String> GMS_STUB_APPS = new java.util.HashSet<>(java.util.Arrays.asList(new String[] {
	    "de.hafas.android.db",
	}));

	/** ATL's permission for an app to contact Google's servers (FCM push and the like). */
	public static final String GOOGLE_SERVICES_PERMISSION = "atl.google-services";
	/**
	 * Grant GOOGLE_SERVICES_PERMISSION to this app. Stand-in until the per-app consent dialog
	 * exists; off by default.
	 */
	public static final boolean ATL_GOOGLE_SERVICES = queryBooleanToggle("ATL_GOOGLE_SERVICES", false);

	private ATLToggles() {}

	/**
	 * Whether to report the Google Play services packages (com.google.android.gms, .gsf,
	 * com.android.vending) as installed and signed with Google's certificate: the built-in
	 * gstub.jar when none is installed, an installed microG with Google's signature added.
	 * ATL_GMS_STUB=1 or 0 decides; without it only the apps in GMS_STUB_APPS get it. Other apps
	 * see no Google Play services, like on a phone without them, and take their own fallbacks.
	 */
	public static boolean gmsStub() {
		String env = System.getenv("ATL_GMS_STUB");
		if (env != null && !env.isEmpty())
			return "1".equals(env) || "true".equals(env);
		String primary = ATLLoadedApp.primaryPackageName();
		return primary != null && GMS_STUB_APPS.contains(primary);
	}

	private static boolean queryBooleanToggle(String name, boolean def) {
		String env = System.getenv(name);
		return env == null || env.isEmpty() ? def : "1".equals(env) || "true".equals(env);
	}
}
