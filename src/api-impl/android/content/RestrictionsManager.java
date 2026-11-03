package android.content;

import android.os.Bundle;

/* No device policy manages apps in ATL, so there are no restrictions. */
public class RestrictionsManager {

	public Bundle getApplicationRestrictions() {
		return new Bundle();
	}

	public boolean hasRestrictionsProvider() {
		return false;
	}
}
