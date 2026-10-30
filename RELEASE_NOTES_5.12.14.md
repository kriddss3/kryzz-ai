# Kryzz AI 5.12.14

Public **v2.0**. The Nothing "Eight Arms" redesign, phases A through E, shipped together. versionCode `99`.

Chats, keys, memory, skills and settings carry over from v1.1.2. The database moves to version 13 in place.

## Layout

- Home is the start screen: a board of eight arms (Talk, Make, Find, Crew, Run, Voice, Link, Tune) with live captions, busy cables and the dot squid.
- An 8-dot arm rail on the right edge, with drag-to-scrub.
- An LED ink strip at the top with the screen's status line. Tap it for the Inkwell.
- The chat library is a left drawer: pinned chats, day groups, folders, search, chat actions, backup.

## Arms

- **Talk** (was Chat): departure-board history, gist, source readout, citation superscripts, follow-up queue, Talk options sheet.
- **Run** (was Agent): Fast / Balanced / Max header, live plan card, work chips and log, file cards, cost line.
- **Voice**: LED waveform, REC timer, transcript, mute pill.
- **Make** (new): image and video studio with provider ratios, background renders, gallery save, share, recent ink.
- **Find** (new): Wikimedia Commons images with licence filters and credit lines. Gated by *Allow web search*.
- **Crew** (new): bots with personas, avatars, their own chat, and scheduled routines that run inside that chat.
- **Inkwell** (new): every made image, clip and file in one feed.
- **Tune** (was Settings): one page with thinking mode tiles, the cost cap slider, and rows for every subpage.
- **Link**: a preview page for a future bridge from Nothing's Essential Space. Nothing is connected yet.

## Also

- Four-step onboarding, a kit lock screen with a screen-lock fallback, and an About page with the font licences.
- Stepped LED motion and eased page transitions. All of it stops when animations are off.
- Light, Dark and Auto themes. In Light, yellow is used for fills only: yellow text and small marks become dark gold (5.9:1), and tertiary grey captions move from #858787 to #6F7070 (4.5:1).

## Fixes

- Backup restore merges into chats already on the phone instead of replacing them (the old replace deleted newer messages and file links).
- Cold-start lock bypass closed.
- Drawer swipes no longer click the row underneath.
- Bot routines do not retry on key, credit, validation or context-length errors.
- Dialog windows fit the screen on Android 11+.

## Build

- Room 12 → 13: `bots` table and `scheduled_tasks.botId`. Checked by installing over the previous build and by `DatabaseMigrationTest`.
- Removed the aurora MP4s, the GIF mascot and the code that drew the old backgrounds, wordmark and mascot.
- `assembleRelease` with R8 minify and resource shrinking: builds clean, 6.6 MB unsigned (v1.1.2 was about 19 MB). A debug-signed copy under its own package ran on the emulator: onboarding, a Talk request, a Find search, a new bot, the font licences, and a backup export and import all worked, and it cold-starts in about 0.4 s.
- Unit tests: 780, 0 failures. Lint: 0 errors. Instrumented tests: 41 of 41 passing on the API 35 emulator.
