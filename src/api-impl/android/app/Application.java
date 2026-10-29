package android.app;

import android.R;
import android.atl.ATLLoadedApp;
import android.atl.ATLPaths;
import android.atl.annotation.Export;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageParser;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;

public class Application extends ContextWrapper {
	public long native_window;

	/* for --install-flatpak: reads the app's name, icon and permissions without running its code (its
	 * Application subclass, content providers and activities), since the installer runs outside the
	 * sandbox the app gets */
	@Export
	static Application create_for_installer() {
		ATLLoadedApp app = ATLLoadedApp.getPrimaryApplication();
		Application application = new Application();
		application.atl_attach_base_context(new ContextImpl(app.createDefaultResources(), app, app.pkg.applicationInfo.theme));
		return application;
	}

	/* the <uses-permission> names, separated by ';' */
	@Export
	String get_requested_permissions() {
		return String.join(";", this.get_atl_loaded_app().pkg.requestedPermissions);
	}

	@Export
	private String get_app_icon_path() {
		String icon_path = null;
		try {
			/* the largest one: launchers and the install page show it at 128 px and more, the
			 * one for ATL's own density is 48 px and looks blurry scaled up */
			TypedValue value = new TypedValue();
			getResources().getValueForDensity(this.get_atl_loaded_app().pkg.applicationInfo.icon, DisplayMetrics.DENSITY_XXXHIGH, value, true);
			if (value.string != null)
				icon_path = value.string.toString();
		} catch (android.content.res.Resources.NotFoundException e) {
			e.printStackTrace();
		}
		if (icon_path == null) {
			icon_path = getString(R.mipmap.sym_def_app_icon);
		} else if (icon_path.endsWith(".xml")) {
			icon_path = null;
		}
		return icon_path;
	}

	@Export
	private long get_app_icon_paintable() {
		return getPackageManager().getApplicationIcon(this.get_atl_loaded_app().pkg.applicationInfo).paintable;
	}

	@Export
	String get_app_label() {
		ApplicationInfo info = this.get_atl_loaded_app().pkg.applicationInfo;
		if (info.labelRes != 0)
			return getString(info.labelRes);
		// android:label can also be a literal string
		if (info.nonLocalizedLabel != null && info.nonLocalizedLabel.length() > 0)
			return info.nonLocalizedLabel.toString();
		return info.packageName;
	}

	/* for --install-ask: what installing this apk would do, see enum install_state in main.c */
	static final int INSTALL_NEW = 0;
	static final int INSTALL_UPDATE = 1;
	static final int INSTALL_REINSTALL = 2;
	static final int INSTALL_DOWNGRADE = 3;
	static final int INSTALL_CONFLICT = 4;
	private String installed_version_name;

	@Export
	String get_version_name() {
		return this.get_atl_loaded_app().pkg.mVersionName;
	}

	@Export
	String get_installed_version_name() {
		return installed_version_name;
	}

	/* like Android's installer: an update keeps the signer and does not go back to an older version */
	@Export
	int get_install_state() {
		PackageParser.Package pkg = this.get_atl_loaded_app().pkg;
		File installed_apk = new File(ATLPaths.installed_apks_dir, pkg.packageName + ".apk");
		if (!installed_apk.isFile())
			return INSTALL_NEW;
		PackageParser.Package installed = parse(installed_apk);
		if (installed == null)
			return INSTALL_UPDATE; // a broken install, replacing it is what the user wants
		installed_version_name = installed.mVersionName;
		PackageParser.Package opened = parse(new File(pkg.applicationInfo.sourceDir));
		if (opened == null || installed.mSignatures == null || opened.mSignatures == null ||
		    !new HashSet<>(Arrays.asList(opened.mSignatures)).equals(new HashSet<>(Arrays.asList(installed.mSignatures))))
			return INSTALL_CONFLICT;
		if (opened.mVersionCode < installed.mVersionCode)
			return INSTALL_DOWNGRADE;
		return opened.mVersionCode == installed.mVersionCode ? INSTALL_REINSTALL : INSTALL_UPDATE;
	}

	private static PackageParser.Package parse(File apk) {
		PackageParser parser = new PackageParser(apk.getPath());
		PackageParser.Package pkg = parser.parsePackage(apk, apk.getPath(), new DisplayMetrics(), 0);
		if (pkg != null)
			parser.collectCertificates(pkg, 0);
		return pkg;
	}

	@Export
	String get_supported_mime_types() {
		StringBuilder mimeTypes = new StringBuilder();
		for (PackageParser.Activity activity : this.get_atl_loaded_app().pkg.activities) {
			if (activity.intents == null)
				continue;
			for (PackageParser.IntentInfo intent : activity.intents) {
				for (int i = 0; i < intent.countDataSchemes(); i++) {
					String scheme = intent.getDataScheme(i);
					// ignore http and https, as there is no way to only handle specific hosts in a .desktop file
					if (!"http".equals(scheme) && !"https".equals(scheme))
						mimeTypes.append("x-scheme-handler/").append(intent.getDataScheme(i)).append(";");
				}
			}
		}
		return (mimeTypes.length() == 0) ? null : mimeTypes.toString();
	}

	public interface ActivityLifecycleCallbacks {
		void onActivityCreated(Activity activity, Bundle savedInstanceState);
		void onActivityStarted(Activity activity);
		void onActivityResumed(Activity activity);
		void onActivityPaused(Activity activity);
		void onActivityStopped(Activity activity);
		void onActivitySaveInstanceState(Activity activity, Bundle outState);
		void onActivityDestroyed(Activity activity);
	}
	/**
	 * Callback interface for use with {@link Application#registerOnProvideAssistDataListener}
	 * and {@link Application#unregisterOnProvideAssistDataListener}.
	 */
	public interface OnProvideAssistDataListener {
		/**
		 * This is called when the user is requesting an assist, to build a full
		 * {@link Intent#ACTION_ASSIST} Intent with all of the context of the current
		 * application.  You can override this method to place into the bundle anything
		 * you would like to appear in the {@link Intent#EXTRA_ASSIST_CONTEXT} part
		 * of the assist Intent.
		 */
		public void onProvideAssistData(Activity activity, Bundle data);
	}

	public Application() {
		super(null);
	}

	/**
	 * Called when the application is starting, before any activity, service,
	 * or receiver objects (excluding content providers) have been created.
	 * Implementations should be as quick as possible (for example using
	 * lazy initialization of state) since the time spent in this function
	 * directly impacts the performance of starting the first activity,
	 * service, or receiver in a process.
	 * If you override this method, be sure to call super.onCreate().
	 */
	@Export
	public void onCreate() {
	}
	/**
	 * This method is for use in emulated process environments.  It will
	 * never be called on a production Android device, where processes are
	 * removed by simply killing them; no user code (including this callback)
	 * is executed when doing so.
	 */
	public void onTerminate() {
	}
	public void onConfigurationChanged(Configuration newConfig) {
	}
	public void onLowMemory() {
	}
	public void onTrimMemory(int level) {
	}
	/*public void registerComponentCallbacks(ComponentCallbacks callback) {
	}
	public void unregisterComponentCallbacks(ComponentCallbacks callback) {
	}*/
	public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
	}
	public void unregisterActivityLifecycleCallbacks(ActivityLifecycleCallbacks callback) {
	}
	public void registerOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
	}
	public void unregisterOnProvideAssistDataListener(OnProvideAssistDataListener callback) {
	}
	public static String getProcessName() {
		// note: we currently don't set the process name
		return ATLLoadedApp.getPrimaryApplication().pkg.packageName;
	}
}
