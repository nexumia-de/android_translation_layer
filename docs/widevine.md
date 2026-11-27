---
type: Reference
title: Widevine in ATL
description: How ATL plays Widevine-protected streams with the Widevine CDM of Chrome and Firefox, how to install the CDM for an app Flatpak, and the limits.
tags: [drm, widevine, mediadrm, mediacodec, crunchyroll]
generated: { by: claude-code, at: 2026-11-27T12:40:00+01:00 }
---

# What it does

`MediaDrm` supports Widevine (`edef8ba9-79d6-4ace-a3c8-27dcd51d21ed`) when the Widevine CDM is
installed; ATL does not ship it. The CDM is the one Chrome and Firefox use on Linux, loaded through
Chromium's CDM interface 10 (`content_decryption_module.h`), written out as C vtables in
`src/api-impl-jni/media/android_media_MediaDrm.c`.

- Sessions, key requests and responses: `getKeyRequest` lets the CDM make the request,
  `provideKeyResponse` loads the license. Streaming (temporary) licenses only, security level L3,
  no provisioning.
- Audio: `MediaCodec.queueSecureInputBuffer` decrypts each sample in place (`Decrypt`, cenc and
  cbcs) and ffmpeg decodes it.
- Video: services allow video keys only inside the CDM (robustness `SW_SECURE_DECODE`; plain
  `Decrypt` then answers `kNoKey` although the keys are usable). A video codec configured with a
  `MediaCrypto` therefore sends every sample, clear or encrypted, to the CDM's decoder
  (`DecryptAndDecodeFrame`, H.264 and VP9, SPS/PPS in front of the first sample); its I420 frames
  go to the output queue as `AVFrame`s and are rendered like ffmpeg's.
- `MediaCrypto.requiresSecureDecoderComponent` is always false; no secure decoder exists.

# Installing the CDM

ATL looks for `$ATL_WIDEVINE_CDM`, else `~/.local/share/widevine/libwidevinecdm.so` (the real home,
also inside an app Flatpak). The arm64 CDM comes from a ChromeOS image, e.g. through
[AsahiLinux/widevine-installer](https://github.com/AsahiLinux/widevine-installer); it needs glibc
2.29 and `libnspr4`, both in the GNOME 50 runtime. An app Flatpak needs read access:

```sh
flatpak override --user --filesystem=xdg-data/widevine:ro <app id>
```

Without the CDM nothing changes: no DRM scheme is supported.

# Status

- Crunchyroll 3.130.2 (2026-11-27): its license server accepts the CDM's requests (4 keys); 640x360
  H.264 plays with sound, about 10% CPU on the Fairphone 6. Higher resolutions are not offered to
  L3.
- Not done: offline licenses (downloads), license renewal events, key status events with real
  statuses, `OnEventListener`.
- The CDM is Google's binary under its own license terms; ATL only loads what the user installed.
