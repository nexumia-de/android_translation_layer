package android.media;

import java.io.Closeable;
import java.io.IOException;

/* API 23. Only the type: Bumble's dependency graph refers to it and aborted with
 * NoClassDefFoundError; ATL's players do not read from it */
public abstract class MediaDataSource implements Closeable {

	public MediaDataSource() {}

	public abstract int readAt(long position, byte[] buffer, int offset, int size) throws IOException;

	public abstract long getSize() throws IOException;
}
