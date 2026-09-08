# Kryzz AI 5.6.3

Three adjustments over 5.6.2. Version bumped to `5.6.3` (version code `68`).

## Voice chat — pre-made reaction audio removed

- **Removed the 5.6.2 pre-made reaction audio experiment.** The whole
  voice-to-voice "pre-made reactions" system is gone:
  - Deleted `voice/FillerExpressions.kt` (`FillerExpressions`,
    `VoiceFillerMatcher`, `fillerSlug`) and its matcher unit tests.
  - Removed the per-voice cached-clip storage (`filesDir/voice-filler/…`) and
    its helpers (`fillerVoiceKey`, `fillerAudioFile`, `fillerAudioReady`,
    `deleteFillerCache`) from `AppContainer`.
  - Removed the **Create pre-made audio** / **Recreate pre-made audio** UI and
    the `FillerGenerationState` progress flow from Voice chat settings.
  - Removed the instant sentiment-reaction bridge in `processVoiceClip`
    (`startSentimentFiller` / `cutSentimentFiller` / `voiceSentimentFillerJob`
    and every call site).
- The **live** "checking online" spoken filler (the varied "Let me search that
  up." / "Checking." phrases + the two-note search cue that play while
  Parallel Search runs during a voice turn) is a separate, older feature and is
  unchanged — it synthesises on the fly, not from cached clips.

## Side panel — always closes on selection after a swipe-open

- **Selecting an item now always closes the drawer, even when it was opened by
  swiping.** 5.6.1 made taps register again after a swipe-open, but the panel
  could still be left open over the new screen when the swipe's `open` state had
  not caught up to `true` by the time the user tapped a chat / Settings / New
  chat / Cron: flipping `panelOpen` to `false` was a no-op there, so nothing
  relaunched the close animation.
- `KryzzSidePanelHost` now exposes a `LocalSidePanelClose` that clears any
  lingering drag settlement target and drives the slide-to-closed animation
  directly. `KryzzLibraryPanel`'s `closePanelThen` (chat rows, Settings, New
  chat / Agent / Voice, Cron), the panel's X button, the scrim tap, and the back
  handler all invoke it alongside the caller's `onClose`, so the drawer visibly
  closes the moment an item is chosen regardless of how it was opened.

## Voice chat — DeepSeek V4 Flash removed as a reply model

- **DeepSeek V4 Flash is no longer a selectable voice reply model.** It was
  removed from `VoiceReplyModels.fast`. The default voice-chat reply brain is
  now **Gemini 2.5 Flash Lite** (`VoiceConfig.DEFAULT_LLM_MODEL`). Existing
  installs that had DeepSeek V4 Flash saved are migrated to the new default by
  `coerceVoiceReplyModel` (the id is no longer in the list).
- On the STT question: Gemini 2.5 Flash Lite is a text/vision model, not an
  audio-transcription model, so it cannot replace Grok STT / Whisper on the
  `/audio/transcriptions` endpoint. STT is unchanged (Grok STT 1.0 default).

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: 68
- Version name: 5.6.3
