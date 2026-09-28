# Changelog

Public GitHub versions. Internal Kryzz build numbers are in parentheses.

## v1.1.2 — 2026-09-28 (internal 5.11.0, versionCode 84)

Source and debug APK for the Agent Auto rework that landed after public v1.1 (internal 5.7.2). Internal drops 5.8 through 5.11 are included. There is no public v1.1.1.

### Agent

- Live checklist. Auto can keep a plan card through the turn (`update_plan`). Pure planning rounds do not eat the step budget.
- Time, calculate, weather, and file creation are offered every round, so "put that in a spreadsheet" works without a keyword match.
- `fetch_url` only opens a page the user shared, a search result from this chat, or a site the user named. Injected page text cannot turn the tool into an open fetch.
- Search fires on precise fresh-info phrases, not everyday words like "game" or "now".
- The last few replies carry their citations and file names back into context, so "open source 3" and "add a column to that sheet" have something to point at.
- Substantial answers get one review pass before they are sent. Trivial turns skip it.
- Per-turn cost cap, default $0.25, adjustable up to $2. Hitting the cap still writes a final answer.
- Long research compacts older search and page results once context passes 60k characters. Citations, titles, and URLs stay.
- Step budget is 4–24 (default 16), replacing the old 1–8 tool-round slider. Voice stays on one round.

### Quality

- Agent composer chip: **Fast**, **Balanced**, **Max**.
- Fast is a short, low-reasoning run (8 steps). Balanced uses the settings you already picked. Max uses the strongest configured model, high reasoning, 24 steps, and the review pass.
- Requests are laid out so compatible providers can cache the stable prefix across turns and tool rounds.
- Agent default is `google/gemini-3.8-flash`. Max default is `anthropic/claude-sonnet-5`. Chat stays `openai/gpt-4o-mini`.
- If the selected model cannot call tools, the composer says so.

### Files and activity

- One Markdown model drives DOCX, XLSX, and PDF: headings, lists, tables, code, quotes, links.
- DOCX uses real heading styles, numbered lists, bordered tables, and hyperlinks.
- XLSX writes typed numbers, percentages, booleans, and formulas; freezes the header; adds an autofilter. `### Sheet: Name` starts another sheet.
- PDF renders with system fonts, so Latvian, Polish, Greek, Cyrillic, and CJK text survive. Page numbers included.
- A work-log line sits under each agent answer ("3 searches · read 4 pages · 1 file · 48s") and expands into the steps.
- Activity chips name the query, page, or place, and concurrent calls no longer share one chip.

### Build

- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- versionCode 84 upgrades in place over the v1.1 debug APK (versionCode 80).
- Unit tests: 400, 0 failures on the 5.11.0 drop.

## v1.1 — 2026-09-08 (internal 5.7.2, versionCode 80)

Source, debug APK, and notes for everything shipped after public v1.0 (internal 5.1.15).

### Providers

- MiniMax Token Plan Global as an alternative **chat, agent, and media** provider.
- API setup uses a provider dropdown (OpenRouter, MiniMax, Parallel, Fish Audio) instead of four stacked key cards.
- Switching chat provider reloads that provider's model catalog and resets defaults. OpenRouter benchmarks stay OpenRouter-only.
- Voice transcription, the spoken-reply LLM, and OpenRouter TTS stay on OpenRouter even when MiniMax is the chat provider.
- MiniMax image, video, and music generation work (URL image payloads, required video fields, nested `base_resp` errors surfaced cleanly).

### Agent

- Auto is a real multi-step tool loop: if the model returns tool calls they run; if it returns text that is the answer. One nudge if it *says* it will call a tool and doesn't.
- Auto tools (offered when the request actually needs them): web search, `fetch_url`, `get_current_time`, `calculate`, `get_weather` (Open-Meteo, no extra key), `remember_fact` / `recall_memories`, `schedule_task`, `create_artifact`, `create_skill`, `create_code_project`, `generate_image` / `generate_video` / `generate_audio`, `ask_user`.
- Artifact types accept aliases (`excel`, `xlsx`, `docx`, `pdf`, …). PDF output is new.
- Eight starter skills seed on first launch (research brief, study notes, essay outline, email, decision memo, fact check, daily planner, code walkthrough). Matching ones inject in full; the rest are name + purpose only.
- Failed tool calls are returned to the model instead of deleting the turn.
- Inline XML / Qwen / fenced JSON tool calls are recovered when a model ignores the native `tool_calls` field.
- MiniMax tool protocol: named `tool_choice`, required `type` fields serialized, 2013 retries drop extra utilities but keep search instead of stripping every tool.
- Animated KryzzBot mascot (standby / thinking / speaking) in Agent mode.
- Chain-of-thought `<think>` blocks are scrubbed from the visible bubble.

### Chat and UI

- Interactive **question cards** in Chat (fenced `kryzz-question` blocks) and Agent (`ask_user` tool): lettered options, free-text box, dismiss.
- Side panel: swipe-to-open and swipe-to-close, vertical-scroll lock so list scrolling doesn't close it, keyboard dismissed on open, selection always closes the drawer.
- 120 Hz on displays that support it.
- Agent image/video/music generation shows the animated Thinking indicator instead of static placeholder text.
- Music production replaces the old generic “audio” agent capability.

### Voice and memory

- Spoken replies play on the media volume stream (not in-call volume).
- Voice chat started from the side panel actually starts (lifecycle-aware).
- Voice turns receive the same optional coarse-location context as text chat.
- Auto-memory records self-contained first-person facts and splits compound clauses.

### Build

- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Unit tests: 276, 0 failures on the 5.7.2 drop.

Internal notes for this drop: `RELEASE_NOTES_5.7.2.md`.

## v1.0 — 2026-08-17 (internal 5.1.15, versionCode 59)

First public release of the source. See `RELEASE_NOTES_5.1.15.md`.
