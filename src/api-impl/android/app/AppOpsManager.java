package android.app;

public class AppOpsManager {

	public static String permissionToOp(String permission) {
		return permission;
	}

	public int noteProxyOpNoThrow(String op, String pkg) {
		return 0;
	}

	public int checkOpNoThrow(String op, int uid, String packageName) {
		return 0; // MODE_ALLOWED
	}
}
