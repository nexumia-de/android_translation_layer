package android.content;

/* the app itself as the source of its data accesses; ATL tracks no other sources */
public final class AttributionSource {
	private final int uid;
	private final String packageName;
	private final String attributionTag;

	public AttributionSource(int uid, String packageName, String attributionTag) {
		this.uid = uid;
		this.packageName = packageName;
		this.attributionTag = attributionTag;
	}

	public int getUid() {
		return uid;
	}

	public int getPid() {
		return android.os.Process.myPid();
	}

	public String getPackageName() {
		return packageName;
	}

	public String getAttributionTag() {
		return attributionTag;
	}

	public AttributionSource getNext() {
		return null;
	}

	public boolean isTrusted(Context context) {
		return true;
	}
}
