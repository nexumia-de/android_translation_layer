package android.location;

public final class LocationRequest {

	public static final long PASSIVE_INTERVAL = Long.MAX_VALUE;
	public static final int QUALITY_HIGH_ACCURACY = 100;
	public static final int QUALITY_BALANCED_POWER_ACCURACY = 102;
	public static final int QUALITY_LOW_POWER = 104;

	private static final long IMPLICIT_MIN_UPDATE_INTERVAL = -1;

	private final long intervalMillis;
	private final int quality;
	private final long minUpdateIntervalMillis;
	private final float minUpdateDistanceMeters;
	private final int maxUpdates;
	private final long durationMillis;
	private final long maxUpdateDelayMillis;

	private LocationRequest(long intervalMillis, int quality, long minUpdateIntervalMillis, float minUpdateDistanceMeters,
	                        int maxUpdates, long durationMillis, long maxUpdateDelayMillis) {
		this.intervalMillis = intervalMillis;
		this.quality = quality;
		this.minUpdateIntervalMillis = minUpdateIntervalMillis;
		this.minUpdateDistanceMeters = minUpdateDistanceMeters;
		this.maxUpdates = maxUpdates;
		this.durationMillis = durationMillis;
		this.maxUpdateDelayMillis = maxUpdateDelayMillis;
	}

	public long getIntervalMillis() {
		return intervalMillis;
	}

	public int getQuality() {
		return quality;
	}

	public long getMinUpdateIntervalMillis() {
		if (minUpdateIntervalMillis == IMPLICIT_MIN_UPDATE_INTERVAL)
			return intervalMillis;
		return Math.min(minUpdateIntervalMillis, intervalMillis);
	}

	public float getMinUpdateDistanceMeters() {
		return minUpdateDistanceMeters;
	}

	public int getMaxUpdates() {
		return maxUpdates;
	}

	public long getDurationMillis() {
		return durationMillis;
	}

	public long getMaxUpdateDelayMillis() {
		return maxUpdateDelayMillis;
	}

	public boolean isPassive() {
		return intervalMillis == PASSIVE_INTERVAL;
	}

	@Override
	public String toString() {
		return "LocationRequest[interval=" + intervalMillis + " quality=" + quality + " minInterval=" + getMinUpdateIntervalMillis() + " minDistance=" + minUpdateDistanceMeters + "]";
	}

	public static final class Builder {
		private long intervalMillis;
		private int quality = QUALITY_BALANCED_POWER_ACCURACY;
		private long minUpdateIntervalMillis = IMPLICIT_MIN_UPDATE_INTERVAL;
		private float minUpdateDistanceMeters = 0;
		private int maxUpdates = Integer.MAX_VALUE;
		private long durationMillis = Long.MAX_VALUE;
		private long maxUpdateDelayMillis = 0;

		public Builder(long intervalMillis) {
			setIntervalMillis(intervalMillis);
		}

		public Builder(LocationRequest r) {
			intervalMillis = r.intervalMillis;
			quality = r.quality;
			minUpdateIntervalMillis = r.minUpdateIntervalMillis;
			minUpdateDistanceMeters = r.minUpdateDistanceMeters;
			maxUpdates = r.maxUpdates;
			durationMillis = r.durationMillis;
			maxUpdateDelayMillis = r.maxUpdateDelayMillis;
		}

		public Builder setIntervalMillis(long intervalMillis) {
			if (intervalMillis < 0)
				throw new IllegalArgumentException("intervalMillis < 0");
			this.intervalMillis = intervalMillis;
			return this;
		}

		public Builder setQuality(int quality) {
			this.quality = quality;
			return this;
		}

		public Builder setMinUpdateIntervalMillis(long minUpdateIntervalMillis) {
			if (minUpdateIntervalMillis < 0)
				throw new IllegalArgumentException("minUpdateIntervalMillis < 0");
			this.minUpdateIntervalMillis = minUpdateIntervalMillis;
			return this;
		}

		public Builder clearMinUpdateIntervalMillis() {
			minUpdateIntervalMillis = IMPLICIT_MIN_UPDATE_INTERVAL;
			return this;
		}

		public Builder setMinUpdateDistanceMeters(float minUpdateDistanceMeters) {
			if (minUpdateDistanceMeters < 0)
				throw new IllegalArgumentException("minUpdateDistanceMeters < 0");
			this.minUpdateDistanceMeters = minUpdateDistanceMeters;
			return this;
		}

		public Builder setMaxUpdates(int maxUpdates) {
			if (maxUpdates <= 0)
				throw new IllegalArgumentException("maxUpdates <= 0");
			this.maxUpdates = maxUpdates;
			return this;
		}

		public Builder setDurationMillis(long durationMillis) {
			if (durationMillis <= 0)
				throw new IllegalArgumentException("durationMillis <= 0");
			this.durationMillis = durationMillis;
			return this;
		}

		public Builder setMaxUpdateDelayMillis(long maxUpdateDelayMillis) {
			this.maxUpdateDelayMillis = maxUpdateDelayMillis;
			return this;
		}

		public LocationRequest build() {
			return new LocationRequest(intervalMillis, quality, minUpdateIntervalMillis, minUpdateDistanceMeters,
			                           maxUpdates, durationMillis, maxUpdateDelayMillis);
		}
	}
}
