# Kryzz AI 5.6.9

MiniMax image and video generation fix over 5.6.8. Version bumped to `5.6.9`
(version code `74`).

## Why MiniMax media failed

Selecting MiniMax in API settings set the image model to `image-01` and the
video model to `MiniMax-H3`, which is correct. The request encoder then
dropped the fields MiniMax actually needs.

The app JSON uses `encodeDefaults = false`. Image and video request classes
declared Kotlin defaults (`response_format = "base64"`, `duration = 5`,
`resolution = "2K"`, `ratio = "16:9"`). Those values were omitted from the
JSON even when the executor passed them explicitly.

- **Image:** MiniMax then defaulted to `url`, returned `image_urls`, and the
  app only read `image_base64`. Generation looked like it produced nothing.
  MiniMax's model-tag errors (`<minimax[image-01]>`) also leaked into the
  bubble and rendered as the garbled `<minimax[>[`.
- **Video:** `/v2/video_generation` requires `duration`, `resolution`, and
  (for text-to-video) `ratio`. They were missing. MiniMax rejected the job.
  HTTP 200 responses with a nested `base_resp` error were also ignored.

## Fix

- Required MiniMax image/video fields are no longer Kotlin-defaulted, so
  they always serialize.
- Image requests ask for `response_format=url` and download `image_urls`
  (base64 still accepted if MiniMax sends it).
- Nested `base_resp` is checked on HTTP 200 for image submit, video submit,
  and video poll.
- Error parser reads nested `base_resp` and strips HTML-like `<…>` tags so
  MiniMax model tags never show up as `<minimax[>[`.
- MiniMax is chosen when the chat provider is MiniMax **or** the stored
  model id is a MiniMax image/video id (`image-*`, `MiniMax-H*`, plus a
  stripped `minimax/` OpenRouter prefix).

Covered by `MiniMaxMediaTest`.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 204 tests, 0 failures (new `MiniMaxMediaTest`).
- `:app:assembleDebug` — debug APK placed as `v5.6.9 kryzz ai (debug).apk`.
