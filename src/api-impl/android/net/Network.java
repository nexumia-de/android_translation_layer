package android.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Network {
	public long getNetworkHandle() {
		return 1L;
	}

	public InetAddress[] getAllByName(String host) throws UnknownHostException {
		return InetAddress.getAllByName(host);
	}

	public InetAddress getByName(String host) throws UnknownHostException {
		return InetAddress.getByName(host);
	}
}
