# Kryzz AI 5.6.10

Auto agent tool-calling on MiniMax, plus a real multi-step loop. Version
bumped to `5.6.10` (version code `75`).

## Why Auto still failed on MiniMax

5.6.8 recovered narrated "I'll search" turns, but MiniMax Auto still could
not actually call tools. Same `encodeDefaults = false` trap as the 5.6.9
image/video bug:

- `tools[].type` and `tool_calls[].type` defaulted to `"function"` in Kotlin,
  so they were omitted from the JSON. MiniMax answered with status **2013
  "invalid tool type"**. The retry then dropped `tool_choice`, then stripped
  the tools entirely. Auto could only *talk about* calling tools.
- `stream = true` was also omitted. MiniMax defaults to a non-streaming
  completion, so the bubble stayed empty until the whole turn finished.
- MiniMax M3 puts `<think>…</think>` in `content` and requires that block
  (plus `tool_calls`) to be replayed on the next round. The executor scrubbed
  think tags before appending the assistant message, which broke interleaved
  thinking and later tool rounds.
- Function `arguments` arriving as a JSON object (instead of a string)
  failed the chunk decoder, so the native `tool_calls` were silently dropped.

## Multi-step loop

After the first search, a second "I'll look up the official docs" was treated
as a closing summary and the turn ended. Auto could not do more than one
lookup.

- Follow-up search promises now synthesize another `parallel_search` while
  budget remains.
- "Let me summarise those sources" still forces the final answer.
- Auto search budget is the configured tool-round limit (default 6), not the
  1–3 research-depth slider.
- After tools run, the synthesize-once latch resets so a later no-call turn
  can search again.
- MiniMax requests send `max_tokens=8192` so thinking + a tool call are not
  truncated.

Covered by `MiniMaxToolProtocolTest` and expanded `AgentLoopPolicyTest`.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 210 tests, 0 failures
  (new `MiniMaxToolProtocolTest`, expanded `AgentLoopPolicyTest`).
- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.6.10 kryzz ai (debug).apk`
  (~78 MB). Manifest: `versionName=5.6.10`, `versionCode=75`.
