package android.companion;

import android.content.ComponentName;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/* There is no companion device service on Linux: no device is ever associated, and an association
 * request fails like on an Android device without the feature, so apps fall back to plain pairing. */
public final class CompanionDeviceManager {
	public static final int RESULT_OK = -1;
	public static final int RESULT_CANCELED = 0;
	public static final int RESULT_USER_REJECTED = 1;
	public static final int RESULT_DISCOVERY_TIMEOUT = 2;
	public static final int RESULT_INTERNAL_ERROR = 3;
	public static final int RESULT_SECURITY_ERROR = 4;
	public static final String EXTRA_DEVICE = "android.companion.extra.DEVICE";
	public static final String EXTRA_ASSOCIATION = "android.companion.extra.ASSOCIATION";

	private static final String NOT_AVAILABLE = "Companion device pairing is not available";

	public static abstract class Callback {
		public void onDeviceFound(IntentSender intentSender) {}
		public void onAssociationPending(IntentSender intentSender) {}
		public void onAssociationCreated(AssociationInfo associationInfo) {}
		public abstract void onFailure(CharSequence error);
	}

	public List<String> getAssociations() {
		return new ArrayList<>();
	}

	public List<AssociationInfo> getMyAssociations() {
		return new ArrayList<>();
	}

	public void associate(AssociationRequest request, Callback callback, Handler handler) {
		/* Android reports the result later, never from inside associate() */
		(handler != null ? handler : new Handler(Looper.getMainLooper())).post(failure(callback));
	}

	public void associate(AssociationRequest request, Executor executor, Callback callback) {
		executor.execute(failure(callback));
	}

	private static Runnable failure(final Callback callback) {
		return new Runnable() {
			@Override
			public void run() {
				callback.onFailure(NOT_AVAILABLE);
			}
		};
	}

	public void disassociate(String deviceMacAddress) {}

	public void disassociate(int associationId) {}

	public boolean hasNotificationAccess(ComponentName component) {
		return false;
	}

	public void requestNotificationAccess(ComponentName component) {}

	public void startObservingDevicePresence(String deviceAddress) {}

	public void stopObservingDevicePresence(String deviceAddress) {}
}
