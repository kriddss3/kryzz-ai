# Kryzz AI 5.6.5

Release build of the 5.6.4 work. Version bumped to `5.6.5` (version code `70`).
No behavioural changes over 5.6.4 — this is the consolidated release of the
voice / location / memory adjustments:

- Voice chat opened through the side panel now starts reliably (lifecycle-aware
  voice start).
- The voice chatting bot now receives your approximate location directly.
- Auto memory creation fixed for self-contained facts (age / gender / demonym
  without trailing punctuation) and for compound first-person clauses
  ("my name is Alex and I live in Gilly" now produces two memories, not one).

See `RELEASE_NOTES_5.6.4.md` for the full details of each adjustment.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: 70
- Version name: 5.6.5
