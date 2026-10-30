# Changelog

Public GitHub versions. Internal Kryzz build numbers are in parentheses.

## v2.1 — 2026-10-30 (internal 5.13.0, versionCode 100)

Fixes and additions from phone testing of v2.0. The database moves to version 14 in place (answers gain a thinking trace); nothing is lost.

### Voice

- **Far less sensitive to wind and noise.** The mic now only counts sound that has the pitch of a voice: it filters out the low rumble of wind first, then checks each 20 ms for a voice's periodicity. Gusts, bumps, rustling and traffic no longer start a turn or cut Kryzz off. The phone's own noise suppressor and echo canceller are switched on when it has them.
- **Mic sensitivity** in Tune > Voice: Low (outdoors, wind), Normal, High (quiet rooms).
- **Gemini hears you directly.** With Gemini 2.5 Flash Lite as the reply model (the default), the recording goes straight to Gemini, then Fish Audio speaks the answer. No separate transcriber step, so replies start sooner. Gemini also writes down what it heard, which becomes your message in the chat. If a clip holds no speech for Kryzz, it says so and Kryzz just keeps listening. Turn it off in Tune > Voice > Hearing; other reply models still use the transcriber.

### Talk and Run

- **Thinking you can see.** Every answer has a THINKING row while it is being worked out (with the seconds so far and its newest thought). Tap it to read every step: the model's reasoning, what it said before it went to work, and each search or tool it ran. Nothing disappears between steps any more. Finished answers keep a "THOUGHT FOR 12S · 3 STEPS" row.
- **Hold to talk.** Hold the reply line (or Home's "Ask, search, or make" bar) to dictate. Let go and the words land in the field; slide up to cancel. A tap still opens the keyboard, and once the field is focused a long-press still pastes.
- **Save to phone.** A made image or clip in a chat has a Save to phone button that puts it straight in the gallery (Pictures/Kryzz, Movies/Kryzz), plus Share.
- A stopped answer is now marked as stopped instead of looking like it is still writing.

### Make

- **Nothing style.** A Style switch (Plain / Nothing) dresses your prompt in the Nothing OS look: black, white and one red, dot-matrix lettering and patterns, Glyph light strips, see-through industrial parts. Your prompt is saved as you wrote it.
- **Roomier video layout.** Under a wide frame the model gets its own row and the other dials go two a row, so nothing is cut off. "Run in background", Cancel, Save to phone and Make a variant each have room for their words, and the Image/Video switch fills the title row.

### First run

- **Tour.** A fresh install gets a seven-page tour on Home: the arms, the rail, the chat drawer, Talk and Run, Voice and hold-to-talk, Make/Find/Crew, and your keys. Open it again from Tune > Tour.
- **Get a key links** for OpenRouter (openrouter.ai/settings/keys), Fish Audio (fish.audio/app/api-keys) and Parallel in onboarding, the tour and Tune > Keys.

## v2.0 — 2026-10-30 (internal 5.12.14, versionCode 99)

A new look and a new layout. The whole app moves to a Nothing-style dot-matrix design ("Eight Arms"): black and white, one yellow accent, LED type and stepped motion. Chats, keys, memory, skills and settings carry over. Internal drops 5.12.0 through 5.12.14 are included.

### Layout

- **Home** is a board of eight arms (Talk, Make, Find, Crew, Run, Voice, Link, Tune). Each port shows what that arm is doing, and lit cables show which jobs are running. The dot-matrix squid in the middle replaces the old GIF mascot. Link is a preview of a future bridge from Nothing's Essential Space and is not connected yet.
- **Arm rail.** An 8-dot rail on the right edge switches arms from any screen. Drag along it to scrub between arms.
- **Ink strip.** An LED line at the top says what the screen is doing ("RUN 3 OF 5", "VOICE LISTENING"). Tap it to open the Inkwell.
- **Chat library** in a left drawer: swipe in from the left or tap a screen title. Pinned chats, day groups, folders, search, rename, move, delete, and backup.

### Talk (was Chat)

- Questions and answers read as a departure board. Each answer can show a short gist and a source readout; citations are yellow superscripts.
- The reply line queues a follow-up while an answer is still streaming.
- "+" opens Talk options: attach an image, photo or file, web search, model, voice, chats, new chat.

### Run (was Agent)

- Run header with Fast / Balanced / Max, a live plan card, work chips with a full work log, file cards with save and share, and a cost line.
- Stop is always on the bottom bar while a run works. A finished run keeps its plan card.

### Voice

- Full-screen voice with the squid, a 44 × 15 LED waveform, a REC timer and a transcript of the last turns. New mute pill. Barge-in and automatic turn-taking work as before.

### New: Make

- An image and video studio. Pick a ratio the provider actually supports, render, then save to the gallery (`Pictures/Kryzz`, `Movies/Kryzz`) or share.
- A render keeps going when you leave Make. A "recent ink" row shows the newest files Kryzz made in any chat; clips get a frame scrubber.

### New: Find

- Search **Wikimedia Commons** for images you are free to reuse, with file type, licence and size filters.
- Saves and shares carry the credit line (title, author, licence, source). Save also copies the credit to the clipboard.
- Off whenever *Allow web search* is off. No key needed.

### New: Crew

- Personal bots with a name, persona and dot avatar. Each bot has its own chat.
- Routines run on a schedule inside the bot's chat, so the bot remembers its runs. "Run now" runs one at once.

### New: Inkwell

- One feed of everything Kryzz made: images, clips and files, with filters and a NEW badge for pieces you have not seen.

### Tune (was Settings)

- One page with thinking mode tiles (Fast / Balanced / Max), the per-turn cost cap on a 24-dot slider, and rows for keys, persona, tools, privacy, appearance, voice, memory, skills, models, catalog, routines and About.
- Appearance: Dark, Light or Auto, plus an optional dot-grid background.

### Onboarding, lock and About

- Four-step onboarding: local first, keys, memory, privacy. Only an OpenRouter key is required.
- The lock screen can fall back to the phone's screen lock when no fingerprint is enrolled.
- About is a page with the version, credits and the font licences (Doto, Geist, Geist Mono and Space Mono, all SIL OFL 1.1).

### Motion

- LED things move in steps (strip wipe, rail, busy cables, caret blink, REC blink, a dot "develop" on fresh renders). Everything else eases.
- Turning animations off in Tune, or the system animator scale to 0, makes everything still.

### Fixes

- Restoring a backup on the same phone no longer deletes newer messages, tool steps and file links. A chat already on the phone is merged; a deleted chat comes back in full.
- The biometric lock could be skipped on a slow cold start. Settings now load before the lock decides.
- A swipe on the drawer no longer also taps the row under your finger.
- Bot routines no longer retry after a key, credit, or context-length error (a retry re-sent the prompt).
- Dialogs on Android 11+ no longer cut off their buttons or misplace the keyboard.
- Light theme: citation numbers, live dots, meters and other small yellow marks turn dark gold, and grey captions are a step darker, so they stay readable on white.

### Build

- Database version 13 (new `bots` table, `scheduled_tasks.botId`). It migrates in place from v1.1.2.
- Removed the aurora background videos and the GIF mascot (about 13 MB of raw assets). The release APK is about 7 MB (v1.1.2: about 19 MB).
- The release build is checked with R8 shrinking, including a run of the minified app on an emulator.
- Unit tests: 780, 0 failures. Instrumented tests: 41 of 41 passing on an API 35 emulator.

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
