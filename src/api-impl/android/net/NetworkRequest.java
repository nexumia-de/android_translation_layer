package android.net;

public class NetworkRequest {

	public static class Builder {

		public NetworkRequest build() {
			return new NetworkRequest();
		}

		public Builder addCapability(int capability) {
			return this;
		}

		public Builder removeCapability(int capability) {
			return this;
		}
	}
}
