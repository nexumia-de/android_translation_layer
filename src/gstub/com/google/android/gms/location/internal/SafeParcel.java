/*
 * Reader and writer for Google's SafeParcelable wire format and calls into
 * binders of the app. The format follows microG's SafeParcelReader and
 * SafeParcelWriter (Apache-2.0, https://github.com/microg/GmsCore).
 */
package com.google.android.gms.location.internal;

import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import java.lang.reflect.Method;
import java.util.List;

final class SafeParcel {
	private static final int OBJECT_HEADER = 0x4f45;

	private SafeParcel() {}

	/* -- reading -- */

	/** Field ids and positions of one object; call field() until it returns -1. */
	static final class Reader {
		private final Parcel p;
		private final int end;
		private int fieldEnd;
		int size;

		Reader(Parcel p) {
			this.p = p;
			int header = p.readInt();
			int size = readSize(header);
			if ((header & 0xffff) != OBJECT_HEADER)
				throw new IllegalArgumentException("not a SafeParcelable: " + Integer.toHexString(header));
			end = p.dataPosition() + size;
			fieldEnd = p.dataPosition();
		}

		private int readSize(int header) {
			return (header & 0xffff0000) == 0xffff0000 ? p.readInt() : header >>> 16;
		}

		/** Next field id, or -1 at the end. Skips what the caller did not read. */
		int field() {
			p.setDataPosition(fieldEnd);
			if (fieldEnd >= end)
				return -1;
			int header = p.readInt();
			size = readSize(header);
			fieldEnd = p.dataPosition() + size;
			return header & 0xffff;
		}

		int readInt() {
			return p.readInt();
		}

		IBinder readBinder() {
			return p.readStrongBinder();
		}

		/** A nested SafeParcelable field. */
		Reader readObject() {
			return new Reader(p);
		}

		void finish() {
			p.setDataPosition(end);
		}
	}

	/** An "in" parcelable argument: null marker, then the object. */
	static Reader readArg(Parcel p) {
		return p.readInt() == 0 ? null : new Reader(p);
	}

	/** Skips an "in" parcelable argument. */
	static void skipArg(Parcel p) {
		Reader r = readArg(p);
		if (r != null)
			r.finish();
	}

	/* -- writing -- */

	static int begin(Parcel p, int id) {
		p.writeInt(0xffff0000 | id);
		p.writeInt(0);
		return p.dataPosition();
	}

	static void end(Parcel p, int start) {
		int end = p.dataPosition();
		p.setDataPosition(start - 4);
		p.writeInt(end - start);
		p.setDataPosition(end);
	}

	static int beginObject(Parcel p) {
		return begin(p, OBJECT_HEADER);
	}

	static void writeInt(Parcel p, int id, int value) {
		p.writeInt((4 << 16) | id);
		p.writeInt(value);
	}

	static void writeLong(Parcel p, int id, long value) {
		p.writeInt((8 << 16) | id);
		p.writeLong(value);
	}

	static void writeBoolean(Parcel p, int id, boolean value) {
		writeInt(p, id, value ? 1 : 0);
	}

	/** com.google.android.gms.common.api.Status */
	static void writeStatus(Parcel p, int statusCode) {
		int o = beginObject(p);
		writeInt(p, 1, statusCode);
		writeInt(p, 1000, 1);
		end(p, o);
	}

	/** A Status as field id of another object. */
	static void writeStatusField(Parcel p, int id, int statusCode) {
		int f = begin(p, id);
		writeStatus(p, statusCode);
		end(p, f);
	}

	/** com.google.android.gms.location.LocationResult */
	static void writeLocationResult(Parcel p, List<Location> locations) {
		int o = beginObject(p);
		int f = begin(p, 1);
		p.writeInt(locations.size());
		for (Location location : locations) {
			int start = p.dataPosition();
			p.writeInt(1);
			int data = p.dataPosition();
			location.writeToParcel(p, 0);
			int end = p.dataPosition();
			p.setDataPosition(start);
			p.writeInt(end - data);
			p.setDataPosition(end);
		}
		end(p, f);
		end(p, o);
	}

	/** com.google.android.gms.location.LocationAvailability */
	static void writeLocationAvailability(Parcel p, boolean available) {
		int o = beginObject(p);
		writeInt(p, 1, available ? 0 : 1000);
		writeInt(p, 2, available ? 0 : 1000);
		writeLong(p, 3, 0);
		writeInt(p, 4, available ? 0 : 1000);
		writeInt(p, 1000, 2);
		end(p, o);
	}

	/** An "in" Location argument. */
	static void writeLocationArg(Parcel p, Location location) {
		if (location == null) {
			p.writeInt(0);
		} else {
			p.writeInt(1);
			location.writeToParcel(p, 0);
		}
	}

	/* -- calling the app -- */

	/**
	 * Calls a binder of the app. ATL's Binder.transact does not dispatch, so a
	 * Binder subclass (an AIDL stub) gets its onTransact called directly.
	 */
	static void call(IBinder binder, int code, Parcel data) {
		if (binder == null)
			return;
		Parcel reply = Parcel.obtain();
		data.setDataPosition(0);
		try {
			if (binder instanceof Binder) {
				Method m = findOnTransact(binder.getClass());
				m.invoke(binder, code, data, reply, 0);
			} else {
				binder.transact(code, data, reply, 0);
			}
		} catch (Exception e) {
			System.err.println("ATL gms: callback " + code + " to " + binder + " failed: " + e);
		}
	}

	private static Method findOnTransact(Class<?> cls) throws NoSuchMethodException {
		for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
			try {
				Method m = c.getDeclaredMethod("onTransact", int.class, Parcel.class, Parcel.class, int.class);
				m.setAccessible(true);
				return m;
			} catch (NoSuchMethodException e) {
			}
		}
		throw new NoSuchMethodException("onTransact in " + cls);
	}
}
