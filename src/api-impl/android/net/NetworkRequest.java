package android.net;

public class NetworkRequest {

	private final NetworkCapabilities mNetworkCapabilities;

	private NetworkRequest(NetworkCapabilities networkCapabilities) {
		mNetworkCapabilities = networkCapabilities;
	}

	public boolean hasCapability(int capability) {
		return mNetworkCapabilities.hasCapability(capability);
	}

	public boolean hasTransport(int transportType) {
		return mNetworkCapabilities.hasTransport(transportType);
	}

	public int[] getCapabilities() {
		return mNetworkCapabilities.getCapabilities();
	}

	public int[] getTransportTypes() {
		return mNetworkCapabilities.getTransportTypes();
	}

	/* like Android: every requested capability, and one of the requested transports if any */
	public boolean canBeSatisfiedBy(NetworkCapabilities nc) {
		if (nc == null)
			return false;
		for (int capability : getCapabilities()) {
			if (!nc.hasCapability(capability))
				return false;
		}
		int[] transports = getTransportTypes();
		if (transports.length == 0)
			return true;
		for (int transport : transports) {
			if (nc.hasTransport(transport))
				return true;
		}
		return false;
	}

	public static class Builder {

		private final NetworkCapabilities mNetworkCapabilities = new NetworkCapabilities();

		public Builder() {
			mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
		}

		public NetworkRequest build() {
			NetworkCapabilities copy = new NetworkCapabilities();
			for (int capability : copy.getCapabilities())
				copy.removeCapability(capability);
			for (int capability : mNetworkCapabilities.getCapabilities())
				copy.addCapability(capability);
			for (int transport : mNetworkCapabilities.getTransportTypes())
				copy.addTransportType(transport);
			return new NetworkRequest(copy);
		}

		public Builder addCapability(int capability) {
			mNetworkCapabilities.addCapability(capability);
			return this;
		}

		public Builder removeCapability(int capability) {
			mNetworkCapabilities.removeCapability(capability);
			return this;
		}

		public Builder addTransportType(int transportType) {
			mNetworkCapabilities.addTransportType(transportType);
			return this;
		}

		public Builder removeTransportType(int transportType) {
			mNetworkCapabilities.removeTransportType(transportType);
			return this;
		}
	}
}
