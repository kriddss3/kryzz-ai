# Kryzz AI 5.6.8

Agent-mode tool-calling rewrite over 5.6.7. Version bumped to `5.6.8`
(version code `73`).

## Why Auto still felt broken

5.6.6–5.6.7 recovered native `tool_calls` and a few XML wrappers, but Auto
still died on the common case: the model **said** it would search / look
something up and then wrote that sentence as the final answer. Nothing ran,
the bubble sat there, and multi-step work never started. User-created skills
were dumped into the prompt as optional extras ("apply only if relevant"),
so they almost never actually drove the turn.

## Agent loop

New `domain/AgentLoopPolicy.kt` (covered by `AgentLoopPolicyTest`) decides
what happens after a model turn that produced **no** structured tool call:

1. **Synthesize the search.** If Auto needed fresh facts, or the model
   narrated a search ("let me check the weather", "I'll look that up"), the
   executor now builds a real `parallel_search` call from the user's question
   and runs it. The turn no longer ends on the promise.
2. **Nudge once.** If the model named a non-search tool in prose
   (`generate_image`, `create_artifact`, …) it gets one more round with an
   explicit "call it, don't describe it" instruction.
3. **Force the final answer.** After tools have already run, a blank reply
   or another "let me summarise…" is no longer accepted. One closing round
   with no tools is forced so the user actually gets the weather / result.

Recovered tool formats now also include Qwen `✿FUNCTION✿` / `✿ARGS✿`,
`<function=name>…</function>`, fenced ` ```tool ` JSON, and a bare JSON
object whose `name` is a known Kryzz tool. Search arguments accept
`search_queries`, `searchQueries`, `queries`, or a lone `query` string, so
camelCase from weaker models no longer fails the tool and stalls the loop.

## Multi-step + MiniMax

- Tool-round cap raised from **3 → 8** (default **6**). Settings slider
  matches. Auto is instructed to keep going after each tool result.
- MiniMax no longer gets the rejected `"required"` string. When a specific
  tool must run it sends the named-function `tool_choice` object MiniMax
  accepts. A validation error first retries with `tool_choice=auto` **and
  keeps the tools**; only the last resort strips them. The old path retried
  as plain chat immediately, which is why MiniMax Auto only *talked about*
  calling tools.

## Skills auto-activate

Enabled user skills are active on every Agent request. Matching skills are
marked PRIMARY and must be followed; the rest stay listed as also-active.
The prompt no longer says "apply only if relevant", and the Tools & skills
copy now says they activate automatically.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 199 tests, 0 failures
  (new `AgentLoopPolicyTest`, expanded `ToolCallXmlTest` / `AgentSupportTest`).
- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.6.8 kryzz ai (debug).apk`
  (~78 MB). Manifest: `versionName=5.6.8`, `versionCode=73`.
