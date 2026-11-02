package android.content;

import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/* An insert, update, delete or assert for ContentProvider.applyBatch, with
 * the back references of Android's class (values and selection arguments
 * taken from the results of earlier operations in the batch). */
public class ContentProviderOperation {
	private static final int TYPE_INSERT = 1;
	private static final int TYPE_UPDATE = 2;
	private static final int TYPE_DELETE = 3;
	private static final int TYPE_ASSERT = 4;

	private final int mType;
	private final Uri mUri;
	private final String mSelection;
	private final String[] mSelectionArgs;
	private final ContentValues mValues;
	private final Integer mExpectedCount;
	private final ContentValues mValuesBackReferences;
	private final Map<Integer, Integer> mSelectionArgsBackReferences;
	private final boolean mYieldAllowed;

	private ContentProviderOperation(Builder builder) {
		mType = builder.mType;
		mUri = builder.mUri;
		mValues = builder.mValues;
		mSelection = builder.mSelection;
		mSelectionArgs = builder.mSelectionArgs;
		mExpectedCount = builder.mExpectedCount;
		mSelectionArgsBackReferences = builder.mSelectionArgsBackReferences;
		mValuesBackReferences = builder.mValuesBackReferences;
		mYieldAllowed = builder.mYieldAllowed;
	}

	public static Builder newInsert(Uri uri) {
		return new Builder(TYPE_INSERT, uri);
	}

	public static Builder newUpdate(Uri uri) {
		return new Builder(TYPE_UPDATE, uri);
	}

	public static Builder newDelete(Uri uri) {
		return new Builder(TYPE_DELETE, uri);
	}

	public static Builder newAssertQuery(Uri uri) {
		return new Builder(TYPE_ASSERT, uri);
	}

	public Uri getUri() {
		return mUri;
	}

	public boolean isYieldAllowed() {
		return mYieldAllowed;
	}

	public boolean isInsert() {
		return mType == TYPE_INSERT;
	}

	public boolean isUpdate() {
		return mType == TYPE_UPDATE;
	}

	public boolean isDelete() {
		return mType == TYPE_DELETE;
	}

	public boolean isAssertQuery() {
		return mType == TYPE_ASSERT;
	}

	public boolean isWriteOperation() {
		return mType != TYPE_ASSERT;
	}

	public boolean isReadOperation() {
		return mType == TYPE_ASSERT;
	}

	public ContentProviderResult apply(ContentProvider provider, ContentProviderResult[] backRefs, int numBackRefs) throws OperationApplicationException {
		ContentValues values = resolveValueBackReferences(backRefs, numBackRefs);
		String[] selectionArgs = resolveSelectionArgsBackReferences(backRefs, numBackRefs);

		if (mType == TYPE_INSERT) {
			Uri newUri = provider.insert(mUri, values);
			if (newUri == null)
				throw new OperationApplicationException("insert failed");
			return new ContentProviderResult(newUri);
		}

		int numRows;
		if (mType == TYPE_DELETE) {
			numRows = provider.delete(mUri, mSelection, selectionArgs);
		} else if (mType == TYPE_UPDATE) {
			numRows = provider.update(mUri, values, mSelection, selectionArgs);
		} else {
			String[] projection = null;
			if (values != null) {
				projection = values.keySet().toArray(new String[0]);
			}
			Cursor cursor = provider.query(mUri, projection, mSelection, selectionArgs, null);
			try {
				numRows = cursor.getCount();
				if (projection != null) {
					while (cursor.moveToNext()) {
						for (int i = 0; i < projection.length; i++) {
							String cursorValue = cursor.getString(i);
							Object expected = values.get(projection[i]);
							String expectedValue = expected == null ? null : expected.toString();
							if (cursorValue == null ? expectedValue != null : !cursorValue.equals(expectedValue)) {
								throw new OperationApplicationException("Found value " + cursorValue + " when expected " + expectedValue + " for column " + projection[i]);
							}
						}
					}
				}
			} finally {
				cursor.close();
			}
		}

		if (mExpectedCount != null && mExpectedCount != numRows) {
			throw new OperationApplicationException("wrong number of rows: " + numRows);
		}
		return new ContentProviderResult(numRows);
	}

	public ContentValues resolveValueBackReferences(ContentProviderResult[] backRefs, int numBackRefs) {
		if (mValuesBackReferences == null) {
			return mValues;
		}
		final ContentValues values = mValues == null ? new ContentValues() : new ContentValues(mValues);
		for (String key : mValuesBackReferences.keySet()) {
			Integer backRefIndex = mValuesBackReferences.getAsInteger(key);
			values.put(key, backRefToValue(backRefs, numBackRefs, backRefIndex));
		}
		return values;
	}

	public String[] resolveSelectionArgsBackReferences(ContentProviderResult[] backRefs, int numBackRefs) {
		if (mSelectionArgsBackReferences == null) {
			return mSelectionArgs;
		}
		String[] newArgs = new String[mSelectionArgs.length];
		System.arraycopy(mSelectionArgs, 0, newArgs, 0, mSelectionArgs.length);
		for (Map.Entry<Integer, Integer> entry : mSelectionArgsBackReferences.entrySet()) {
			newArgs[entry.getKey()] = String.valueOf(backRefToValue(backRefs, numBackRefs, entry.getValue()));
		}
		return newArgs;
	}

	private static long backRefToValue(ContentProviderResult[] backRefs, int numBackRefs, Integer backRefIndex) {
		if (backRefIndex >= numBackRefs) {
			throw new ArrayIndexOutOfBoundsException("asked for back ref " + backRefIndex + " but there are only " + numBackRefs + " back refs");
		}
		ContentProviderResult backRef = backRefs[backRefIndex];
		return backRef.uri != null ? ContentUris.parseId(backRef.uri) : backRef.count;
	}

	@Override
	public String toString() {
		return "ContentProviderOperation(type=" + mType + " uri=" + mUri + ")";
	}

	public static class Builder {
		private final int mType;
		private final Uri mUri;
		private String mSelection;
		private String[] mSelectionArgs;
		private ContentValues mValues;
		private Integer mExpectedCount;
		private ContentValues mValuesBackReferences;
		private Map<Integer, Integer> mSelectionArgsBackReferences;
		private boolean mYieldAllowed;

		private Builder(int type, Uri uri) {
			if (uri == null)
				throw new IllegalArgumentException("uri must not be null");
			mType = type;
			mUri = uri;
		}

		public ContentProviderOperation build() {
			if (mType == TYPE_UPDATE && (mValues == null || mValues.size() == 0) && (mValuesBackReferences == null || mValuesBackReferences.size() == 0)) {
				throw new IllegalArgumentException("Empty values");
			}
			if (mType == TYPE_ASSERT && (mValues == null || mValues.size() == 0) && (mValuesBackReferences == null || mValuesBackReferences.size() == 0) && mExpectedCount == null) {
				throw new IllegalArgumentException("Empty values");
			}
			return new ContentProviderOperation(this);
		}

		private ContentValues values() {
			if (mValues == null)
				mValues = new ContentValues();
			return mValues;
		}

		public Builder withValueBackReferences(ContentValues backReferences) {
			mValuesBackReferences = backReferences;
			return this;
		}

		public Builder withValueBackReference(String key, int previousResult) {
			if (mValuesBackReferences == null)
				mValuesBackReferences = new ContentValues();
			mValuesBackReferences.put(key, previousResult);
			return this;
		}

		public Builder withSelectionBackReference(int selectionArgIndex, int previousResult) {
			if (mSelectionArgsBackReferences == null)
				mSelectionArgsBackReferences = new HashMap<Integer, Integer>();
			mSelectionArgsBackReferences.put(selectionArgIndex, previousResult);
			return this;
		}

		public Builder withValues(ContentValues values) {
			values().putAll(values);
			return this;
		}

		public Builder withValue(String key, Object value) {
			values().putObjectInternal(key, value);
			return this;
		}

		public Builder withSelection(String selection, String[] selectionArgs) {
			mSelection = selection;
			if (selectionArgs == null) {
				mSelectionArgs = null;
			} else {
				mSelectionArgs = new String[selectionArgs.length];
				System.arraycopy(selectionArgs, 0, mSelectionArgs, 0, selectionArgs.length);
			}
			return this;
		}

		public Builder withExpectedCount(int count) {
			mExpectedCount = count;
			return this;
		}

		public Builder withYieldAllowed(boolean yieldAllowed) {
			mYieldAllowed = yieldAllowed;
			return this;
		}
	}
}
