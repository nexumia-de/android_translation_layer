package android.location;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;

public class Location implements Parcelable {

	private static final int HAS_ALTITUDE = 1 << 0;
	private static final int HAS_SPEED = 1 << 1;
	private static final int HAS_BEARING = 1 << 2;
	private static final int HAS_ACCURACY = 1 << 3;
	private static final int HAS_VERTICAL_ACCURACY = 1 << 4;
	private static final int HAS_SPEED_ACCURACY = 1 << 5;
	private static final int HAS_BEARING_ACCURACY = 1 << 6;
	private static final int HAS_ELAPSED_REALTIME_UNCERTAINTY = 1 << 7;
	private static final int HAS_MSL_ALTITUDE = 1 << 8;
	private static final int HAS_MSL_ALTITUDE_ACCURACY = 1 << 9;
	private static final int IS_MOCK = 1 << 10;

	private String provider;
	private long time;
	private long elapsedRealtimeNanos;
	private double elapsedRealtimeUncertaintyNanos;
	private int fields;
	private double latitude;
	private double longitude;
	private double altitude;
	private float speed;
	private float bearing;
	private float accuracy;
	private float verticalAccuracy;
	private float speedAccuracy;
	private float bearingAccuracy;
	private double mslAltitude;
	private float mslAltitudeAccuracy;
	private Bundle extras;

	public Location(String provider) {
		this.provider = provider;
	}

	public Location(Location l) {
		set(l);
	}

	public void set(Location l) {
		provider = l.provider;
		time = l.time;
		elapsedRealtimeNanos = l.elapsedRealtimeNanos;
		elapsedRealtimeUncertaintyNanos = l.elapsedRealtimeUncertaintyNanos;
		fields = l.fields;
		latitude = l.latitude;
		longitude = l.longitude;
		altitude = l.altitude;
		speed = l.speed;
		bearing = l.bearing;
		accuracy = l.accuracy;
		verticalAccuracy = l.verticalAccuracy;
		speedAccuracy = l.speedAccuracy;
		bearingAccuracy = l.bearingAccuracy;
		mslAltitude = l.mslAltitude;
		mslAltitudeAccuracy = l.mslAltitudeAccuracy;
		extras = (l.extras == null) ? null : new Bundle(l.extras);
	}

	public void reset() {
		provider = null;
		time = 0;
		elapsedRealtimeNanos = 0;
		elapsedRealtimeUncertaintyNanos = 0;
		fields = 0;
		latitude = longitude = altitude = mslAltitude = 0;
		speed = bearing = accuracy = verticalAccuracy = speedAccuracy = bearingAccuracy = mslAltitudeAccuracy = 0;
		extras = null;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public long getElapsedRealtimeNanos() {
		return elapsedRealtimeNanos;
	}

	public long getElapsedRealtimeMillis() {
		return elapsedRealtimeNanos / 1000000;
	}

	public long getElapsedRealtimeAgeMillis() {
		return getElapsedRealtimeAgeMillis(SystemClock.elapsedRealtime());
	}

	public long getElapsedRealtimeAgeMillis(long referenceRealtimeMs) {
		return referenceRealtimeMs - getElapsedRealtimeMillis();
	}

	public void setElapsedRealtimeNanos(long nanos) {
		elapsedRealtimeNanos = nanos;
	}

	public boolean hasElapsedRealtimeUncertaintyNanos() {
		return (fields & HAS_ELAPSED_REALTIME_UNCERTAINTY) != 0;
	}

	public double getElapsedRealtimeUncertaintyNanos() {
		return elapsedRealtimeUncertaintyNanos;
	}

	public void setElapsedRealtimeUncertaintyNanos(double nanos) {
		elapsedRealtimeUncertaintyNanos = nanos;
		fields |= HAS_ELAPSED_REALTIME_UNCERTAINTY;
	}

	public void removeElapsedRealtimeUncertaintyNanos() {
		fields &= ~HAS_ELAPSED_REALTIME_UNCERTAINTY;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public boolean hasAltitude() {
		return (fields & HAS_ALTITUDE) != 0;
	}

	public double getAltitude() {
		return altitude;
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
		fields |= HAS_ALTITUDE;
	}

	public void removeAltitude() {
		altitude = 0;
		fields &= ~HAS_ALTITUDE;
	}

	public boolean hasMslAltitude() {
		return (fields & HAS_MSL_ALTITUDE) != 0;
	}

	public double getMslAltitudeMeters() {
		return mslAltitude;
	}

	public void setMslAltitudeMeters(double altitude) {
		mslAltitude = altitude;
		fields |= HAS_MSL_ALTITUDE;
	}

	public void removeMslAltitude() {
		fields &= ~HAS_MSL_ALTITUDE;
	}

	public boolean hasMslAltitudeAccuracy() {
		return (fields & HAS_MSL_ALTITUDE_ACCURACY) != 0;
	}

	public float getMslAltitudeAccuracyMeters() {
		return mslAltitudeAccuracy;
	}

	public void setMslAltitudeAccuracyMeters(float accuracy) {
		mslAltitudeAccuracy = accuracy;
		fields |= HAS_MSL_ALTITUDE_ACCURACY;
	}

	public void removeMslAltitudeAccuracy() {
		fields &= ~HAS_MSL_ALTITUDE_ACCURACY;
	}

	public boolean hasSpeed() {
		return (fields & HAS_SPEED) != 0;
	}

	public float getSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
		fields |= HAS_SPEED;
	}

	public void removeSpeed() {
		speed = 0;
		fields &= ~HAS_SPEED;
	}

	public boolean hasBearing() {
		return (fields & HAS_BEARING) != 0;
	}

	public float getBearing() {
		return bearing;
	}

	public void setBearing(float bearing) {
		while (bearing < 0.0f)
			bearing += 360.0f;
		while (bearing >= 360.0f)
			bearing -= 360.0f;
		this.bearing = bearing;
		fields |= HAS_BEARING;
	}

	public void removeBearing() {
		bearing = 0;
		fields &= ~HAS_BEARING;
	}

	public boolean hasAccuracy() {
		return (fields & HAS_ACCURACY) != 0;
	}

	public float getAccuracy() {
		return accuracy;
	}

	public void setAccuracy(float accuracy) {
		this.accuracy = accuracy;
		fields |= HAS_ACCURACY;
	}

	public void removeAccuracy() {
		accuracy = 0;
		fields &= ~HAS_ACCURACY;
	}

	public boolean hasVerticalAccuracy() {
		return (fields & HAS_VERTICAL_ACCURACY) != 0;
	}

	public float getVerticalAccuracyMeters() {
		return verticalAccuracy;
	}

	public void setVerticalAccuracyMeters(float accuracy) {
		verticalAccuracy = accuracy;
		fields |= HAS_VERTICAL_ACCURACY;
	}

	public void removeVerticalAccuracy() {
		fields &= ~HAS_VERTICAL_ACCURACY;
	}

	public boolean hasSpeedAccuracy() {
		return (fields & HAS_SPEED_ACCURACY) != 0;
	}

	public float getSpeedAccuracyMetersPerSecond() {
		return speedAccuracy;
	}

	public void setSpeedAccuracyMetersPerSecond(float accuracy) {
		speedAccuracy = accuracy;
		fields |= HAS_SPEED_ACCURACY;
	}

	public void removeSpeedAccuracy() {
		fields &= ~HAS_SPEED_ACCURACY;
	}

	public boolean hasBearingAccuracy() {
		return (fields & HAS_BEARING_ACCURACY) != 0;
	}

	public float getBearingAccuracyDegrees() {
		return bearingAccuracy;
	}

	public void setBearingAccuracyDegrees(float accuracy) {
		bearingAccuracy = accuracy;
		fields |= HAS_BEARING_ACCURACY;
	}

	public void removeBearingAccuracy() {
		fields &= ~HAS_BEARING_ACCURACY;
	}

	public boolean isMock() {
		return (fields & IS_MOCK) != 0;
	}

	public boolean isFromMockProvider() {
		return isMock();
	}

	public void setMock(boolean mock) {
		if (mock)
			fields |= IS_MOCK;
		else
			fields &= ~IS_MOCK;
	}

	public void setIsFromMockProvider(boolean mock) {
		setMock(mock);
	}

	public Bundle getExtras() {
		return extras;
	}

	public void setExtras(Bundle extras) {
		this.extras = (extras == null) ? null : new Bundle(extras);
	}

	public boolean isComplete() {
		return provider != null && hasAccuracy() && time != 0 && elapsedRealtimeNanos != 0;
	}

	public void makeComplete() {
		if (provider == null)
			provider = "";
		if (!hasAccuracy())
			setAccuracy(100.0f);
		if (time == 0)
			time = System.currentTimeMillis();
		if (elapsedRealtimeNanos == 0)
			elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos();
	}

	public float distanceTo(Location dest) {
		float[] results = new float[1];
		distanceBetween(latitude, longitude, dest.latitude, dest.longitude, results);
		return results[0];
	}

	public float bearingTo(Location dest) {
		float[] results = new float[2];
		distanceBetween(latitude, longitude, dest.latitude, dest.longitude, results);
		return results[1];
	}

	/**
	 * Distance on the WGS84 ellipsoid (Vincenty's inverse formula), like Android.
	 * results[0] distance in meters, results[1] initial bearing, results[2] final bearing.
	 */
	public static void distanceBetween(double startLatitude, double startLongitude,
	                                   double endLatitude, double endLongitude, float[] results) {
		if (results == null || results.length < 1)
			throw new IllegalArgumentException("results is null or has length < 1");
		final double a = 6378137.0;
		final double b = 6356752.3142;
		final double f = (a - b) / a;
		final double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

		double L = Math.toRadians(endLongitude - startLongitude);
		double U1 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(startLatitude)));
		double U2 = Math.atan((1.0 - f) * Math.tan(Math.toRadians(endLatitude)));
		double cosU1 = Math.cos(U1), sinU1 = Math.sin(U1);
		double cosU2 = Math.cos(U2), sinU2 = Math.sin(U2);
		double cosU1cosU2 = cosU1 * cosU2;
		double sinU1sinU2 = sinU1 * sinU2;

		double sigma = 0, deltaSigma = 0, cosSqAlpha, cos2SM, cosSigma = 0, sinSigma = 0;
		double cosLambda = 0, sinLambda = 0;
		double lambda = L;
		double A = 0;
		for (int iter = 0; iter < 20; iter++) {
			double lambdaOrig = lambda;
			cosLambda = Math.cos(lambda);
			sinLambda = Math.sin(lambda);
			double t1 = cosU2 * sinLambda;
			double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
			double sinSqSigma = t1 * t1 + t2 * t2;
			sinSigma = Math.sqrt(sinSqSigma);
			cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda;
			sigma = Math.atan2(sinSigma, cosSigma);
			double sinAlpha = (sinSigma == 0) ? 0.0 : cosU1cosU2 * sinLambda / sinSigma;
			cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
			cos2SM = (cosSqAlpha == 0) ? 0.0 : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha;
			double uSquared = cosSqAlpha * aSqMinusBSqOverBSq;
			A = 1 + (uSquared / 16384.0) * (4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)));
			double B = (uSquared / 1024.0) * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
			double C = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
			double cos2SMSq = cos2SM * cos2SM;
			deltaSigma = B * sinSigma * (cos2SM + (B / 4.0) * (cosSigma * (-1.0 + 2.0 * cos2SMSq) - (B / 6.0) * cos2SM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)));
			lambda = L + (1.0 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SM + C * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)));
			if (Math.abs((lambda - lambdaOrig) / lambda) < 1.0e-12)
				break;
		}
		results[0] = (float)(b * A * (sigma - deltaSigma));
		if (results.length > 1) {
			float initialBearing = (float)Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
			results[1] = (float)Math.toDegrees(initialBearing);
			if (results.length > 2) {
				float finalBearing = (float)Math.atan2(cosU1 * sinLambda, -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
				results[2] = (float)Math.toDegrees(finalBearing);
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("Location[");
		s.append(provider).append(' ').append(String.format("%.6f,%.6f", latitude, longitude));
		if (hasAccuracy())
			s.append(" hAcc=").append(accuracy);
		s.append(" et=").append(elapsedRealtimeNanos / 1000000).append("ms");
		if (hasAltitude())
			s.append(" alt=").append(altitude);
		if (hasSpeed())
			s.append(" vel=").append(speed);
		if (hasBearing())
			s.append(" bear=").append(bearing);
		if (isMock())
			s.append(" mock");
		return s.append(']').toString();
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(provider);
		dest.writeInt(fields);
		dest.writeLong(time);
		dest.writeLong(elapsedRealtimeNanos);
		dest.writeDouble(elapsedRealtimeUncertaintyNanos);
		dest.writeDouble(latitude);
		dest.writeDouble(longitude);
		dest.writeDouble(altitude);
		dest.writeFloat(speed);
		dest.writeFloat(bearing);
		dest.writeFloat(accuracy);
		dest.writeFloat(verticalAccuracy);
		dest.writeFloat(speedAccuracy);
		dest.writeFloat(bearingAccuracy);
		dest.writeDouble(mslAltitude);
		dest.writeFloat(mslAltitudeAccuracy);
		dest.writeBundle(extras);
	}

	public static final Parcelable.Creator<Location> CREATOR = new Parcelable.Creator<Location>() {
		@Override
		public Location createFromParcel(Parcel in) {
			Location l = new Location(in.readString());
			l.fields = in.readInt();
			l.time = in.readLong();
			l.elapsedRealtimeNanos = in.readLong();
			l.elapsedRealtimeUncertaintyNanos = in.readDouble();
			l.latitude = in.readDouble();
			l.longitude = in.readDouble();
			l.altitude = in.readDouble();
			l.speed = in.readFloat();
			l.bearing = in.readFloat();
			l.accuracy = in.readFloat();
			l.verticalAccuracy = in.readFloat();
			l.speedAccuracy = in.readFloat();
			l.bearingAccuracy = in.readFloat();
			l.mslAltitude = in.readDouble();
			l.mslAltitudeAccuracy = in.readFloat();
			try {
				l.extras = in.readBundle();
			} catch (ReflectiveOperationException e) {
				l.extras = null;
			}
			return l;
		}

		@Override
		public Location[] newArray(int size) {
			return new Location[size];
		}
	};
}
