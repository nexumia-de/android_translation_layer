#ifndef COLOR_FILTER_H
#define COLOR_FILTER_H

#include <graphene.h>
#include <jni.h>

/* Converts an android.graphics.ColorMatrix array (rows r, g, b, a; columns r, g, b, a and a
 * translation in 0-255) to a GSK color matrix and offset. Both work on unpremultiplied colors.
 * Returns false if matrix_array is NULL, meaning no color filter. */
static inline bool color_filter_from_java(JNIEnv *env, jfloatArray matrix_array, graphene_matrix_t *matrix, graphene_vec4_t *offset)
{
	if (!matrix_array || (*env)->GetArrayLength(env, matrix_array) < 20)
		return false;
	float m[20];
	float gsk[16];
	(*env)->GetFloatArrayRegion(env, matrix_array, 0, 20, m);
	/* graphene multiplies row vectors: out[row] = sum over col of in[col] * gsk[col * 4 + row] */
	for (int row = 0; row < 4; row++)
		for (int col = 0; col < 4; col++)
			gsk[col * 4 + row] = m[row * 5 + col];
	graphene_matrix_init_from_float(matrix, gsk);
	graphene_vec4_init(offset, m[4] / 255.f, m[9] / 255.f, m[14] / 255.f, m[19] / 255.f);
	return true;
}

#endif
