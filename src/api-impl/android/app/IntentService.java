package android.app;

import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

/* As on Android, the intents are handled one after another on a worker thread, and the service
 * stops itself when the last one is done. ATL called onHandleIntent() on the main thread: Bumble's
 * LexemSyncService then blocked the UI for up to three minutes, waiting for work that needs the
 * main thread */
public abstract class IntentService extends Service {
	private final String name;
	private HandlerThread thread;
	private Handler serviceHandler;
	private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private boolean redelivery;

	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			onHandleIntent((Intent)msg.obj);
			final int startId = msg.arg1;
			/* ATL's service bookkeeping runs on the main thread */
			mainHandler.post(new Runnable() {
				@Override
				public void run() {
					stopSelf(startId);
				}
			});
		}
	}

	public IntentService(String name) {
		this.name = name;
	}

	public void setIntentRedelivery(boolean enabled) {
		redelivery = enabled;
	}

	private synchronized void startThread() {
		if (serviceHandler != null)
			return;
		thread = new HandlerThread("IntentService[" + name + "]");
		thread.start();
		serviceHandler = new ServiceHandler(thread.getLooper());
	}

	@Override
	public void onCreate() {
		super.onCreate();
		startThread();
	}

	@Override
	public void onStart(Intent intent, int startId) {
		/* in case a subclass's onCreate() does not call super.onCreate() */
		startThread();
		Message msg = serviceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		serviceHandler.sendMessage(msg);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		onStart(intent, startId);
		return redelivery ? START_REDELIVER_INTENT : START_NOT_STICKY;
	}

	@Override
	public synchronized void onDestroy() {
		if (thread != null)
			thread.quit();
		thread = null;
		serviceHandler = null;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	protected abstract void onHandleIntent(Intent intent);
}
