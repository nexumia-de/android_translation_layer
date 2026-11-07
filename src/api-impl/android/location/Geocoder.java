package android.location;

import android.content.Context;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/* there is no geocoding backend, so lookups fail like on an Android device without one */
public class Geocoder {

	public Geocoder(Context context) {}

	public Geocoder(Context context, Locale locale) {}

	public List<Address> getFromLocation(double latitude, double longitude, int maxResults) throws IOException {
		throw new IOException("Service not Available");
	}

	public List<Address> getFromLocationName(String locationName, int maxResults) throws IOException {
		throw new IOException("Service not Available");
	}

	public static boolean isPresent() {
		return false;
	}
}
