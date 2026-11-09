package android.media;

import android.atl.annotation.Export;
import android.atl.ATLLoadedApp;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Slog;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

public class MediaPlayer {
	/* the GtkMediaFile; created on the first call that needs it, so a player
	 * that is only prepared (sound effects) costs no GStreamer pipeline */
	private long gtk_media_stream;
	private String path;

	public interface OnCompletionListener {
		@Export
		void onCompletion(MediaPlayer media_player);
	}
	public interface OnErrorListener {
	}
	public interface OnPreparedListener {
		void onPrepared(MediaPlayer mp);
	}
	public interface OnBufferingUpdateListener {
	}
	public interface OnInfoListener {
	}
	public interface OnSeekCompleteListener {
	}
	public interface OnVideoSizeChangedListener {
	}
	public interface MediaPlayerControl {
	}

	private OnCompletionListener completionListener;
	private OnPreparedListener preparedListener;
	private boolean looping;
	private float volume = 1f;

	public static MediaPlayer create(Context context, int resId) {
		MediaPlayer player = new MediaPlayer();
		try {
			AssetFileDescriptor afd = context.getResources().openRawResourceFd(resId);
			player.setDataSource(afd);
			afd.close();
			player.prepare();
		} catch (IOException e) {
			Slog.w("MediaPlayer", "create: " + e);
			return null;
		}
		return player;
	}

	public static MediaPlayer create(Context context, int resId, AudioAttributes attributes, int audioSessionId) {
		return create(context, resId);
	}

	public void setDataSource(AssetFileDescriptor afd) throws IOException {
		if (afd.fileName == null) {
			setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
			return;
		}
		String packageCodePath = ATLLoadedApp.getPrimaryApplication().pkg.applicationInfo.sourceDir;
		AssetManager.extractFromAPK(packageCodePath, afd.fileName, afd.fileName);
		setDataSource(Environment.getExternalStorageDirectory().getPath() + "/" + afd.fileName);
	}

	public void setDataSource(FileDescriptor src) throws IOException {
		setDataSource(src, 0, Long.MAX_VALUE);
	}

	/* GtkMediaFile needs a name: copy what the descriptor holds */
	public void setDataSource(FileDescriptor src, long offset, long length) throws IOException {
		File file = File.createTempFile("mediaplayer", null);
		file.deleteOnExit();
		FileInputStream in = new FileInputStream(src); // not closed, the caller owns src
		try (FileOutputStream out = new FileOutputStream(file)) {
			in.getChannel().position(offset);
			byte[] buffer = new byte[65536];
			long left = length;
			int count;
			while (left > 0 && (count = in.read(buffer, 0, (int)Math.min(left, buffer.length))) > 0) {
				out.write(buffer, 0, count);
				left -= count;
			}
		}
		setDataSource(file.getPath());
	}

	public void setDataSource(String path) {
		if (gtk_media_stream != 0)
			native_release(gtk_media_stream);
		gtk_media_stream = 0;
		this.path = path;
	}

	/* returns the GtkMediaFile, created on first use; 0 without a data source */
	private long stream() {
		if (gtk_media_stream == 0) {
			if (path == null) {
				Slog.w("MediaPlayer", "no data source set");
				return 0;
			}
			gtk_media_stream = native_create(path);
			native_setLooping(gtk_media_stream, looping);
			if (completionListener != null)
				native_setOnCompletionListener(gtk_media_stream, completionListener);
		}
		return gtk_media_stream;
	}

	public void setDataSource(Context context, Uri uri) {
		System.out.println("setDataSource(" + uri + ") called");
		setDataSource(uri.getPath());
	}

	public void setDataSource(Context context, Uri uri, Map<String, String> headers) {
		System.out.println("setDataSource(" + uri + ") called");
		setDataSource(uri.getPath());
	}

	public void setLooping(boolean looping) {
		this.looping = looping;
		if (gtk_media_stream != 0)
			native_setLooping(gtk_media_stream, looping);
	}

	public boolean isLooping() { return looping; }

	public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
		completionListener = listener;
		if (gtk_media_stream != 0)
			native_setOnCompletionListener(gtk_media_stream, listener);
	}

	public void setOnErrorListener(MediaPlayer.OnErrorListener dummy) {}
	public void setOnPreparedListener(MediaPlayer.OnPreparedListener listener) { preparedListener = listener; }
	public void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener dummy) {}
	public void setOnInfoListener(MediaPlayer.OnInfoListener dummy) {}
	public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener dummy) {}
	public void setOnVideoSizeChangedListener(MediaPlayer.OnVideoSizeChangedListener dummy) {}
	public void setAudioAttributes(AudioAttributes attributes) {}
	public void setAudioStreamType(int dummy) {}

	public void start() {
		long stream = stream();
		if (stream != 0)
			native_start(stream, volume);
	}

	public void stop() {
		if (gtk_media_stream != 0)
			native_stop(gtk_media_stream);
	}

	public void pause() {
		if (gtk_media_stream != 0)
			native_pause(gtk_media_stream);
	}

	/* the file is opened on the first start(), seekTo() or duration query */
	public void prepare() {}

	public void prepareAsync() {
		stream(); // open the file now, so the duration is known when the app asks
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				if (preparedListener != null)
					preparedListener.onPrepared(MediaPlayer.this);
			}
		});
	}

	public void reset() {
		if (gtk_media_stream != 0)
			native_release(gtk_media_stream);
		gtk_media_stream = 0;
		path = null;
	}

	public void release() {
		reset();
	}

	public boolean isPlaying() {
		return gtk_media_stream != 0 && native_isPlaying(gtk_media_stream);
	}

	public void seekTo(int msec) {
		long stream = stream();
		if (stream != 0)
			native_seekTo(stream, msec);
	}

	public void seekTo(long msec, int mode) {
		seekTo((int)msec);
	}

	public void setScreenOnWhilePlaying(boolean screenOn) {}

	public void setVolume(float leftVolume, float rightVolume) {
		volume = Math.max(leftVolume, rightVolume);
		if (gtk_media_stream != 0)
			native_setVolume(gtk_media_stream, volume);
	}

	public int getDuration() {
		long stream = stream();
		return stream != 0 ? native_getDuration(stream) : 0;
	}

	public int getCurrentPosition() {
		return gtk_media_stream != 0 ? native_getCurrentPosition(gtk_media_stream) : 0;
	}

	public int getAudioSessionId() { return 0; }

	public void setWakeMode(Context context, int mode) {}

	public native long native_create(String path);
	public static native void native_setOnCompletionListener(long gtk_media_stream, MediaPlayer.OnCompletionListener listener);
	public static native void native_start(long gtk_media_stream, float volume);
	public static native void native_pause(long gtk_media_stream);
	public static native void native_stop(long gtk_media_stream);
	public static native void native_seekTo(long gtk_media_stream, int msec);
	public static native void native_setLooping(long gtk_media_stream, boolean looping);
	public static native void native_setVolume(long gtk_media_stream, float volume);
	public static native boolean native_isPlaying(long gtk_media_stream);
	public static native void native_release(long gtk_media_stream);
	public static native int native_getDuration(long gtk_media_stream);
	public static native int native_getCurrentPosition(long gtk_media_stream);
}
