# Kryzz AI 5.7.2

Interactive question cards: the AI can now **ask you a question with tappable
options** — in both Chat and Agent mode — instead of guessing. Version bumped to
`5.7.2` (version code `80`).

## What it looks like

The model asks "Which subject first?" and the app renders a card with the
question on top, lettered options (A, B, C …) you can tap, a "Type your own
answer" box for a custom reply, and an X to dismiss. Your choice goes back to
the model and the conversation continues with it.

## Two delivery paths, one card

- **Chat mode — inline protocol.** Chat has no tool calling, so the model asks
  by emitting a fenced ```` ```kryzz-question ```` block with a small JSON body
  (`{"question": "…", "options": ["…", "…"]}`). The new pure
  `InlineQuestionProtocol` parser (`domain/InlineQuestion.kt`) lifts valid
  blocks out of the assistant reply and the UI renders them as cards in place
  (`QuestionCard` in `ui/ChatScreen.kt`). Malformed or partially streamed
  blocks stay inline as ordinary markdown/code, so a card can never break a
  reply. Answering sends the choice as the next user message
  (`You asked: "…" — my answer: …`) via `ChatViewModel.sendQuestionAnswer`, so
  the model unambiguously connects the answer to its own question. Chat-mode
  system prompts carry a short protocol note; voice chat is excluded.
- **Agent mode — real `ask_user` tool.** The AUTO agent's utility tool set gains
  `ask_user(question, options[])`. When the model calls it, the executor shows
  the floating card and **suspends the tool call** on a `CompletableDeferred`
  until the UI delivers the answer (`AgentExecutor.pendingQuestion` +
  `answerPendingQuestion`). The answer becomes the tool result
  (`{"status": "answered", …}`) and the agent loop continues with it. The X
  dismisses the card with a `skipped` result telling the model to proceed with
  its best judgment and not re-ask. Cancelling the generation clears the card
  via `finally`. A second concurrent `ask_user` call is rejected with a tool
  error instead of queueing (single-slot card). The AUTO capability prompt now
  mentions asking instead of guessing, and the activity chip reads "Waiting
  for your answer".

Both paths share the same `InlineQuestion` model, the same caps (240-char
question, 8 × 120-char options), and the same `QuestionCard` composable:
title + X, lettered option rows with dividers, free-text field with IME-send,
and an answered state that highlights the chosen option.

Agent cards also work when a model in Agent mode writes the fenced block in
prose — the inline parser runs on every assistant message regardless of mode.

## Tests

- New `InlineQuestionTest` (14 tests): valid blocks parse with text before /
  after preserved, free-text questions (no options), malformed JSON and
  partial streams degrade to markdown, blank/oversized questions rejected,
  options trimmed / blank-dropped / deduped / capped at 8, multiple blocks,
  unknown JSON keys ignored, answer-echo format, option-length cap.
- `AgentTurnPolicyTest` extended (3 tests): `ask_user` is offered to the AUTO
  agent but never forced; withheld in voice mode and during a forced first
  search; absent on the terminal round.
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 276 tests, 0 failures.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.7.2 kryzz ai (debug).apk`.
  Manifest: `versionName=5.7.2`, `versionCode=80`.
