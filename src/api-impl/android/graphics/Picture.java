package android.graphics;

/* Recorded into a bitmap, at a higher resolution than the picture's size so that it stays sharp when it is
 * drawn scaled up (SVG images are recorded at their document size, usually in dp). */
public class Picture {

	private static final int SCALE = 3;
	private static final int MAX_SIZE = 2048;

	private int width;
	private int height;
	private float scale = 1;
	private Bitmap bitmap;

	public Picture() {}

	public Picture(Picture src) {
		if (src != null) {
			width = src.width;
			height = src.height;
			scale = src.scale;
			bitmap = src.bitmap;
		}
	}

	public Canvas beginRecording(int width, int height) {
		this.width = width;
		this.height = height;
		scale = Math.max(1, Math.min(SCALE, Math.min((float)MAX_SIZE / Math.max(width, 1), (float)MAX_SIZE / Math.max(height, 1))));
		bitmap = Bitmap.createBitmap(Math.max(1, (int)Math.ceil(width * scale)), Math.max(1, (int)Math.ceil(height * scale)), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);
		canvas.scale(scale, scale);
		return canvas;
	}

	public void endRecording() {}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public boolean requiresHardwareAcceleration() {
		return false;
	}

	public void draw(Canvas canvas) {
		if (bitmap == null)
			return;
		canvas.drawBitmap(bitmap, null, new RectF(0, 0, width, height), null);
	}
}
