# Kryzz AI 5.6.4

Three adjustments over 5.6.3. Version bumped to `5.6.4` (version code `69`).

## Voice chat — opens correctly from the side panel

- **Starting voice chat through the side panel's "Voice" button now works the
  same as tapping the mic inside an existing chat.** 5.6.3 wired the panel's
  Voice entry to create a fresh conversation and navigate into it with
  `voice=true`, and a first-composition `LaunchedEffect` then called
  `requestVoice()`. That path was racy: the navigation transition was still
  settling, the side-panel close animation was still running, and — when the
  microphone permission had not been granted yet — the permission launcher was
  asked to pop before the new back-stack entry was fully `RESUMED`, so the
  system could drop the request and the session never started. Tapping the mic
  inside an existing chat never hit this because a real user tap already implies
  the activity is resumed.
- `ChatScreen` now tracks its local lifecycle's `RESUMED` state and only kicks
  off `requestVoice()` once the new screen is actually resumed. The
  `LaunchedEffect` re-evaluates whenever the lifecycle (or the consumed flag)
  flips, so a brief pause/transition no longer swallows the voice start. The
  inside-chat tap path is unchanged.

## Voice chat — sees your location directly

- **The voice chatting bot now receives your approximate location in its
  system context, just like text chat always has.** 5.6.x gated the
  `locationContext` prompt fragment behind `if (voiceMode) "" else ...`, so a
  spoken "what's the weather", "any pharmacies nearby", "news around here" had
  no regional context to answer with. The gate is removed: voice turns now get
  the same cached coarse-location block (`The user's approximate location is
  …`) as text turns. Nothing changes about how the fix is sourced — it still
  only fires when Location is enabled in Settings and the coarse permission is
  granted, and nothing leaves the device except as text in the user's own
  OpenRouter requests.

## Memory — auto-creation fixed for self-contained facts

- **Auto memory creation from chatting (and voice chatting) now records
  self-contained first-person facts that have no trailing punctuation.** The
  memory engine splits a message into sentences and, for each signal phrase,
  validated the *rest* (the words after the signal). For signals whose fact
  lives in the words that follow ("my name is Alex", "I live in Gilly") that is
  correct — a blank rest means nothing was said. But three signal families
  carry the whole fact *inside* the signal itself:
  - age — "I'm 16 years old"
  - gender — "I'm a man" / "I'm male"
  - demonym — "I'm an Italian" / "I'm a Lebanese"
  For those, the rest after the signal is empty whenever the user did not put a
  period at the end — which is the norm for transcribed speech (STT rarely adds
  punctuation) and for casual chat. The blank-rest filter therefore dropped
  these facts entirely, so "I'm 16 years old" said in chat or over voice was
  never remembered.
- `MemoryEngine` now marks self-contained signals with `capturesRest = false`
  (previously the flag existed but was unused dead code, always `true`). For
  those patterns the blank-rest rejection is skipped; the rest is still checked
  for empty-pronoun tails ("I'm a man that" is still rejected) and the full
  sentence still has to meet the length gate. Signal-following patterns keep
  their existing rest requirement, so "I like" / "I prefer" with nothing after
  still produce nothing.
- **Compound first-person clauses are now split into separate memories.**
  Casual chat (and transcribed voice) packs several facts into one run-on
  sentence joined by "and" / "but" / "so" / "because" — "hey, my name is Alex
  and I live in Gilly and I like JDM cars". The matcher only ran the first
  signal in a sentence, so every fact after the first was swallowed and never
  reached the Memory tab. `MemoryEngine.extract` now splits a sentence on a
  conjunction that introduces a new first-person clause (`… and I …`, `… but
  my …`), so each clause is matched on its own and becomes its own memory.
  The lookahead requires the next clause to start with I/my, so "I like cats
  and dogs" stays one sentence ("dogs" is not a new first-person clause) and
  the whole preference is remembered. `compactSentence` now also strips a
  trailing comma/semicolon left behind by the split.
- Covered by unit tests in `MemoryEngineTest` (self-contained facts, compound
  clause splitting, non-first-person conjunctions not split) and a Robolectric
  `MemoryRepositoryIngestTest` that asserts `ingest` → `observe` actually
  persists and surfaces the facts in the same flow the Memory tab renders —
  proving the save → display path is intact. All 18 memory-engine tests and
  the 3 repository tests pass.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: 69
- Version name: 5.6.4
