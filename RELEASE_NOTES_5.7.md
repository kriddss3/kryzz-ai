# Kryzz AI 5.7

Agent Auto loop redesign: the orchestration is now driven by what the model
**did**, not by regex-matching what it **said**. Version bumped to `5.7`
(version code `78`).

## The problem

Through 5.6.6 → 5.6.12, Auto kept getting patches for "it says it will call a
tool, then doesn't" and "multi-step doesn't work". The root cause was the loop
itself: after every model round, `AgentLoopPolicy.decideAfterModelText`
pattern-matched the streamed prose (tool-promise regexes, an ≥80-char
"completeness" floor, closing-promise detection) to decide whether to
continue. That produced four recurring failures:

- **FORCE_FINAL_ANSWER stripped every tool for the rest of the turn** after
  one blank/short/promise-looking round following a tool call — so a second
  step (fetch after search, calculate after time, a follow-up search) could
  never run. This was the direct multi-step killer.
- **SYNTHESIZE_SEARCH invented searches** from stopword-stripped keywords
  whenever the prose "promised" one — burning tool rounds on queries the
  model never chose.
- **Mid-thread `system` nudges** confused providers (MiniMax especially).
- The flag soup (`forcedFinalAnswer` / `alreadyNudgedForTools` /
  `alreadySynthesizedSearch`) interacted badly with the MiniMax 2013
  validation-retry cascade.

## The redesign — `AgentTurnPolicy`

New pure, unit-tested state machine (`domain/AgentTurnPolicy.kt`). The model
drives the loop; the harness is a deterministic executor:

- **Model returned tool calls → run them**, feed results back, offer tools
  again. Multi-step chaining is the default path, not an exception.
- **Model returned text without calls → that IS the final answer.** No
  completeness regex, no length floor, no prose judging.
- **One high-precision nudge, round 0 only**: if the model narrates a tool
  ("let me check online…", names a tool) without invoking it, it is re-asked
  once to actually call it. After that, its text is accepted.
- **One terminal no-tools round** when the tool budget runs out or the model
  goes quiet after tool work — the turn now ends with a written answer
  instead of the old "tool limit reached" error notice.
- **Inline-XML tool-call recovery stays** (models that emit `<tool_call>`
  blocks still get their tools run), but it is disabled on the terminal round
  so a recovered call cannot resurrect ruled-out work and loop forever.
- **Forcing rules unchanged**: AUTO's fresh-info gate still forces the first
  `parallel_search`; dedicated capabilities (artifact / skill / code / deep /
  wide) still force their tool; MiniMax gets the named `tool_choice`. A guard
  now ensures a forced tool is always one that was actually offered.
- The keyword gates for weather / fetch / schedule / media / artifact tools,
  the MiniMax 2013 retry cascade, per-call error isolation, and the voice
  one-search cap are all unchanged.

`AgentLoopPolicy` keeps only what other code still uses: the known-tool
registry for XML recovery, lenient search-argument parsing, and skill
auto-activation. The prose decision tree is deleted.

## Tests

- New `AgentTurnPolicyTest` (20 tests): multi-step chaining after a search,
  budget-exhaustion terminal round, one-shot nudge, terminal round never
  runs calls, forced tool always offered, voice caps, deep-research passes.
- `AgentLoopPolicyTest` rewritten for the trimmed helper surface, plus a
  registry-consistency test (every plannable tool is XML-recoverable).
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 247 tests, 0 failures.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.7 kryzz ai (debug).apk`.
  Manifest: `versionName=5.7`, `versionCode=78`.
