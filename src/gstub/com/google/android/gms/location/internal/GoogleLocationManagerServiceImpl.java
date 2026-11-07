package com.google.android.gms.location.internal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;

/*
 * Fused location for the GMS client library, on ATL's LocationManager, like
 * microG's. The broker answers the client's getService; the location binder
 * implements the methods the client uses when the service names no features:
 * getLastLocation, getLocationAvailabilityWithPackage and
 * updateLocationRequest. Parcelables of the client library are SafeParcels,
 * read and written here by hand.
 */
public class GoogleLocationManagerServiceImpl extends Service {
	private static final String TAG = "GmsLocation";
	static final String BROKER = "com.google.android.gms.common.internal.IGmsServiceBroker";
	static final String CALLBACKS = "com.google.android.gms.common.internal.IGmsCallbacks";
	static final String SERVICE = "com.google.android.gms.location.internal.IGoogleLocationManagerService";
	static final String LISTENER = "com.google.android.gms.location.ILocationListener";
	static final String CALLBACK = "com.google.android.gms.location.ILocationCallback";
	static final String FUSED_CALLBACK = "com.google.android.gms.location.internal.IFusedLocationProviderCallback";

	/* transaction codes: AIDL ids + 1 */
	private static final int GET_SERVICE = 46;
	private static final int GET_LAST_LOCATION = 7;
	private static final int GET_LAST_LOCATION_WITH_PACKAGE = 21;
	private static final int GET_LOCATION_AVAILABILITY = 34;
	private static final int UPDATE_LOCATION_REQUEST = 59;

	private static final int SAFE_PARCEL_MAGIC = 0x4f45;
	private static final int PRIORITY_HIGH_ACCURACY = 100;

	/* one request per listener or callback binder, as the client sends it */
	private static class Request implements LocationListener {
		IBinder listener;
		IBinder callback;
		int remaining;
		LocationManager lm;

		@Override
		public void onLocationChanged(Location location) {
			deliver(this, location);
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {}

		@Override
		public void onProviderEnabled(String provider) {}

		@Override
		public void onProviderDisabled(String provider) {}
	}

	private static final ArrayList<Request> requests = new ArrayList<>();

	private LocationManager locationManager() {
		return (LocationManager)getSystemService(Context.LOCATION_SERVICE);
	}

	private Location lastLocation() {
		try {
			return locationManager().getLastKnownLocation(LocationManager.FUSED_PROVIDER);
		} catch (RuntimeException e) {
			return null;
		}
	}

	private final Binder location = new Binder() {
		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
			if (data != null)
				data.enforceInterface(SERVICE);
			switch (code) {
				case GET_LAST_LOCATION:
				case GET_LAST_LOCATION_WITH_PACKAGE: {
					Location l = lastLocation();
					reply.writeNoException();
					if (l == null) {
						reply.writeInt(0);
					} else {
						reply.writeInt(1);
						l.writeToParcel(reply, 0);
					}
					return true;
				}
				case GET_LOCATION_AVAILABILITY:
					reply.writeNoException();
					reply.writeInt(1);
					writeAvailability(reply, locationAllowed());
					return true;
				case UPDATE_LOCATION_REQUEST:
					updateLocationRequest(data);
					if (reply != null)
						reply.writeNoException();
					return true;
				default:
					Log.w(TAG, "not implemented: transaction " + code);
					if (reply != null)
						reply.writeNoException();
					return true;
			}
		}
	};

	private static boolean locationAllowed() {
		return LocationManager.atlLocationAllowed();
	}

	private void updateLocationRequest(Parcel data) {
		if (data.readInt() == 0)
			return;
		int end = beginRead(data);
		int opCode = 0;
		int priority = PRIORITY_HIGH_ACCURACY;
		long interval = 5000;
		int maxUpdates = Integer.MAX_VALUE;
		IBinder listener = null, callback = null, fusedCallback = null;
		while (data.dataPosition() < end) {
			int header = data.readInt();
			int size = fieldSize(data, header);
			int next = data.dataPosition() + size;
			switch (header & 0xffff) {
				case 1:
					opCode = data.readInt();
					break;
				case 2: { /* LocationRequestInternal, field 1 is the LocationRequest */
					int innerEnd = beginRead(data);
					while (data.dataPosition() < innerEnd) {
						int h = data.readInt();
						int s = fieldSize(data, h);
						int n = data.dataPosition() + s;
						if ((h & 0xffff) == 1) {
							int reqEnd = beginRead(data);
							while (data.dataPosition() < reqEnd) {
								int rh = data.readInt();
								int rs = fieldSize(data, rh);
								int rn = data.dataPosition() + rs;
								if ((rh & 0xffff) == 1)
									priority = data.readInt();
								else if ((rh & 0xffff) == 2)
									interval = data.readLong();
								else if ((rh & 0xffff) == 6)
									maxUpdates = data.readInt();
								data.setDataPosition(rn);
							}
						}
						data.setDataPosition(n);
					}
					break;
				}
				case 3:
					listener = data.readStrongBinder();
					break;
				case 5:
					callback = data.readStrongBinder();
					break;
				case 6:
					fusedCallback = data.readStrongBinder();
					break;
			}
			data.setDataPosition(next);
		}
		data.setDataPosition(end);

		IBinder key = listener != null ? listener : callback;
		if (key != null) {
			synchronized (requests) {
				Request old = find(key);
				if (old != null) {
					requests.remove(old);
					old.lm.removeUpdates(old);
				}
				if (opCode == 1) {
					Request r = new Request();
					r.listener = listener;
					r.callback = callback;
					r.remaining = maxUpdates > 0 ? maxUpdates : Integer.MAX_VALUE;
					r.lm = locationManager();
					requests.add(r);
					/* power and low power requests still get positions; ATL asks the portal for its accuracy */
					r.lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, Math.max(interval, 0), 0, r, Looper.getMainLooper());
					Log.i(TAG, "location updates, priority " + priority + ", every " + interval + " ms");
				}
			}
		}
		if (fusedCallback != null) {
			Parcel p = Parcel.obtain();
			p.writeInterfaceToken(FUSED_CALLBACK);
			p.writeInt(1);
			int start = beginWrite(p);
			int f = beginField(p, 1);
			writeStatus(p, 0);
			endField(p, f);
			writeIntField(p, 1000, 1);
			endWrite(p, start);
			transact(fusedCallback, 1, p);
		}
	}

	private static Request find(IBinder key) {
		for (Request r : requests)
			if (r.listener == key || r.callback == key)
				return r;
		return null;
	}

	private static void deliver(Request r, Location l) {
		synchronized (requests) {
			if (!requests.contains(r))
				return;
			if (--r.remaining <= 0) {
				requests.remove(r);
				r.lm.removeUpdates(r);
			}
		}
		if (r.listener != null) {
			Parcel p = Parcel.obtain();
			p.writeInterfaceToken(LISTENER);
			p.writeInt(1);
			l.writeToParcel(p, 0);
			transact(r.listener, 1, p);
		}
		if (r.callback != null) {
			Parcel p = Parcel.obtain();
			p.writeInterfaceToken(CALLBACK);
			p.writeInt(1);
			int start = beginWrite(p);
			int f = beginField(p, 1);
			p.writeInt(1); /* list of one Location */
			int before = p.dataPosition();
			p.writeInt(0);
			int itemStart = p.dataPosition();
			l.writeToParcel(p, 0);
			int itemEnd = p.dataPosition();
			p.setDataPosition(before);
			p.writeInt(itemEnd - itemStart);
			p.setDataPosition(itemEnd);
			endField(p, f);
			writeIntField(p, 1000, 2);
			endWrite(p, start);
			transact(r.callback, 1, p);
		}
	}

	private static void transact(IBinder binder, int code, Parcel p) {
		try {
			binder.transact(code, p, Parcel.obtain(), 1 /* FLAG_ONEWAY */);
		} catch (RemoteException | RuntimeException e) {
			Log.w(TAG, "callback failed", e);
		}
	}

	/* --- SafeParcel --- */

	private static int beginRead(Parcel p) {
		int header = p.readInt();
		if ((header & 0xffff) != SAFE_PARCEL_MAGIC)
			throw new IllegalArgumentException("not a SafeParcel: " + Integer.toHexString(header));
		int size = fieldSize(p, header);
		return p.dataPosition() + size;
	}

	private static int fieldSize(Parcel p, int header) {
		return (header & 0xffff0000) != 0xffff0000 ? (header >>> 16) & 0xffff : p.readInt();
	}

	private static int beginWrite(Parcel p) {
		return beginField(p, SAFE_PARCEL_MAGIC);
	}

	private static void endWrite(Parcel p, int start) {
		endField(p, start);
	}

	private static int beginField(Parcel p, int id) {
		p.writeInt(0xffff0000 | id);
		p.writeInt(0);
		return p.dataPosition();
	}

	private static void endField(Parcel p, int start) {
		int end = p.dataPosition();
		p.setDataPosition(start - 4);
		p.writeInt(end - start);
		p.setDataPosition(end);
	}

	private static void writeIntField(Parcel p, int id, int value) {
		p.writeInt((4 << 16) | id);
		p.writeInt(value);
	}

	private static void writeLongField(Parcel p, int id, long value) {
		p.writeInt((8 << 16) | id);
		p.writeLong(value);
	}

	private static void writeStatus(Parcel p, int statusCode) {
		int start = beginWrite(p);
		writeIntField(p, 1, statusCode);
		writeIntField(p, 1000, 1);
		endWrite(p, start);
	}

	private static void writeAvailability(Parcel p, boolean available) {
		int start = beginWrite(p);
		writeIntField(p, 1, 1); /* cell status: unknown */
		writeIntField(p, 2, 1); /* wifi status: unknown */
		writeLongField(p, 3, SystemClock.elapsedRealtimeNanos());
		writeIntField(p, 4, available ? 0 : 1000);
		writeIntField(p, 1000, 2);
		endWrite(p, start);
	}

	private final Binder broker = new Binder() {
		@Override
		protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
			if (code != GET_SERVICE)
				return false;
			data.enforceInterface(BROKER);
			IBinder callbacks = data.readStrongBinder();
			if (reply != null)
				reply.writeNoException();
			Parcel p = Parcel.obtain();
			p.writeInterfaceToken(CALLBACKS);
			p.writeInt(0); /* SUCCESS */
			p.writeStrongBinder(location);
			p.writeInt(0); /* no Bundle */
			callbacks.transact(1, p, Parcel.obtain(), 0); /* onPostInitComplete */
			return true;
		}
	};

	{
		/* the client checks the descriptor; no local owner, so it talks through transact */
		location.attachInterface(null, SERVICE);
		broker.attachInterface(null, BROKER);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return broker;
	}
}
