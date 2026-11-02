package android.location;

import android.atl.annotation.Export;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * All providers are backed by the same source: the location portal of
 * xdg-desktop-portal, which gets its positions from GeoClue (GNSS, Wi-Fi,
 * cell towers). Every provider name is accepted, and each location is
 * delivered with the provider name the caller asked for.
 *
 * Location access is off unless ATL_UGLY_ENABLE_LOCATION is set, until ATL
 * can ask the user for permission; then all providers report disabled.
 */
public class LocationManager {

	public static final String GPS_PROVIDER = "gps";
	public static final String NETWORK_PROVIDER = "network";
	public static final String PASSIVE_PROVIDER = "passive";
	public static final String FUSED_PROVIDER = "fused";
	public static final String KEY_LOCATION_CHANGED = "location";
	public static final String KEY_PROVIDER_ENABLED = "providerEnabled";
	public static final String KEY_STATUS_CHANGED = "status";
	public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";
	public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";
	public static final String EXTRA_PROVIDER_NAME = "android.location.extra.PROVIDER_NAME";
	public static final String EXTRA_PROVIDER_ENABLED = "android.location.extra.PROVIDER_ENABLED";
	public static final String EXTRA_LOCATION_ENABLED = "android.location.extra.LOCATION_ENABLED";

	private static final List<String> PROVIDERS = Collections.unmodifiableList(Arrays.asList(GPS_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER, PASSIVE_PROVIDER));

	private static class Registration {
		String provider;
		long minTimeMs;
		float minDistanceM;
		Executor executor;
		Location lastDelivered;
		boolean oneShot;
		LocationListener listener;
		Consumer<Location> consumer;
	}

	/* keyed by listener or consumer, compared by identity like on Android */
	private static final Map<Object, Registration> registrations = new IdentityHashMap<>();
	private static Location lastLocation;
	private static boolean monitorRunning;

	private static boolean locationAllowed() {
		return System.getenv("ATL_UGLY_ENABLE_LOCATION") != null;
	}

	private static Executor executorFor(Looper looper) {
		if (looper == null)
			looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
		final Handler handler = new Handler(looper);
		return new Executor() {
			@Override
			public void execute(Runnable command) {
				handler.post(command);
			}
		};
	}

	public boolean isLocationEnabled() {
		return locationAllowed();
	}

	public boolean isProviderEnabled(String provider) {
		if (provider == null)
			throw new IllegalArgumentException("invalid provider: null");
		return locationAllowed();
	}

	public List<String> getAllProviders() {
		return PROVIDERS;
	}

	public List<String> getProviders(boolean enabledOnly) {
		return (enabledOnly && !locationAllowed()) ? Collections.emptyList() : PROVIDERS;
	}

	public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
		return getProviders(enabledOnly);
	}

	public String getBestProvider(Criteria criteria, boolean enabledOnly) {
		return (enabledOnly && !locationAllowed()) ? null : FUSED_PROVIDER;
	}

	public boolean hasProvider(String provider) {
		return PROVIDERS.contains(provider);
	}

	public Location getLastKnownLocation(String provider) {
		synchronized (registrations) {
			if (lastLocation == null || !locationAllowed())
				return null;
			Location location = new Location(lastLocation);
			location.setProvider(provider);
			return location;
		}
	}

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener) {
		requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, null);
	}

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener, Looper looper) {
		requestLocationUpdates(provider, minTimeMs, minDistanceM, executorFor(looper), listener);
	}

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, Executor executor, LocationListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("invalid null listener");
		Registration r = new Registration();
		r.provider = provider;
		r.minTimeMs = minTimeMs;
		r.minDistanceM = minDistanceM;
		r.executor = executor;
		r.listener = listener;
		register(listener, r);
	}

	public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, LocationListener listener, Looper looper) {
		requestLocationUpdates(FUSED_PROVIDER, minTimeMs, minDistanceM, listener, looper);
	}

	public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
		requestLocationUpdates(provider, 0, 0, listener, looper);
		synchronized (registrations) {
			Registration r = registrations.get(listener);
			if (r != null)
				r.oneShot = true;
		}
	}

	public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
		requestSingleUpdate(FUSED_PROVIDER, listener, looper);
	}

	public void getCurrentLocation(String provider, CancellationSignal cancellationSignal, Executor executor, final Consumer<Location> consumer) {
		Registration r = new Registration();
		r.provider = provider;
		r.executor = executor;
		r.consumer = consumer;
		r.oneShot = true;
		if (cancellationSignal != null)
			cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
				@Override
				public void onCancel() {
					unregister(consumer);
				}
			});
		register(consumer, r);
	}

	public void removeUpdates(LocationListener listener) {
		unregister(listener);
	}

	private static void register(Object key, Registration r) {
		synchronized (registrations) {
			registrations.put(key, r);
			if (!locationAllowed())
				return;
			if (!monitorRunning) {
				monitorRunning = true;
				nativeStartMonitor();
			}
			/* hand out a recent position right away, as Android's providers do when they have one */
			if (lastLocation != null && SystemClock.elapsedRealtime() - lastLocation.getElapsedRealtimeMillis() < 30000)
				deliver(key, r, lastLocation);
		}
	}

	private static void unregister(Object key) {
		synchronized (registrations) {
			registrations.remove(key);
			if (registrations.isEmpty() && monitorRunning) {
				monitorRunning = false;
				nativeStopMonitor();
			}
		}
	}

	/* called with the registrations lock held */
	private static void deliver(final Object key, final Registration r, Location source) {
		if (r.lastDelivered != null) {
			if (source.getElapsedRealtimeMillis() - r.lastDelivered.getElapsedRealtimeMillis() < r.minTimeMs)
				return;
			if (r.minDistanceM > 0 && source.distanceTo(r.lastDelivered) < r.minDistanceM)
				return;
		}
		final Location location = new Location(source);
		location.setProvider(r.provider);
		r.lastDelivered = location;
		if (r.oneShot)
			registrations.remove(key);
		r.executor.execute(new Runnable() {
			@Override
			public void run() {
				synchronized (registrations) {
					/* removeUpdates() may have run since this was queued */
					if (!r.oneShot && registrations.get(key) != r)
						return;
				}
				if (r.listener != null)
					r.listener.onLocationChanged(location);
				else
					r.consumer.accept(location);
			}
		});
	}

	private static native void nativeStartMonitor();
	private static native void nativeStopMonitor();

	@Export
	private static void locationUpdated(double latitude, double longitude, double altitude, double accuracy,
	                                    double speed, double bearing, long timestampMs) {
		Location location = new Location(FUSED_PROVIDER);
		location.setLatitude(latitude);
		location.setLongitude(longitude);
		/* the portal reports unknown values as -G_MAXDOUBLE */
		if (altitude > -Double.MAX_VALUE)
			location.setAltitude(altitude);
		if (accuracy >= 0)
			location.setAccuracy((float)accuracy);
		if (speed >= 0)
			location.setSpeed((float)speed);
		if (bearing >= 0)
			location.setBearing((float)bearing);
		location.setTime(timestampMs != 0 ? timestampMs : System.currentTimeMillis());
		location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		synchronized (registrations) {
			lastLocation = location;
			for (Map.Entry<Object, Registration> e : new ArrayList<>(registrations.entrySet()))
				deliver(e.getKey(), e.getValue(), location);
			if (registrations.isEmpty() && monitorRunning) {
				monitorRunning = false;
				nativeStopMonitor();
			}
		}
	}

	public boolean sendExtraCommand(String provider, String command, Bundle extras) {
		return true;
	}

	public GpsStatus getGpsStatus(GpsStatus status) {
		if (status == null) {
			status = new GpsStatus();
		}
		return status;
	}

	public boolean registerGnssStatusCallback(GnssStatus.Callback callback, Handler handler) {
		new Handler(Looper.getMainLooper()).post(new Runnable() {
			@Override
			public void run() {
				callback.onSatelliteStatusChanged(new GnssStatus());
			}
		});
		return true;
	}

	public boolean registerGnssStatusCallback(GnssStatus.Callback callback) {
		return registerGnssStatusCallback(callback, null);
	}

	public boolean registerGnssStatusCallback(Executor executor, GnssStatus.Callback callback) {
		return registerGnssStatusCallback(callback, null);
	}

	public void unregisterGnssStatusCallback(GnssStatus.Callback callback) {
	}

	/* GeoClue does not report satellites, so there are no GPS status events */
	public boolean addGpsStatusListener(GpsStatus.Listener listener) {
		return true;
	}

	public void removeGpsStatusListener(GpsStatus.Listener listener) {
	}
}
