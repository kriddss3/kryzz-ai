# Kryzz AI 5.6.12

MiniMax Auto tool-calling fix over 5.6.11. Version bumped to `5.6.12`
(version code `77`).

## Why Auto still errored

5.6.11 added `get_current_time` on **every** Agent turn with this schema:

```json
{ "type": "object", "properties": {}, "additionalProperties": false }
```

Empty `properties` plus `additionalProperties: false` is invalid JSON Schema.
MiniMax answered **2013**. The retry then dropped `tool_choice`, hit 2013
again, and **stripped every tool**. Auto could only talk about calling tools,
or surface the 2013 text as an error.

## Fix

- `get_current_time` now requires a `timezone` argument (`"local"` = phone clock).
- A 2013 / validation retry no longer jumps from “all tools” to “no tools”.
  It drops only the extra utility tools and keeps `parallel_search`.
- Covered by `MiniMaxToolSafetyTest`.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 235 tests, 0 failures
  (new `MiniMaxToolSafetyTest`).
- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.6.12 kryzz ai (debug).apk`
  (~78 MB). Manifest: `versionName=5.6.12`, `versionCode=77`.
