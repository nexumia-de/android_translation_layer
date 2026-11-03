/*
 * Local fused location for apps that use Google Play services, on top of
 * ATL's LocationManager. Nothing is sent to any server. Transaction codes
 * and parcels follow microG's IGmsServiceBroker and
 * IGoogleLocationManagerService (Apache-2.0, https://github.com/microg/GmsCore).
 */
package com.google.android.gms.location.internal;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;

public class GoogleLocationManagerService extends Service {
	static final String BROKER = "com.google.android.gms.common.internal.IGmsServiceBroker";
	static final String CALLBACKS = "com.google.android.gms.common.internal.IGmsCallbacks";
	static final int SERVICE_LOCATION_MANAGER = 23;
	static final int API_UNAVAILABLE = 16;

	private LocationBinder location;

	@Override
	public IBinder onBind(Intent intent) {
		if (location == null)
			location = new LocationBinder(this);
		return new Broker();
	}

	/** IGmsServiceBroker: hands out the location service, refuses everything else at once. */
	private class Broker extends Binder {
		@Override
		public String getInterfaceDescriptor() {
			return BROKER;
		}

		@Override
		public IInterface queryLocalInterface(String descriptor) {
			return null;
		}

		@Override
		public boolean transact(int code, Parcel data, Parcel reply, int flags) {
			data.setDataPosition(0);
			data.enforceInterface(BROKER);
			IBinder callbacks = data.readStrongBinder();
			int serviceId = -1;
			if (code == 46) { // getService(IGmsCallbacks, GetServiceRequest)
				SafeParcel.Reader r = SafeParcel.readArg(data);
				for (int id; r != null && (id = r.field()) != -1;)
					if (id == 2)
						serviceId = r.readInt();
			} else if (code == 8) { // getGoogleLocationManagerService (old clients)
				serviceId = SERVICE_LOCATION_MANAGER;
			}
			boolean ok = serviceId == SERVICE_LOCATION_MANAGER;
			System.err.println("ATL gms: broker call " + code + " for service " + serviceId + (ok ? "" : ", refused"));
			Parcel cb = Parcel.obtain();
			cb.writeInterfaceToken(CALLBACKS);
			cb.writeInt(ok ? 0 : API_UNAVAILABLE);
			cb.writeStrongBinder(ok ? location : null);
			cb.writeInt(0); // no Bundle
			SafeParcel.call(callbacks, 1, cb); // onPostInitComplete
			if (reply != null) {
				reply.writeNoException();
				reply.setDataPosition(0);
			}
			return true;
		}
	}
}
