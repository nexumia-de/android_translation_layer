package android.location;

import android.os.Bundle;
import android.os.SystemClock;

public class Location {

	public static final int FORMAT_DEGREES = 0;
	public static final int FORMAT_MINUTES = 1;
	public static final int FORMAT_SECONDS = 2;

	private static final int HAS_ALTITUDE = 1 << 0;
	private static final int HAS_SPEED = 1 << 1;
	private static final int HAS_BEARING = 1 << 2;
	private static final int HAS_ACCURACY = 1 << 3;
	private static final int HAS_VERTICAL_ACCURACY = 1 << 4;
	private static final int HAS_SPEED_ACCURACY = 1 << 5;
	private static final int HAS_BEARING_ACCURACY = 1 << 6;
	private static final int HAS_ELAPSED_REALTIME_UNCERTAINTY = 1 << 7;

	private String provider;
	private long time;
	private long elapsedRealtimeNanos;
	private double elapsedRealtimeUncertaintyNanos;
	private double latitude;
	private double longitude;
	private double altitude;
	private float speed;
	private float bearing;
	private float accuracy;
	private float verticalAccuracy;
	private float speedAccuracy;
	private float bearingAccuracy;
	private int fields;
	private boolean mock;
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
		latitude = l.latitude;
		longitude = l.longitude;
		altitude = l.altitude;
		speed = l.speed;
		bearing = l.bearing;
		accuracy = l.accuracy;
		verticalAccuracy = l.verticalAccuracy;
		speedAccuracy = l.speedAccuracy;
		bearingAccuracy = l.bearingAccuracy;
		fields = l.fields;
		mock = l.mock;
		extras = l.extras == null ? null : new Bundle(l.extras);
	}

	public void reset() {
		String p = provider;
		set(new Location((String)null));
		provider = p;
	}

	public String getProvider() { return provider; }
	public void setProvider(String provider) { this.provider = provider; }

	public long getTime() { return time; }
	public void setTime(long time) { this.time = time; }

	public long getElapsedRealtimeNanos() { return elapsedRealtimeNanos; }
	public void setElapsedRealtimeNanos(long nanos) { elapsedRealtimeNanos = nanos; }
	public long getElapsedRealtimeMillis() { return elapsedRealtimeNanos / 1000000; }
	public long getElapsedRealtimeAgeMillis() { return getElapsedRealtimeAgeMillis(SystemClock.elapsedRealtime()); }
	public long getElapsedRealtimeAgeMillis(long referenceRealtimeMs) { return referenceRealtimeMs - getElapsedRealtimeMillis(); }

	public boolean hasElapsedRealtimeUncertaintyNanos() { return (fields & HAS_ELAPSED_REALTIME_UNCERTAINTY) != 0; }
	public double getElapsedRealtimeUncertaintyNanos() { return elapsedRealtimeUncertaintyNanos; }
	public void setElapsedRealtimeUncertaintyNanos(double nanos) {
		elapsedRealtimeUncertaintyNanos = nanos;
		fields |= HAS_ELAPSED_REALTIME_UNCERTAINTY;
	}
	public void removeElapsedRealtimeUncertaintyNanos() { fields &= ~HAS_ELAPSED_REALTIME_UNCERTAINTY; }

	public double getLatitude() { return latitude; }
	public void setLatitude(double latitude) { this.latitude = latitude; }
	public double getLongitude() { return longitude; }
	public void setLongitude(double longitude) { this.longitude = longitude; }

	public boolean hasAltitude() { return (fields & HAS_ALTITUDE) != 0; }
	public double getAltitude() { return altitude; }
	public void setAltitude(double altitude) {
		this.altitude = altitude;
		fields |= HAS_ALTITUDE;
	}
	public void removeAltitude() { fields &= ~HAS_ALTITUDE; }

	public boolean hasSpeed() { return (fields & HAS_SPEED) != 0; }
	public float getSpeed() { return speed; }
	public void setSpeed(float speed) {
		this.speed = speed;
		fields |= HAS_SPEED;
	}
	public void removeSpeed() { fields &= ~HAS_SPEED; }

	public boolean hasBearing() { return (fields & HAS_BEARING) != 0; }
	public float getBearing() { return bearing; }
	public void setBearing(float bearing) {
		bearing %= 360.0f;
		if (bearing < 0)
			bearing += 360.0f;
		this.bearing = bearing;
		fields |= HAS_BEARING;
	}
	public void removeBearing() { fields &= ~HAS_BEARING; }

	public boolean hasAccuracy() { return (fields & HAS_ACCURACY) != 0; }
	public float getAccuracy() { return accuracy; }
	public void setAccuracy(float accuracy) {
		this.accuracy = accuracy;
		fields |= HAS_ACCURACY;
	}
	public void removeAccuracy() { fields &= ~HAS_ACCURACY; }

	public boolean hasVerticalAccuracy() { return (fields & HAS_VERTICAL_ACCURACY) != 0; }
	public float getVerticalAccuracyMeters() { return verticalAccuracy; }
	public void setVerticalAccuracyMeters(float accuracy) {
		verticalAccuracy = accuracy;
		fields |= HAS_VERTICAL_ACCURACY;
	}
	public void removeVerticalAccuracy() { fields &= ~HAS_VERTICAL_ACCURACY; }

	public boolean hasSpeedAccuracy() { return (fields & HAS_SPEED_ACCURACY) != 0; }
	public float getSpeedAccuracyMetersPerSecond() { return speedAccuracy; }
	public void setSpeedAccuracyMetersPerSecond(float accuracy) {
		speedAccuracy = accuracy;
		fields |= HAS_SPEED_ACCURACY;
	}
	public void removeSpeedAccuracy() { fields &= ~HAS_SPEED_ACCURACY; }

	public boolean hasBearingAccuracy() { return (fields & HAS_BEARING_ACCURACY) != 0; }
	public float getBearingAccuracyDegrees() { return bearingAccuracy; }
	public void setBearingAccuracyDegrees(float accuracy) {
		bearingAccuracy = accuracy;
		fields |= HAS_BEARING_ACCURACY;
	}
	public void removeBearingAccuracy() { fields &= ~HAS_BEARING_ACCURACY; }

	public boolean isMock() { return mock; }
	public boolean isFromMockProvider() { return mock; }
	public void setMock(boolean mock) { this.mock = mock; }
	public void setIsFromMockProvider(boolean mock) { this.mock = mock; }

	public boolean isComplete() {
		return provider != null && hasAccuracy() && time != 0 && elapsedRealtimeNanos != 0;
	}

	public Bundle getExtras() { return extras; }
	public void setExtras(Bundle extras) { this.extras = extras == null ? null : new Bundle(extras); }

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
	 * Vincenty's inverse formula on the WGS84 ellipsoid. results[0] is the
	 * distance in meters, results[1] the initial and results[2] the final
	 * bearing in degrees, as far as the array is long enough.
	 */
	public static void distanceBetween(double startLatitude, double startLongitude,
	                                   double endLatitude, double endLongitude, float[] results) {
		if (results == null || results.length < 1)
			throw new IllegalArgumentException("results is null or has length < 1");
		final double a = 6378137.0;
		final double f = 1 / 298.257223563;
		final double b = a * (1 - f);
		double L = Math.toRadians(endLongitude - startLongitude);
		double U1 = Math.atan((1 - f) * Math.tan(Math.toRadians(startLatitude)));
		double U2 = Math.atan((1 - f) * Math.tan(Math.toRadians(endLatitude)));
		double sinU1 = Math.sin(U1), cosU1 = Math.cos(U1);
		double sinU2 = Math.sin(U2), cosU2 = Math.cos(U2);
		double lambda = L, sinLambda = 0, cosLambda = 0;
		double sinSigma = 0, cosSigma = 0, sigma = 0, cosSqAlpha = 0, cos2SigmaM = 0;
		for (int i = 0; i < 20; i++) {
			sinLambda = Math.sin(lambda);
			cosLambda = Math.cos(lambda);
			double t1 = cosU2 * sinLambda;
			double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
			sinSigma = Math.sqrt(t1 * t1 + t2 * t2);
			if (sinSigma == 0)
				break; // coincident points
			cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
			sigma = Math.atan2(sinSigma, cosSigma);
			double sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
			cosSqAlpha = 1 - sinAlpha * sinAlpha;
			cos2SigmaM = cosSqAlpha != 0 ? cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha : 0;
			double C = f / 16 * cosSqAlpha * (4 + f * (4 - 3 * cosSqAlpha));
			double lambdaPrev = lambda;
			lambda = L + (1 - C) * f * sinAlpha * (sigma + C * sinSigma * (cos2SigmaM + C * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)));
			if (Math.abs(lambda - lambdaPrev) < 1e-12)
				break;
		}
		double uSq = cosSqAlpha * (a * a - b * b) / (b * b);
		double A = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)));
		double B = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)));
		double deltaSigma = B * sinSigma * (cos2SigmaM + B / 4 * (cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) - B / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)));
		results[0] = (float)(b * A * (sigma - deltaSigma));
		if (results.length > 1)
			results[1] = (float)Math.toDegrees(Math.atan2(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda));
		if (results.length > 2)
			results[2] = (float)Math.toDegrees(Math.atan2(cosU1 * sinLambda, -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda));
	}

	public static String convert(double coordinate, int outputType) {
		if (outputType != FORMAT_DEGREES && outputType != FORMAT_MINUTES && outputType != FORMAT_SECONDS)
			throw new IllegalArgumentException("outputType=" + outputType);
		StringBuilder sb = new StringBuilder();
		if (coordinate < 0) {
			sb.append('-');
			coordinate = -coordinate;
		}
		java.text.DecimalFormat df = new java.text.DecimalFormat("###.#####", java.text.DecimalFormatSymbols.getInstance(java.util.Locale.US));
		if (outputType == FORMAT_MINUTES || outputType == FORMAT_SECONDS) {
			int degrees = (int)Math.floor(coordinate);
			sb.append(degrees).append(':');
			coordinate = (coordinate - degrees) * 60.0;
			if (outputType == FORMAT_SECONDS) {
				int minutes = (int)Math.floor(coordinate);
				sb.append(minutes).append(':');
				coordinate = (coordinate - minutes) * 60.0;
			}
		}
		sb.append(df.format(coordinate));
		return sb.toString();
	}

	@Override
	public String toString() {
		return "Location[" + provider + " " + latitude + "," + longitude
		    + (hasAccuracy() ? " hAcc=" + accuracy : "") + " t=" + time
		    + (hasAltitude() ? " alt=" + altitude : "")
		    + (hasSpeed() ? " vel=" + speed : "")
		    + (hasBearing() ? " bear=" + bearing : "") + "]";
	}
}
