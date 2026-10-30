# Kryzz AI 5.13.0

Public **v2.1**. Fixes and additions from phone testing of v2.0. versionCode `100`.

The database moves from version 13 to 14 in place: answers gain a `thinkingJson` column (default `[]`). Chats, keys, memory, skills, bots and settings carry over.

## Voice

- **Speech detection.** New `SpeechFrameAnalyzer` on the capture thread: a 200 Hz high-pass (wind and handling rumble), then per 20 ms frame a pitch check (normalised autocorrelation over 70–400 Hz on a 40 ms window at 8 kHz, after pre-emphasis) with a 160 ms hangover for consonants. Only voiced frames can open, hold or barge into a turn; loud non-voice sound only raises the noise floor. Synthetic tests: voice scores 0.8–0.97, wind and hiss stay under 0.26.
- **Mic sensitivity** (Tune > Voice > Hearing): Low / Normal / High set the loudness gate, the pitch threshold, and how long voice must last to start a turn, to be sent, and to talk over a reply.
- **Noise suppressor and echo canceller** are attached to the mic session when the phone offers them.
- **Direct hearing.** When the reply model hears audio (Gemini chat models; the default is Gemini 2.5 Flash Lite), the trimmed 16 kHz WAV goes to it as `input_audio`. The model writes `<heard>…</heard>` first: those words replace the user's "Voice message" row (history, title, memory) and show in the voice transcript; only the reply after the tag is spoken. `<heard></heard>` means no speech for Kryzz: both rows are removed and the session keeps listening quietly. If the model refuses audio before hearing anything, the turn falls back to the transcriber. Switch: Tune > Voice > Hearing.

## Talk and Run

- **Thinking trace.** One message now carries the whole turn: later tool rounds reuse it instead of deleting and recreating it. A `TurnTrace` keeps the provider's reasoning (now requested with `exclude: false`, and read from `reasoning`, `reasoning_content`, `reasoning_details` or inline `<think>` blocks), what the model said before calling tools, and each tool call with its detail. It is written to the answer as it streams (at most every 150 ms) and shown as a THINKING row above the answer; tap to open every step.
- A stopped turn now writes its CANCELLED state under `NonCancellable`, so it no longer stays "streaming".
- **Hold to talk** on the reply line and on Home's composer, through a shared `DictationSession` (the transcriber you picked). A tap still focuses the field; a still press of 300 ms dictates until the finger lifts; sliding up 72 dp cancels.
- **Save to phone** for made images and clips in chats (MediaStore, no permission on Android 10+); other files still use the file picker.

## Make

- **Style: Plain / Nothing** (saved in settings). Nothing appends a style prompt to what the provider gets; the chat keeps the user's prompt. A running render shows a "Nothing style" pill.
- Layout: wide-frame dials go model first, then two a row; Run in background and Cancel stack; Save to phone and Share on one row with Make a variant below; the Image/Video switch fills the title row.

## First run

- Seven-page tour on Home after onboarding (`tour_pending`), also from Tune > Tour.
- Get-a-key links (OpenRouter, Fish Audio, Parallel) in onboarding, the tour and Tune > Keys.
