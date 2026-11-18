package android.graphics;

public class BitmapShader extends Shader {

	Bitmap bitmap;
	TileMode tileX;
	TileMode tileY;

	public BitmapShader(Bitmap bitmap, TileMode tileX, TileMode tileY) {
		this.bitmap = bitmap;
		this.tileX = tileX;
		this.tileY = tileY;
	}

	public Bitmap getBitmap() {
		return bitmap;
	}
}
