package android.location;

import android.os.Parcel;
import android.os.Parcelable;

public class Location implements Parcelable {

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

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeDouble(latitude);
		dest.writeDouble(longitude);
		dest.writeDouble(altitude);
		dest.writeDouble(accuracy);
		dest.writeDouble(speed);
		dest.writeDouble(bearing);
		dest.writeLong(timestamp);
	}

	public static final Parcelable.Creator<Location> CREATOR = new Parcelable.Creator<Location>() {
		@Override
		public Location createFromParcel(Parcel in) {
			return new Location(in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(),
			                    in.readDouble(), in.readDouble(), in.readLong());
		}

		@Override
		public Location[] newArray(int size) {
			return new Location[size];
		}
	};
}
