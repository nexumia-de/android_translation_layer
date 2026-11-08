package android.content;

import android.atl.ATLLoadedApp;
import android.atl.ATLMediaContentProvider;
import android.atl.ATLProvider;
import android.atl.annotation.Export;
import android.content.pm.PackageParser;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public abstract class ContentProvider {

	static final HashMap<String, ATLProvider> atl_providers = new HashMap<>();

	@Export
	static void createContentProviders() {
		atl_providers.put("media", new ATLProvider(new ATLMediaContentProvider()));
		ATLLoadedApp primary = ATLLoadedApp.getPrimaryApplication();
		for (PackageParser.Provider provider_parsed : primary.pkg.providers) {
			String process_name = provider_parsed.info.processName;
			if (process_name != null && process_name.contains(":")) {
				/* NOTE: even if it doesn't contain `:`, if it's not null we probably
				 * need to check what it's requesting; `:` means it wants us to spawn
				 * a new process, which we currently don't support */
				System.out.println("not creating provider " + provider_parsed.className + ", it wants to be started in a new process (" + process_name + ")");
				continue;
			}
			atl_providers.put(provider_parsed.info.authority,
			                  new ATLProvider(primary, provider_parsed));
		}
		// getContentProvider() initializes the content providers,
		// we also opportunistically remove the ones that failed to load
		atl_providers.values().removeIf(new Predicate<ATLProvider>() {
			@Override
			public boolean test(ATLProvider atlProvider) {
				return atlProvider.getContentProvider() == null;
			}
		});
	}

	static ContentProvider atl_get_content_provider(String authority) {
		ATLProvider atlProvider = atl_providers.get(authority);
		return atlProvider == null ? null : atlProvider.getContentProvider();
	}

	public boolean onCreate() { return false; }

	public Bundle call(String method, String arg, Bundle extras) {
		return null;
	}

	public Bundle call(String authority, String method, String arg, Bundle extras) {
		return call(method, arg, extras);
	}

	public Context getContext() {
		return ATLLoadedApp.getPrimaryApplication().getApplication();
	}

	public ContentProviderResult[] applyBatch(String authority, ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
		return applyBatch(operations);
	}

	public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
		final int numOperations = operations.size();
		final ContentProviderResult[] results = new ContentProviderResult[numOperations];
		for (int i = 0; i < numOperations; i++) {
			results[i] = operations.get(i).apply(this, results, i);
		}
		return results;
	}

	public abstract Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder);

	public abstract Uri insert(Uri uri, ContentValues values);

	public abstract int update(Uri uri, ContentValues values, String selection, String[] selectionArgs);

	public abstract int delete(Uri uri, String selection, String[] selectionArgs);

	public abstract String getType(Uri uri);

	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		throw new FileNotFoundException("No files supported by provider at " + uri);
	}

	public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
		return openFile(uri, mode);
	}

	public AssetFileDescriptor openAssetFile(Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
		return openAssetFile(uri, mode);
	}

	public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
		return null;
	}

	public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts) throws FileNotFoundException {
		if ("*/*".equals(mimeTypeFilter)) {
			return openAssetFile(uri, "r");
		}
		String baseType = getType(uri);
		if (baseType != null && ClipDescription.compareMimeTypes(baseType, mimeTypeFilter)) {
			return openAssetFile(uri, "r");
		}
		throw new FileNotFoundException("Can't open " + uri + " as type " + mimeTypeFilter);
	}

	public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts, CancellationSignal signal) throws FileNotFoundException {
		return openTypedAssetFile(uri, mimeTypeFilter, opts);
	}

	public interface PipeDataWriter<T> {
		public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType, Bundle opts, T args);
	}

	/* as on Android: the writer runs on a background thread and fills the
	 * write end of a pipe, the caller gets the read end */
	public <T> ParcelFileDescriptor openPipeHelper(final Uri uri, final String mimeType, final Bundle opts, final T args, final PipeDataWriter<T> func) throws FileNotFoundException {
		try {
			final ParcelFileDescriptor[] fds = ParcelFileDescriptor.createPipe();
			new Thread("ContentProvider pipe") {
				@Override
				public void run() {
					func.writeDataToPipe(fds[1], uri, mimeType, opts, args);
					try {
						fds[1].close();
					} catch (IOException e) {
					}
				}
			}.start();
			return fds[0];
		} catch (IOException e) {
			throw new FileNotFoundException("failure making pipe");
		}
	}

	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
		return query(uri, projection, selection, selectionArgs, sortOrder);
	}

	public Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
		if (queryArgs == null)
			queryArgs = Bundle.EMPTY;
		String sortOrder = queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER);
		return query(uri, projection, queryArgs.getString(ContentResolver.QUERY_ARG_SQL_SELECTION),
		             queryArgs.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS), sortOrder, cancellationSignal);
	}

	public int bulkInsert(Uri uri, ContentValues[] values) {
		for (ContentValues v : values) {
			insert(uri, v);
		}
		return values.length;
	}

	public boolean isTemporary() {
		return false;
	}

	public void onConfigurationChanged(Configuration newConfig) {}

	public void onLowMemory() {}

	public void onTrimMemory(int level) {}

	public void shutdown() {}

	public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
		ParcelFileDescriptor fd = openFile(uri, mode);
		return fd != null ? new AssetFileDescriptor(fd, 0, -1) : null;
	}

	public void attachInfo(Context context, ProviderInfo provider) {}

	public String getCallingPackage() {
		return ATLLoadedApp.getPrimaryApplication().pkg.packageName;
	}
}
