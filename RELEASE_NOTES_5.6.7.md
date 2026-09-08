# Kryzz AI 5.6.7

Agent-mode mascot + tool-calling fix pass over 5.6.6. Version bumped to
`5.6.7` (version code `72`).

## Agent mode — animated mascot GIFs

The code-drawn KryzzBot vector in Agent mode is replaced by the three
mascot GIFs from `mascot GIF/`. New: `ui/agent/MascotGif.kt` decodes a
`res/raw` GIF with the platform `android.graphics.Movie` decoder and
animates it frame-by-frame on a Compose Canvas (no extra image library,
works fully offline). `ui/agent/KryzzMascot.kt` is a drop-in replacement
that takes the old `BotMood`/`BotSize` args, so every existing call site
switches over.

The three states, mapped from `BotMood`:

- **Standby** (`mascot_standby.gif`) — the base / "online" pose, shown on
  finished agent replies and the empty workspace.
- **Thinking** (`mascot_thinking.gif`) — while the model is reasoning or
  running tools (before any text lands, or while tool activity / swarm is
  running).
- **Speaking** (`mascot_speaking.gif`) — while a streamed answer is being
  written into the bubble. `BotMood.SPEAKING` was added and
  `agentBotMood(...)` now returns it once the live streaming row has text.

In the chat list (`MessageItem`), agent assistant bubbles are restructured:
the bubble is wrapped in a `Box` with the mascot GIF pinned to the
top-left (30 dp) and the message column padded 38 dp to the right, so the
mascot reads like a chat profile picture to the left of the text. Per-row
state via `agentMessageMascotState()`: empty streaming -> THINKING,
streaming with text -> SPEAKING, finished -> STANDBY. The top
`AgentBotStatusCard` and `EmptyWorkspace` hero also use the mascot, and
`AgentCapabilitySheet`'s small decorative bot now shows the mascot too.

## Agent mode — inline tool-call recovery (the "it can't call tools" fix)

The remaining tool-calling failure: some models (several OpenRouter / Qwen
/ Hermes variants) ignore the native `tool_calls` field and instead emit
their tool calls inline in the content stream wrapped in tag blocks
(`tools`, `tool_call`, `tool`). The result was exactly the reported
behaviour: the chat bubble showed the raw tag block, no tool actually ran,
and the user got no answer (e.g. "let me check the weather" -> nothing).

New `domain/ToolCallXml.kt` (covered by `ToolCallXmlTest`):

1. **Scrubbing.** Both streaming `onText` callbacks in `AgentExecutor` now
   run `ToolCallXml.scrubToolBlocks(value.scrubThinkTags())`, which strips
   completed *and* still-streaming partial tag blocks before the bubble or
   the persisted row ever see them — mirroring how `scrubThinkTags` hides
   `think` blocks. The raw tool XML no longer leaks into the chat.

2. **Recovery + execution.** `collectWithRetry` now also runs
   `ToolCallXml.parseToolCallXml(text)` on the accumulated stream and
   merges any recovered calls into the same `calls` map used for native
   tool deltas. The existing tool-execution loop then actually runs them
   (e.g. `parallel_search`), feeds results back, and the model streams the
   real final answer — so you finally get the weather. The parser
   tolerantly handles the common Hermes/Qwen/Granite shapes: JSON body
   (`{"name":"...","arguments":{...}}`), the `parameters` alias, the
   `<name>`/`<arguments>` XML body form, the `name="..."` attribute form,
   and multiple calls inside a `tools` wrapper.

The `StreamedResult.text` returned is now the scrubbed text as well, so
even if a caller ever reads it directly it never contains the tag blocks.

## Build

Built offline with JDK 17 (`~/.jdks/jdk-17.0.20+8`), Android SDK at
`C:\Users\krist\AppData\Local\Android\Sdk`, Gradle 8.9 wrapper.

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL (incl. new `ToolCallXmlTest`
  and existing `ReasoningScrubTest`).
- `:app:assembleDebug` — BUILD SUCCESSFUL, `app-debug.apk` (~81 MB) placed
  as `v5.6.7 kryzz ai (debug).apk`.

Only the pre-existing deprecation warnings (`Movie`, `statusBarColor`,
some `Icons.Outlined.*` and `menuAnchor` usages) — no new warnings
introduced beyond the expected `android.graphics.Movie` deprecations from
the GIF decoder.

## Follow-up tweaks

- **Removed the top "KryzzBot is working" status card.** The card that
  appeared at the top of the Agent chat while a prompt was running is
  gone. State is now shown per-bubble by the mascot (thinking / speaking)
  and by the tool-activity chips, so there is no redundant banner.
- **Bigger mascot, repositioned.** The mascot GIF now sits *above* the
  message text, on the left, inline with the "Kryzz · Agent" label (a
  header row of `[mascot] Kryzz · Agent`), sized 46 dp so there is room
  for it to be clearly visible. The bubble content uses the full width
  below it.
- **Friendlier tool chips.** The running-tool chip now reads
  "Searching the web…" for `parallel_search` (and similar human labels
  for the other tools) instead of the raw tool name.
- **Guaranteed final answer after a search.** When the model finishes a
  tool round (e.g. a web search for the weather) but streams no visible
  answer, Agent mode now forces one extra round with no tools offered and
  an explicit system instruction to answer the original question from the
  tool results — so it actually comes back with the weather instead of
  stalling on "let me check". The guard fires at most once per turn.
