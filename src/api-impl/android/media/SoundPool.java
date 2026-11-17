package android.media;

import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import java.io.IOException;
import java.util.ArrayList;

public class SoundPool {

	private long nativePool;

	public interface OnLoadCompleteListener {
		/**
		 * Called when a sound has completed loading.
		 *
		 * @param soundPool SoundPool object from the load() method
		 * @param soundPool the sample ID of the sound loaded.
		 * @param status the status of the load operation (0 = success)
		 */
		public void onLoadComplete(SoundPool soundPool, int sampleId, int status);
	}

	private OnLoadCompleteListener loadCompleteListener;

	/* A sound's native player (a GStreamer pipeline with its threads) is created on the
	 * first play() and on the main thread; apps load many sounds at once from worker
	 * threads. */
	private final ArrayList<String> paths = new ArrayList<String>();     // index: sample id - 1
	private final ArrayList<Integer> nativeIds = new ArrayList<Integer>(); // -1: not created yet
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private int lastStreamId;

	public SoundPool(int maxStreams, int streamType, int srcQuality) {
		nativePool = native_constructor();
	}

	private int addSample(String path) {
		final int sampleId;
		synchronized (paths) {
			paths.add(path);
			nativeIds.add(-1);
			sampleId = paths.size();
		}
		mainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (loadCompleteListener != null)
					loadCompleteListener.onLoadComplete(SoundPool.this, sampleId, 0);
			}
		});
		return sampleId;
	}

	public int load(AssetFileDescriptor afd, int priority) throws IOException {
		String packageCodePath = ATLLoadedApp.getPrimaryApplication().pkg.applicationInfo.sourceDir;
		AssetManager.extractFromAPK(packageCodePath, afd.fileName, afd.fileName);
		return addSample(Environment.getExternalStorageDirectory().getPath() + "/" + afd.fileName);
	}

	public int load(Context context, int resId, int priority) throws IOException {
		String fileName = context.getResources().getString(resId);
		AssetManager.extractFromAPK(context.getPackageCodePath(), fileName, fileName);
		return addSample(Environment.getExternalStorageDirectory().getPath() + "/" + fileName);
	}

	public int load(String path, int priority) {
		return addSample(path);
	}

	public final boolean unload(int soundID) {
		synchronized (paths) {
			if (soundID < 1 || soundID > paths.size() || paths.get(soundID - 1) == null)
				return false;
			paths.set(soundID - 1, null); // the native stream, if any, stays until the process ends
			return true;
		}
	}

	/**
	 * Sets the callback hook for the OnLoadCompleteListener.
	 */
	public void setOnLoadCompleteListener(OnLoadCompleteListener listener) {
		loadCompleteListener = listener;
	}

	public final int play(final int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate) {
		final int streamId;
		synchronized (paths) {
			if (soundID < 1 || soundID > paths.size() || paths.get(soundID - 1) == null)
				return 0;
			streamId = ++lastStreamId;
		}
		Runnable play = new Runnable() {
			@Override
			public void run() {
				int nativeId;
				synchronized (paths) {
					String path = paths.get(soundID - 1);
					if (path == null)
						return;
					nativeId = nativeIds.get(soundID - 1);
					if (nativeId < 0) {
						nativeId = nativeLoad(nativePool, path);
						nativeIds.set(soundID - 1, nativeId);
					}
				}
				nativePlay(nativePool, nativeId);
			}
		};
		if (Looper.myLooper() == Looper.getMainLooper())
			play.run();
		else
			mainHandler.post(play);
		return streamId;
	}

	/* sounds play to their end, at the volume of the output */
	public final void stop(int streamID) {}

	public final void pause(int streamID) {}

	public final void resume(int streamID) {}

	public final void autoPause() {}

	public final void autoResume() {}

	public final void setVolume(int streamID, float leftVolume, float rightVolume) {}

	public final void setLoop(int streamID, int loop) {}

	public final void setRate(int streamID, float rate) {}

	public final void setPriority(int streamID, int priority) {}

	public static class Builder {
		public Builder setAudioAttributes(AudioAttributes attributes) {
			return this;
		}

		public Builder setMaxStreams(int maxStreams) {
			return this;
		}

		public SoundPool build() {
			return new SoundPool(0, 0, 0);
		}
	}

	public final void release() {
		synchronized (paths) {
			for (int i = 0; i < paths.size(); i++)
				paths.set(i, null);
		}
	}

	private static native long native_constructor();
	private static native int nativeLoad(long nativePool, String path);
	public static native int nativePlay(long nativePool, int soundID);
}
