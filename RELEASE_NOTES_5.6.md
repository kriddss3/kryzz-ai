# Kryzz AI 5.6

Ten targeted adjustments over 5.5.1, plus a version bump to `5.6` (version code `65`).

## Side panel
- **Closing drawer can no longer trigger actions.** While the drawer is settling
  closed (scrim tap, back, or a release after a drag) or actively being dragged, the
  panel content is covered by a transparent pointer-consuming shield. Taps that land on
  a conversation row, the New chat / Agent / Voice / Cron buttons, or the Settings
  footer during the close slide can no longer fire by accident. The shield is gated on
  `open` (not an animation-progress threshold), so it lifts the instant the caller
  considers the panel open and every button always works while the drawer is open.
- **Opening Settings from the panel always closes the panel.** The route-watcher now
  forces the panel closed on any non-chat route (Settings home and every subpage), so
  the side panel can never be left dangling over the Settings surface regardless of how
  Settings was entered.

## Agent mode
- **`<think>` reasoning is no longer shown.** Some providers (DeepSeek, Qwen, Granite,
  and others) stream chain-of-thought inline as `<think>…</think>` blocks inside the
  content delta rather than via the OpenRouter `reasoning` field. The agent now scrubs
  completed think blocks and hides an in-progress (still-open) block from the live
  bubble, and the persisted message stores the cleaned text only. A small
  `ReasoningScrubTest` covers complete, partial, multiple, and unclosed cases. The
  existing `reasoning.exclude = true` flag is still sent so providers that honour it
  keep reasoning out of the stream entirely.
- **AUTO agent can generate media.** AUTO no longer requires the user to pre-pick
  Image / Video / Music. The AUTO workspace now exposes `generate_image`,
  `generate_video`, and `generate_audio` tools, **but only when the user's message
  actually asks for that kind of media** (keyword-gated: "image/picture/photo", "video/
  clip/animation", "music/song/track", …). This keeps AUTO from calling `generate_video`
  on a plain text question (which would hang for minutes on the provider poll) while
  still letting it produce media on its own when asked. The dedicated Image / Video /
  Music capabilities keep their direct, no-tool path. `MediaIntentTest` covers the word-
  boundary guards (e.g. "imagine" does not trigger `generate_image`).
- **Stronger autonomous web search.** The AUTO and research system prompts now explicitly
  instruct the model to call `parallel_search` whenever the answer depends on fresh,
  niche, uncertain, or source-backed facts, and the search tool is always offered on the
  first round when Parallel is enabled. The previous wording was too passive and the
  model often answered from memory even when a search was warranted.

## Voice chat
- **Mute now persists across reply cycles.** The mic mute toggle no longer resets when
  Kryzz finishes speaking and re-arms the mic. Mute is only cleared when the user
  explicitly unmutes or ends the voice session. If you mute while Kryzz is talking, the
  next listening turn stays muted until you tap Unmute.
- **In-call volume routing for replies.** Spoken replies now play through the
  `USAGE_VOICE_COMMUNICATION` audio attributes so the volume is governed by the phone's
  in-call volume slider (the same one used during a phone call), while keeping the
  `CONTENT_TYPE_SPEECH` classification so no telephony-style narrowband down-sampling is
  applied. The level capture visualiser is unchanged.
- **Voice expression previews.** The Voice chat settings picker now has a row of
  expression chips ("Mmm", "Oh", "Hmm", "Ah", "Wow") that synthesise and play the
  selected voice saying that expression, so you can audition how a voice sounds beyond a
  plain "Hello, this is Kryzz speaking." preview.
- **Searching feedback in voice mode.** When a web search runs during a voice turn, the
  overlay now switches to a dedicated "Checking online" state with an internet icon
  (instead of the generic "Thinking" label), a short two-note ascending cue plays, and a
  varied spoken filler ("Let me search that up", "Checking", …) is piped through the
  selected voice. The filler listener is now always started regardless of the
  TTS provider / Fish-streaming toggle, so OpenRouter TTS users also hear it.

## MiniMax music generation
- **Reworked against the async music API.** MiniMax's `/v1/music_generation` is
  asynchronous: a submit call returns a task id (in `data.audio`) rather than a finished
  URL, and the result is fetched from `/v1/query/music_generation?task_id=…`. The Music
  capability now submits, polls the query endpoint until `audio_url` is ready (or the
  task fails / a 5-minute timeout is hit), then downloads the file. The catalog's music
  model ids were corrected to the real, documented MiniMax music model ids so the
  request no longer 400s on an unknown model.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: `65`
- Version name: `5.6`
