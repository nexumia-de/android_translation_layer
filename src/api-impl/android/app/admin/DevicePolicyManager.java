package android.app.admin;

import android.content.ComponentName;
import java.util.List;

/* no device administration: no app is device or profile owner or an active admin */
public class DevicePolicyManager {

	public boolean isDeviceOwnerApp(String packageName) {
		return false;
	}

	public boolean isProfileOwnerApp(String packageName) {
		return false;
	}

	public boolean isAdminActive(ComponentName admin) {
		return false;
	}

	public List<ComponentName> getActiveAdmins() {
		return null;
	}
}
