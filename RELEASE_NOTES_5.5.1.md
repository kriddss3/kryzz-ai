# Kryzz AI 5.5.1

Patch drop on top of 5.5.0. Three targeted fixes plus the usual build/version bump.

## Side panel
- **Swipe-to-open from the left edge works again.** The 5.5.0 settle-target
  change over-corrected for "drawer resurrects after a tap" by forcing the
  target to `0f` whenever `open == false`, which also killed the one-frame
  window where a fresh swipe-to-open is still waiting for the caller state to
  catch up. The settle effect now tracks the previous `open` value so it can
  tell a fresh swipe (prevOpen false → target honoured) apart from a stale
  opening target left behind after the caller closed the drawer mid-animation
  (open true → false → target dropped). Scrim, back, and conversation/settings
  taps can no longer resurrect a half-opened drawer, and a swipe-open actually
  opens.
- `panelAnimationTarget` once again honours a fresh drag settlement, and a new
  `shouldClearStaleOpenTarget` guard encodes the resurrect-prevention as a
  pure, unit-tested rule. `SidePanelStateTest` covers the fresh-swipe,
  stale-target, close-settlement, and open-follows-caller cases.

## MiniMax agent (status 2013 "invalid tool type")
- MiniMax's chat-completions endpoint does not accept the OpenAI-style
  `tool_choice: "required"` string and rejects the whole agent request with
  `base_resp.status_code` 2013. The agent now sends `"auto"` for MiniMax
  instead of `"required"` (tool descriptions are strong enough for M3 to call
  them), so the standard agent/search flows no longer 2013 out of the box.
- **Graceful fallback:** if a provider still rejects a tools payload with a
  validation fault (MiniMax 2013 in-stream or as an HTTP 400), the agent
  retries that round once as plain chat with `tools = null` so the user gets
  an answer instead of a hard error, then continues normally.
- MiniMax errors now surface the vendor `status_code` next to the message
  (e.g. `invalid tool type (2013)`), and inline `base_resp` errors emitted
  mid-stream are parsed and forwarded as failures instead of being silently
  dropped.

## Agent workspace (UI)
- Removed the duplicate KryzzBot mascot from the chat top bar so he appears
  only once on screen at a time (either the empty-workspace hero or the
  live activity status card, never the header).
- The Agent empty workspace is now lean: just the KryzzBot hero and
  "KryzzBot is online". The mission blurb and the "Mission module" card have
  been removed to cut the wall of text on a fresh agent screen.

## Build
- Debug package: `ai.daylight.assistant.debug`
- Release package: `ai.daylight.assistant`
- Version code: `64`
- Version name: `5.5.1`
