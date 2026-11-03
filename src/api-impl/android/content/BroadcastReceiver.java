package android.content;

public abstract class BroadcastReceiver {

	public static class PendingResult {

		public void setResultCode(int resultCode) {}

		public void finish() {}
	}

	public abstract void onReceive(Context context, Intent intent);

	private boolean abortBroadcast;

	public final void abortBroadcast() {
		abortBroadcast = true;
	}

	public final boolean getAbortBroadcast() {
		return abortBroadcast;
	}

	public final void clearAbortBroadcast() {
		abortBroadcast = false;
	}

	public boolean isOrderedBroadcast() {
		return true;
	}

	public PendingResult goAsync() {
		return new PendingResult();
	}
}
