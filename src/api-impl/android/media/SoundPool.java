package android.media;

import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import java.io.IOException;

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

	public SoundPool(int maxStreams, int streamType, int srcQuality) {
		nativePool = native_constructor();
	}

	private void notifyLoaded(final int sampleId) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				if (loadCompleteListener != null)
					loadCompleteListener.onLoadComplete(SoundPool.this, sampleId, 0);
			}
		});
	}

	public int load(AssetFileDescriptor afd, int priority) throws IOException {
		String packageCodePath = ATLLoadedApp.getPrimaryApplication().pkg.applicationInfo.sourceDir;
		AssetManager.extractFromAPK(packageCodePath, afd.fileName, afd.fileName);
		int sampleId = nativeLoad(nativePool, Environment.getExternalStorageDirectory().getPath() + "/" + afd.fileName);
		notifyLoaded(sampleId);
		return sampleId;
	}

	public int load(Context context, int resId, int priority) throws IOException {
		String fileName = context.getResources().getString(resId);
		AssetManager.extractFromAPK(context.getPackageCodePath(), fileName, fileName);
		int sampleId = nativeLoad(nativePool, Environment.getExternalStorageDirectory().getPath() + "/" + fileName);
		notifyLoaded(sampleId);
		return sampleId;
	}

	/**
	 * Sets the callback hook for the OnLoadCompleteListener.
	 */
	public void setOnLoadCompleteListener(OnLoadCompleteListener listener) {
		loadCompleteListener = listener;
	}

	public final int play(int soundID, float leftVolume, float rightVolume, int priority, int loop, float rate) {
		return nativePlay(nativePool, soundID);
	}

	public final void stop(int streamID) {} // sounds play to their end

	public void autoResume() {
		System.out.println("WARNING: SoundPool.autoResume not implemented yet");
	}

	public void autoPause() {
		System.out.println("WARNING: SoundPool.autoPause not implemented yet");
	}

	public void setVolume(int streamType, float leftVolume, float rightVolume) {
		System.out.println("WARNING: SoundPool.setVolume not implemented yet");
	}

	public class Builder {
		public Builder setAudioAttributes(AudioAttributes attributes) {
			return this;
		}

		public Builder setMaxStreams(int maxStreams) {
			return this;
		}

		public SoundPool build() {
			return new SoundPool(0, 0, 0); // FIXME
		}
	}

	public void release() {}

	private static native long native_constructor();
	private static native int nativeLoad(long nativePool, String path);
	public static native int nativePlay(long nativePool, int soundID);
}
