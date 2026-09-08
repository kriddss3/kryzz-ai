# Changelog

Public GitHub versions. Internal Kryzz build numbers are in parentheses.

## v1.1 — 2026-09-08 (internal 5.7.2, versionCode 80)

Source, debug APK, and notes for everything shipped after public v1.0 (internal 5.1.15).

### Providers

- MiniMax Token Plan Global as an alternative **chat, agent, and media** provider.
- API setup uses a provider dropdown (OpenRouter, MiniMax, Parallel, Fish Audio) instead of four stacked key cards.
- Switching chat provider reloads that provider's model catalog and resets defaults. OpenRouter benchmarks stay OpenRouter-only.
- Voice transcription, the spoken-reply LLM, and OpenRouter TTS stay on OpenRouter even when MiniMax is the chat provider.
- MiniMax image, video, and music generation work (URL image payloads, required video fields, nested `base_resp` errors surfaced cleanly).

### Agent

- Auto is a real multi-step tool loop: if the model returns tool calls they run; if it returns text that is the answer. One nudge if it *says* it will call a tool and doesn't.
- Auto tools (offered when the request actually needs them): web search, `fetch_url`, `get_current_time`, `calculate`, `get_weather` (Open-Meteo, no extra key), `remember_fact` / `recall_memories`, `schedule_task`, `create_artifact`, `create_skill`, `create_code_project`, `generate_image` / `generate_video` / `generate_audio`, `ask_user`.
- Artifact types accept aliases (`excel`, `xlsx`, `docx`, `pdf`, …). PDF output is new.
- Eight starter skills seed on first launch (research brief, study notes, essay outline, email, decision memo, fact check, daily planner, code walkthrough). Matching ones inject in full; the rest are name + purpose only.
- Failed tool calls are returned to the model instead of deleting the turn.
- Inline XML / Qwen / fenced JSON tool calls are recovered when a model ignores the native `tool_calls` field.
- MiniMax tool protocol: named `tool_choice`, required `type` fields serialized, 2013 retries drop extra utilities but keep search instead of stripping every tool.
- Animated KryzzBot mascot (standby / thinking / speaking) in Agent mode.
- Chain-of-thought `<think>` blocks are scrubbed from the visible bubble.

### Chat and UI

- Interactive **question cards** in Chat (fenced `kryzz-question` blocks) and Agent (`ask_user` tool): lettered options, free-text box, dismiss.
- Side panel: swipe-to-open and swipe-to-close, vertical-scroll lock so list scrolling doesn't close it, keyboard dismissed on open, selection always closes the drawer.
- 120 Hz on displays that support it.
- Agent image/video/music generation shows the animated Thinking indicator instead of static placeholder text.
- Music production replaces the old generic “audio” agent capability.

### Voice and memory

- Spoken replies play on the media volume stream (not in-call volume).
- Voice chat started from the side panel actually starts (lifecycle-aware).
- Voice turns receive the same optional coarse-location context as text chat.
- Auto-memory records self-contained first-person facts and splits compound clauses.

### Build

- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Unit tests: 276, 0 failures on the 5.7.2 drop.

Internal patch files: `RELEASE_NOTES_5.2.2.md` through `RELEASE_NOTES_5.7.2.md`.

## v1.0 — 2026-08-17 (internal 5.1.15, versionCode 59)

First public release of the source. See `RELEASE_NOTES_5.1.15.md`.
