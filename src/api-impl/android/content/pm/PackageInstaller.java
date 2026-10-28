package android.content.pm;

import android.atl.ATLLoadedApp;
import android.atl.ATLLoadedAppManager;
import android.atl.ATLPaths;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Slog;
import android.view.Display;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.xmlpull.v1.XmlPullParser;

/**
 * Installs an APK by handing it to `android-translation-layer --install`, which copies it to
 * _installed_apks_/<package name>.apk, split APKs to _installed_apks_/<package name>.apk.splits/,
 * and asks the desktop portal for a launcher.
 */
public class PackageInstaller {
	private static final String TAG = "PackageInstaller";

	public static final String ACTION_SESSION_DETAILS = "android.content.pm.action.SESSION_DETAILS";
	public static final String ACTION_SESSION_COMMITTED = "android.content.pm.action.SESSION_COMMITTED";
	public static final String ACTION_SESSION_UPDATED = "android.content.pm.action.SESSION_UPDATED";
	public static final String ACTION_CONFIRM_INSTALL = "android.content.pm.action.CONFIRM_INSTALL";

	public static final String EXTRA_SESSION_ID = "android.content.pm.extra.SESSION_ID";
	public static final String EXTRA_SESSION = "android.content.pm.extra.SESSION";
	public static final String EXTRA_STATUS = "android.content.pm.extra.STATUS";
	public static final String EXTRA_STATUS_MESSAGE = "android.content.pm.extra.STATUS_MESSAGE";
	public static final String EXTRA_PACKAGE_NAME = "android.content.pm.extra.PACKAGE_NAME";
	public static final String EXTRA_LEGACY_STATUS = "android.content.pm.extra.LEGACY_STATUS";
	public static final String EXTRA_OTHER_PACKAGE_NAME = "android.content.pm.extra.OTHER_PACKAGE_NAME";
	public static final String EXTRA_STORAGE_PATH = "android.content.pm.extra.STORAGE_PATH";

	public static final int STATUS_PENDING_USER_ACTION = -1;
	public static final int STATUS_SUCCESS = 0;
	public static final int STATUS_FAILURE = 1;
	public static final int STATUS_FAILURE_BLOCKED = 2;
	public static final int STATUS_FAILURE_ABORTED = 3;
	public static final int STATUS_FAILURE_INVALID = 4;
	public static final int STATUS_FAILURE_CONFLICT = 5;
	public static final int STATUS_FAILURE_STORAGE = 6;
	public static final int STATUS_FAILURE_INCOMPATIBLE = 7;
	public static final int STATUS_FAILURE_TIMEOUT = 8;

	public static final int PACKAGE_SOURCE_UNSPECIFIED = 0;
	public static final int PACKAGE_SOURCE_OTHER = 1;
	public static final int PACKAGE_SOURCE_STORE = 2;
	public static final int PACKAGE_SOURCE_LOCAL_FILE = 3;
	public static final int PACKAGE_SOURCE_DOWNLOADED_FILE = 4;

	/* exit status of `android-translation-layer --install` when the user cancelled the portal dialog */
	private static final int INSTALL_EXIT_CANCELLED = 2;

	// sessions live in the process, like the PackageInstaller instances handed out by PackageManager
	private static final HashMap<Integer, Session> sessions = new HashMap<>();
	private static final ArrayList<SessionCallback> callbacks = new ArrayList<>();
	private static final Random random = new Random();

	public int createSession(SessionParams params) throws IOException {
		File base = new File(ATLPaths.app_data_dir_base, "_installer_sessions_");
		base.mkdirs();
		File dir = Files.createTempDirectory(base.toPath(), "session").toFile();
		Session session;
		synchronized (sessions) {
			int id;
			do {
				id = random.nextInt(Integer.MAX_VALUE - 1) + 1;
			} while (sessions.containsKey(id));
			session = new Session(id, params, dir);
			sessions.put(id, session);
		}
		Slog.i(TAG, "createSession: " + session.id + " for " + params.appPackageName + " in " + dir);
		for (SessionCallback callback : callbacks)
			callback.onCreated(session.id);
		return session.id;
	}

	public Session openSession(int sessionId) throws IOException {
		synchronized (sessions) {
			Session session = sessions.get(sessionId);
			if (session == null)
				throw new SecurityException("Session " + sessionId + " does not exist");
			return session;
		}
	}

	public void abandonSession(int sessionId) {
		Session session;
		synchronized (sessions) {
			session = sessions.get(sessionId);
		}
		if (session == null)
			throw new SecurityException("Session " + sessionId + " does not exist");
		session.abandon();
	}

	public SessionInfo getSessionInfo(int sessionId) {
		synchronized (sessions) {
			Session session = sessions.get(sessionId);
			return session != null ? session.getInfo() : null;
		}
	}

	public List<SessionInfo> getMySessions() {
		List<SessionInfo> list = new ArrayList<>();
		synchronized (sessions) {
			for (Session session : sessions.values())
				list.add(session.getInfo());
		}
		return list;
	}

	public List<SessionInfo> getAllSessions() {
		return getMySessions();
	}

	public void registerSessionCallback(SessionCallback callback) {
		callbacks.add(callback);
	}

	public void registerSessionCallback(SessionCallback callback, Handler handler) {
		callbacks.add(callback);
	}

	public void unregisterSessionCallback(SessionCallback callback) {
		callbacks.remove(callback);
	}

	public void uninstall(String packageName, IntentSender statusReceiver) {
		boolean success = uninstallPackage(packageName);
		sendStatus(statusReceiver, -1, packageName, success ? STATUS_SUCCESS : STATUS_FAILURE, success ? null : "not installed");
	}

	/**
	 * Removes an APK installed to _installed_apks_, its launcher, its D-Bus service file and its
	 * data, and tells the app that the package is gone.
	 */
	public static boolean uninstallPackage(String packageName) {
		File apk = new File(ATLPaths.installed_apks_dir, packageName + ".apk");
		if (!apk.isFile()) {
			Slog.w(TAG, "uninstall: " + packageName + " is not installed");
			return false;
		}
		Slog.i(TAG, "uninstall: " + packageName);
		apk.delete();
		deleteRecursively(new File(ATLPaths.installed_apks_dir, packageName + ".apk.splits"));
		deleteRecursively(new File(ATLPaths.app_data_dir_base, packageName + ".apk_"));
		String dataHome = System.getenv("XDG_DATA_HOME");
		if (dataHome == null || dataHome.isEmpty())
			dataHome = System.getenv("HOME") + "/.local/share";
		new File(dataHome + "/dbus-1/services/" + packageName + ".service").delete();
		// dbus-broker (unlike dbus-daemon) only reads service files again when asked to
		run("gdbus", "call", "--session", "--dest", "org.freedesktop.DBus", "--object-path", "/org/freedesktop/DBus",
		    "--method", "org.freedesktop.DBus.ReloadConfig");
		/* the launcher ATL writes itself outside a sandbox, with its icon */
		new File(dataHome + "/applications/" + packageName + ".desktop").delete();
		for (String extension : new String[] {"png", "webp", "svg"})
			new File(ATLPaths.app_data_dir_base, "_launcher_icons_/" + packageName + "." + extension).delete();
		String cacheHome = System.getenv("XDG_CACHE_HOME");
		if (cacheHome == null || cacheHome.isEmpty())
			cacheHome = System.getenv("HOME") + "/.cache";
		deleteRecursively(new File(cacheHome + "/android_translation_layer/" + packageName));
		String output = run("gdbus", "call", "--session", "--dest", "org.freedesktop.portal.Desktop",
		                    "--object-path", "/org/freedesktop/portal/desktop",
		                    "--method", "org.freedesktop.portal.DynamicLauncher.Uninstall",
		                    packageName + ".desktop", "{}");
		Slog.i(TAG, "uninstall: removing launcher: " + output);
		ATLLoadedAppManager.forgetPackage(packageName);
		sendPackageBroadcast(Intent.ACTION_PACKAGE_REMOVED, packageName, false);
		return true;
	}

	private static void deleteRecursively(File file) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				if (!Files.isSymbolicLink(child.toPath()))
					deleteRecursively(child);
				else
					child.delete();
			}
		}
		file.delete();
	}

	/* runs a command and returns its exit status followed by its output */
	private static String run(String... command) {
		try {
			Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
			p.getOutputStream().close();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			InputStream in = p.getInputStream();
			byte[] buffer = new byte[8192];
			int n;
			while ((n = in.read(buffer)) > 0)
				out.write(buffer, 0, n);
			return p.waitFor() + "\n" + out.toString();
		} catch (IOException | InterruptedException e) {
			return "-1\n" + e;
		}
	}

	private static void sendPackageBroadcast(String action, String packageName, boolean replacing) {
		final Intent intent = new Intent(action, Uri.fromParts("package", packageName, null));
		intent.putExtra(Intent.EXTRA_REPLACING, replacing);
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				ATLLoadedApp.getPrimaryApplication().getApplication().sendBroadcast(intent);
			}
		});
	}

	private static void sendStatus(final IntentSender statusReceiver, int sessionId, String packageName, int status, String message) {
		Slog.i(TAG, "session " + sessionId + " (" + packageName + "): status " + status + (message != null ? ": " + message : ""));
		if (statusReceiver == null)
			return;
		final Intent fillIn = new Intent();
		fillIn.putExtra(EXTRA_SESSION_ID, sessionId);
		fillIn.putExtra(EXTRA_PACKAGE_NAME, packageName);
		fillIn.putExtra(EXTRA_STATUS, status);
		if (message != null)
			fillIn.putExtra(EXTRA_STATUS_MESSAGE, message);
		// Android delivers the result on the main thread
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				try {
					statusReceiver.sendIntent(ATLLoadedApp.getPrimaryApplication().getApplication(), 0, fillIn, null, null);
				} catch (IntentSender.SendIntentException e) {
					Slog.e(TAG, "failed to send install status", e);
				}
			}
		});
	}

	public static class Session implements Closeable {
		private final int id;
		private final SessionParams params;
		private final File dir;
		private float progress;
		private boolean active;

		private Session(int id, SessionParams params, File dir) {
			this.id = id;
			this.params = params;
			this.dir = dir;
		}

		private SessionInfo getInfo() {
			SessionInfo info = new SessionInfo();
			info.sessionId = id;
			info.appPackageName = params.appPackageName;
			info.appLabel = params.appLabel;
			info.appIcon = params.appIcon;
			info.mode = params.mode;
			info.size = params.sizeBytes;
			info.progress = progress;
			info.active = active;
			info.installerPackageName = ATLLoadedApp.getPrimaryApplication().pkg.packageName;
			return info;
		}

		public OutputStream openWrite(String name, long offsetBytes, long lengthBytes) throws IOException {
			if (name.contains("/") || name.startsWith("."))
				throw new IllegalArgumentException("Invalid name: " + name);
			FileOutputStream out = new FileOutputStream(new File(dir, name + ".apk"), offsetBytes > 0);
			if (offsetBytes > 0)
				out.getChannel().position(offsetBytes);
			active = true;
			return out;
		}

		public InputStream openRead(String name) throws IOException {
			return new java.io.FileInputStream(new File(dir, name + ".apk"));
		}

		public void fsync(OutputStream out) throws IOException {
			if (out instanceof FileOutputStream)
				((FileOutputStream)out).getFD().sync();
		}

		public String[] getNames() {
			File[] files = dir.listFiles();
			if (files == null)
				return new String[0];
			String[] names = new String[files.length];
			for (int i = 0; i < files.length; i++)
				names[i] = files[i].getName().replaceAll("\\.apk$", "");
			return names;
		}

		public void removeSplit(String splitName) throws IOException {
			new File(dir, splitName + ".apk").delete();
		}

		public void setStagingProgress(float progress) {
			this.progress = progress;
			for (SessionCallback callback : callbacks)
				callback.onProgressChanged(id, progress);
		}

		public void commit(final IntentSender statusReceiver) {
			Slog.i(TAG, "commit: session " + id);
			synchronized (sessions) {
				sessions.remove(id);
			}
			new Thread(new Runnable() {
				@Override
				public void run() {
					install(statusReceiver);
				}
			}, "PackageInstaller session " + id).start();
		}

		private void install(IntentSender statusReceiver) {
			String packageName = params.appPackageName;
			int status;
			String message = null;
			File base;
			try {
				File[] files = dir.listFiles();
				if (files == null || files.length == 0) {
					status = STATUS_FAILURE_INVALID;
					message = "INSTALL_FAILED_INVALID_APK: no APK written";
				} else if ((base = findBaseApk(files)) == null) {
					status = STATUS_FAILURE_INVALID;
					message = "INSTALL_FAILED_INVALID_APK: no base APK, only splits";
				} else {
					PackageParser parser = new PackageParser(base.getPath());
					PackageParser.Package pkg = parser.parsePackage(base, base.getPath(), new DisplayMetrics(), 0);
					if (pkg == null) {
						status = STATUS_FAILURE_INVALID;
						message = "INSTALL_FAILED_INVALID_APK: failed to parse " + base.getName();
					} else if (packageName != null && !packageName.equals(pkg.packageName)) {
						status = STATUS_FAILURE_INVALID;
						message = "INSTALL_FAILED_INVALID_APK: package " + pkg.packageName + " does not match " + packageName;
					} else {
						packageName = pkg.packageName;
						parser.collectCertificates(pkg, 0);
						String conflict = checkSignatures(pkg);
						if (conflict != null) {
							status = STATUS_FAILURE_CONFLICT;
							message = conflict;
						} else {
							// --install names the installed file after the file it gets
							File apk = new File(dir, packageName + ".apk");
							base.renameTo(apk);
							File installed = new File(ATLPaths.installed_apks_dir, apk.getName());
							boolean replacing = installed.isFile();
							// the installed app starts with the installer's window size instead of the 960x540 default
							List<String> command = new ArrayList<>(Arrays.asList("/usr/bin/env", "android-translation-layer", "--install",
							                    "-w", String.valueOf(Display.window_width), "-h", String.valueOf(Display.window_height)));
							// a store started with --sdk-int passes it on to the apps it installs
							String sdkInt = System.getProperty("Build.VERSION.SDK_INT");
							if (sdkInt != null)
								command.addAll(Arrays.asList("--sdk-int", sdkInt));
							// --install takes the split APKs after the base APK
							command.add(apk.getPath());
							for (File file : files) {
								if (file != base)
									command.add(file.getPath());
							}
							String output = run(command.toArray(new String[0]));
							int exit = Integer.parseInt(output.substring(0, output.indexOf('\n')));
							// its output is a whole ATL startup log, only worth reading when it failed
							if (exit == 0 || exit == INSTALL_EXIT_CANCELLED)
								Slog.i(TAG, "android-translation-layer --install exited with " + exit);
							else
								Slog.w(TAG, "android-translation-layer --install exited with " + output);
							if (exit == INSTALL_EXIT_CANCELLED) {
								status = STATUS_FAILURE_ABORTED;
								message = "INSTALL_FAILED_ABORTED: User rejected the launcher";
								if (!replacing)
									installed.delete();
							} else if (exit != 0 || !installed.isFile()) {
								status = STATUS_FAILURE;
								message = "INSTALL_FAILED_INTERNAL_ERROR: android-translation-layer --install exited with " + exit;
							} else {
								status = STATUS_SUCCESS;
								ATLLoadedAppManager.forgetPackage(packageName);
								sendPackageBroadcast(Intent.ACTION_PACKAGE_ADDED, packageName, replacing);
							}
						}
					}
				}
			} catch (Exception e) {
				Slog.e(TAG, "install failed", e);
				status = STATUS_FAILURE;
				message = "INSTALL_FAILED_INTERNAL_ERROR: " + e;
			}
			deleteRecursively(dir);
			for (SessionCallback callback : callbacks)
				callback.onFinished(id, status == STATUS_SUCCESS);
			sendStatus(statusReceiver, id, packageName, status, message);
		}

		/* the base APK is the one whose manifest has no split attribute */
		private File findBaseApk(File[] files) {
			for (File file : files) {
				if (splitName(file) == null)
					return file;
			}
			return null;
		}

		private String splitName(File apk) {
			AssetManager assets = new AssetManager();
			assets.addAssetPath(apk.getPath());
			try (XmlResourceParser parser = assets.openXmlResourceParser(1, "AndroidManifest.xml")) {
				int type;
				while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT)
					;
				return type == XmlPullParser.START_TAG ? parser.getAttributeValue(null, "split") : null;
			} catch (Exception e) {
				Slog.w(TAG, "could not read the manifest of " + apk, e);
				return null;
			}
		}

		/* like Android, refuse an update signed by someone else */
		private String checkSignatures(PackageParser.Package pkg) {
			PackageParser.Package installed = ATLLoadedAppManager.getPackageFromPackageName(pkg.packageName);
			if (installed == null || installed.mSignatures == null || installed.mSignatures.length == 0)
				return null;
			if (pkg.mSignatures == null || !new HashSet<>(Arrays.asList(pkg.mSignatures)).equals(new HashSet<>(Arrays.asList(installed.mSignatures))))
				return "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match the installed " + pkg.packageName;
			return null;
		}

		public void abandon() {
			synchronized (sessions) {
				sessions.remove(id);
			}
			deleteRecursively(dir);
			for (SessionCallback callback : callbacks)
				callback.onFinished(id, false);
		}

		@Override
		public void close() {}
	}

	public static class SessionParams {
		public static final int MODE_INVALID = -1;
		public static final int MODE_FULL_INSTALL = 1;
		public static final int MODE_INHERIT_EXISTING = 2;

		public static final int USER_ACTION_UNSPECIFIED = 0;
		public static final int USER_ACTION_REQUIRED = 1;
		public static final int USER_ACTION_NOT_REQUIRED = 2;

		int mode;
		String appPackageName;
		String appLabel;
		Bitmap appIcon;
		long sizeBytes = -1;

		public SessionParams(int mode) {
			this.mode = mode;
		}

		public void setAppPackageName(String appPackageName) {
			this.appPackageName = appPackageName;
		}

		public void setAppLabel(CharSequence appLabel) {
			this.appLabel = appLabel != null ? appLabel.toString() : null;
		}

		public void setAppIcon(Bitmap appIcon) {
			this.appIcon = appIcon;
		}

		public void setSize(long sizeBytes) {
			this.sizeBytes = sizeBytes;
		}

		public void setInstallLocation(int installLocation) {}

		public void setInstallReason(int installReason) {}

		public void setInstallScenario(int installScenario) {}

		public void setRequireUserAction(int requireUserAction) {}

		public void setPackageSource(int packageSource) {}

		public void setRequestUpdateOwnership(boolean enable) {}

		public void setOriginatingUri(Uri originatingUri) {}

		public void setOriginatingUid(int originatingUid) {}

		public void setReferrerUri(Uri referrerUri) {}
	}

	public static class SessionInfo {
		int sessionId;
		String appPackageName;
		String appLabel;
		Bitmap appIcon;
		String installerPackageName;
		int mode;
		long size;
		float progress;
		boolean active;

		public int getSessionId() {
			return sessionId;
		}

		public String getAppPackageName() {
			return appPackageName;
		}

		public CharSequence getAppLabel() {
			return appLabel;
		}

		public Bitmap getAppIcon() {
			return appIcon;
		}

		public String getInstallerPackageName() {
			return installerPackageName;
		}

		public int getMode() {
			return mode;
		}

		public long getSize() {
			return size;
		}

		public float getProgress() {
			return progress;
		}

		public boolean isActive() {
			return active;
		}

		public boolean isSealed() {
			return false;
		}

		public boolean isStaged() {
			return false;
		}
	}

	public abstract static class SessionCallback {
		public abstract void onActiveChanged(int sessionId, boolean active);

		public abstract void onBadgingChanged(int sessionId);

		public abstract void onCreated(int sessionId);

		public abstract void onFinished(int sessionId, boolean success);

		public abstract void onProgressChanged(int sessionId, float progress);
	}
}
