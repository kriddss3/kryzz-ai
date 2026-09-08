# Kryzz AI 5.6.2

Experimental voice-to-voice improvement over 5.6.1. Version bumped to `5.6.2`
(version code `67`).

## Voice chat — pre-made reaction audio

- **New: pre-made reaction clips.** In Settings → Voice chat, a **"Create
  pre-made audio"** button renders every short interjection (`oh no`, `yeah`,
  `oh`, `wow`, `uhh`, `hmm`, `well`, `let's see`, `okay`, `right`, `so`, `whoa`,
  `ohh`, `interesting`, `yes`, `exactly`, `absolutely`, `sure`, `awesome`,
  `great`, `perfect`, `definitely`, `you bet`, `actually`, `look`,
  `here's the thing`, `i see`, `certainly`, `you know`, `oh wow`) once for the
  selected voice/engine/speed and caches them as MP3s in persistent on-device
  storage (`filesDir/voice-filler/<voiceKey>/`). They survive across sessions
  and are never regenerated automatically.
  - A live progress bar shows `Generating X/Y: <expression>`, plus a status
    line (`Ready — all 30 clips cached` / `N of 30` / `Not created yet`).
  - Clips are keyed per voice: Fish voices key on the `reference_id`;
    OpenRouter TTS keys on `or:<model>:<voice>`. Switching voices keeps separate
    folders. **Recreate** re-renders after a speed/engine change.

- **New: instant sentiment reactions during voice chat.** The moment speech is
  transcribed, a local matcher scans the user's line (no model call, so it fires
  the same millisecond the transcript lands) and plays the matching cached clip
  as a bridge while the full reply is still being generated — so the
  conversation feels fluent and natural instead of dropping into silence during
  the STT → LLM → TTS round-trip.
  - Priority-ordered lexicons:
    - **Negative** (death, loss, bad news) → `oh no` — e.g. *"my dog died"*
      plays **oh no** instantly.
    - **Positive** (wins, promotion, good news) → rotates
      `wow` / `awesome` / `great` / `perfect`.
    - **Agreement** → `exactly` / `yes` / `absolutely` / `sure` / …
    - **Thinking** → `hmm` / `uhh` / `let's see` / `well`.
    - **Discourse** → `okay` / `right` / `i see` / `so` / `you know`.
  - Plain questions with no sentiment signal (`what's the weather`) play
    nothing, so it never gets noisy.
  - The clip keeps playing across the **full** STT → LLM → TTS latency and is
    only cut the instant the real reply's first audio chunk is about to play.
  - If no clip is cached for the matched expression + voice, nothing plays (no
    fallback TTS call — that would defeat the purpose). Best-effort: any
    failure is swallowed so it never breaks a voice turn.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
