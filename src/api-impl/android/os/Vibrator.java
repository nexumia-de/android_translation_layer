package android.os;

import android.media.AudioAttributes;
import android.util.Slog;

public class Vibrator {
	int fd; // vibrator /dev/input/eventX

	public Vibrator() {
		fd = native_constructor();
	}

	public boolean hasVibrator() {
		return true;
	}

	public void vibrate(long millis) {
		if (fd != -1)
			native_vibrate(fd, millis);
		else
			Slog.v("Vibrator", "vibration motor go burrrr for " + millis + "ms");
	}

	private Thread vibrateThread;

	public void vibrate(final long[] pattern, int repeat) {
		if (repeat < -1 || repeat >= pattern.length)
			throw new ArrayIndexOutOfBoundsException();
		vibrateThread = new Thread(new Runnable() {
			public void run() {
				for (int i = 0; i < pattern.length; i++) {
					if (i % 2 == 0)
						try {
							Thread.sleep(pattern[i]);
						} catch (InterruptedException e) {
							return;
						}
					else
						vibrate(pattern[i]);
				}
			}
		});
		vibrateThread.start();
	}

	public void vibrate(VibrationEffect effect) {
		if (effect.timings.length == 2 && effect.timings[0] == 0 && effect.repeat == -1)
			vibrate(effect.timings[1]);
		else
			vibrate(effect.timings, effect.repeat);
	}

	public void vibrate(VibrationEffect effect, AudioAttributes attributes) {
		vibrate(effect);
	}

	public void cancel() {
		if (vibrateThread != null) {
			vibrateThread.interrupt();
			vibrateThread = null;
		}
	}

	private native void native_vibrate(int fd, long millis);
	private native int native_constructor();
}
