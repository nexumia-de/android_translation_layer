#ifndef ATL_WIDEVINE_H
#define ATL_WIDEVINE_H

#include <libavutil/frame.h>
#include <stdbool.h>
#include <stdint.h>

/* the Widevine CDM's own video decoder, for keys that may only be used decrypting and decoding
 * inside the CDM (robustness SW_SECURE_DECODE); see android_media_MediaDrm.c */

enum { ATL_WIDEVINE_H264 = 2, ATL_WIDEVINE_VP9 = 3 };

bool atl_widevine_video_init(int codec, int width, int height);
void atl_widevine_video_reset(void);
void atl_widevine_video_deinit(void);

/* decrypts and decodes one sample; returns the CDM status (0 success, 1 need more data, 2 no key)
 * and on success a YUV420P frame owned by the caller. data == NULL drains the decoder. */
int atl_widevine_video_decode(const uint8_t *data, uint32_t size, const uint8_t *key_id, uint32_t key_id_size,
                              const uint8_t *iv, uint32_t iv_size, int scheme, const uint32_t *clear_bytes,
                              const uint32_t *cipher_bytes, uint32_t num_subsamples, uint32_t crypt_blocks,
                              uint32_t skip_blocks, int64_t timestamp, AVFrame **frame);

#endif
