package android.location;

import android.annotation.FloatRange;

public class Location {

	private double latitude;
	private double longitude;
	private double altitude;
	private double accuracy;
	private double speed;
	private double bearing;
	private long timestamp;

	public Location(String provider) {}

	/* for internal use */
	public Location(double latitude,
	                double longitude,
	                double altitude,
	                double accuracy,
	                double speed,
	                double bearing,
	                long timestamp) {
		this.latitude = latitude;
		this.longitude = longitude;
		this.altitude = altitude;
		this.accuracy = accuracy;
		this.speed = speed;
		this.bearing = bearing;
		this.timestamp = timestamp;
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
		return altitude != -Double.MAX_VALUE;
	}

	public double getAltitude() {
		return altitude;
	}

	public boolean hasAccuracy() {
		return true;
	}

	public float getAccuracy() {
		return (float)accuracy;
	}

	public void setAccuracy(float accuracy) {
		this.accuracy = accuracy;
	}

	public boolean hasSpeed() {
		return speed != -1;
	}

	public float getSpeed() {
		return (float)speed;
	}

	public boolean hasBearing() {
		return bearing != -1;
	}

	public float getBearing() {
		return (float)bearing;
	}

	public long getTime() {
		return timestamp;
	}

	public String getProvider() {
		return "fused";
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	private static void computeDistanceAndBearing(double lat1, double lon1,
	                                              double lat2, double lon2, BearingDistanceCache results) {
		// Based on http://www.ngs.noaa.gov/PUBS_LIB/inverse.pdf
		// using the "Inverse Formula" (section 4)

		// Convert lat/long to radians
		lat1 *= Math.PI / 180.0;
		lat2 *= Math.PI / 180.0;
		lon1 *= Math.PI / 180.0;
		lon2 *= Math.PI / 180.0;

		double a = 6378137.0;    // WGS84 major axis
		double b = 6356752.3142; // WGS84 semi-major axis
		double f = (a - b) / a;
		double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

		double l = lon2 - lon1;
		double aA = 0.0;
		double u1 = Math.atan((1.0 - f) * Math.tan(lat1));
		double u2 = Math.atan((1.0 - f) * Math.tan(lat2));

		double cosU1 = Math.cos(u1);
		double cosU2 = Math.cos(u2);
		double sinU1 = Math.sin(u1);
		double sinU2 = Math.sin(u2);
		double cosU1cosU2 = cosU1 * cosU2;
		double sinU1sinU2 = sinU1 * sinU2;

		double sigma = 0.0;
		double deltaSigma = 0.0;
		double cosSqAlpha;
		double cos2SM;
		double cosSigma;
		double sinSigma;
		double cosLambda = 0.0;
		double sinLambda = 0.0;

		double lambda = l; // initial guess
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
			aA = 1 + (uSquared / 16384.0) * (4096.0 + uSquared * (-768 + uSquared * (320.0 - 175.0 * uSquared)));
			double bB = (uSquared / 1024.0) * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
			double cC = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
			double cos2SMSq = cos2SM * cos2SM;
			deltaSigma = bB * sinSigma * (cos2SM + (bB / 4.0) * (cosSigma * (-1.0 + 2.0 * cos2SMSq) - (bB / 6.0) * cos2SM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)));

			lambda = l + (1.0 - cC) * f * sinAlpha * (sigma + cC * sinSigma * (cos2SM + cC * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)));

			double delta = (lambda - lambdaOrig) / lambda;
			if (Math.abs(delta) < 1.0e-12) {
				break;
			}
		}

		results.mDistance = (float)(b * aA * (sigma - deltaSigma));
		float initialBearing = (float)Math.atan2(cosU2 * sinLambda,
		                                         cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
		initialBearing = (float)(initialBearing * (180.0 / Math.PI));
		results.mInitialBearing = initialBearing;
		float finalBearing = (float)Math.atan2(cosU1 * sinLambda,
		                                       -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
		finalBearing = (float)(finalBearing * (180.0 / Math.PI));
		results.mFinalBearing = finalBearing;
		results.mLat1 = lat1;
		results.mLat2 = lat2;
		results.mLon1 = lon1;
		results.mLon2 = lon2;
	}

	/**
	 * Copyright (C) 2007 The Android Open Source Project
	 *
	 * Computes the approximate distance in meters between two
	 * locations, and optionally the initial and final bearings of the
	 * shortest path between them.  Distance and bearing are defined using the
	 * WGS84 ellipsoid.
	 *
	 * <p> The computed distance is stored in results[0].  If results has length
	 * 2 or greater, the initial bearing is stored in results[1]. If results has
	 * length 3 or greater, the final bearing is stored in results[2].
	 *
	 * @param startLatitude the starting latitude
	 * @param startLongitude the starting longitude
	 * @param endLatitude the ending latitude
	 * @param endLongitude the ending longitude
	 * @param results an array of floats to hold the results
	 *
	 * @throws IllegalArgumentException if results is null or has length < 1
	 */
	public static void distanceBetween(
	    @FloatRange(from = -90.0, to = 90.0) double startLatitude,
	    @FloatRange(from = -180.0, to = 180.0) double startLongitude,
	    @FloatRange(from = -90.0, to = 90.0) double endLatitude,
	    @FloatRange(from = -180.0, to = 180.0) double endLongitude,
	    float[] results) {
		if (results == null || results.length < 1) {
			throw new IllegalArgumentException("results is null or has length < 1");
		}
		BearingDistanceCache cache = new BearingDistanceCache();
		computeDistanceAndBearing(startLatitude, startLongitude,
		                          endLatitude, endLongitude, cache);
		results[0] = cache.mDistance;
		if (results.length > 1) {
			results[1] = cache.mInitialBearing;
			if (results.length > 2) {
				results[2] = cache.mFinalBearing;
			}
		}
	}

	/* Copyright (C) 2007 The Android Open Source Project */
	private static class BearingDistanceCache {
		double mLat1 = 0.0;
		double mLon1 = 0.0;
		double mLat2 = 0.0;
		double mLon2 = 0.0;
		float mDistance = 0.0f;
		float mInitialBearing = 0.0f;
		float mFinalBearing = 0.0f;
	}
}
