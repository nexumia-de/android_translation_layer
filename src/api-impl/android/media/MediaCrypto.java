package android.media;

import java.util.UUID;

/* the link of a MediaCodec to a MediaDrm session, whose keys decrypt the samples queued with
 * queueSecureInputBuffer; the decoders are ATL's own, so no secure decoder is ever required */
public final class MediaCrypto {
	private byte[] sessionId;

	public static boolean isCryptoSchemeSupported(UUID uuid) {
		return MediaDrm.isCryptoSchemeSupported(uuid);
	}

	public MediaCrypto(UUID uuid, byte[] initData) throws MediaCryptoException {
		if (!MediaDrm.isCryptoSchemeSupported(uuid))
			throw new MediaCryptoException("unsupported scheme " + uuid);
		sessionId = initData;
	}

	public boolean requiresSecureDecoderComponent(String mime) {
		return false;
	}

	public void setMediaDrmSession(byte[] sessionId) throws MediaCryptoException {
		this.sessionId = sessionId;
	}

	byte[] getSessionId() {
		return sessionId;
	}

	public void release() {}
}
