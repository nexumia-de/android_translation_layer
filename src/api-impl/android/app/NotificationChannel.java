package android.app;

import android.media.AudioAttributes;
import android.net.Uri;

public class NotificationChannel {

	private final String id;
	private CharSequence name;
	private int importance;
	private String description;
	private String group;
	private int lockscreenVisibility;
	private boolean showBadge = true;
	private boolean lights;
	private int lightColor;
	private long[] vibrationPattern;
	private boolean vibration;
	private Uri sound;
	private AudioAttributes audioAttributes;
	private boolean bypassDnd;

	public NotificationChannel(String id, CharSequence name, int importance) {
		this.id = id;
		this.name = name;
		this.importance = importance;
	}

	public String getId() { return id; }
	public CharSequence getName() { return name; }
	public void setName(CharSequence name) { this.name = name; }
	public int getImportance() { return importance; }
	public void setImportance(int importance) { this.importance = importance; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public String getGroup() { return group; }
	public void setGroup(String group) { this.group = group; }
	public int getLockscreenVisibility() { return lockscreenVisibility; }
	public void setLockscreenVisibility(int visibility) { this.lockscreenVisibility = visibility; }
	public boolean canShowBadge() { return showBadge; }
	public void setShowBadge(boolean showBadge) { this.showBadge = showBadge; }
	public boolean shouldShowLights() { return lights; }
	public void enableLights(boolean lights) { this.lights = lights; }
	public int getLightColor() { return lightColor; }
	public void setLightColor(int color) { this.lightColor = color; }
	public long[] getVibrationPattern() { return vibrationPattern; }
	public void setVibrationPattern(long[] pattern) {
		this.vibration = pattern != null && pattern.length > 0;
		this.vibrationPattern = pattern;
	}
	public boolean shouldVibrate() { return vibration; }
	public void enableVibration(boolean vibration) { this.vibration = vibration; }
	public Uri getSound() { return sound; }
	public AudioAttributes getAudioAttributes() { return audioAttributes; }
	public void setSound(Uri sound, AudioAttributes attributes) {
		this.sound = sound;
		this.audioAttributes = attributes;
	}
	public boolean canBypassDnd() { return bypassDnd; }
	public void setBypassDnd(boolean bypassDnd) { this.bypassDnd = bypassDnd; }
}
