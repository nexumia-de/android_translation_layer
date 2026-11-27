/*
 * Android MediaCodec implementation for Android Translation Layer
 *
 * Large part of the video decoding code was taken from https://git.sr.ht/~emersion/vaapi-decoder/tree/wayland/
 */

#include <libavutil/frame.h>
#include <libavutil/hwcontext.h>
#include <libavutil/pixfmt.h>
#include <stdio.h>

#include <gdk/gdk.h>
#include <gtk/gtk.h>

#include <drm_fourcc.h>
#include <libavcodec/avcodec.h>
#include <libavutil/hwcontext_drm.h>
#include <libavutil/pixdesc.h>
#include <libswresample/swresample.h>
#include <libswscale/swscale.h>

#include <stdlib.h>

#include "../defines.h"
#include "../util.h"
#include "../generated_headers/android_media_MediaCodec.h"
#include "../../libandroid/native_window.h"
#include "../widgets/android_view_SurfaceView.h"
#include "jni.h"
#include "atl_widevine.h"

struct ATL_codec_context {
	AVCodecContext *codec;
	union {
		struct {
			SwrContext *swr;
			int sample_rate;
		} audio;
		struct {
			struct SwsContext *sws; // for software decoding
			SurfaceViewWidget *surface_view_widget;
			size_t extradata_size;
			uint8_t *extradata;
		} video;
	};
	/* video decoded by the Widevine CDM: its frames wait here; a NULL entry marks the end of stream */
	bool cdm_video;
	GQueue *cdm_frames;
};

JNIEXPORT jlong JNICALL Java_android_media_MediaCodec_native_1constructor(JNIEnv *env, jobject this, jstring codec_name)
{
	const char *name = (*env)->GetStringUTFChars(env, codec_name, NULL);
	const AVCodec *codec = avcodec_find_decoder_by_name(name);
	if (!codec) {
		printf("Codec %s not found\n", name);
		exit(0);
	}
	(*env)->ReleaseStringUTFChars(env, codec_name, name);
	if (!codec)
		return 0;
	AVCodecContext *codec_ctx = avcodec_alloc_context3(codec);

	struct ATL_codec_context *ctx = calloc(1, sizeof(struct ATL_codec_context));
	ctx->codec = codec_ctx;
	return _INTPTR(ctx);
}

JNIEXPORT void JNICALL Java_android_media_MediaCodec_native_1configure_1audio(JNIEnv *env, jobject this, jlong codec, jobject extradata, jint sample_rate, jint nb_channels)
{
	struct ATL_codec_context *ctx = _PTR(codec);
	AVCodecContext *codec_ctx = ctx->codec;
	jarray array_ref;
	jbyte *array;
	void *data;

	printf("Java_android_media_MediaCodec_native_1configure_1audio(%s, %d, %d)\n", codec_ctx->codec->name, sample_rate, nb_channels);

	ctx->audio.sample_rate = sample_rate;
	codec_ctx->sample_rate = sample_rate;
	if (nb_channels == 1)
		codec_ctx->ch_layout = (AVChannelLayout)AV_CHANNEL_LAYOUT_MONO;
	else if (nb_channels == 2)
		codec_ctx->ch_layout = (AVChannelLayout)AV_CHANNEL_LAYOUT_STEREO;
	else {
		printf("MediaCodec: Unsupported number of channels %d\n", nb_channels);
		exit(0);
	}

	if (extradata) {
		codec_ctx->extradata_size = get_nio_buffer_size(env, extradata);
		data = get_nio_buffer(env, extradata, &array_ref, &array);
		codec_ctx->extradata = av_mallocz(codec_ctx->extradata_size + AV_INPUT_BUFFER_PADDING_SIZE);
		memcpy(codec_ctx->extradata, data, codec_ctx->extradata_size);
		release_nio_buffer(env, array_ref, array);
	}

	for (int i = 0; i < codec_ctx->extradata_size; i++) {
		printf("params->extradata[%d] = %x\n", i, codec_ctx->extradata[i]);
	}
}

/*
 * Helper functions for hardware accelerated video decoding using Wayland DMA-Buf protocol
 */

static const struct {
	uint32_t format;
	int nb_layers;
	uint32_t layers[AV_DRM_MAX_PLANES];
} drm_format_map[] = {
	{DRM_FORMAT_NV12, 2, {DRM_FORMAT_R8, DRM_FORMAT_GR88}},
};

static uint32_t get_drm_frame_format(const AVDRMFrameDescriptor *drm_frame_desc)
{
	if (drm_frame_desc->nb_layers == 1) {
		return drm_frame_desc->layers[0].format;
	}
	for (size_t i = 0; i < ARRAY_SIZE(drm_format_map); i++) {
		if (drm_format_map[i].nb_layers != drm_frame_desc->nb_layers) {
			continue;
		}
		int match = 1;
		for (int j = 0; j < drm_frame_desc->nb_layers; j++) {
			match &= drm_frame_desc->layers[j].format == drm_format_map[i].layers[j];
		}
		if (match) {
			return drm_format_map[i].format;
		}
	}
	return DRM_FORMAT_INVALID;
}

static enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
                                        const enum AVPixelFormat *pix_fmts)
{
	size_t i;
	for (i = 0; pix_fmts[i] != AV_PIX_FMT_NONE; i++) {
		if (pix_fmts[i] == AV_PIX_FMT_VAAPI || pix_fmts[i] == AV_PIX_FMT_DRM_PRIME) {
			return pix_fmts[i];
		}
	}

	fprintf(stderr, "Failed to find HW pixel format\n");
	if (i > 0) {
		printf("falling back to software decode\n");
		return pix_fmts[i - 1]; // last pixel format should be for software decoding
	}
	return AV_PIX_FMT_NONE;
}

struct render_frame_data {
	AVFrame *frame;
	GdkTexture *texture; // for software decoding
	SurfaceViewWidget *surface_view_widget;
	long release_time_ns; // wall-clock (monotonic, ns) at which the frame should be shown; 0 = now
};

// 5ms lead time (~0.5 vsync) to correct possible GTK + the Wayland compositor latency
#define RENDER_LEAD_US (5000)

// schedule a render so the frame reaches the screen by (or fractionally before) its release time
static guint schedule_render(GSourceFunc render_fn, struct render_frame_data *data, GdkTexture *texture)
{
	gint64 delay_us = 0;
	if (data->release_time_ns > 0)
		delay_us = (data->release_time_ns - RENDER_LEAD_US * 1000 - g_get_monotonic_time() * 1000) / 1000; // all in ns
	if (delay_us > 0)
		return g_timeout_add_full(G_PRIORITY_DEFAULT_IDLE, MAX(delay_us / 1000, 1), render_fn, data, NULL);
	return g_idle_add(render_fn, data);
}

static void handle_dmabuftexture_destroy(void *data)
{
	AVFrame *drm_frame = data;
	av_frame_free(&drm_frame);
}

static GdkTexture *import_drm_frame_desc_as_texture(const AVDRMFrameDescriptor *drm_frame_desc, int width, int height, AVFrame *drm_frame)
{
	// VA-API drivers may use separate layers with one plane each, or a single
	// layer with multiple planes. We need to handle both.
	uint32_t drm_format = get_drm_frame_format(drm_frame_desc);
	if (drm_format == DRM_FORMAT_INVALID) {
		fprintf(stderr, "Failed to get DRM frame format\n");
		return NULL;
	}
	GdkDmabufTextureBuilder *builder = gdk_dmabuf_texture_builder_new();
	gdk_dmabuf_texture_builder_set_display(builder, gdk_display_get_default());
	int k = 0;
	for (int i = 0; i < drm_frame_desc->nb_layers; i++) {
		const AVDRMLayerDescriptor *drm_layer = &drm_frame_desc->layers[i];

		for (int j = 0; j < drm_layer->nb_planes; j++) {
			const AVDRMPlaneDescriptor *drm_plane = &drm_layer->planes[j];
			const AVDRMObjectDescriptor *drm_object =
			    &drm_frame_desc->objects[drm_plane->object_index];

			gdk_dmabuf_texture_builder_set_modifier(builder, drm_object->format_modifier);
			gdk_dmabuf_texture_builder_set_offset(builder, k, drm_plane->offset);
			gdk_dmabuf_texture_builder_set_stride(builder, k, drm_plane->pitch);
			gdk_dmabuf_texture_builder_set_fd(builder, k, drm_object->fd);
			k++;
		}
	}
	gdk_dmabuf_texture_builder_set_n_planes(builder, k);
	gdk_dmabuf_texture_builder_set_width(builder, width);
	gdk_dmabuf_texture_builder_set_height(builder, height);
	gdk_dmabuf_texture_builder_set_fourcc(builder, drm_format);
	GError *error = NULL;
	GdkTexture *texture = gdk_dmabuf_texture_builder_build(builder, handle_dmabuftexture_destroy, drm_frame, &error);
	if (error) {
		fprintf(stderr, "Failed to build texture: %s\n", error->message);
		exit(1);
	}
	g_object_unref(builder);
	return texture;
}

JNIEXPORT void JNICALL Java_android_media_MediaCodec_native_1configure_1video(JNIEnv *env, jobject this, jlong codec, jobject csd0, jobject csd1, jobject surface_obj)
{
	struct ATL_codec_context *ctx = _PTR(codec);
	AVCodecContext *codec_ctx = ctx->codec;
	jarray array_ref;
	jbyte *array;
	int sps_size = 0;
	int pps_size = 0;

	printf("Java_android_media_MediaCodec_native_1configure_video(%s)\n", codec_ctx->codec->name);

	if (csd0)
		sps_size = get_nio_buffer_size(env, csd0);
	if (csd1)
		pps_size = get_nio_buffer_size(env, csd1);

	size_t extradata_size = sps_size + pps_size;
	uint8_t *extradata = av_mallocz(extradata_size + AV_INPUT_BUFFER_PADDING_SIZE);
	if (csd0) {
		memcpy(extradata, get_nio_buffer(env, csd0, &array_ref, &array), extradata_size);
		release_nio_buffer(env, array_ref, array);
	}
	if (csd1) {
		memcpy(extradata + sps_size, get_nio_buffer(env, csd1, &array_ref, &array), extradata_size);
		release_nio_buffer(env, array_ref, array);
	}

	for (int i = 0; i < extradata_size; i++) {
		printf("extradata[%d] = %x\n", i, extradata[i]);
	}

	/* For some reason, using AVCodecContext.extradata doesn't work with livestreams.
	   As a workaround, we inject the extradata into the first frame. */
	ctx->video.extradata = extradata;
	ctx->video.extradata_size = extradata_size;

	int i = 0;
	while (1) {
		const AVCodecHWConfig *config = avcodec_get_hw_config(codec_ctx->codec, i);
		if (!config) {
			fprintf(stderr, "Decoder %s doesn't support pixel format VAAPI or DRM_PRIME\n",
			        codec_ctx->codec->name);
			break;
		}

		if ((config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX) && (config->pix_fmt == AV_PIX_FMT_VAAPI || config->pix_fmt == AV_PIX_FMT_DRM_PRIME)) {
			fprintf(stderr, "Selected pixel format %s\n", av_get_pix_fmt_name(config->pix_fmt));
			codec_ctx->get_format = get_hw_format;

			AVBufferRef *hw_device_ctx = NULL;
			int ret = av_hwdevice_ctx_create(&hw_device_ctx, config->device_type, NULL, NULL, 0);
			if (ret >= 0) {
				codec_ctx->hw_device_ctx = hw_device_ctx;
				break;
			}
		}

		i++;
	}

	SurfaceViewWidget *surface_view_widget = SURFACE_VIEW_WIDGET(gtk_widget_get_first_child(_PTR(_GET_LONG_FIELD(surface_obj, "widget"))));
	ctx->video.surface_view_widget = surface_view_widget;
}

JNIEXPORT void JNICALL Java_android_media_MediaCodec_native_1start(JNIEnv *env, jobject this, jlong codec)
{
	struct ATL_codec_context *ctx = _PTR(codec);
	AVCodecContext *codec_ctx = ctx->codec;

	if (avcodec_open2(codec_ctx, codec_ctx->codec, NULL) < 0) {
		printf("Codec cannot be found");
	}
}

#define INFO_TRY_AGAIN_LATER -1

JNIEXPORT jint JNICALL Java_android_media_MediaCodec_native_1queueInputBuffer(JNIEnv *env, jobject this, jlong codec, jobject buffer, jlong presentationTimeUs)
{
	jarray array_ref = NULL;
	jbyte *array = NULL;
	int ret;
	struct ATL_codec_context *ctx = _PTR(codec);
	AVCodecContext *codec_ctx = ctx->codec;
	AVPacket *pkt = NULL;
	if (ctx->cdm_video) {
		if (!buffer) { /* drain the CDM's decoder, then mark the end of stream */
			AVFrame *frame;
			while (atl_widevine_video_decode(NULL, 0, NULL, 0, NULL, 0, 0, NULL, NULL, 0, 0, 0, 0, &frame) == 0 && frame)
				g_queue_push_tail(ctx->cdm_frames, frame);
			g_queue_push_tail(ctx->cdm_frames, NULL);
		}
		return 0;
	}
	if (buffer) { // buffer can be null if we're sending EOF
		pkt = av_packet_alloc();
		pkt->size = get_nio_buffer_size(env, buffer);
		pkt->data = get_nio_buffer(env, buffer, &array_ref, &array);
		if (codec_ctx->codec_type == AVMEDIA_TYPE_VIDEO && ctx->video.extradata_size) {
			uint8_t *data = pkt->data;
			pkt->data = av_malloc(pkt->size + ctx->video.extradata_size + AV_INPUT_BUFFER_PADDING_SIZE);
			memcpy(pkt->data, ctx->video.extradata, ctx->video.extradata_size);
			memcpy(pkt->data + ctx->video.extradata_size, data, pkt->size);
			pkt->size += ctx->video.extradata_size;
			ctx->video.extradata_size = 0;
		}
		pkt->pts = presentationTimeUs;
	}
	ret = avcodec_send_packet(codec_ctx, pkt);
	if (ret < 0 && ret != AVERROR(EAGAIN)) {
		fprintf(stderr, "Error while sending packet: %d = %s\n", ret, av_err2str(ret));
	}
	if (buffer) {
		release_nio_buffer(env, array_ref, array);
		av_packet_free(&pkt);
	}
	return ret;
}

JNIEXPORT jint JNICALL Java_android_media_MediaCodec_native_1dequeueOutputBuffer(JNIEnv *env, jobject this, jlong codec, jobject buffer, jobject buffer_info)
{
	struct ATL_codec_context *ctx = _PTR(codec);
	AVCodecContext *codec_ctx = ctx->codec;
	AVFrame *frame;
	int ret;
	jarray array_ref;
	jbyte *array;

	if (ctx->cdm_video) {
		if (g_queue_is_empty(ctx->cdm_frames))
			return INFO_TRY_AGAIN_LATER;
		frame = g_queue_pop_head(ctx->cdm_frames);
		uint8_t *raw_buffer = get_nio_buffer(env, buffer, &array_ref, &array);
		*((AVFrame **)raw_buffer) = frame;
		release_nio_buffer(env, array_ref, array);
		_SET_INT_FIELD(buffer_info, "flags", frame ? 0 : android_media_MediaCodec_BUFFER_FLAG_END_OF_STREAM);
		_SET_INT_FIELD(buffer_info, "offset", 0);
		_SET_INT_FIELD(buffer_info, "size", frame ? sizeof(AVFrame *) : 0);
		_SET_LONG_FIELD(buffer_info, "presentationTimeUs", frame ? frame->pts : 0);
		return 0;
	}
	frame = av_frame_alloc();
	ret = avcodec_receive_frame(codec_ctx, frame);
	if (ret < 0) {
		if (ret == AVERROR_EOF) {
			_SET_INT_FIELD(buffer_info, "flags", android_media_MediaCodec_BUFFER_FLAG_END_OF_STREAM);
			_SET_INT_FIELD(buffer_info, "offset", 0);
			_SET_INT_FIELD(buffer_info, "size", 0);
			_SET_LONG_FIELD(buffer_info, "presentationTimeUs", 0);
			av_frame_free(&frame);
			// set the buffer to NULL, so we don't try to render it
			uint8_t *raw_buffer = get_nio_buffer(env, buffer, &array_ref, &array);
			*((AVFrame **)raw_buffer) = NULL;
			release_nio_buffer(env, array_ref, array);
			return 0;
		}
		if (ret != AVERROR(EAGAIN)) {
			printf("avcodec_receive_frame returned %d\n", ret);
			printf("frame->data = %p frame->nb_samples = %d\n", frame->data[0], frame->nb_samples);
		}
		av_frame_free(&frame);
		return INFO_TRY_AGAIN_LATER;
	}
	_SET_INT_FIELD(buffer_info, "flags", 0);
	_SET_LONG_FIELD(buffer_info, "presentationTimeUs", frame->pts);

	if (codec_ctx->codec_type == AVMEDIA_TYPE_AUDIO) {
		if (!ctx->audio.swr) {
			printf("ctx->sample_rate = %d\n", codec_ctx->sample_rate);
			printf("ctx->ch_layout.nb_channels = %d\n", codec_ctx->ch_layout.nb_channels);
			printf("ctx->sample_fmt = %d\n", codec_ctx->sample_fmt);

			int ret = swr_alloc_set_opts2(&ctx->audio.swr,
			                              &codec_ctx->ch_layout,
			                              AV_SAMPLE_FMT_S16,
			                              ctx->audio.sample_rate,
			                              &codec_ctx->ch_layout,
			                              codec_ctx->sample_fmt,
			                              codec_ctx->sample_rate,
			                              0,
			                              NULL);
			if (ret != 0) {
				fprintf(stderr, "FFmpegDecoder error: Swresampler alloc fail\n");
			}
			swr_init(ctx->audio.swr);
		}
		uint8_t *raw_buffer = get_nio_buffer(env, buffer, &array_ref, &array);
		int outSamples = swr_convert(ctx->audio.swr, &raw_buffer, frame->nb_samples, (uint8_t const **)(frame->data), frame->nb_samples);
		release_nio_buffer(env, array_ref, array);
		_SET_INT_FIELD(buffer_info, "offset", 0);
		_SET_INT_FIELD(buffer_info, "size", outSamples * 2 * codec_ctx->ch_layout.nb_channels);

		av_frame_free(&frame);
	} else if (codec_ctx->codec_type == AVMEDIA_TYPE_VIDEO) {
		// copy frame pointer into data buffer to be read by releaseOutputBuffer function
		uint8_t *raw_buffer = get_nio_buffer(env, buffer, &array_ref, &array);
		*((AVFrame **)raw_buffer) = frame;
		release_nio_buffer(env, array_ref, array);
		_SET_INT_FIELD(buffer_info, "offset", 0);
		_SET_INT_FIELD(buffer_info, "size", sizeof(AVFrame *));
	}
	return 0;
}

// callback to perform wayland stuff on main thread
static gboolean render_frame(void *data)
{
	struct render_frame_data *d = (struct render_frame_data *)data;
	AVFrame *frame = d->frame;
	int ret;

	AVFrame *drm_frame;
	if (frame->format != AV_PIX_FMT_DRM_PRIME) {
		drm_frame = av_frame_alloc();
		drm_frame->format = AV_PIX_FMT_DRM_PRIME;
		drm_frame->hw_frames_ctx = av_buffer_ref(frame->hw_frames_ctx);

		// Convert the VA-API frame into a DMA-BUF frame
		ret = av_hwframe_map(drm_frame, frame, 0);
		if (ret < 0) {
			fprintf(stderr, "Failed to map frame: %s\n", av_err2str(ret));
			exit(1);
		}
		av_frame_free(&frame);
	} else {
		drm_frame = frame;
	}

	AVDRMFrameDescriptor *drm_frame_desc = (void *)drm_frame->data[0];

	GdkTexture *texture = import_drm_frame_desc_as_texture(drm_frame_desc, drm_frame->width, drm_frame->height, drm_frame);
	surface_view_widget_set_texture(d->surface_view_widget, texture, FALSE, TRUE);
	free(d);

	return G_SOURCE_REMOVE;
}

static gboolean render_texture(void *data)
{
	struct render_frame_data *d = (struct render_frame_data *)data;

	surface_view_widget_set_texture(d->surface_view_widget, d->texture, FALSE, TRUE);

	free(d);
	return G_SOURCE_REMOVE;
}

JNIEXPORT void JNICALL Java_android_media_MediaCodec_native_1releaseOutputBuffer(JNIEnv *env, jobject this, jlong codec, jobject buffer, jboolean render, jlong presentationTimeNs)
{
	struct ATL_codec_context *ctx = _PTR(codec);
	jarray array_ref;
	jbyte *array;
	AVFrame *frame;

	if (ctx->codec->codec_type == AVMEDIA_TYPE_VIDEO) {
		AVFrame **raw_buffer = get_nio_buffer(env, buffer, &array_ref, &array);
		frame = *raw_buffer;
		*raw_buffer = NULL;
		release_nio_buffer(env, array_ref, array);

		if (!frame)
			return;

		if (!render) {
			fprintf(stderr, "skipping %dx%d frame!\n", frame->width, frame->height);
			av_frame_free(&frame);
			return;
		}

		if (!frame->hw_frames_ctx) {
			const AVPixFmtDescriptor *desc = av_pix_fmt_desc_get(frame->format);
			enum AVPixelFormat gdk_pix_fmt;
			GdkMemoryFormat gdk_mem_fmt;
			int stride;
			if (desc != NULL && (desc->flags & AV_PIX_FMT_FLAG_ALPHA)) {
				gdk_pix_fmt = AV_PIX_FMT_RGBA;
				gdk_mem_fmt = GDK_MEMORY_R8G8B8A8;
				stride = frame->width * 4;
			} else {
				gdk_pix_fmt = AV_PIX_FMT_RGB24;
				gdk_mem_fmt = GDK_MEMORY_R8G8B8;
				stride = frame->width * 3;
			}

			// use swscale to convert YUV to RGB
			ctx->video.sws = sws_getCachedContext(ctx->video.sws, frame->width, frame->height, frame->format,
			                                      frame->width, frame->height, gdk_pix_fmt, 0, NULL, NULL, NULL);
			guchar *data_rgb = g_try_malloc0(frame->height * stride);
			sws_scale(ctx->video.sws, (const uint8_t *const *)frame->data, frame->linesize, 0,
			          frame->height, (uint8_t *[1]){data_rgb}, (int[1]){stride});

			GBytes *bytes = g_bytes_new_take(data_rgb, frame->height * stride);
			GdkTexture *texture = gdk_memory_texture_new(frame->width, frame->height, gdk_mem_fmt, bytes, stride);
			struct render_frame_data *data = malloc(sizeof(struct render_frame_data));
			data->texture = texture;
			data->surface_view_widget = ctx->video.surface_view_widget;
			data->release_time_ns = presentationTimeNs;
			schedule_render(render_texture, data, texture);
			g_bytes_unref(bytes);
			av_frame_free(&frame);
			return;
		}

		struct render_frame_data *data = malloc(sizeof(struct render_frame_data));
		data->frame = frame;
		data->release_time_ns = presentationTimeNs;
		data->surface_view_widget = ctx->video.surface_view_widget;
		schedule_render(render_frame, data, NULL);
	}
}

static void drop_cdm_frames(struct ATL_codec_context *ctx)
{
	AVFrame *frame;
	while (ctx->cdm_frames && !g_queue_is_empty(ctx->cdm_frames))
		if ((frame = g_queue_pop_head(ctx->cdm_frames)))
			av_frame_free(&frame);
}

JNIEXPORT void JNICALL Java_android_media_MediaCodec_native_1flush(JNIEnv *env, jobject this, jlong codec)
{
	if (((struct ATL_codec_context *)_PTR(codec))->cdm_video) {
		drop_cdm_frames(_PTR(codec));
		atl_widevine_video_reset();
	}
	struct ATL_codec_context *ctx = _PTR(codec);
	avcodec_flush_buffers(ctx->codec);
}

JNIEXPORT void JNICALL Java_android_media_MediaCodec_native_1release(JNIEnv *env, jobject this, jlong codec)
{
	if (((struct ATL_codec_context *)_PTR(codec))->cdm_video) {
		drop_cdm_frames(_PTR(codec));
		g_queue_free(((struct ATL_codec_context *)_PTR(codec))->cdm_frames);
		atl_widevine_video_deinit();
	}
	struct ATL_codec_context *ctx = _PTR(codec);
	if (ctx->codec->codec_type == AVMEDIA_TYPE_VIDEO && ctx->video.sws) {
		sws_freeContext(ctx->video.sws);
	}

	avcodec_free_context(&ctx->codec);
	free(ctx);
}

/* a sample of a stream whose key may only be used by the Widevine CDM's own decoder: decrypted
 * and decoded there, the frames go to the output queue. The first sample gets the SPS/PPS in front,
 * as for ffmpeg. Returns the CDM status. */
JNIEXPORT jint JNICALL Java_android_media_MediaCodec_native_1queueSecureVideo(JNIEnv *env, jobject this, jlong codec, jobject buffer, jint size,
                                                                              jbyteArray key_id, jbyteArray iv, jint mode, jintArray clear_bytes,
                                                                              jintArray cipher_bytes, jint num_subsamples, jint crypt_blocks,
                                                                              jint skip_blocks, jlong presentation_time_us, jint width, jint height)
{
	struct ATL_codec_context *ctx = _PTR(codec);
	if (!ctx->cdm_video) {
		int cdm_codec = strcmp(ctx->codec->codec->name, "vp9") ? ATL_WIDEVINE_H264 : ATL_WIDEVINE_VP9;
		if (!atl_widevine_video_init(cdm_codec, width, height))
			return 3; /* kInitializationError */
		ctx->cdm_video = true;
		ctx->cdm_frames = g_queue_new();
	}
	jbyte *key = key_id ? (*env)->GetByteArrayElements(env, key_id, NULL) : NULL;
	jsize key_size = key_id ? (*env)->GetArrayLength(env, key_id) : 0;
	jbyte *ivp = iv ? (*env)->GetByteArrayElements(env, iv, NULL) : NULL;
	jsize iv_size = iv ? MIN((*env)->GetArrayLength(env, iv), 16) : 0;
	jint *clear = clear_bytes ? (*env)->GetIntArrayElements(env, clear_bytes, NULL) : NULL;
	jint *cipher = cipher_bytes ? (*env)->GetIntArrayElements(env, cipher_bytes, NULL) : NULL;

	/* the SPS/PPS go in front of the first sample, as a clear subsample of their own */
	size_t prefix = ctx->video.extradata_size;
	uint32_t n = num_subsamples + (prefix ? 1 : 0);
	uint32_t *clear32 = g_new0(uint32_t, n ? n : 1), *cipher32 = g_new0(uint32_t, n ? n : 1);
	uint32_t k = 0;
	if (prefix)
		clear32[k++] = prefix;
	for (int i = 0; i < num_subsamples; i++, k++) {
		clear32[k] = clear ? clear[i] : 0;
		cipher32[k] = cipher ? cipher[i] : 0;
	}
	/* clear samples have no subsamples; encrypted ones without any are encrypted whole */
	if (mode == 0 || (num_subsamples == 0 && !prefix))
		n = 0;
	else if (num_subsamples == 0)
		cipher32[0] = size; /* one entry: the prefix clear, the whole sample after it encrypted */

	jarray array_ref;
	jbyte *array;
	uint8_t *data = get_nio_buffer(env, buffer, &array_ref, &array);
	uint8_t *sample = data;
	if (prefix) {
		sample = av_malloc(prefix + size);
		memcpy(sample, ctx->video.extradata, prefix);
		memcpy(sample + prefix, data, size);
		ctx->video.extradata_size = 0;
	}
	AVFrame *frame = NULL;
	int status = atl_widevine_video_decode(sample, prefix + size, (const uint8_t *)key, key_size, (const uint8_t *)ivp, iv_size, mode,
	                                       clear32, cipher32, n, crypt_blocks, skip_blocks, presentation_time_us, &frame);
	release_nio_buffer(env, array_ref, array);
	if (sample != data)
		av_free(sample);
	if (status == 0 && frame)
		g_queue_push_tail(ctx->cdm_frames, frame);

	g_free(clear32);
	g_free(cipher32);
	if (cipher)
		(*env)->ReleaseIntArrayElements(env, cipher_bytes, cipher, JNI_ABORT);
	if (clear)
		(*env)->ReleaseIntArrayElements(env, clear_bytes, clear, JNI_ABORT);
	if (ivp)
		(*env)->ReleaseByteArrayElements(env, iv, ivp, JNI_ABORT);
	if (key)
		(*env)->ReleaseByteArrayElements(env, key_id, key, JNI_ABORT);
	return status == 1 ? 0 : status; /* kNeedMoreData is fine */
}
