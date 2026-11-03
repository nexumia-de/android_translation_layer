package android.security.keystore;

import java.security.Provider;
import java.security.Security;

/* Android's default provider (Conscrypt) is named AndroidOpenSSL, and apps
 * ask for it by name, e.g. for the RSA cipher of a keystore key pair. ATL
 * has no such provider: every lookup falls through to the first other
 * provider with the service, so getInstance(..., "AndroidOpenSSL") behaves
 * like getInstance(...) without a provider. Keystore providers are skipped,
 * their services work only with keystore keys. */
public class AndroidOpenSSLProvider extends Provider {

	public AndroidOpenSSLProvider() {
		super("AndroidOpenSSL", 1.0, "Android's default provider name, served by the other providers");
	}

	@Override
	public synchronized Service getService(String type, String algorithm) {
		for (Provider provider : Security.getProviders()) {
			if (provider == this || provider.getName().startsWith("AndroidKeyStore"))
				continue;
			Service service = provider.getService(type, algorithm);
			if (service != null)
				return service;
		}
		return null;
	}
}
