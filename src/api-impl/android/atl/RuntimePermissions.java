package android.atl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Slog;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * ATL-specific: runtime permissions the user decides, stored per app.
 *
 * Only groups ATL can really serve are asked for (location through the
 * location portal, microphone through PipeWire); the others stay denied
 * until they are mapped. A decision is stored in the app's data directory
 * (file atl-permissions, lines "group=granted" or "group=denied") and is not
 * asked again; deleting the line or the file resets it. The environment
 * variables ATL_UGLY_ENABLE_LOCATION and ATL_UGLY_ENABLE_MICROPHONE stay as
 * test overrides that grant without asking.
 */
public final class RuntimePermissions {
	private static final String TAG = "RuntimePermissions";
	public static final String LOCATION = "location";
	public static final String MICROPHONE = "microphone";
	private static final String GRANTED = "granted";
	private static final String DENIED = "denied";

	private static Properties decisions;

	private RuntimePermissions() {}

	/* the group of a runtime permission ATL can ask for, or null */
	public static String groupOf(String permission) {
		if (permission == null)
			return null;
		switch (permission) {
			case "android.permission.ACCESS_FINE_LOCATION":
			case "android.permission.ACCESS_COARSE_LOCATION":
			case "android.permission.ACCESS_BACKGROUND_LOCATION":
				return LOCATION;
			case "android.permission.RECORD_AUDIO":
				return MICROPHONE;
			default:
				return null;
		}
	}

	private static String overrideVariable(String group) {
		return LOCATION.equals(group) ? "ATL_UGLY_ENABLE_LOCATION" : "ATL_UGLY_ENABLE_MICROPHONE";
	}

	private static File file() {
		return new File(Environment.getExternalStorageDirectory(), "atl-permissions");
	}

	private static synchronized Properties decisions() {
		if (decisions == null) {
			decisions = new Properties();
			try (FileInputStream in = new FileInputStream(file())) {
				decisions.load(in);
			} catch (IOException e) {
				/* no decisions yet */
			}
		}
		return decisions;
	}

	private static synchronized void store(String group, boolean granted) {
		decisions().setProperty(group, granted ? GRANTED : DENIED);
		try (FileOutputStream out = new FileOutputStream(file())) {
			decisions.store(out, "ATL runtime permission decisions of this app; delete a line to be asked again");
		} catch (IOException e) {
			Slog.w(TAG, "could not store the decision for " + group + ": " + e);
		}
	}

	/* the decision for a group: true, false, or null when the user was not asked yet */
	public static Boolean decision(String group) {
		if (System.getenv(overrideVariable(group)) != null)
			return Boolean.TRUE;
		String value = decisions().getProperty(group);
		return value == null ? null : Boolean.valueOf(GRANTED.equals(value));
	}

	/* also called from native code (atl_permission_granted) before it uses the device */
	public static boolean isGranted(String group) {
		return Boolean.TRUE.equals(decision(group));
	}

	/**
	 * Activity.requestPermissions: asks once per undecided group, one dialog
	 * after the other, then reports every permission's state.
	 */
	public static void request(final Activity activity, final String[] permissions, final Runnable done) {
		List<String> ask = new ArrayList<>();
		for (String permission : permissions) {
			String group = groupOf(permission);
			if (group != null && decision(group) == null && !ask.contains(group))
				ask.add(group);
		}
		askNext(activity, ask, 0, done);
	}

	private static void askNext(final Activity activity, final List<String> groups, final int index, final Runnable done) {
		if (index >= groups.size()) {
			done.run();
			return;
		}
		final String group = groups.get(index);
		final boolean[] answered = {false};
		final AlertDialog dialog = new AlertDialog(activity);
		/* the label from the app's own resources; loadLabel would parse the framework package */
		android.content.pm.ApplicationInfo info = activity.getApplicationInfo();
		CharSequence label = info.nonLocalizedLabel;
		if (label == null && info.labelRes != 0)
			label = activity.getResources().getText(info.labelRes);
		if (label == null)
			label = activity.getPackageName();
		dialog.setTitle(LOCATION.equals(group) ? "Location" : "Microphone");
		dialog.setMessage("Allow " + label + " to " + (LOCATION.equals(group) ? "access this device's location?" : "record audio?"));
		dialog.setCanceledOnTouchOutside(false);
		DialogInterface.OnClickListener answer = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface d, int which) {
				if (answered[0])
					return;
				answered[0] = true;
				store(group, which == DialogInterface.BUTTON_POSITIVE);
				askNext(activity, groups, index + 1, done);
			}
		};
		dialog.setButton(DialogInterface.BUTTON_POSITIVE, "Allow", answer);
		dialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Don't allow", answer);
		dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface d) {
				/* closed without an answer: denied for this request only, as on Android */
				if (answered[0])
					return;
				answered[0] = true;
				askNext(activity, groups, index + 1, done);
			}
		});
		dialog.show();
	}

	public static int check(String group) {
		return isGranted(group) ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
	}
}
