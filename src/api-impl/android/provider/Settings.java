package android.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.AndroidException;
import android.util.Slog;

public class Settings {
	public static class NameValueTable implements BaseColumns {}

	public static final class Secure extends NameValueTable {
		private final static String TAG = "Settings$Secure";
		public static final Uri CONTENT_URI = Uri.parse("content://settings/secure");

		public static Uri getUriFor(String name) {
			return Uri.withAppendedPath(CONTENT_URI, name);
		}

		public static String getString(ContentResolver content_resolver, String key) {
			switch (key) {
				case "android_id":
					return "_totally_an_androidID"; // TODO: is this a good ID? :P
				case "advertising_id":
					return "";
				case "enabled_notification_listeners": {
					if (!android.service.notification.NotificationListenerService.isEnabled())
						return "";
					StringBuilder listeners = new StringBuilder();
					for (android.content.ComponentName component : android.atl.ATLLoadedApp.getPrimaryApplication().getNotificationListeners())
						listeners.append(listeners.length() > 0 ? ":" : "").append(component.flattenToString());
					return listeners.toString();
				}
				/* the input methods ATL knows are the running app's own: one an IME
				 * app runs as (ATLKeyboardViewer) is the current one */
				case "default_input_method":
				case "enabled_input_methods": {
					StringBuilder ids = new StringBuilder();
					for (android.content.pm.PackageParser.Service s : android.atl.ATLLoadedApp.getPrimaryApplication().pkg.services) {
						for (android.content.pm.PackageParser.ServiceIntentInfo filter : s.intents) {
							if (filter.matchAction("android.view.InputMethod")) {
								ids.append(ids.length() > 0 ? ":" : "").append(s.getComponentName().flattenToShortString());
								break;
							}
						}
						if (key.equals("default_input_method") && ids.length() > 0)
							break;
					}
					return ids.toString();
				}
				default:
					Slog.w(TAG, "!!!! getString: unknown key: >" + key + "<");
					return "NOTICEME";
			}
		}

		protected static Integer getIntOrNull(ContentResolver content_resolver, String key) {
			switch (key) {
				case "limit_ad_tracking":
					return 1; // obviously, duh
				case "user_setup_complete":
					return 1;
				case "install_non_market_apps":
					return 1; // apps below SDK 26 check this instead of canRequestPackageInstalls
				default:
					Slog.w(TAG, "!!!! getInt: unknown key: >" + key + "<");
					return null;
			}
		}

		public static int getInt(ContentResolver content_resolver, String key) {
			Integer output = getIntOrNull(content_resolver, key);
			if (output != null) {
				return output.intValue();
			} else {
				return -1; // NOTE: should actually throw a Settings$SettingNotFoundException
			}
		}

		public static int getInt(ContentResolver content_resolver, String key, int def) {
			Integer output = getIntOrNull(content_resolver, key);
			if (output != null) {
				return output.intValue();
			} else {
				return def;
			}
		}

		protected static Float getFloatOrNull(ContentResolver cr, String key) {
			switch (key) {
				default:
					Slog.w(TAG, "!!!! getFloat: unknown key: >" + key + "<");
					return null;
			}
		}

		public static float getFloat(ContentResolver cr, String key) {
			Float output = getFloatOrNull(cr, key);
			if (output != null) {
				return output.floatValue();
			} else {
				return 0.0f; // NOTE: should actually throw a Settings$SettingNotFoundException
			}
		}

		public static float getFloat(ContentResolver cr, String key, float def) {
			Float output = getFloatOrNull(cr, key);
			if (output != null) {
				return output.floatValue();
			} else {
				return def;
			}
		}
	}

	public static final class System extends NameValueTable {
		private final static String TAG = "Settings$System";
		public static final Uri CONTENT_URI = Uri.parse("content://settings/system");

		public static final Uri DEFAULT_NOTIFICATION_URI = getUriFor("notification_sound");

		public static final Uri DEFAULT_RINGTONE_URI = getUriFor("ringtone");

		public static Uri getUriFor(String name) {
			return Uri.withAppendedPath(CONTENT_URI, name);
		}

		protected static Integer getIntOrNull(ContentResolver cr, String key) {
			switch (key) {
				case "accelerometer_rotation":
					return 0; // degrees? no clue
				case "always_finish_activities":
					return 0; // we certainly don't aggressively kill activities :P
				default:
					Slog.w(TAG, "!!!! getInt: unknown key: >" + key + "<");
					return null;
			}
		}

		public static int getInt(ContentResolver content_resolver, String key) {
			Integer output = getIntOrNull(content_resolver, key);
			if (output != null) {
				return output.intValue();
			} else {
				return -1; // NOTE: should actually throw a Settings$SettingNotFoundException
			}
		}

		public static int getInt(ContentResolver content_resolver, String key, int def) {
			Integer output = getIntOrNull(content_resolver, key);
			if (output != null) {
				return output.intValue();
			} else {
				return def;
			}
		}

		protected static Float getFloatOrNull(ContentResolver cr, String key) {
			switch (key) {
				case "font_scale":
					return 1f;
				default:
					Slog.w(TAG, "!!!! getFloat: unknown key: >" + key + "<");
					return null;
			}
		}

		public static float getFloat(ContentResolver cr, String key) {
			Float output = getFloatOrNull(cr, key);
			if (output != null) {
				return output.floatValue();
			} else {
				return 0.0f; // NOTE: should actually throw a Settings$SettingNotFoundException
			}
		}

		public static float getFloat(ContentResolver cr, String key, float def) {
			Float output = getFloatOrNull(cr, key);
			if (output != null) {
				return output.floatValue();
			} else {
				return def;
			}
		}
	}

	public static final class Global extends NameValueTable {
		private final static String TAG = "Settings$Global";
		public static final Uri CONTENT_URI = Uri.parse("content://settings/global");

		public static Uri getUriFor(String name) {
			return Uri.withAppendedPath(CONTENT_URI, name);
		}

		public static String getString(ContentResolver cr, String key) {
			switch (key) {
				default:
					Slog.w(TAG, "!!!! getString: unknown key: >" + key + "<");
					return "STRING_FROM_SETTINGS_GLOBAL_WITH_KEY_" + key;
			}
		}

		protected static Integer getIntOrNull(ContentResolver content_resolver, String key) {
			switch (key) {
				default:
					Slog.w(TAG, "!!!! getInt: unknown key: >" + key + "<");
					return null;
			}
		}

		public static int getInt(ContentResolver content_resolver, String key) {
			Integer output = getIntOrNull(content_resolver, key);
			if (output != null) {
				return output.intValue();
			} else {
				return -1; // NOTE: should actually throw a Settings$SettingNotFoundException
			}
		}

		public static int getInt(ContentResolver content_resolver, String key, int def) {
			Integer output = getIntOrNull(content_resolver, key);
			if (output != null) {
				return output.intValue();
			} else {
				return def;
			}
		}

		protected static Float getFloatOrNull(ContentResolver cr, String key) {
			switch (key) {
				case "animator_duration_scale":
					return 1.f;
				default:
					Slog.w(TAG, "!!!! getFloat: unknown key: >" + key + "<");
					return null;
			}
		}

		public static float getFloat(ContentResolver cr, String key) {
			Float output = getFloatOrNull(cr, key);
			if (output != null) {
				return output.floatValue();
			} else {
				return 0.0f; // NOTE: should actually throw a Settings$SettingNotFoundException
			}
		}

		public static float getFloat(ContentResolver cr, String key, float def) {
			Float output = getFloatOrNull(cr, key);
			if (output != null) {
				return output.floatValue();
			} else {
				return def;
			}
		}
	}

	public static class SettingNotFoundException extends AndroidException {}

	public static boolean canDrawOverlays(Context context) {
		return true;
	}
}
