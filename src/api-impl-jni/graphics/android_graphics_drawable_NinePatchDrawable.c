#include <arpa/inet.h>
#include <gdk/gdk.h>

#include "../defines.h"
#include "NinePatchPaintable.h"

#include "../generated_headers/android_graphics_drawable_NinePatchDrawable.h"

JNIEXPORT jlong JNICALL Java_android_graphics_drawable_NinePatchDrawable_nativeCreate___3BJ(JNIEnv *env, jobject this, jbyteArray chunk_array, jlong texture_ptr)
{
	GdkTexture *texture = GDK_TEXTURE(_PTR(texture_ptr));
	int size = (*env)->GetArrayLength(env, chunk_array);
	struct Res_png_9patch *chunk = malloc(size);
	(*env)->GetByteArrayRegion(env, chunk_array, 0, size, (jbyte *)chunk);
	GdkPaintable *paintable = ninepatch_paintable_new(chunk, size, g_object_ref(texture));
	return _INTPTR(paintable);
}

/* The PNG file could theoretically come from untrusted sources. So, our parsing code must be
 * safe against buffer overflows and other possible attacks. */
JNIEXPORT jlong JNICALL Java_android_graphics_drawable_NinePatchDrawable_nativeCreate__Ljava_lang_String_2(JNIEnv *env, jobject this, jstring path_jstr)
{
	GdkPaintable *paintable = NULL;
	struct Res_png_9patch *chunk = NULL;
	uint32_t chunk_size = 0;
	uint8_t buf[8];

	const char *path = (*env)->GetStringUTFChars(env, path_jstr, NULL);
	GdkTexture *texture = gdk_texture_new_from_filename(path, NULL);
	FILE *f = fopen(path, "rb");
	(*env)->ReleaseStringUTFChars(env, path_jstr, path);

	if (!f)
		return 0;
	// always check that we actually read all bytes, to avoid uninitialized data
	if (fread(buf, 1, 8, f) != 8) {
		fclose(f);
		return 0;
	}
	if (buf[0] != 0x89 || buf[1] != 'P' || buf[2] != 'N' || buf[3] != 'G') {
		fclose(f);
		return 0;
	}
	while (fread(buf, 1, 8, f) == 8) {

		chunk_size = ntohl(((uint32_t *)buf)[0]);
		chunk_size += 4; // 4 bytes checksum

		if (!strncmp((char *)&buf[4], "npTc", 4)) {
			// check minimal size to avoid out-of-bounds access and limit maximum size to 1MB to make potential unknown vulnerabilities harder to exploit
			if (chunk_size < sizeof(struct Res_png_9patch) || chunk_size > 1024 * 1024) {
				fclose(f);
				return 0;
			}
			chunk = malloc(chunk_size);
			if (!chunk) {
				fclose(f);
				return 0;
			}
			if (fread(chunk, 1, chunk_size, f) != chunk_size) {
				free(chunk);
				fclose(f);
				return 0;
			}
			break;
		} else {
			fseek(f, chunk_size, SEEK_CUR);
		}
	}
	fclose(f);

	if (chunk)
		paintable = ninepatch_paintable_new(chunk, chunk_size, texture);

	return _INTPTR(paintable);
}

JNIEXPORT void JNICALL Java_android_graphics_drawable_NinePatchDrawable_native_1setScale(JNIEnv *env, jclass clazz, jlong ptr, jfloat scale)
{
	NINEPATCH_PAINTABLE(_PTR(ptr))->scale = scale;
}

JNIEXPORT jint JNICALL Java_android_graphics_drawable_NinePatchDrawable_native_1getIntrinsicWidth(JNIEnv *env, jclass clazz, jlong ptr)
{
	return gdk_paintable_get_intrinsic_width(GDK_PAINTABLE(_PTR(ptr)));
}

JNIEXPORT jint JNICALL Java_android_graphics_drawable_NinePatchDrawable_native_1getIntrinsicHeight(JNIEnv *env, jclass clazz, jlong ptr)
{
	return gdk_paintable_get_intrinsic_height(GDK_PAINTABLE(_PTR(ptr)));
}

/* the content padding of the nine-patch, scaled like the image: left, top, right, bottom */
JNIEXPORT jboolean JNICALL Java_android_graphics_drawable_NinePatchDrawable_native_1getPadding(JNIEnv *env, jclass clazz, jlong ptr, jintArray out)
{
	NinePatchPaintable *ninepatch = NINEPATCH_PAINTABLE(_PTR(ptr));
	struct Res_png_9patch *chunk = ninepatch->chunk;
	if (!chunk)
		return JNI_FALSE;
	float scale = ninepatch->scale > 0 ? ninepatch->scale : 1;
	jint padding[4] = {
		ntohl(chunk->paddingLeft) * scale + 0.5f, ntohl(chunk->paddingTop) * scale + 0.5f,
		ntohl(chunk->paddingRight) * scale + 0.5f, ntohl(chunk->paddingBottom) * scale + 0.5f,
	};
	(*env)->SetIntArrayRegion(env, out, 0, 4, padding);
	return padding[0] || padding[1] || padding[2] || padding[3];
}
