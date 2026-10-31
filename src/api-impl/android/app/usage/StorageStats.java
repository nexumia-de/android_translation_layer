package android.app.usage;

/* sizes are not measured, apps get 0 */
public final class StorageStats {
	public long getAppBytes() {
		return 0;
	}

	public long getDataBytes() {
		return 0;
	}

	public long getCacheBytes() {
		return 0;
	}
}
