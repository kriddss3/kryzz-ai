# Kryzz AI 5.2.2 — MiniMax Token Plan Global support

## New provider switch

- **Settings → API setup** now has a **Chat provider** dropdown:
  - **OpenRouter** — existing behaviour; chat, agent, and media models come from OpenRouter.
  - **MiniMax** — chat, agent, and media models come from MiniMax Token Plan Global.
- Switching providers resets the default, agent, research, image, video, and audio models to provider-appropriate defaults.

## MiniMax integration

- Added a secure **MiniMax API key** slot in API setup with Test & save.
- Chat streaming routes through `https://api.minimax.io/v1/chat/completions` when MiniMax is selected.
- Media generation routes to MiniMax for model IDs that belong to its catalog:
  - Image: `image-01`
  - Video: `MiniMax-H3`
  - Music/audio: `music-3.0-free`, `music-3.0`, `speech-2.6-turbo`, `speech-2.6-hd`
- MiniMax media endpoints wired: `/v1/image_generation`, `/v2/video_generation`, `/v1/music_generation`, `/v1/t2a_v2`.
- Model catalog and picker load MiniMax's curated media lists and `/v1/models` chat list when MiniMax is active.

## Voice chat stays OpenRouter-only

- Voice transcription (STT), the spoken-reply LLM, and OpenRouter TTS continue to use OpenRouter services regardless of the chat provider setting.
- Fish Audio voice rendering is unchanged.

## UI updates

- API setup subtitle now mentions OpenRouter, MiniMax, Parallel, and Fish Audio.
- Provider privacy notice updated to cover the new provider choice.
- Model catalog subtitle shows the active provider name.
- Intelligence benchmarks are hidden when MiniMax is selected (OpenRouter benchmark data does not apply).

## Version

- Version name: 5.2.2
- Version code: 60
