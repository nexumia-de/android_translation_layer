package android.content;

import android.net.Uri;
import android.os.Bundle;

public class ContentProviderResult {
	public final Uri uri;
	public final Integer count;
	public final Bundle extras;
	public final Throwable exception;

	public ContentProviderResult(Uri uri) {
		this.uri = uri;
		this.count = null;
		this.extras = null;
		this.exception = null;
	}

	public ContentProviderResult(int count) {
		this.uri = null;
		this.count = count;
		this.extras = null;
		this.exception = null;
	}

	public ContentProviderResult(Bundle extras) {
		this.uri = null;
		this.count = null;
		this.extras = extras;
		this.exception = null;
	}

	public ContentProviderResult(Throwable exception) {
		this.uri = null;
		this.count = null;
		this.extras = null;
		this.exception = exception;
	}

	@Override
	public String toString() {
		if (uri != null)
			return "ContentProviderResult(uri=" + uri + ")";
		if (count != null)
			return "ContentProviderResult(count=" + count + ")";
		return "ContentProviderResult(" + (extras != null ? "extras=" + extras : "exception=" + exception) + ")";
	}
}
