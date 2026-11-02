package android.os;

import java.util.UUID;

/** A UUID that can be put into a Parcel. */
public final class ParcelUuid implements Parcelable {

	private final UUID uuid;

	public ParcelUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public static ParcelUuid fromString(String uuid) {
		return new ParcelUuid(UUID.fromString(uuid));
	}

	public UUID getUuid() {
		return uuid;
	}

	@Override
	public String toString() {
		return uuid.toString();
	}

	@Override
	public int hashCode() {
		return uuid.hashCode();
	}

	@Override
	public boolean equals(Object object) {
		return object instanceof ParcelUuid && uuid.equals(((ParcelUuid)object).uuid);
	}

	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(uuid.getMostSignificantBits());
		dest.writeLong(uuid.getLeastSignificantBits());
	}

	public static final Parcelable.Creator<ParcelUuid> CREATOR = new Parcelable.Creator<ParcelUuid>() {
		public ParcelUuid createFromParcel(Parcel source) {
			long msb = source.readLong();
			long lsb = source.readLong();
			return new ParcelUuid(new UUID(msb, lsb));
		}

		public ParcelUuid[] newArray(int size) {
			return new ParcelUuid[size];
		}
	};
}
