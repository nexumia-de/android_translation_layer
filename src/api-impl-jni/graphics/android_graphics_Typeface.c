#include <fontconfig/fontconfig.h>
#include <fontconfig/fcfreetype.h>
#include <pango/pangofc-fontmap.h>
#include <pango/pangocairo.h>

#include "../defines.h"
#include "../util.h"

#include "../generated_headers/android_graphics_Typeface.h"

static gboolean font_map_config_changed(gpointer user_data)
{
	pango_fc_font_map_config_changed(PANGO_FC_FONT_MAP(pango_cairo_font_map_get_default()));
	return G_SOURCE_REMOVE;
}

JNIEXPORT jstring JNICALL Java_android_graphics_Typeface_nativeAddFontFile(JNIEnv *env, jclass class, jstring path_jstr, jboolean on_main_thread)
{
	const char *path = (*env)->GetStringUTFChars(env, path_jstr, NULL);
	jstring family_jstr = NULL;
	int count = 0;
	FcPattern *pattern = FcFreeTypeQuery((const FcChar8 *)path, 0, NULL, &count);
	FcChar8 *family = NULL;
	if (pattern && FcPatternGetString(pattern, FC_FAMILY, 0, &family) == FcResultMatch &&
	    FcConfigAppFontAddFile(FcConfigGetCurrent(), (const FcChar8 *)path)) {
		family_jstr = (*env)->NewStringUTF(env, (const char *)family);
		if (on_main_thread)
			font_map_config_changed(NULL);
		else // the font map belongs to the main thread
			g_idle_add(font_map_config_changed, NULL);
	} else {
		printf("Typeface: can't read %s\n", path);
	}
	if (pattern)
		FcPatternDestroy(pattern);
	(*env)->ReleaseStringUTFChars(env, path_jstr, path);
	return family_jstr;
}
