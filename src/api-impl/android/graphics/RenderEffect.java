package android.graphics;

public final class RenderEffect {

	private RenderEffect() {}

	public static RenderEffect createBlurEffect(float radiusX, float radiusY, Shader.TileMode edgeTreatment) {
		return new RenderEffect();
	}

	public static RenderEffect createBlurEffect(float radiusX, float radiusY, RenderEffect inputEffect, Shader.TileMode edgeTreatment) {
		return new RenderEffect();
	}

	public static RenderEffect createColorFilterEffect(ColorFilter colorFilter) {
		return new RenderEffect();
	}

	public static RenderEffect createColorFilterEffect(ColorFilter colorFilter, RenderEffect renderEffect) {
		return new RenderEffect();
	}

	public static RenderEffect createOffsetEffect(float offsetX, float offsetY) {
		return new RenderEffect();
	}

	public static RenderEffect createOffsetEffect(float offsetX, float offsetY, RenderEffect input) {
		return new RenderEffect();
	}
}
