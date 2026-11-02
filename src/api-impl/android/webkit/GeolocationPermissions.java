package android.webkit;

public class GeolocationPermissions {
	public interface Callback {
		void invoke(String origin, boolean allow, boolean retain);
	}

	private static final GeolocationPermissions instance = new GeolocationPermissions();

	public static GeolocationPermissions getInstance() {
		return instance;
	}

	public void clear(String origin) {}

	public void clearAll() {}
}
