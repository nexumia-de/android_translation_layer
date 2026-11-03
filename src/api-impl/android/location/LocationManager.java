package android.location;

import android.app.PendingIntent;
import android.atl.ATLLoadedApp;
import android.atl.ATLPaths;
import android.atl.annotation.Export;
import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * All providers are fed by one stream of fixes from the XDG location portal
 * (geoclue behind it). Each registration is filtered by its own minimum
 * interval and distance; the portal session runs while at least one
 * registration exists.
 */
public class LocationManager {

	private static final String TAG = "LocationManager";

	public static final String GPS_PROVIDER = "gps";
	public static final String NETWORK_PROVIDER = "network";
	public static final String PASSIVE_PROVIDER = "passive";
	public static final String FUSED_PROVIDER = "fused";

	public static final String KEY_LOCATION_CHANGED = "location";
	public static final String KEY_LOCATIONS = "locations";
	public static final String KEY_PROVIDER_ENABLED = "providerEnabled";
	public static final String KEY_STATUS_CHANGED = "status";
	public static final String KEY_PROXIMITY_ENTERING = "entering";
	public static final String KEY_FLUSH_COMPLETE = "flushComplete";
	public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";
	public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";
	public static final String EXTRA_PROVIDER_NAME = "android.location.extra.PROVIDER_NAME";
	public static final String EXTRA_PROVIDER_ENABLED = "android.location.extra.PROVIDER_ENABLED";
	public static final String EXTRA_LOCATION_ENABLED = "android.location.extra.LOCATION_ENABLED";

	private static final List<String> PROVIDERS = Collections.unmodifiableList(Arrays.asList(PASSIVE_PROVIDER, GPS_PROVIDER, NETWORK_PROVIDER, FUSED_PROVIDER));

	/* accuracy values of XdpLocationAccuracy */
	private static final int ACCURACY_NEIGHBORHOOD = 3;
	private static final int ACCURACY_EXACT = 5;

	private static class Registration {
		String provider;
		long minTimeMs;
		float minDistanceM;
		int maxUpdates;
		long expireAtRealtime;
		LocationListener listener;
		PendingIntent pendingIntent;
		Executor executor;
		Location lastDelivered;
		/* removed by the app or expired: queued fixes are dropped */
		boolean cancelled;
		/* getCurrentLocation: null on timeout or refusal */
		boolean nullOnExpiry;
	}

	private static final Object lock = new Object();
	private static final List<Registration> registrations = new ArrayList<>();
	private static Location lastLocation;
	private static boolean running;
	private static boolean denied;

	/* On the host only with ATL_UGLY_ENABLE_LOCATION; in a Flatpak the location
	 * portal asks the user, until it refused once in this process. */
	private static boolean enabled() {
		if (System.getenv("ATL_UGLY_ENABLE_LOCATION") != null)
			return true;
		return ATLPaths.in_flatpak && !denied;
	}

	/** @hide whether location is reachable: what checkPermission reports */
	public static boolean atlLocationAllowed() {
		return enabled();
	}

	/* ACCESS_COARSE_LOCATION alone gets about 1 km, like Android's coarse fixes */
	private static int portalAccuracy() {
		try {
			List<String> perms = ATLLoadedApp.getPrimaryApplication().pkg.requestedPermissions;
			if (perms != null && !perms.contains("android.permission.ACCESS_FINE_LOCATION") && perms.contains("android.permission.ACCESS_COARSE_LOCATION"))
				return ACCURACY_NEIGHBORHOOD;
		} catch (RuntimeException e) {
		}
		return ACCURACY_EXACT;
	}

	private static String normalize(String provider) {
		return provider == null ? FUSED_PROVIDER : provider;
	}

	private static Executor looperExecutor(Looper looper) {
		if (looper == null)
			looper = Looper.myLooper();
		if (looper == null)
			looper = Looper.getMainLooper();
		final Handler handler = new Handler(looper);
		return new Executor() {
			@Override
			public void execute(Runnable r) {
				if (!handler.post(r))
					Log.w(TAG, "listener looper is gone");
			}
		};
	}

	private void add(Registration r) {
		r.provider = normalize(r.provider);
		boolean start;
		synchronized (lock) {
			removeLocked(r.listener, r.pendingIntent);
			registrations.add(r);
			start = !running && !PASSIVE_PROVIDER.equals(r.provider);
			if (start)
				running = true;
		}
		if (start && !nativeStart(portalAccuracy())) {
			synchronized (lock) {
				running = false;
			}
		}
		if (r.expireAtRealtime != Long.MAX_VALUE) {
			final Registration reg = r;
			new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
				@Override
				public void run() {
					expire(reg);
				}
			}, Math.max(0, r.expireAtRealtime - SystemClock.elapsedRealtime()));
		}
	}

	/* the duration of a request ran out without (enough) fixes */
	private static void expire(Registration r) {
		synchronized (lock) {
			if (!registrations.remove(r))
				return;
			r.cancelled = true;
		}
		if (r.nullOnExpiry)
			deliverNull(r);
		stopIfUnused();
	}

	private static boolean removeLocked(LocationListener listener, PendingIntent pendingIntent) {
		boolean removed = false;
		for (int i = registrations.size() - 1; i >= 0; i--) {
			Registration r = registrations.get(i);
			if ((listener != null && r.listener == listener) || (pendingIntent != null && pendingIntent.equals(r.pendingIntent))) {
				registrations.remove(i);
				r.cancelled = true;
				removed = true;
			}
		}
		return removed;
	}

	private static void stopIfUnused() {
		boolean stop = false;
		synchronized (lock) {
			if (running) {
				stop = true;
				for (Registration r : registrations)
					if (!PASSIVE_PROVIDER.equals(r.provider))
						stop = false;
				if (stop)
					running = false;
			}
		}
		if (stop)
			nativeStop();
	}

	private Registration registration(String provider, long minTimeMs, float minDistanceM) {
		Registration r = new Registration();
		r.provider = provider;
		r.minTimeMs = Math.max(0, minTimeMs);
		r.minDistanceM = Math.max(0, minDistanceM);
		r.maxUpdates = Integer.MAX_VALUE;
		r.expireAtRealtime = Long.MAX_VALUE;
		return r;
	}

	private static String providerFor(Criteria criteria) {
		return FUSED_PROVIDER;
	}

	/* --- requests with a LocationListener --- */

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener) {
		requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, null);
	}

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener, Looper looper) {
		requestLocationUpdates(provider, minTimeMs, minDistanceM, looperExecutor(looper), listener);
	}

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, Executor executor, LocationListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("invalid null listener");
		Registration r = registration(provider, minTimeMs, minDistanceM);
		r.listener = listener;
		r.executor = executor;
		add(r);
	}

	public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, LocationListener listener, Looper looper) {
		requestLocationUpdates(providerFor(criteria), minTimeMs, minDistanceM, listener, looper);
	}

	public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, Executor executor, LocationListener listener) {
		requestLocationUpdates(providerFor(criteria), minTimeMs, minDistanceM, executor, listener);
	}

	public void requestLocationUpdates(String provider, LocationRequest request, Executor executor, LocationListener listener) {
		if (listener == null)
			throw new IllegalArgumentException("invalid null listener");
		Registration r = fromRequest(provider, request);
		r.listener = listener;
		r.executor = executor;
		add(r);
	}

	/* --- requests with a PendingIntent --- */

	public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, PendingIntent pendingIntent) {
		if (pendingIntent == null)
			throw new IllegalArgumentException("invalid null pending intent");
		Registration r = registration(provider, minTimeMs, minDistanceM);
		r.pendingIntent = pendingIntent;
		r.executor = looperExecutor(Looper.getMainLooper());
		add(r);
	}

	public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, PendingIntent pendingIntent) {
		requestLocationUpdates(providerFor(criteria), minTimeMs, minDistanceM, pendingIntent);
	}

	public void requestLocationUpdates(String provider, LocationRequest request, PendingIntent pendingIntent) {
		if (pendingIntent == null)
			throw new IllegalArgumentException("invalid null pending intent");
		Registration r = fromRequest(provider, request);
		r.pendingIntent = pendingIntent;
		r.executor = looperExecutor(Looper.getMainLooper());
		add(r);
	}

	private Registration fromRequest(String provider, LocationRequest request) {
		Registration r = registration(provider, request.getMinUpdateIntervalMillis(), request.getMinUpdateDistanceMeters());
		r.maxUpdates = request.getMaxUpdates();
		if (request.getDurationMillis() != Long.MAX_VALUE)
			r.expireAtRealtime = SystemClock.elapsedRealtime() + request.getDurationMillis();
		return r;
	}

	/* --- single updates --- */

	public void requestSingleUpdate(String provider, LocationListener listener, Looper looper) {
		Registration r = registration(provider, 0, 0);
		r.listener = listener;
		r.executor = looperExecutor(looper);
		r.maxUpdates = 1;
		add(r);
	}

	public void requestSingleUpdate(Criteria criteria, LocationListener listener, Looper looper) {
		requestSingleUpdate(providerFor(criteria), listener, looper);
	}

	public void requestSingleUpdate(String provider, PendingIntent pendingIntent) {
		Registration r = registration(provider, 0, 0);
		r.pendingIntent = pendingIntent;
		r.executor = looperExecutor(Looper.getMainLooper());
		r.maxUpdates = 1;
		add(r);
	}

	public void requestSingleUpdate(Criteria criteria, PendingIntent pendingIntent) {
		requestSingleUpdate(providerFor(criteria), pendingIntent);
	}

	public void getCurrentLocation(String provider, CancellationSignal cancellationSignal, Executor executor, final Consumer<Location> consumer) {
		final LocationListener listener = new LocationListener() {
			@Override
			public void onLocationChanged(Location location) {
				consumer.accept(location);
			}
		};
		Registration r = registration(provider, 0, 0);
		r.listener = listener;
		r.executor = executor;
		r.maxUpdates = 1;
		r.expireAtRealtime = SystemClock.elapsedRealtime() + 30000;
		r.nullOnExpiry = true;
		if (cancellationSignal != null) {
			cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
				@Override
				public void onCancel() {
					removeUpdates(listener);
				}
			});
		}
		add(r);
	}

	public void getCurrentLocation(String provider, LocationRequest request, CancellationSignal cancellationSignal, Executor executor, Consumer<Location> consumer) {
		getCurrentLocation(provider, cancellationSignal, executor, consumer);
	}

	/* --- removal --- */

	public void removeUpdates(LocationListener listener) {
		synchronized (lock) {
			removeLocked(listener, null);
		}
		stopIfUnused();
	}

	public void removeUpdates(PendingIntent pendingIntent) {
		synchronized (lock) {
			removeLocked(null, pendingIntent);
		}
		stopIfUnused();
	}

	/* --- delivery, called from native on the main thread --- */

	@Export
	private static void locationUpdated(double latitude, double longitude, double altitude, double accuracy,
	                                    double speed, double bearing, long timeMs) {
		Location base = new Location(FUSED_PROVIDER);
		base.setLatitude(latitude);
		base.setLongitude(longitude);
		/* the portal reports -DBL_MAX for unknown altitude and -1 for unknown speed or heading */
		if (altitude != -Double.MAX_VALUE)
			base.setAltitude(altitude);
		base.setAccuracy((float)accuracy);
		if (speed >= 0)
			base.setSpeed((float)speed);
		if (bearing >= 0)
			base.setBearing((float)bearing);
		base.setTime(timeMs);
		base.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

		List<Registration> due = new ArrayList<>();
		long now = SystemClock.elapsedRealtime();
		synchronized (lock) {
			lastLocation = base;
			for (int i = registrations.size() - 1; i >= 0; i--) {
				Registration r = registrations.get(i);
				if (now > r.expireAtRealtime) {
					continue; /* expire() removes it and answers */
				}
				Location last = r.lastDelivered;
				if (last != null) {
					/* like Android, allow a fix slightly early so a 1 s source is not halved */
					long minNanos = r.minTimeMs * 900000L;
					if (base.getElapsedRealtimeNanos() - last.getElapsedRealtimeNanos() < minNanos)
						continue;
					if (r.minDistanceM > 0 && base.distanceTo(last) < r.minDistanceM)
						continue;
				}
				Location l = new Location(base);
				l.setProvider(r.provider);
				r.lastDelivered = l;
				if (--r.maxUpdates <= 0)
					registrations.remove(i);
				due.add(r);
			}
		}
		for (Registration r : due)
			deliver(r, new Location(r.lastDelivered));
		stopIfUnused();
	}

	private static void deliverNull(final Registration r) {
		r.executor.execute(new Runnable() {
			@Override
			public void run() {
				r.listener.onLocationChanged((Location)null);
			}
		});
	}

	@Export
	private static void locationDenied() {
		List<Registration> regs;
		synchronized (lock) {
			denied = true;
			running = false;
			regs = new ArrayList<>(registrations);
		}
		for (final Registration r : regs) {
			if (r.nullOnExpiry) { /* getCurrentLocation: answer null at once */
				synchronized (lock) {
					registrations.remove(r);
					r.cancelled = true;
				}
				deliverNull(r);
				continue;
			}
			if (r.listener == null)
				continue;
			r.executor.execute(new Runnable() {
				@Override
				public void run() {
					r.listener.onProviderDisabled(r.provider);
				}
			});
		}
	}

	private static void deliver(final Registration r, final Location location) {
		r.executor.execute(new Runnable() {
			@Override
			public void run() {
				/* removeUpdates may have run since the fix was queued; a finished
				 * single update is not cancelled and keeps its one fix */
				synchronized (lock) {
					if (r.cancelled)
						return;
				}
				if (r.listener != null) {
					r.listener.onLocationChanged(location);
				} else {
					Intent intent = new Intent();
					intent.putExtra(KEY_LOCATION_CHANGED, location);
					r.pendingIntent.send(null, 0, intent);
				}
			}
		});
	}

	/* --- providers and state --- */

	public Location getLastKnownLocation(String provider) {
		if (!enabled())
			return null;
		synchronized (lock) {
			if (lastLocation == null)
				return null;
			Location l = new Location(lastLocation);
			l.setProvider(normalize(provider));
			return l;
		}
	}

	public String getBestProvider(Criteria criteria, boolean enabledOnly) {
		if (enabledOnly && !enabled())
			return null;
		return FUSED_PROVIDER;
	}

	public List<String> getAllProviders() {
		return PROVIDERS;
	}

	public List<String> getProviders(boolean enabledOnly) {
		if (enabledOnly && !enabled())
			return Collections.emptyList();
		return PROVIDERS;
	}

	public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
		return getProviders(enabledOnly);
	}

	public boolean hasProvider(String provider) {
		return PROVIDERS.contains(provider);
	}

	public boolean isProviderEnabled(String provider) {
		return enabled() && PROVIDERS.contains(provider);
	}

	public boolean isLocationEnabled() {
		return enabled();
	}

	public boolean sendExtraCommand(String provider, String command, Bundle extras) {
		return true;
	}

	/* --- GNSS status: the portal has no satellite data --- */

	public GpsStatus getGpsStatus(GpsStatus status) {
		if (status == null) {
			status = new GpsStatus();
		}
		return status;
	}

	public boolean addGpsStatusListener(GpsStatus.Listener listener) {
		return true;
	}

	public void removeGpsStatusListener(GpsStatus.Listener listener) {}

	public boolean addNmeaListener(GpsStatus.NmeaListener listener) {
		return true;
	}

	public boolean addNmeaListener(OnNmeaMessageListener listener) {
		return true;
	}

	public boolean addNmeaListener(OnNmeaMessageListener listener, Handler handler) {
		return true;
	}

	public boolean addNmeaListener(Executor executor, OnNmeaMessageListener listener) {
		return true;
	}

	public void removeNmeaListener(GpsStatus.NmeaListener listener) {}

	public void removeNmeaListener(OnNmeaMessageListener listener) {}

	public boolean registerGnssStatusCallback(GnssStatus.Callback callback) {
		return registerGnssStatusCallback(callback, null);
	}

	public boolean registerGnssStatusCallback(final GnssStatus.Callback callback, Handler handler) {
		if (handler == null)
			handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
			@Override
			public void run() {
				callback.onSatelliteStatusChanged(new GnssStatus());
			}
		});
		return true;
	}

	public boolean registerGnssStatusCallback(Executor executor, final GnssStatus.Callback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				callback.onSatelliteStatusChanged(new GnssStatus());
			}
		});
		return true;
	}

	public void unregisterGnssStatusCallback(GnssStatus.Callback callback) {}

	private static native boolean nativeStart(int accuracy);
	private static native void nativeStop();
}
