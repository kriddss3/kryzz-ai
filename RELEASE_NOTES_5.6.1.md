# Kryzz AI 5.6.1

Two hotfix adjustments over 5.6. Version bumped to `5.6.1` (version code `66`).

## Voice chat
- **Spoken replies play through the normal media volume again.** 5.6 routed TTS
  playback through `USAGE_VOICE_COMMUNICATION`, so reply loudness was tied to the
  in-call (phone-call) volume slider. This restores the pre-5.6 behaviour: replies
  play on the media stream, governed by the phone's normal volume rocker (the same
  one that controls music/media). Both the local MP3 `MediaPlayer` path and the
  Fish streaming `AudioTrack` path are switched back to `USAGE_MEDIA` with
  `CONTENT_TYPE_SPEECH`, so there is no telephony narrowband down-sampling. The mic
  still uses `VOICE_COMMUNICATION` for echo cancellation on the recording side.

## Side panel
- **Buttons work again after opening the panel by swiping.** 5.6 added a
  transparent "accident shield" sibling overlay on top of the panel content that
  consumed every pointer event on the Initial pass while the drawer was dragging or
  settling. That overlay is exactly the sibling-`pointerInput` anti-pattern that
  starves children of down events (the same class of bug that killed every button in
  5.1.4/5.1.5). After a left-to-right swipe-open the shield could stay mounted long
  enough to eat the first taps, so chat rows / New chat / Agent / Voice / Cron /
  Settings did not respond when the panel was opened by swiping — only when opened
  via the hamburger button. The shield is removed; the panel is once again a plain
  child of the parent Box, and the parent's Final-pass swipe handler is the only
  gesture consumer. Tap-to-close on the scrim and the close-swipe are unchanged.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: `66`
- Version name: `5.6.1`
