package android.media;

import android.os.Handler;
import android.os.Looper;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/* Widevine through the Widevine CDM of Chrome and Firefox, when it is installed (see
 * android_media_MediaDrm.c); otherwise no DRM scheme is available, as on a device without Widevine,
 * and apps that use MediaDrm for a device id catch UnsupportedSchemeException and fall back.
 * Streaming (temporary) licenses only, security level L3. */
public final class MediaDrm implements AutoCloseable {
	public static final String PROPERTY_DEVICE_UNIQUE_ID = "deviceUniqueId";
	public static final String PROPERTY_VENDOR = "vendor";
	public static final String PROPERTY_VERSION = "version";
	public static final String PROPERTY_DESCRIPTION = "description";
	public static final String PROPERTY_ALGORITHMS = "algorithms";

	public static final int KEY_TYPE_STREAMING = 1;
	public static final int KEY_TYPE_OFFLINE = 2;
	public static final int KEY_TYPE_RELEASE = 3;

	public static final int EVENT_PROVISION_REQUIRED = 1;
	public static final int EVENT_KEY_REQUIRED = 2;
	public static final int EVENT_KEY_EXPIRED = 3;
	public static final int EVENT_VENDOR_DEFINED = 4;
	public static final int EVENT_SESSION_RECLAIMED = 5;

	public static final int SECURITY_LEVEL_UNKNOWN = 0;
	public static final int SECURITY_LEVEL_SW_SECURE_CRYPTO = 1;
	public static final int SECURITY_LEVEL_SW_SECURE_DECODE = 2;
	public static final int SECURITY_LEVEL_HW_SECURE_CRYPTO = 3;
	public static final int SECURITY_LEVEL_HW_SECURE_DECODE = 4;
	public static final int SECURITY_LEVEL_HW_SECURE_ALL = 5;

	public static final int HDCP_LEVEL_UNKNOWN = 0;
	public static final int HDCP_NONE = 1;

	static final UUID WIDEVINE_UUID = new UUID(0xedef8ba979d64aceL, 0xa3c827dcd51d21edL);

	/* the app's session ids, for the CDM's: the CDM makes a session only with the first request */
	private static final HashMap<String, String> cdmSessions = new HashMap<>();
	private static final AtomicInteger nextSession = new AtomicInteger(1);

	private OnKeyStatusChangeListener keyStatusListener;
	private Handler eventHandler;
	private final HashMap<String, String> properties = new HashMap<>();

	private static boolean isWidevine(UUID uuid) {
		return WIDEVINE_UUID.equals(uuid);
	}

	public static boolean isCryptoSchemeSupported(UUID uuid) {
		return isWidevine(uuid) && native_isAvailable();
	}

	public static boolean isCryptoSchemeSupported(UUID uuid, String mimeType) {
		if (!isCryptoSchemeSupported(uuid))
			return false;
		return mimeType == null || mimeType.isEmpty() || mimeType.startsWith("video/") || mimeType.startsWith("audio/") || mimeType.equals("cenc");
	}

	public static boolean isCryptoSchemeSupported(UUID uuid, String mimeType, int securityLevel) {
		return isCryptoSchemeSupported(uuid, mimeType) && securityLevel <= SECURITY_LEVEL_SW_SECURE_DECODE;
	}

	public static List<UUID> getSupportedCryptoSchemes() {
		List<UUID> list = new ArrayList<>();
		if (native_isAvailable())
			list.add(WIDEVINE_UUID);
		return list;
	}

	public static int getMaxSecurityLevel() {
		return SECURITY_LEVEL_SW_SECURE_CRYPTO;
	}

	public MediaDrm(UUID uuid) throws UnsupportedSchemeException {
		if (!isCryptoSchemeSupported(uuid))
			throw new UnsupportedSchemeException("no DRM scheme is supported: " + uuid);
		properties.put("securityLevel", "L3");
		properties.put(PROPERTY_VENDOR, "Google");
		String version = native_getVersion();
		properties.put(PROPERTY_VERSION, version != null ? version : "");
		properties.put(PROPERTY_DESCRIPTION, "Widevine CDM");
		properties.put(PROPERTY_ALGORITHMS, "AES/CBC/NoPadding,HmacSHA256");
		properties.put("systemId", "4464");
		properties.put("hdcpLevel", "HDCP-1.4");
		properties.put("maxHdcpLevel", "HDCP-1.4");
		properties.put("oemCryptoApiVersion", "16");
	}

	public byte[] openSession() throws NotProvisionedException, ResourceBusyException {
		return ("atl-" + nextSession.getAndIncrement()).getBytes(StandardCharsets.UTF_8);
	}

	public byte[] openSession(int level) throws NotProvisionedException, ResourceBusyException {
		return openSession();
	}

	public void closeSession(byte[] sessionId) {
		String cdmSession;
		synchronized (cdmSessions) {
			cdmSession = cdmSessions.remove(key(sessionId));
		}
		if (cdmSession != null)
			native_close(cdmSession);
	}

	static String key(byte[] sessionId) {
		return new String(sessionId, StandardCharsets.UTF_8);
	}

	/* the CDM session of an app session, or null before the first key request */
	static String cdmSession(byte[] sessionId) {
		synchronized (cdmSessions) {
			return cdmSessions.get(key(sessionId));
		}
	}

	/* a pssh box from the init data: Android takes a box or only its Widevine data */
	private static byte[] psshBox(byte[] init) {
		if (init.length >= 8 && init[4] == 'p' && init[5] == 's' && init[6] == 's' && init[7] == 'h')
			return init;
		ByteBuffer box = ByteBuffer.allocate(32 + init.length);
		box.putInt(32 + init.length);
		box.put(new byte[] {'p', 's', 's', 'h'});
		box.putInt(0);
		box.putLong(WIDEVINE_UUID.getMostSignificantBits());
		box.putLong(WIDEVINE_UUID.getLeastSignificantBits());
		box.putInt(init.length);
		box.put(init);
		return box.array();
	}

	public KeyRequest getKeyRequest(byte[] scope, byte[] init, String mimeType, int keyType, HashMap<String, String> optionalParameters) throws NotProvisionedException {
		if (keyType != KEY_TYPE_STREAMING)
			throw new IllegalStateException("only streaming licenses are supported");
		String cdmSession = cdmSession(scope);
		if (cdmSession != null) {
			/* a renewal, or the request again */
			byte[] message = native_takeMessage(cdmSession);
			return new KeyRequest(message != null ? message : new byte[0], "", KeyRequest.REQUEST_TYPE_RENEWAL);
		}
		if (init == null)
			throw new IllegalArgumentException("no init data for a new session");
		Object[] result = native_generateRequest(psshBox(init));
		synchronized (cdmSessions) {
			cdmSessions.put(key(scope), (String)result[0]);
		}
		return new KeyRequest((byte[])result[1], "", KeyRequest.REQUEST_TYPE_INITIAL);
	}

	public byte[] provideKeyResponse(byte[] scope, byte[] response) throws NotProvisionedException, DeniedByServerException {
		String cdmSession = cdmSession(scope);
		if (cdmSession == null)
			throw new IllegalStateException("no key request for this session");
		String error = native_update(cdmSession, response);
		if (error != null)
			throw new DeniedByServerException(error);
		/* the keys are usable now */
		final OnKeyStatusChangeListener listener = keyStatusListener;
		if (listener != null) {
			final byte[] session = scope;
			Runnable notify = new Runnable() {
				@Override
				public void run() {
					listener.onKeyStatusChange(MediaDrm.this, session, new ArrayList<KeyStatus>(), true);
				}
			};
			if (eventHandler != null)
				eventHandler.post(notify);
			else
				notify.run();
		}
		return new byte[0];
	}

	public void restoreKeys(byte[] sessionId, byte[] keySetId) {
		throw new IllegalStateException("offline licenses are not supported");
	}

	public void removeKeys(byte[] sessionId) {}

	public HashMap<String, String> queryKeyStatus(byte[] sessionId) {
		HashMap<String, String> status = new HashMap<>();
		status.put("LicenseType", "Streaming");
		status.put("PlayAllowed", "True");
		status.put("PersistAllowed", "False");
		status.put("RenewAllowed", "True");
		status.put("LicenseDurationRemaining", String.valueOf(Long.MAX_VALUE / 1000));
		status.put("PlaybackDurationRemaining", String.valueOf(Long.MAX_VALUE / 1000));
		return status;
	}

	/* the CDM needs no provisioning */
	public ProvisionRequest getProvisionRequest() {
		return new ProvisionRequest(new byte[0], "");
	}

	public void provideProvisionResponse(byte[] response) throws DeniedByServerException {}

	public String getPropertyString(String propertyName) {
		String value = properties.get(propertyName);
		if (value == null)
			throw new IllegalArgumentException("unknown property " + propertyName);
		return value;
	}

	public void setPropertyString(String propertyName, String value) {
		properties.put(propertyName, value);
	}

	/* a stable id per user and app, as the CDM has none to give out */
	public byte[] getPropertyByteArray(String propertyName) {
		if (!PROPERTY_DEVICE_UNIQUE_ID.equals(propertyName))
			throw new IllegalArgumentException("unknown property " + propertyName);
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			String home = System.getProperty("user.home", "");
			return digest.digest(("atl-widevine:" + home + ":" + android.app.ActivityThread.currentPackageName()).getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			return new byte[32];
		}
	}

	public void setPropertyByteArray(String propertyName, byte[] value) {}

	public int getSecurityLevel(byte[] sessionId) {
		return SECURITY_LEVEL_SW_SECURE_CRYPTO;
	}

	public int getConnectedHdcpLevel() {
		return HDCP_NONE;
	}

	public int getMaxHdcpLevel() {
		return HDCP_NONE;
	}

	public int getOpenSessionCount() {
		synchronized (cdmSessions) {
			return cdmSessions.size();
		}
	}

	public int getMaxSessionCount() {
		return 16;
	}

	public boolean requiresSecureDecoder(String mime) {
		return false;
	}

	public boolean requiresSecureDecoder(String mime, int level) {
		return false;
	}

	public List<byte[]> getOfflineLicenseKeySetIds() {
		return new ArrayList<>();
	}

	public android.os.PersistableBundle getMetrics() {
		return new android.os.PersistableBundle();
	}

	/* the CDM's events (renewals, expiry) are not passed on yet */
	public void setOnEventListener(OnEventListener listener) {}

	public void setOnEventListener(OnEventListener listener, Handler handler) {}

	public void setOnKeyStatusChangeListener(OnKeyStatusChangeListener listener, Handler handler) {
		keyStatusListener = listener;
		eventHandler = handler != null ? handler : new Handler(Looper.getMainLooper());
	}

	public void setOnExpirationUpdateListener(OnExpirationUpdateListener listener, Handler handler) {}

	public void setOnSessionLostStateListener(OnSessionLostStateListener listener, Handler handler) {}

	public void clearOnEventListener() {}

	public void clearOnKeyStatusChangeListener() {
		keyStatusListener = null;
	}

	public void clearOnExpirationUpdateListener() {}

	public void clearOnSessionLostStateListener() {}

	public void close() {}

	public void release() {}

	public static final class KeyRequest {
		public static final int REQUEST_TYPE_UNKNOWN = -1;
		public static final int REQUEST_TYPE_INITIAL = 0;
		public static final int REQUEST_TYPE_RENEWAL = 1;
		public static final int REQUEST_TYPE_RELEASE = 2;
		public static final int REQUEST_TYPE_NONE = 3;
		public static final int REQUEST_TYPE_UPDATE = 4;

		private final byte[] data;
		private final String defaultUrl;
		private final int requestType;

		KeyRequest(byte[] data, String defaultUrl, int requestType) {
			this.data = data;
			this.defaultUrl = defaultUrl;
			this.requestType = requestType;
		}

		public byte[] getData() { return data; }
		public String getDefaultUrl() { return defaultUrl; }
		public int getRequestType() { return requestType; }
	}

	public static final class ProvisionRequest {
		private final byte[] data;
		private final String defaultUrl;

		ProvisionRequest(byte[] data, String defaultUrl) {
			this.data = data;
			this.defaultUrl = defaultUrl;
		}

		public byte[] getData() { return data; }
		public String getDefaultUrl() { return defaultUrl; }
	}

	public static final class KeyStatus {
		public static final int STATUS_USABLE = 0;
		public static final int STATUS_EXPIRED = 1;
		public static final int STATUS_OUTPUT_NOT_ALLOWED = 2;
		public static final int STATUS_PENDING = 3;
		public static final int STATUS_INTERNAL_ERROR = 4;
		public static final int STATUS_USABLE_IN_FUTURE = 5;

		private final byte[] keyId;
		private final int statusCode;

		KeyStatus(byte[] keyId, int statusCode) {
			this.keyId = keyId;
			this.statusCode = statusCode;
		}

		public byte[] getKeyId() { return keyId; }
		public int getStatusCode() { return statusCode; }
	}

	public static final class MediaDrmStateException extends IllegalStateException {
		public MediaDrmStateException(String detailMessage) { super(detailMessage); }
		public String getDiagnosticInfo() { return getMessage(); }
		public int getErrorCode() { return 0; }
	}

	public static class SessionException extends RuntimeException {
		public static final int ERROR_UNKNOWN = 0;
		public static final int ERROR_RESOURCE_CONTENTION = 1;

		public SessionException(int errorCode, String detailMessage) { super(detailMessage); }
		public int getErrorCode() { return ERROR_UNKNOWN; }
	}

	public interface OnEventListener {
		void onEvent(MediaDrm md, byte[] sessionId, int event, int extra, byte[] data);
	}

	public interface OnKeyStatusChangeListener {
		void onKeyStatusChange(MediaDrm md, byte[] sessionId, List<KeyStatus> keyInformation, boolean hasNewUsableKey);
	}

	public interface OnExpirationUpdateListener {
		void onExpirationUpdate(MediaDrm md, byte[] sessionId, long expirationTime);
	}

	public interface OnSessionLostStateListener {
		void onSessionLostState(MediaDrm md, byte[] sessionId);
	}

	private static native boolean native_isAvailable();
	private static native String native_getVersion();
	private static native Object[] native_generateRequest(byte[] initData);
	private static native byte[] native_takeMessage(String cdmSession);
	private static native String native_update(String cdmSession, byte[] response);
	private static native void native_close(String cdmSession);
	static native int native_decrypt(ByteBuffer buffer, int offset, int size, byte[] keyId, byte[] iv, int mode,
	                                 int[] clearBytes, int[] cipherBytes, int numSubsamples, int cryptBlocks, int skipBlocks, long timestamp);
}
