package android.media.midi;

import android.os.Bundle;

public final class MidiDeviceInfo {

	public static final int TYPE_USB = 1;
	public static final int TYPE_VIRTUAL = 2;
	public static final int TYPE_BLUETOOTH = 3;

	public static final class PortInfo {
		public static final int TYPE_INPUT = 1;
		public static final int TYPE_OUTPUT = 2;

		private final int type;
		private final int portNumber;
		private final String name;

		PortInfo(int type, int portNumber, String name) {
			this.type = type;
			this.portNumber = portNumber;
			this.name = name;
		}

		public int getType() {
			return type;
		}

		public int getPortNumber() {
			return portNumber;
		}

		public String getName() {
			return name;
		}
	}

	private final int id;
	private final int type;

	MidiDeviceInfo(int id, int type) {
		this.id = id;
		this.type = type;
	}

	public int getId() {
		return id;
	}

	public int getType() {
		return type;
	}

	public int getInputPortCount() {
		return 0;
	}

	public int getOutputPortCount() {
		return 0;
	}

	public PortInfo[] getPorts() {
		return new PortInfo[0];
	}

	public Bundle getProperties() {
		return new Bundle();
	}
}
