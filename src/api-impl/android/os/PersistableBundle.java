package android.os;

public class PersistableBundle extends BaseBundle {

	public static final PersistableBundle EMPTY = new PersistableBundle();

	public PersistableBundle() {
		super();
	}

	/* the capacity is only a hint */
	public PersistableBundle(int capacity) {
		super();
	}

	public PersistableBundle(PersistableBundle b) {
		super();
		if (b != null)
			mMap.putAll(b.mMap);
	}

	@Override
	public synchronized String toString() {
		return "PersistableBundle[" + mMap.toString() + "]";
	}
}
