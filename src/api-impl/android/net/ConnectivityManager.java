package android.net;

import android.atl.annotation.Export;
import android.os.Handler;
import android.os.Looper;

class ProxyInfo {}

public class ConnectivityManager {

	public static class NetworkCallback {
		public void onAvailable(Network network) {}

		public void onLost(Network network) {}

		public void onCapabilitiesChanged(Network network, NetworkCapabilities networkCapabilities) {}

		public void onLinkPropertiesChanged(Network network, LinkProperties linkProperties) {}
	}

	/* like Android: onAvailable, then the network's capabilities */
	private static void dispatchAvailable(NetworkCallback callback) {
		ConnectivityManager connectivityManager = new ConnectivityManager();
		Network network = connectivityManager.getActiveNetwork();
		callback.onAvailable(network);
		NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
		if (capabilities != null)
			callback.onCapabilitiesChanged(network, capabilities);
	}

	@Export
	private static void onNetworkChanged(NetworkCallback callback, boolean available) {
		if (available)
			dispatchAvailable(callback);
		else
			callback.onLost(new Network());
	}

	public NetworkInfo getNetworkInfo(int networkType) {
		return new NetworkInfo(nativeGetNetworkAvailable());
	}

	public NetworkInfo getActiveNetworkInfo() {
		return new NetworkInfo(nativeGetNetworkAvailable());
	}

	private native void nativeRegisterNetworkCallback(NetworkCallback callback);

	/* Android calls back right away when a matching network is up; apps wait for that to know they
	 * are online. All requests are treated as matching the one network GLib reports */
	public void registerNetworkCallback(NetworkRequest request, final NetworkCallback callback, Handler handler) {
		nativeRegisterNetworkCallback(callback);
		if (nativeGetNetworkAvailable()) {
			if (handler == null)
				handler = new Handler(Looper.getMainLooper());
			handler.post(new Runnable() {
				public void run() {
					dispatchAvailable(callback);
				}
			});
		}
	}

	public void registerNetworkCallback(NetworkRequest request, NetworkCallback callback) {
		registerNetworkCallback(request, callback, null);
	}

	public void unregisterNetworkCallback(NetworkCallback callback) {}

	public native boolean isActiveNetworkMetered();

	public static final int RESTRICT_BACKGROUND_STATUS_DISABLED = 1;

	// there is no data saver to restrict apps in the background
	public int getRestrictBackgroundStatus() {
		return RESTRICT_BACKGROUND_STATUS_DISABLED;
	}

	protected native boolean nativeGetNetworkAvailable();

	public NetworkInfo[] getAllNetworkInfo() {
		return new NetworkInfo[] {getActiveNetworkInfo()};
	}

	public Network getActiveNetwork() {
		return new Network();
	}

	public Network[] getAllNetworks() {
		return new Network[] {getActiveNetwork()};
	}

	public NetworkCapabilities getNetworkCapabilities(Network network) {
		// as in getActiveNetworkInfo, a null network means there is none to report on
		if (network == null || !nativeGetNetworkAvailable())
			return null;

		NetworkCapabilities capabilities = new NetworkCapabilities();
		capabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
		// WorkManager's network constraint on API 26+ also needs VALIDATED, else it stops running work
		capabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);

		if (!isActiveNetworkMetered())
			capabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);

		capabilities.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

		return capabilities;
	}

	public void registerDefaultNetworkCallback(NetworkCallback cb, Handler hdl) {
		registerNetworkCallback(null, cb, hdl);
	}

	public void registerDefaultNetworkCallback(NetworkCallback cb) {
		registerNetworkCallback(null, cb, null);
	}

	public ProxyInfo getDefaultProxy() { return null; }

	public LinkProperties getLinkProperties(Network network) { return null; }
}
