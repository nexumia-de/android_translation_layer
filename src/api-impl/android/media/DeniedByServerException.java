package android.media;

public class DeniedByServerException extends MediaDrmException {
	public DeniedByServerException(String detailMessage) {
		super(detailMessage);
	}
}
