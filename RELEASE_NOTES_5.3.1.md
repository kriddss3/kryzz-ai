# Kryzz AI 5.3.1 — UX polish, voice search feedback, music production, 120Hz

## Side panel improvements

- **Settings closes the panel**: navigating to Settings from any entry point (panel footer, chat header, skills) now closes the side panel automatically so it never stays open over the settings surface.
- **Keyboard dismissal on open**: opening the side panel from chat already hides the keyboard; this is now guaranteed across all open paths via `LaunchedEffect`.
- **Vertical scroll lock**: scrolling the chat list inside the side panel is now locked to the vertical axis. A `horizontalDominant` gate compares cumulative `totalDx` vs `totalDy` and only engages the close-swipe when horizontal movement is dominant, preventing accidental panel closes during a vertical scroll.

## Voice mode search feedback

- **Varied filler phrases**: when Parallel Search fires during voice chat, the user now hears one of four randomized phrases ("Let me search that up.", "Checking.", "Let me look that up.", "Give me a second.") instead of a single fixed phrase.
- **Search sound effect**: a short `ToneGenerator` beep plays alongside the filler phrase so the user is audibly assured the search is in progress.
- **PROCESSING hint updated**: the voice overlay's processing hint now reads "Thinking · searching if needed" to set the right expectation.
- **Gemini + Parallel Search**: the Gemini 2.5 Flash Lite voice reply model uses Parallel Search (the app's only search backend) — it has never used Gemini's built-in search. This is the existing architecture and remains unchanged.

## Agent capabilities

- **Audio → Music production**: the AUDIO agent capability is now "Music production" (short label: "Music"). The instruction tells the agent to produce original music tracks using OpenRouter's audio generation models, specifying genre, mood, and instrumentation. The workflow sheet icon is now `MusicNote` instead of `VolumeUp`.
- **Voice settings**: the "Transcriber" section in Voice settings is renamed to "Music production" with an updated description referencing the OpenRouter audio generation model.

## 120Hz high-refresh support

- The app was previously running at the system default (60Hz). `MainActivity.enableHighRefreshRate()` now iterates the display's supported modes and applies the highest refresh rate (120Hz / 90Hz) through `Window.attributes.preferredDisplayModeId`. Animations, the voice bubble, and scrolling are now smooth on high-refresh panels.

## Version

- Version name: 5.3.1
- Version code: 61
- Base source: 5.1.15 (the 5.2.x line was reverted due to bugs)
