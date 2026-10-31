package android.os.storage;

import android.content.Context;

public class StorageVolume {
	public boolean isPrimary() { return true; }
	public String getPath() { return ""; }
	public String getDescription(Context context) { return ""; }
	// like Android's primary internal storage, which has no UUID
	public String getUuid() { return null; }
}
