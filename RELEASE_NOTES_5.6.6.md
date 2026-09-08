# Kryzz AI 5.6.6

Agent-mode reliability pass over 5.6.5. Version bumped to `5.6.6` (version code `71`).

## Agent mode — tool calling actually completes now

The core complaint: in Agent mode (especially **Auto**), the model "simply can't
call tools properly and can't do multiple steps itself." Four concrete causes,
four fixes, all in `domain/AgentExecutor.kt`:

### 1. A failed tool call no longer erases the turn
Before this, any throw inside a tool handler (`parallel_search` network blip,
`validateSearchArgs` rejecting malformed model arguments, a media provider
timeout, a missing key) propagated straight out of `generateReply`. The
streaming placeholder had already been deleted for the tool round, so the
outer `catch` had `activeId = null` and **saved no error message at all** —
the chat bubble just vanished and the user saw only a transient banner. That
matched "agent can't call tools" exactly.

Each tool call is now wrapped in `try / catch / finally`:
- **Cancellation** is re-thrown unchanged.
- Any other tool failure is serialised as a `{"status":"error","error":…}`
  tool result, persisted as a `TOOL` row, and appended to `working` so the
  **model is told the tool failed and can recover** — retry with different
  arguments, fall back to memory, or tell the user it could not finish that
  step. The agent loop continues instead of aborting.
- `toolActivity(started = false)` now emits in `finally`, so a failed tool
  no longer leaves a "Using …" chip stuck on screen.

The outer `catch` was also hardened: if `activeId` is null when a non-tool
error escapes, it now restores a visible error row instead of swallowing it.

### 2. Auto proactively searches when the question needs fresh facts
Auto never forced a tool call (`forceTool` was always false for Auto), so
`tool_choice` was always `"auto"` and the model frequently answered from
stale memory even for "what's the weather", "who won", "current price of…".
Deep Search / Wide Search force a first `parallel_search` round; Auto now
does too, but only when the user's message plausibly depends on
fresh / time-sensitive / source-backed facts — gated by a new
`messageLikelyNeedsSearch` heuristic (`domain/SearchIntent.kt`, word-boundary
keyword match: current, latest, today, now, news, price, score, weather, who
is the, recent years, explicit "look up / search / check online", …). Plain
chat, greetings, creative writing, and static-fact questions keep
`tool_choice = "auto"` so "hi" / "write me a poem" never trigger a web
search. When Auto forces its first search round it offers **only**
`parallel_search` (not `search_past_chats`) so the required call is a web
search. Covered by `SearchIntentTest`.

### 3. Empty follow-up after a tool round is a soft fallback, not a hard error
After a successful tool round, some provider/model combinations stream an
empty follow-up (notably when `reasoning.exclude` interferes). Previously
`allowEmpty = outputs.isNotEmpty() || skillCreated` was false for search
rounds, so `collectWithRetry` threw "model returned no text" — the agent
errored out **after** a successful search, which read as "can't do multiple
steps." `allowEmpty` now also accepts `completedToolRounds > 0`, and the
empty-final-text branch produces a visible recovery note ("I ran the
searches above but the model did not stream a summary back. Tap
regenerate, or ask me to summarise…") instead of throwing, so the tool
results above are not erased.

### 4. Long media tool polls show progress
When Auto calls `generate_video` / `generate_audio`, the streaming
placeholder was deleted **before** the minutes-long provider poll, leaving
the chat looking frozen. A heartbeat streaming message is now created for
the duration of `produceVideoOutput` / `produceAudioOutput` (the same
pattern the dedicated Video / Music capabilities already use) and removed in
`finally`, so the ThinkingIndicator stays up while the provider processes.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: 71
- Version name: 5.6.6
