package android.app.usage;

import android.os.UserHandle;
import java.util.UUID;

public class StorageStatsManager {
	public StorageStats queryStatsForPackage(UUID storageUuid, String packageName, UserHandle user) {
		return new StorageStats();
	}
}
