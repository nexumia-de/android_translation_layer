package android.atl;

import android.annotation.Nullable;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageParser;
import android.util.DisplayMetrics;
import android.util.Slog;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public final class ATLLoadedAppManager {
	private static final String TAG = "ATLLoadedAppManager";
	private static final HashMap<String, ATLLoadedApp> loaded_apps = new HashMap<>();
	private static final HashMap<String, PackageParser.Package> loaded_packages = new HashMap<>();
	private static final Object load_lock = new Object();
	private static final HashSet<String> always_visible_packages = new HashSet<>();
	private static final HashSet<String> always_allow_query_all_packages = new HashSet<>();

	static {
		// Always allow MicroG to be visible for application compatibility.
		always_visible_packages.addAll(ATLLoadedApp.play_services);
		// Always allow MicroG to see all applications for compatibility.
		always_allow_query_all_packages.addAll(ATLLoadedApp.play_services);
	}

	private ATLLoadedAppManager() {}

	@Nullable
	public static PackageParser.Package getPackageFromPackageName(String packageName) {
		ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
		if (app.pkg.packageName.equals(packageName)) {
			return app.pkg;
		}
		if ("android".equals(packageName) || "atl".equals(packageName)) {
			return ATLLoadedApp.getSystemApplication().pkg;
		}
		app = loaded_apps.get(packageName);
		if (app != null) {
			return app.pkg;
		}
		// an app that looks at other packages hears when they are installed or uninstalled
		PackageInstaller.watchInstalledApks();
		PackageParser.Package pkg = loaded_packages.get(packageName);
		if (pkg != null) {
			return pkg;
		}
		return loadPackage(packageName);
	}

	@Nullable
	public static ATLLoadedApp peekAppFromPackageName(String packageName) {
		ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
		if (app.pkg.packageName.equals(packageName)) {
			return app;
		}
		if ("android".equals(packageName) || "atl".equals(packageName)) {
			return ATLLoadedApp.getSystemApplication();
		}
		return loaded_apps.get(packageName);
	}

	@Nullable
	public static ATLLoadedApp getAppFromPackageName(String packageName) {
		ATLLoadedApp app = peekAppFromPackageName(packageName);
		if (app != null) {
			return app;
		}
		return loadApplication(packageName);
	}

	private static PackageParser.Package loadPackage(String packageName) {
		if (!isPackageVisible(ATLLoadedApp.getPrimaryApplication().pkg.packageName, packageName)) {
			// No right to view this package.
			return null;
		}
		PackageParser.Package pkg;
		synchronized (load_lock) {
			ATLLoadedApp app = loaded_apps.get(packageName);
			if (app != null) {
				return app.pkg;
			}
			pkg = loaded_packages.get(packageName);
			if (pkg != null) {
				return pkg;
			}
			File installed_apk_location = new File(ATLPaths.installed_apks_dir, packageName + ".apk");
			if (!installed_apk_location.isFile()) {
				PackageInstaller.refreshInstalled(false);
			}
			if (!installed_apk_location.isFile()) {
				if (ATLLoadedApp.play_services.contains(packageName)) {
					return loadGStub(packageName).pkg;
				}
				return null;
			}
			PackageParser packageParser = new PackageParser(installed_apk_location.getPath());
			pkg = packageParser.parsePackage(installed_apk_location,
			                                 installed_apk_location.getPath(), new DisplayMetrics(), 0);
			if (pkg != null) {
				packageParser.collectCertificates(pkg, 0);
				if (ATLLoadedApp.play_services.contains(packageName)) {
					ATLSigHelper.addGMSSignatures(pkg);
				}
				loaded_packages.put(packageName, pkg);
			} else {
				Slog.e(TAG, "Failed to load package " + packageName);
			}
		}
		return pkg;
	}

	private static ATLLoadedApp loadApplication(String packageName) {
		if (!isPackageVisible(ATLLoadedApp.getPrimaryApplication().pkg.packageName, packageName)) {
			// No right to view this package.
			return null;
		}
		ATLLoadedApp app;
		synchronized (load_lock) {
			app = loaded_apps.get(packageName);
			if (app != null) {
				return app;
			}
			Slog.i(TAG, "Attempting to load " + packageName + " into " + ATLLoadedApp.getPrimaryApplication().pkg.packageName);
			File installed_apk_location = new File(ATLPaths.installed_apks_dir, packageName + ".apk");
			File installed_lib_location = new File(ATLPaths.app_data_dir_base, packageName + "_/lib");
			if (!installed_apk_location.isFile()) {
				if (ATLLoadedApp.play_services.contains(packageName)) {
					return loadGStub(packageName);
				}
				return null;
			}
			String apk_path = installed_apk_location.getPath();
			String lib_path = installed_lib_location.isDirectory() ? installed_lib_location.getPath() : null;
			try {
				app = ATLLoadedApp.loadFromPath(apk_path, lib_path, apk_path);
				app.pkg.applicationInfo.nativeLibraryDir = lib_path;
				app.pkg.applicationInfo.sourceDir = apk_path;
				Slog.i(TAG, "loadApplication(\"" + packageName + "\")");
				loaded_packages.put(packageName, app.pkg);
				loaded_apps.put(packageName, app);
			} catch (IOException e) {
				Slog.e(TAG, "Failed to load " + packageName, e);
				return null;
			}
		}
		return app;
	}

	private static boolean isPackageVisible(String sourcePackageName, String targetPackageName) {
		return always_visible_packages.contains(targetPackageName) || always_allow_query_all_packages.contains(sourcePackageName) || (sourcePackageName.equals(ATLLoadedApp.getPrimaryApplication().pkg.packageName) && ATLLoadedApp.getPrimaryApplication().pkg.requestedPermissions.contains("android.permission.QUERY_ALL_PACKAGES"));
	}

	/**
	 * Drops a cached package after it was installed, updated or uninstalled, so the next lookup
	 * reads _installed_apks_ again. Applications already loaded into this process stay while
	 * their APK is still there.
	 */
	public static void forgetPackage(String packageName) {
		synchronized (load_lock) {
			loaded_packages.remove(packageName);
			if (!new File(ATLPaths.installed_apks_dir, packageName + ".apk").isFile())
				loaded_apps.remove(packageName);
		}
	}

	/** Package names of the APKs in _installed_apks_ that the primary application may see. */
	public static List<String> getInstalledPackageNames() {
		PackageInstaller.refreshInstalled(false);
		PackageInstaller.watchInstalledApks();
		List<String> names = new ArrayList<>();
		String[] files = ATLPaths.installed_apks_dir.list();
		if (files == null)
			return names;
		String source = ATLLoadedApp.getPrimaryApplication().pkg.packageName;
		for (String file : files) {
			if (!file.endsWith(".apk"))
				continue;
			String packageName = file.substring(0, file.length() - ".apk".length());
			if (isPackageVisible(source, packageName))
				names.add(packageName);
		}
		return names;
	}

	private static ATLLoadedApp loadGStub(String packageName) {
		ATLLoadedApp app = ATLGStub.loadFakePlayServices(packageName);
		Slog.i(TAG, "loadGStub(\"" + packageName + "\")");
		loaded_packages.put(packageName, app.pkg);
		loaded_apps.put(packageName, app);
		return app;
	}
}
