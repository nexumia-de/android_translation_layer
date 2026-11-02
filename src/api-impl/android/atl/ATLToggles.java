package android.atl;

public final class ATLToggles {
	public static final boolean ATL_GHAX = queryBooleanToggle("ATL_GHAX", true);
	/* Apps compared with and without the fake GMS and not worse with it, see docs/gms-compat.md. */
	private static final java.util.Set<String> GMS_STUB_APPS = new java.util.HashSet<>(java.util.Arrays.asList(new String[] {
	    "de.dhl.paket",
	    "de.hafas.android.db",
	}));

	private ATLToggles() {}

	/**
	 * Whether to report the Google Play services packages (com.google.android.gms, .gsf,
	 * com.android.vending) as installed and signed with Google's certificate: the built-in
	 * gstub.jar with its local services when none is installed, an installed microG with Google's
	 * signature added. ATL_GMS_STUB=1 or 0 decides; without it, only the apps in GMS_STUB_APPS
	 * get it. Other apps see no Google Play services, like on a phone without them.
	 */
	public static boolean gmsStub() {
		String env = System.getenv("ATL_GMS_STUB");
		if (env != null && !env.isEmpty())
			return "1".equals(env) || "true".equals(env);
		ATLLoadedApp primary = ATLLoadedApp.peekPrimaryApplication();
		return primary != null && GMS_STUB_APPS.contains(primary.pkg.packageName);
	}

	private static boolean queryBooleanToggle(String name, boolean def) {
		String env = System.getenv(name);
		return env == null || env.isEmpty() ? def : "1".equals(env) || "true".equals(env);
	}
}
