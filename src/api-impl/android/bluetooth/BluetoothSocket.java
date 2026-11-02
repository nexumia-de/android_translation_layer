package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/*
 * RFCOMM client socket. Not implemented yet: it needs a BlueZ Profile1
 * object that BlueZ hands the connected socket to. connect() fails as on a
 * device where the remote service is not reachable.
 */
public final class BluetoothSocket implements Closeable {
	public static final int TYPE_RFCOMM = 1;
	public static final int TYPE_SCO = 2;
	public static final int TYPE_L2CAP = 3;

	private final BluetoothDevice device;
	private final UUID uuid;
	private boolean closed;

	BluetoothSocket(BluetoothDevice device, UUID uuid, boolean secure) {
		this.device = device;
		this.uuid = uuid;
	}

	public BluetoothDevice getRemoteDevice() {
		return device;
	}

	public void connect() throws IOException {
		if (closed)
			throw new IOException("socket closed");
		throw new IOException("RFCOMM sockets are not implemented (service " + uuid + ")");
	}

	public boolean isConnected() {
		return false;
	}

	public InputStream getInputStream() throws IOException {
		throw new IOException("socket not connected");
	}

	public OutputStream getOutputStream() throws IOException {
		throw new IOException("socket not connected");
	}

	public int getConnectionType() {
		return TYPE_RFCOMM;
	}

	public int getMaxTransmitPacketSize() {
		return 0;
	}

	public int getMaxReceivePacketSize() {
		return 0;
	}

	public void close() throws IOException {
		closed = true;
	}
}
