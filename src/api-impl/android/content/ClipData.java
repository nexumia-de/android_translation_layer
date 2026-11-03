package android.content;

import android.net.Uri;
import java.util.ArrayList;

/* Clipboard data: a description and a list of items, as on Android. */
public class ClipData {

	public static class Item {
		final CharSequence mText;
		final String mHtmlText;
		final Intent mIntent;
		final Uri mUri;

		public Item(CharSequence text) {
			this(text, null, null, null);
		}

		public Item(CharSequence text, String htmlText) {
			this(text, htmlText, null, null);
		}

		public Item(Intent intent) {
			this(null, null, intent, null);
		}

		public Item(Uri uri) {
			this(null, null, null, uri);
		}

		public Item(CharSequence text, Intent intent, Uri uri) {
			this(text, null, intent, uri);
		}

		public Item(CharSequence text, String htmlText, Intent intent, Uri uri) {
			mText = text;
			mHtmlText = htmlText;
			mIntent = intent;
			mUri = uri;
		}

		public CharSequence getText() {
			return mText;
		}

		public String getHtmlText() {
			return mHtmlText;
		}

		public Intent getIntent() {
			return mIntent;
		}

		public Uri getUri() {
			return mUri;
		}

		public CharSequence coerceToText(Context context) {
			if (mText != null)
				return mText;
			if (mUri != null)
				return mUri.toString();
			if (mIntent != null)
				return mIntent.toString();
			return "";
		}
	}

	final ClipDescription mClipDescription;
	final ArrayList<Item> mItems = new ArrayList<>();
	String text;

	public ClipData(ClipDescription description, Item item) {
		mClipDescription = description;
		if (item != null)
			addItem(item);
	}

	public ClipData(CharSequence label, String[] mimeTypes, Item item) {
		this(new ClipDescription(label, mimeTypes), item);
	}

	public static ClipData newPlainText(CharSequence label, CharSequence text) {
		return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_PLAIN}, new Item(text));
	}

	public static ClipData newHtmlText(CharSequence label, CharSequence text, String htmlText) {
		return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_HTML}, new Item(text, htmlText));
	}

	public static ClipData newRawUri(CharSequence label, Uri uri) {
		return new ClipData(label, new String[] {ClipDescription.MIMETYPE_TEXT_URILIST}, new Item(uri));
	}

	public static ClipData newUri(ContentResolver resolver, CharSequence label, Uri uri) {
		return newRawUri(label, uri);
	}

	public void addItem(Item item) {
		mItems.add(item);
		if (text == null && item != null) {
			CharSequence t = item.coerceToText(null);
			text = t != null ? t.toString() : null;
		}
	}

	public void addItem(ContentResolver resolver, Item item) {
		addItem(item);
	}

	public ClipDescription getDescription() {
		return mClipDescription;
	}

	public Item getItemAt(int index) {
		return mItems.get(index);
	}

	public int getItemCount() {
		return mItems.size();
	}
}
