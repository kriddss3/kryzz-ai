# Kryzz AI 5.13.10

Public **v2.2**. Ten internal drops since v2.1 (5.13.1 to 5.13.10), shipped together. versionCode `110`.

No database change. Chats, keys, memory, skills, bots and settings carry over from v2.1.

## Talk and Run

- **Files from a file name.** Talk and Run now name the file and its extension picks the format: PDF, Word, Excel, **PowerPoint (.pptx)**, HTML, vCard, SQLite, SVG, ZIP (with Word, JSON and other files inside), and any text or code file (.py, .json, .yaml and so on). "Make me a pdf" no longer gets "I can't create PDF files": Talk has its everyday tools on every turn, and says so.
- **Real slide decks.** Markdown becomes a 16:9 deck: a dark cover, white content slides with a yellow rule, bullets, numbered lists, tables, quotes and code. Slides split at `---` and at slide-level headings.
- **Edit what it made.** The source of the latest reply's files goes back to the model, so "add a slide" edits the deck instead of rewriting it from memory.
- **Calendar events open the phone's calendar editor**, prefilled, so saving adds the event. (The .ics card used to do nothing on most phones.)
- **"make ..." and "find ..." work from Talk.** A message that starts with *make* generates an image (or a clip when the rest asks for one); *find* searches photos. "Find out" stays a normal reply.
- **Rate limits.** A 429 that arrives mid-stream, after HTTP 200, is now retried briefly, and on OpenRouter the route that just refused is skipped, instead of showing "Rate limit reached" on every message.

## Voice

- **Kryzz stops hearing itself.** A voice session now runs on the phone's voice-call audio path, the only stream the platform echo canceller can subtract from the mic. Spoken replies keep the speech content type so they are not narrowband down-sampled. Adds `MODIFY_AUDIO_SETTINGS`; the route is restored when the session ends.
- **Echo-aware barge-in.** Talking over a reply is judged against the real playback level (the playback waveform was being read as signed bytes, which turned silence into full scale). Outshouting the speaker or a soft interruption both count; speaker leak does not.

## Make and Find

- **Start from your own photos and clips.** Add up to four photos or clips to the next render: the model edits them, or uses one as the start frame or a reference.
- **Make from a finished render.** Put any finished image or clip back on the draft to edit it or continue from it.
- **Find has a Web mode.** Next to Images, a Web switch runs a live search through Parallel and returns a synthesized answer with clickable sources, with loading and retry states. Off whenever *Allow web search* is off.
- **Image viewer.** Save, Source and Share, plus **Use in Make**, which stages the image in Make's composer. Drag down, tap the handle or press Back to close.

## Models and Memory

- **Models is a Tune board.** A word index for the purpose, then six recommended models pinned on top (each slot is the newest match in the live catalog, so a newer Sonnet or Gemini Flash takes the row), saved models, and the rest as hairline rows. The row in use carries the yellow dot, the favourite star and its thinking level.
- **Auto memory.** After each finished turn a tiny model (or a local fallback when none is configured) extracts or updates durable facts about you. Anything that looks like an API key, token or private key is never stored.
- **Core profile recall.** Pinned memories and your preference facts are recalled even when a prompt shares no words with them.
- **Edit and pin** memories from the Memory page.

## Look

- **LED icon set.** 64 generated dot-matrix icons (`kz_led_*`): lit dots follow the tint, the live dot stays yellow, unlit dots stay grey.
- **New splash and launcher marks**, in dark and light.
- **Talk splash.** A water-ripple dot grid on Talk, with a secondary colour you pick in Tune.
- **Chat-library mark** in the ink strip: three rows of dots with one live yellow dot.
- **Arm rail scrub.** Dragging along the rail ticks on every dot and writes the arm's name to the left of your thumb. The arm under the finger opens on release, so scrubbing past Voice never starts the microphone.

## Under the hood

- New tests for the slide planner, the .pptx package, file formats, Talk commands, echo-aware barge-in, the dot splash, auto memory, calendar events, Find's web session, recommended models and Make sources, plus a device test that runs a whole Talk turn that makes seven files.
