package android.media;

import java.util.UUID;

/* no DRM scheme is available (as on a device without Widevine): apps that use it for a
 * device id (MediaDrm "deviceUniqueId") catch UnsupportedSchemeException and fall back */
public final class MediaDrm implements AutoCloseable {
	public static final String PROPERTY_DEVICE_UNIQUE_ID = "deviceUniqueId";

	public static boolean isCryptoSchemeSupported(UUID uuid) {
		return false;
	}

	public static boolean isCryptoSchemeSupported(UUID uuid, String mimeType) {
		return false;
	}

	public MediaDrm(UUID uuid) throws UnsupportedSchemeException {
		throw new UnsupportedSchemeException("no DRM scheme is supported: " + uuid);
	}

	public byte[] getPropertyByteArray(String propertyName) {
		throw new IllegalStateException();
	}

	public String getPropertyString(String propertyName) {
		throw new IllegalStateException();
	}

	public void close() {}

	public void release() {}
}
