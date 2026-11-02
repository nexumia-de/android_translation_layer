package android.bluetooth;

import java.io.Closeable;
import java.io.IOException;

/* never handed out: BluetoothAdapter.listenUsingRfcommWithServiceRecord throws until RFCOMM is implemented */
public final class BluetoothServerSocket implements Closeable {
	private BluetoothServerSocket() {}

	public BluetoothSocket accept() throws IOException {
		throw new IOException("RFCOMM server sockets are not implemented");
	}

	public BluetoothSocket accept(int timeout) throws IOException {
		return accept();
	}

	public void close() throws IOException {}
}
