package android.os;

public class UserManager {
	public static final String DISALLOW_INSTALL_APPS = "no_install_apps";
	public static final String DISALLOW_UNINSTALL_APPS = "no_uninstall_apps";
	public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES = "no_install_unknown_sources";
	public static final String DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY = "no_install_unknown_sources_globally";
	public static final String DISALLOW_MODIFY_ACCOUNTS = "no_modify_accounts";
	public static final String DISALLOW_CONFIG_WIFI = "no_config_wifi";
	public static final String DISALLOW_SHARE_LOCATION = "no_share_location";
	public static final String DISALLOW_DEBUGGING_FEATURES = "no_debugging_features";

	public boolean isUserUnlocked() {
		return true;
	}
	public static boolean supportsMultipleUsers() {
		return false;
	}

	public long getSerialNumberForUser(UserHandle user) {
		return user.getIdentifier();
	}

	/* there is one user and no device policy, so nothing is restricted */
	public boolean hasUserRestriction(String restrictionKey) {
		return false;
	}

	public boolean hasUserRestriction(String restrictionKey, UserHandle userHandle) {
		return false;
	}

	public Bundle getUserRestrictions() {
		return new Bundle();
	}

	public Bundle getUserRestrictions(UserHandle userHandle) {
		return new Bundle();
	}

	public boolean isManagedProfile() {
		return false;
	}

	public boolean isDemoUser() {
		return false;
	}
}
