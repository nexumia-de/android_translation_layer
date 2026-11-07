package android.os;

public interface IBinder {

	public interface DeathRecipient {}

	public IInterface queryLocalInterface(String descriptor);

	public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException;

	public String getInterfaceDescriptor();

	/* binders live in the app's own process here, so they are alive as long as it runs */
	public default boolean isBinderAlive() {
		return true;
	}

	public default boolean pingBinder() {
		return true;
	}
}
