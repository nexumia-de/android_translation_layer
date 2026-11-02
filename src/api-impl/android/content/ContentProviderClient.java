package android.content;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class ContentProviderClient implements AutoCloseable {
	private final ContentProvider contentProvider;

	ContentProviderClient(ContentProvider contentProvider) {
		this.contentProvider = contentProvider;
	}

	public ContentProvider getLocalContentProvider() {
		return contentProvider;
	}

	public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder) throws RemoteException {
		return contentProvider.query(url, projection, selection, selectionArgs, sortOrder);
	}

	public String getType(Uri url) throws RemoteException {
		return contentProvider.getType(url);
	}

	public Uri insert(Uri url, ContentValues initialValues) throws RemoteException {
		return contentProvider.insert(url, initialValues);
	}

	public int delete(Uri url, String selection, String[] selectionArgs) throws RemoteException {
		return contentProvider.delete(url, selection, selectionArgs);
	}

	public int update(Uri url, ContentValues values, String selection, String[] selectionArgs) throws RemoteException {
		return contentProvider.update(url, values, selection, selectionArgs);
	}

	public ParcelFileDescriptor openFile(Uri url, String mode) throws RemoteException, FileNotFoundException {
		return contentProvider.openFile(url, mode);
	}

	public AssetFileDescriptor openAssetFile(Uri url, String mode) throws RemoteException, FileNotFoundException {
		return contentProvider.openAssetFile(url, mode);
	}

	public InputStream openInputStream(Uri url) throws FileNotFoundException, RemoteException {
		ParcelFileDescriptor fd = openFile(url, "r");
		return fd != null ? new ParcelFileDescriptor.AutoCloseInputStream(fd) : null;
	}

	public OutputStream openOutputStream(Uri url) throws FileNotFoundException, RemoteException {
		ParcelFileDescriptor fd = openFile(url, "w");
		return fd != null ? new ParcelFileDescriptor.AutoCloseOutputStream(fd) : null;
	}

	public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal signal) throws RemoteException {
		return contentProvider.query(url, projection, selection, selectionArgs, sortOrder, signal);
	}

	public Bundle call(String method, String arg, Bundle extras) throws RemoteException {
		return contentProvider.call(method, arg, extras);
	}

	public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws RemoteException, OperationApplicationException {
		return contentProvider.applyBatch(operations);
	}

	public int bulkInsert(Uri url, ContentValues[] values) throws RemoteException {
		return contentProvider.bulkInsert(url, values);
	}

	public boolean release() {
		return true;
	}

	@Override
	public void close() {
		release();
	}
}
