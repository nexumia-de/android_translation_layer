package android.net;

import android.content.Context;
import java.io.File;

/* ATL's TLS (wolfSSL) keeps no persistent session cache, so this only exists for apps that create
 * one: Bumble builds it for its own server connection and aborted with NoSuchMethodError */
public class SSLSessionCache {

	public SSLSessionCache(File cacheDir) {}

	public SSLSessionCache(Context context) {
		this(context.getDir("sslcache", Context.MODE_PRIVATE));
	}
}
