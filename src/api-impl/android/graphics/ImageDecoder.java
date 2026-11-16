package android.graphics;

import android.content.ContentResolver;
import android.content.res.Resources;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Size;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * ImageDecoder (API 28) on top of BitmapFactory. Animated images decode to their first frame.
 */
public final class ImageDecoder implements AutoCloseable {

	public static final int ALLOCATOR_DEFAULT = 0;
	public static final int ALLOCATOR_SOFTWARE = 1;
	public static final int ALLOCATOR_SHARED_MEMORY = 2;
	public static final int ALLOCATOR_HARDWARE = 3;

	public static abstract class Source {
		Resources getResources() { return null; }
		abstract Bitmap decode(BitmapFactory.Options opts) throws IOException;
	}

	public static class ImageInfo {
		private final Size size;
		private final String mimeType;

		ImageInfo(Size size, String mimeType) {
			this.size = size;
			this.mimeType = mimeType;
		}

		public Size getSize() { return size; }

		public String getMimeType() { return mimeType; }

		public boolean isAnimated() { return false; }
	}

	public static interface OnHeaderDecodedListener {
		public void onHeaderDecoded(ImageDecoder decoder, ImageInfo info, Source source);
	}

	private int targetWidth = -1;
	private int targetHeight = -1;
	private boolean mutableRequired;

	private ImageDecoder() {}

	public static Source createSource(final Resources res, final int resId) {
		return new Source() {
			@Override
			Resources getResources() { return res; }
			@Override
			Bitmap decode(BitmapFactory.Options opts) {
				return BitmapFactory.decodeResource(res, resId, opts);
			}
		};
	}

	public static Source createSource(final ContentResolver cr, final Uri uri) {
		return new Source() {
			@Override
			Bitmap decode(BitmapFactory.Options opts) throws IOException {
				try (InputStream is = cr.openInputStream(uri)) {
					if (is == null)
						throw new FileNotFoundException(String.valueOf(uri));
					return BitmapFactory.decodeStream(is, null, opts);
				}
			}
		};
	}

	public static Source createSource(final File file) {
		return new Source() {
			@Override
			Bitmap decode(BitmapFactory.Options opts) {
				return BitmapFactory.decodeFile(file.getPath(), opts);
			}
		};
	}

	public static Source createSource(ByteBuffer buffer) {
		ByteBuffer copy = buffer.slice();
		final byte[] data = new byte[copy.remaining()];
		copy.get(data);
		return new Source() {
			@Override
			Bitmap decode(BitmapFactory.Options opts) {
				return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
			}
		};
	}

	public void setTargetSize(int width, int height) {
		if (width <= 0 || height <= 0)
			throw new IllegalArgumentException("Dimensions must be positive! provided (" + width + ", " + height + ")");
		targetWidth = width;
		targetHeight = height;
	}

	public void setAllocator(int allocator) {}

	public void setMutableRequired(boolean mutable) {
		mutableRequired = mutable;
	}

	public static Bitmap decodeBitmap(Source src, OnHeaderDecodedListener listener) throws IOException {
		ImageDecoder decoder = new ImageDecoder();
		BitmapFactory.Options opts = new BitmapFactory.Options();
		if (listener != null) {
			opts.inJustDecodeBounds = true;
			src.decode(opts);
			if (opts.outWidth <= 0 || opts.outHeight <= 0)
				throw new IOException("Failed to decode the image header");
			listener.onHeaderDecoded(decoder, new ImageInfo(new Size(opts.outWidth, opts.outHeight), opts.outMimeType), src);
			opts.inJustDecodeBounds = false;
		}
		opts.inMutable = decoder.mutableRequired;
		Bitmap bitmap = src.decode(opts);
		if (bitmap == null)
			throw new IOException("Failed to decode the image");
		if (decoder.targetWidth > 0 && (decoder.targetWidth != bitmap.getWidth() || decoder.targetHeight != bitmap.getHeight()))
			bitmap = Bitmap.createScaledBitmap(bitmap, decoder.targetWidth, decoder.targetHeight, true);
		return bitmap;
	}

	public static Bitmap decodeBitmap(Source src) throws IOException {
		return decodeBitmap(src, null);
	}

	public static Drawable decodeDrawable(Source src, OnHeaderDecodedListener listener) throws IOException {
		return new BitmapDrawable(src.getResources(), decodeBitmap(src, listener));
	}

	public static Drawable decodeDrawable(Source src) throws IOException {
		return decodeDrawable(src, null);
	}

	@Override
	public void close() {}
}
