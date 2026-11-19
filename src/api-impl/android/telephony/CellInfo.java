package android.telephony;

public abstract class CellInfo {
	public boolean isRegistered() {
		return false;
	}

	public long getTimeStamp() {
		return 0;
	}

	public long getTimestampMillis() {
		return 0;
	}
}
