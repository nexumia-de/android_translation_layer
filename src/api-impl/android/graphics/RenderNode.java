package android.graphics;

/* The public API 29 name for the node Compose and others record into. Apps with minSdk 29 or more have
 * no code path left for android.view.RenderNode, which holds the implementation */
public class RenderNode extends android.view.RenderNode {

	private RecordingCanvas canvas;

	public RenderNode(String name) {}

	public RecordingCanvas beginRecording(int width, int height) {
		canvas = (RecordingCanvas)start(width, height);
		return canvas;
	}

	public RecordingCanvas beginRecording() {
		return beginRecording(getWidth(), getHeight());
	}

	public void endRecording() {
		end(canvas);
		canvas = null;
	}

	public boolean hasDisplayList() {
		return isValid();
	}

	public boolean setPosition(int left, int top, int right, int bottom) {
		return setLeftTopRightBottom(left, top, right, bottom);
	}

	public boolean setPosition(Rect position) {
		return setLeftTopRightBottom(position.left, position.top, position.right, position.bottom);
	}

	public boolean setRotationZ(float rotation) {
		return setRotation(rotation);
	}

	public boolean setUseCompositingLayer(boolean forceToLayer, Paint paint) {
		return false;
	}

	public boolean setRenderEffect(RenderEffect renderEffect) {
		return false;
	}

	public boolean setAmbientShadowColor(int color) {
		return false;
	}

	public boolean setSpotShadowColor(int color) {
		return false;
	}
}
