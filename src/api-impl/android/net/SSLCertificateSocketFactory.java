package android.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/* Delegates to the default TLS socket factory (wolfSSL). The session cache is ignored (see
 * SSLSessionCache); the handshake timeout is applied as the socket timeout during the handshake.
 * Bumble gets its server connection's factory from getDefault(int, SSLSessionCache). */
public class SSLCertificateSocketFactory extends SSLSocketFactory {

	private final int handshakeTimeoutMillis;
	private SSLSocketFactory delegate;

	@Deprecated
	public SSLCertificateSocketFactory(int handshakeTimeoutMillis) {
		this.handshakeTimeoutMillis = handshakeTimeoutMillis;
	}

	public static SocketFactory getDefault(int handshakeTimeoutMillis) {
		return new SSLCertificateSocketFactory(handshakeTimeoutMillis);
	}

	public static SSLSocketFactory getDefault(int handshakeTimeoutMillis, SSLSessionCache cache) {
		return new SSLCertificateSocketFactory(handshakeTimeoutMillis);
	}

	/* no insecure variant: certificates are always verified */
	public static SSLSocketFactory getInsecure(int handshakeTimeoutMillis, SSLSessionCache cache) {
		return new SSLCertificateSocketFactory(handshakeTimeoutMillis);
	}

	private synchronized SSLSocketFactory getDelegate() {
		if (delegate == null) {
			try {
				delegate = SSLContext.getDefault().getSocketFactory();
			} catch (NoSuchAlgorithmException e) {
				delegate = HttpsURLConnection.getDefaultSSLSocketFactory();
			}
		}
		return delegate;
	}

	private Socket prepare(Socket socket) throws IOException {
		if (handshakeTimeoutMillis > 0 && socket instanceof SSLSocket) {
			SSLSocket ssl = (SSLSocket)socket;
			int timeout = ssl.getSoTimeout();
			ssl.setSoTimeout(handshakeTimeoutMillis);
			ssl.startHandshake();
			ssl.setSoTimeout(timeout);
		}
		return socket;
	}

	public void setHostname(Socket socket, String hostName) {}

	public void setUseSessionTickets(Socket socket, boolean useSessionTickets) {}

	public void setNpnProtocols(byte[][] npnProtocols) {}

	public void setAlpnProtocols(byte[][] protocols) {}

	public byte[] getNpnSelectedProtocol(Socket socket) {
		return null;
	}

	public byte[] getAlpnSelectedProtocol(Socket socket) {
		return null;
	}

	public void setSoWriteTimeout(Socket socket, int writeTimeoutMilliseconds) {}

	@Override
	public String[] getDefaultCipherSuites() {
		return getDelegate().getDefaultCipherSuites();
	}

	@Override
	public String[] getSupportedCipherSuites() {
		return getDelegate().getSupportedCipherSuites();
	}

	/* unconnected socket: the caller connects it, the handshake starts on first use */
	@Override
	public Socket createSocket() throws IOException {
		return getDelegate().createSocket();
	}

	@Override
	public Socket createSocket(Socket k, String host, int port, boolean close) throws IOException {
		return prepare(getDelegate().createSocket(k, host, port, close));
	}

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		return prepare(getDelegate().createSocket(host, port));
	}

	@Override
	public Socket createSocket(String host, int port, InetAddress localAddr, int localPort) throws IOException {
		return prepare(getDelegate().createSocket(host, port, localAddr, localPort));
	}

	@Override
	public Socket createSocket(InetAddress addr, int port) throws IOException {
		return prepare(getDelegate().createSocket(addr, port));
	}

	@Override
	public Socket createSocket(InetAddress addr, int port, InetAddress localAddr, int localPort) throws IOException {
		return prepare(getDelegate().createSocket(addr, port, localAddr, localPort));
	}
}
