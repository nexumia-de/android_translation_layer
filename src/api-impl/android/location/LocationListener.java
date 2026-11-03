package android.location;

import android.os.Bundle;
import java.util.List;

public interface LocationListener {

	public void onLocationChanged(Location location);

	default public void onLocationChanged(List<Location> locations) {
		for (Location location : locations)
			onLocationChanged(location);
	}

	default public void onFlushComplete(int requestCode) {}

	default public void onStatusChanged(String provider, int status, Bundle extras) {}

	default public void onProviderEnabled(String provider) {}

	default public void onProviderDisabled(String provider) {}
}
