/*
 * IGoogleLocationManagerService on top of ATL's LocationManager. Transaction
 * codes are microG's IGoogleLocationManagerService.aidl plus one
 * (Apache-2.0, https://github.com/microg/GmsCore). Every call answers at
 * once or through its callback; nothing is left waiting.
 */
package com.google.android.gms.location.internal;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class LocationBinder extends Binder implements LocationListener {
	static final String DESCRIPTOR = "com.google.android.gms.location.internal.IGoogleLocationManagerService";
	private static final String LISTENER = "com.google.android.gms.location.ILocationListener";
	private static final String CALLBACK = "com.google.android.gms.location.ILocationCallback";
	private static final String STATUS_CALLBACK = "com.google.android.gms.location.internal.ILocationStatusCallback";
	private static final String AVAILABILITY_CALLBACK = "com.google.android.gms.location.internal.ILocationAvailabilityStatusCallback";
	private static final String RESULT_CALLBACK = "com.google.android.gms.common.api.internal.IStatusCallback";
	private static final String FUSED_CALLBACK = "com.google.android.gms.location.internal.IFusedLocationProviderCallback";
	private static final String SETTINGS_CALLBACK = "com.google.android.gms.location.internal.ISettingsCallbacks";
	private static final String BOOLEAN_CALLBACK = "com.google.android.gms.location.internal.IBooleanStatusCallback";
	private static final String GEOFENCER_CALLBACK = "com.google.android.gms.location.internal.IGeofencerCallbacks";

	private static final int TYPE_LISTENER = 1;
	private static final int TYPE_CALLBACK = 2;
	private static final int TYPE_STATUS_CALLBACK = 4;
	private static final int TYPE_AVAILABILITY_CALLBACK = 5;

	private static final int SUCCESS = 0;
	private static final int API_NOT_CONNECTED = 17;
	private static final int GEOFENCE_NOT_AVAILABLE = 1000;
	private static final long CURRENT_LOCATION_TIMEOUT_MS = 30000;
	private static final long FRESH_MS = 30000;

	private final Context context;
	private final Handler handler = new Handler(Looper.getMainLooper());
	/* receiver binder -> receiver type; one-shot entries are removed after a fix */
	private final Map<IBinder, Integer> receivers = new HashMap<>();
	private final Map<IBinder, Integer> oneShot = new HashMap<>();
	private LocationManager manager;
	private Location last;

	LocationBinder(Context context) {
		this.context = context;
	}

	@Override
	public String getInterfaceDescriptor() {
		return DESCRIPTOR;
	}

	@Override
	public IInterface queryLocalInterface(String descriptor) {
		return null;
	}

	@Override
	public boolean transact(int code, Parcel data, Parcel reply, int flags) {
		data.setDataPosition(0);
		data.enforceInterface(DESCRIPTOR);
		try {
			handle(code, data, reply);
		} catch (RuntimeException e) {
			System.err.println("ATL gms: location call " + code + " failed: " + e);
			e.printStackTrace();
		}
		if (reply != null)
			reply.setDataPosition(0);
		return true;
	}

	private void handle(int code, Parcel data, Parcel reply) {
		switch (code) {
			case 7: // Location getLastLocation()
			case 21: // getLastLocationWithPackage(String)
			case 80: // getLastLocationWith(String)
				reply.writeNoException();
				SafeParcel.writeLocationArg(reply, lastLocation());
				return;
			case 82: { // getLastLocationWithRequest(LastLocationRequest, ILocationStatusCallback)
				SafeParcel.skipArg(data);
				IBinder cb = data.readStrongBinder();
				reply.writeNoException();
				sendStatusLocation(cb, lastLocation());
				return;
			}
			case 90: { // getLastLocationWithReceiver(LastLocationRequest, LocationReceiver)
				SafeParcel.skipArg(data);
				Receiver r = Receiver.read(data);
				reply.writeNoException();
				if (r != null)
					deliver(r.binder, r.type, lastLocation(), false);
				return;
			}
			case 87: // ICancelToken getCurrentLocation(CurrentLocationRequest, ILocationStatusCallback)
			case 92: { // ICancelToken getCurrentLocationWithReceiver(CurrentLocationRequest, LocationReceiver)
				SafeParcel.skipArg(data);
				IBinder target;
				int type;
				if (code == 87) {
					target = data.readStrongBinder();
					type = TYPE_STATUS_CALLBACK;
				} else {
					Receiver r = Receiver.read(data);
					target = r == null ? null : r.binder;
					type = r == null ? 0 : r.type;
				}
				reply.writeNoException();
				reply.writeStrongBinder(null);
				currentLocation(target, type);
				return;
			}
			case 59: { // updateLocationRequest(LocationRequestUpdateData)
				SafeParcel.Reader r = SafeParcel.readArg(data);
				int op = 0;
				IBinder listener = null, callback = null, fused = null;
				for (int id; r != null && (id = r.field()) != -1;) {
					if (id == 1)
						op = r.readInt();
					else if (id == 3)
						listener = r.readBinder();
					else if (id == 5)
						callback = r.readBinder();
					else if (id == 6)
						fused = r.readBinder();
				}
				reply.writeNoException();
				IBinder target = callback != null ? callback : listener;
				int type = callback != null ? TYPE_CALLBACK : TYPE_LISTENER;
				if (op == 1)
					register(target, type);
				else if (op == 2)
					unregister(target);
				sendFusedResult(fused, SUCCESS);
				return;
			}
			case 88: { // requestLocationUpdatesWithCallback(LocationReceiver, LocationRequest, IStatusCallback)
				Receiver r = Receiver.read(data);
				SafeParcel.skipArg(data);
				IBinder cb = data.readStrongBinder();
				reply.writeNoException();
				if (r != null)
					register(r.binder, r.type);
				sendResult(cb, SUCCESS);
				return;
			}
			case 89: { // removeLocationUpdatesWithCallback(LocationReceiver, IStatusCallback)
				Receiver r = Receiver.read(data);
				IBinder cb = data.readStrongBinder();
				reply.writeNoException();
				if (r != null)
					unregister(r.binder);
				sendResult(cb, SUCCESS);
				return;
			}
			case 8: // requestLocationUpdatesWithListener(LocationRequest, ILocationListener)
			case 20: { // requestLocationUpdatesWithPackage(LocationRequest, ILocationListener, String)
				SafeParcel.skipArg(data);
				IBinder listener = data.readStrongBinder();
				reply.writeNoException();
				register(listener, TYPE_LISTENER);
				return;
			}
			case 10: // removeLocationUpdatesWithListener(ILocationListener)
				unregister(data.readStrongBinder());
				reply.writeNoException();
				return;
			case 34: // LocationAvailability getLocationAvailabilityWithPackage(String)
				reply.writeNoException();
				reply.writeInt(1);
				SafeParcel.writeLocationAvailability(reply, lastLocation() != null);
				return;
			case 91: { // getLocationAvailabilityWithReceiver(LocationAvailabilityRequest, LocationReceiver)
				SafeParcel.skipArg(data);
				Receiver r = Receiver.read(data);
				reply.writeNoException();
				if (r != null && r.type == TYPE_AVAILABILITY_CALLBACK) {
					Parcel p = obtain(AVAILABILITY_CALLBACK);
					p.writeInt(1);
					SafeParcel.writeStatus(p, SUCCESS);
					p.writeInt(1);
					SafeParcel.writeLocationAvailability(p, lastLocation() != null);
					SafeParcel.call(r.binder, 1, p);
				}
				return;
			}
			case 67: // flushLocations(IFusedLocationProviderCallback)
				reply.writeNoException();
				sendFusedResult(data.readStrongBinder(), SUCCESS);
				return;
			case 63: { // requestLocationSettingsDialog(LocationSettingsRequest, ISettingsCallbacks, String)
				SafeParcel.skipArg(data);
				IBinder cb = data.readStrongBinder();
				reply.writeNoException();
				Parcel p = obtain(SETTINGS_CALLBACK);
				p.writeInt(1);
				int o = SafeParcel.beginObject(p); // LocationSettingsResult
				SafeParcel.writeStatusField(p, 1, SUCCESS);
				int f = SafeParcel.begin(p, 2);
				int s = SafeParcel.beginObject(p); // LocationSettingsStates: GPS present and usable
				SafeParcel.writeBoolean(p, 1, true);
				SafeParcel.writeBoolean(p, 2, false);
				SafeParcel.writeBoolean(p, 3, false);
				SafeParcel.writeBoolean(p, 4, true);
				SafeParcel.writeBoolean(p, 5, false);
				SafeParcel.writeBoolean(p, 6, false);
				SafeParcel.writeInt(p, 1000, 2);
				SafeParcel.end(p, s);
				SafeParcel.end(p, f);
				SafeParcel.writeInt(p, 1000, 1);
				SafeParcel.end(p, o);
				SafeParcel.call(cb, 1, p);
				return;
			}
			case 95: { // isGoogleLocationAccuracyEnabled(IBooleanStatusCallback)
				IBinder cb = data.readStrongBinder();
				reply.writeNoException();
				Parcel p = obtain(BOOLEAN_CALLBACK);
				p.writeInt(1);
				SafeParcel.writeStatus(p, SUCCESS);
				p.writeInt(0);
				SafeParcel.call(cb, 1, p);
				return;
			}
			case 12: // setMockMode(boolean)
			case 13: // setMockLocation(Location)
				reply.writeNoException();
				return;
			case 84: // setMockModeWithCallback(boolean, IStatusCallback)
				data.readInt();
				reply.writeNoException();
				sendResult(data.readStrongBinder(), SUCCESS);
				return;
			case 85: // setMockLocationWithCallback(Location, IStatusCallback)
				if (data.readInt() != 0)
					Location.CREATOR.createFromParcel(data);
				reply.writeNoException();
				sendResult(data.readStrongBinder(), SUCCESS);
				return;
			case 97: // addGeofencesWithCallback(GeofencingRequest, PendingIntent, IStatusCallback)
				SafeParcel.skipArg(data);
				skipPendingIntent(data);
				reply.writeNoException();
				sendResult(data.readStrongBinder(), GEOFENCE_NOT_AVAILABLE);
				return;
			case 98: // removeGeofencesWithCallback(RemoveGeofencingRequest, IStatusCallback)
				SafeParcel.skipArg(data);
				reply.writeNoException();
				sendResult(data.readStrongBinder(), GEOFENCE_NOT_AVAILABLE);
				return;
			case 57: { // addGeofences(GeofencingRequest, PendingIntent, IGeofencerCallbacks)
				SafeParcel.skipArg(data);
				skipPendingIntent(data);
				IBinder cb = data.readStrongBinder();
				reply.writeNoException();
				Parcel p = obtain(GEOFENCER_CALLBACK);
				p.writeInt(GEOFENCE_NOT_AVAILABLE);
				p.writeStringArray(new String[0]);
				SafeParcel.call(cb, 1, p);
				return;
			}
			default:
				// Activity recognition, sleep, orientation and the rest: not provided.
				System.err.println("ATL gms: location call " + code + " not provided");
				reply.writeNoException();
		}
	}

	private static void skipPendingIntent(Parcel data) {
		if (data.readInt() != 0)
			data.readStrongBinder();
	}

	/* -- LocationManager -- */

	private synchronized Location lastLocation() {
		if (last == null) {
			LocationManager lm = manager();
			if (lm != null)
				last = lm.getLastKnownLocation(lm.getBestProvider(null, true));
		}
		return last;
	}

	private LocationManager manager() {
		if (manager == null)
			manager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		return manager;
	}

	private synchronized void ensureUpdates() {
		LocationManager lm = manager();
		if (lm != null && (!receivers.isEmpty() || !oneShot.isEmpty()))
			lm.requestLocationUpdates(lm.getBestProvider(null, true), 1000, 0, this);
	}

	@Override
	public void onLocationChanged(Location location) {
		Map<IBinder, Integer> all = new HashMap<>();
		Map<IBinder, Integer> once;
		synchronized (this) {
			last = location;
			all.putAll(receivers);
			once = new HashMap<>(oneShot);
			oneShot.clear();
		}
		for (Map.Entry<IBinder, Integer> e : all.entrySet())
			deliver(e.getKey(), e.getValue(), location, true);
		for (Map.Entry<IBinder, Integer> e : once.entrySet())
			deliver(e.getKey(), e.getValue(), location, false);
	}

	private synchronized void register(IBinder target, int type) {
		if (target == null)
			return;
		receivers.put(target, type);
		ensureUpdates();
	}

	private synchronized void unregister(IBinder target) {
		if (target != null)
			receivers.remove(target);
	}

	private void currentLocation(final IBinder target, final int type) {
		if (target == null)
			return;
		Location location = lastLocation();
		if (location != null && System.currentTimeMillis() - location.getTime() < FRESH_MS) {
			deliver(target, type, location, false);
			return;
		}
		synchronized (this) {
			oneShot.put(target, type);
		}
		ensureUpdates();
		handler.postDelayed(new Runnable() {
			@Override
			public void run() {
				boolean pending;
				synchronized (LocationBinder.this) {
					pending = oneShot.remove(target) != null;
				}
				if (pending) // no fix in time: answer "no location" like Google Play services
					deliver(target, type, null, false);
			}
		}, CURRENT_LOCATION_TIMEOUT_MS);
	}

	/* -- callbacks -- */

	private static Parcel obtain(String descriptor) {
		Parcel p = Parcel.obtain();
		p.writeInterfaceToken(descriptor);
		return p;
	}

	private void deliver(IBinder target, int type, Location location, boolean update) {
		if (type == TYPE_STATUS_CALLBACK) {
			sendStatusLocation(target, location);
		} else if (type == TYPE_CALLBACK) {
			if (location == null)
				return;
			Parcel p = obtain(CALLBACK);
			p.writeInt(1);
			SafeParcel.writeLocationResult(p, Collections.singletonList(location));
			SafeParcel.call(target, 1, p); // onLocationResult
		} else if (type == TYPE_LISTENER) {
			if (location == null)
				return;
			Parcel p = obtain(LISTENER);
			SafeParcel.writeLocationArg(p, location);
			SafeParcel.call(target, 1, p); // onLocationChanged
		}
	}

	private static void sendStatusLocation(IBinder cb, Location location) {
		Parcel p = obtain(STATUS_CALLBACK);
		p.writeInt(1);
		SafeParcel.writeStatus(p, SUCCESS);
		SafeParcel.writeLocationArg(p, location);
		SafeParcel.call(cb, 1, p); // onLocationStatus
	}

	private static void sendResult(IBinder cb, int status) {
		Parcel p = obtain(RESULT_CALLBACK);
		p.writeInt(1);
		SafeParcel.writeStatus(p, status);
		SafeParcel.call(cb, 1, p); // onResult
	}

	private static void sendFusedResult(IBinder cb, int status) {
		Parcel p = obtain(FUSED_CALLBACK);
		p.writeInt(1);
		int o = SafeParcel.beginObject(p); // FusedLocationProviderResult
		SafeParcel.writeStatusField(p, 1, status);
		SafeParcel.writeInt(p, 1000, 1);
		SafeParcel.end(p, o);
		SafeParcel.call(cb, 1, p); // onFusedLocationProviderResult
	}

	/** com.google.android.gms.location.internal.LocationReceiver */
	static final class Receiver {
		int type;
		IBinder binder;

		static Receiver read(Parcel data) {
			SafeParcel.Reader r = SafeParcel.readArg(data);
			if (r == null)
				return null;
			Receiver receiver = new Receiver();
			IBinder old = null;
			for (int id; (id = r.field()) != -1;) {
				if (id == 1)
					receiver.type = r.readInt();
				else if (id == 2)
					old = r.readBinder();
				else if (id == 3)
					receiver.binder = r.readBinder();
			}
			r.finish();
			if (receiver.binder == null)
				receiver.binder = old;
			return receiver;
		}
	}
}
