package android.companion;

import android.os.Parcelable;

/* never handed out: CompanionDeviceManager has no associations */
public final class AssociationInfo implements Parcelable {
	private AssociationInfo() {}

	public int getId() {
		return 0;
	}

	public CharSequence getDisplayName() {
		return null;
	}

	public String getPackageName() {
		return null;
	}
}
