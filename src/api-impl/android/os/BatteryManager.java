package android.os;

import android.content.Intent;
import java.io.File;
import java.nio.file.Files;

public class BatteryManager {

	public static final String ACTION_CHARGING = "android.os.action.CHARGING";
	public static final String ACTION_DISCHARGING = "android.os.action.DISCHARGING";

	public static final String EXTRA_STATUS = "status";
	public static final String EXTRA_HEALTH = "health";
	public static final String EXTRA_PRESENT = "present";
	public static final String EXTRA_LEVEL = "level";
	public static final String EXTRA_BATTERY_LOW = "battery_low";
	public static final String EXTRA_SCALE = "scale";
	public static final String EXTRA_ICON_SMALL = "icon-small";
	public static final String EXTRA_PLUGGED = "plugged";
	public static final String EXTRA_VOLTAGE = "voltage";
	public static final String EXTRA_TEMPERATURE = "temperature";
	public static final String EXTRA_TECHNOLOGY = "technology";

	public static final int BATTERY_STATUS_UNKNOWN = 1;
	public static final int BATTERY_STATUS_CHARGING = 2;
	public static final int BATTERY_STATUS_DISCHARGING = 3;
	public static final int BATTERY_STATUS_NOT_CHARGING = 4;
	public static final int BATTERY_STATUS_FULL = 5;

	public static final int BATTERY_HEALTH_UNKNOWN = 1;
	public static final int BATTERY_HEALTH_GOOD = 2;

	public static final int BATTERY_PLUGGED_AC = 1;
	public static final int BATTERY_PLUGGED_USB = 2;
	public static final int BATTERY_PLUGGED_WIRELESS = 4;

	public static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
	public static final int BATTERY_PROPERTY_CURRENT_NOW = 2;
	public static final int BATTERY_PROPERTY_CURRENT_AVERAGE = 3;
	public static final int BATTERY_PROPERTY_CAPACITY = 4;
	public static final int BATTERY_PROPERTY_ENERGY_COUNTER = 5;
	public static final int BATTERY_PROPERTY_STATUS = 6;

	private static final File POWER_SUPPLY = new File("/sys/class/power_supply");

	private static String read(File dir, String name) {
		try {
			return new String(Files.readAllBytes(new File(dir, name).toPath())).trim();
		} catch (Exception e) {
			return null;
		}
	}

	private static int readInt(File dir, String name, int def) {
		String s = read(dir, name);
		try {
			return s == null ? def : Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static File battery() {
		File[] supplies = POWER_SUPPLY.listFiles();
		if (supplies == null)
			return null;
		for (File f : supplies)
			if ("Battery".equals(read(f, "type")) && !"0".equals(read(f, "present")))
				return f;
		return null;
	}

	private static int plugged() {
		int plugged = 0;
		File[] supplies = POWER_SUPPLY.listFiles();
		if (supplies == null)
			return 0;
		for (File f : supplies) {
			String type = read(f, "type");
			if (type == null || "Battery".equals(type) || readInt(f, "online", 0) == 0)
				continue;
			String name = f.getName().toLowerCase();
			if (type.startsWith("USB") || name.contains("usb"))
				plugged |= BATTERY_PLUGGED_USB;
			else if (type.equals("Wireless") || name.contains("wls") || name.contains("wireless"))
				plugged |= BATTERY_PLUGGED_WIRELESS;
			else if (type.equals("Mains"))
				plugged |= BATTERY_PLUGGED_AC;
		}
		return plugged;
	}

	private static int status(File bat) {
		String s = read(bat, "status");
		if (s == null)
			return BATTERY_STATUS_UNKNOWN;
		switch (s) {
			case "Charging":
				return BATTERY_STATUS_CHARGING;
			case "Discharging":
				return BATTERY_STATUS_DISCHARGING;
			case "Not charging":
				return BATTERY_STATUS_NOT_CHARGING;
			case "Full":
				return BATTERY_STATUS_FULL;
			default:
				return BATTERY_STATUS_UNKNOWN;
		}
	}

	/**
	 * The sticky ACTION_BATTERY_CHANGED intent, read from the kernel's power
	 * supply class. Without a battery (desktop) it reports a full battery on AC.
	 * @hide
	 */
	public static Intent getBatteryChangedIntent() {
		Intent intent = new Intent(Intent.ACTION_BATTERY_CHANGED);
		File bat = battery();
		intent.putExtra(EXTRA_SCALE, 100);
		if (bat == null) {
			intent.putExtra(EXTRA_PRESENT, false);
			intent.putExtra(EXTRA_LEVEL, 100);
			intent.putExtra(EXTRA_STATUS, BATTERY_STATUS_UNKNOWN);
			intent.putExtra(EXTRA_HEALTH, BATTERY_HEALTH_UNKNOWN);
			intent.putExtra(EXTRA_PLUGGED, BATTERY_PLUGGED_AC);
			return intent;
		}
		int level = readInt(bat, "capacity", 100);
		intent.putExtra(EXTRA_PRESENT, true);
		intent.putExtra(EXTRA_LEVEL, level);
		intent.putExtra(EXTRA_BATTERY_LOW, level <= 15);
		intent.putExtra(EXTRA_STATUS, status(bat));
		intent.putExtra(EXTRA_HEALTH, BATTERY_HEALTH_GOOD);
		intent.putExtra(EXTRA_PLUGGED, plugged());
		intent.putExtra(EXTRA_VOLTAGE, readInt(bat, "voltage_now", 0) / 1000);
		intent.putExtra(EXTRA_TEMPERATURE, readInt(bat, "temp", 0));
		String tech = read(bat, "technology");
		intent.putExtra(EXTRA_TECHNOLOGY, tech == null ? "" : tech);
		return intent;
	}

	public int getIntProperty(int id) {
		File bat = battery();
		if (bat == null)
			return Integer.MIN_VALUE;
		switch (id) {
			case BATTERY_PROPERTY_CAPACITY:
				return readInt(bat, "capacity", Integer.MIN_VALUE);
			case BATTERY_PROPERTY_STATUS:
				return status(bat);
			case BATTERY_PROPERTY_CHARGE_COUNTER:
				return readInt(bat, "charge_now", Integer.MIN_VALUE);
			case BATTERY_PROPERTY_CURRENT_NOW:
				return readInt(bat, "current_now", Integer.MIN_VALUE);
			case BATTERY_PROPERTY_CURRENT_AVERAGE:
				return readInt(bat, "current_avg", Integer.MIN_VALUE);
			default:
				return Integer.MIN_VALUE;
		}
	}

	public long getLongProperty(int id) {
		if (id == BATTERY_PROPERTY_ENERGY_COUNTER) {
			File bat = battery();
			String s = bat == null ? null : read(bat, "energy_now");
			try {
				return s == null ? Long.MIN_VALUE : Long.parseLong(s) * 1000;
			} catch (NumberFormatException e) {
				return Long.MIN_VALUE;
			}
		}
		return getIntProperty(id);
	}

	public boolean isCharging() {
		File bat = battery();
		int s = bat == null ? BATTERY_STATUS_UNKNOWN : status(bat);
		return s == BATTERY_STATUS_CHARGING || s == BATTERY_STATUS_FULL;
	}
}
