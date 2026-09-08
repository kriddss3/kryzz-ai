# Kryzz AI 5.6.11

Agent Auto gained a real toolkit and a starter skill pack. Version bumped
to `5.6.11` (version code `76`).

## New Auto tools

These are callable tools, not prompt fluff. Time and calculate are always
offered in Agent mode. The rest appear only when the request matches, so
Auto does not dump 15 schemas on "hi".

| Tool | What it does |
|---|---|
| `get_current_time` | Device date, time, weekday, timezone |
| `calculate` | Safe arithmetic (`+ − × ÷ % ^`, `sqrt`/`abs`/`min`/`max`/`round`) |
| `get_weather` | Open-Meteo 3-day forecast. Place name or the saved phone location. No extra key |
| `fetch_url` | Read a public http(s) page. Private/local addresses blocked |
| `remember_fact` / `recall_memories` | Explicit local memory write/search when Memory is on |
| `schedule_task` | Daily or weekly reminder in its own chat |
| `create_artifact` / `create_skill` / `create_code_project` | Now available in Auto when the user actually asks for a file, skill, or zip |

## Starter skills

Eight skills seed on first launch (`starter.*` ids). Matching ones inject
as PRIMARY; the rest show as name + purpose only so the prompt stays small.
They appear in Tools & skills with a Starter badge and can be toggled off.

- Research brief
- Study notes
- Essay outline
- Ready-to-paste email
- Decision memo
- Fact check
- Daily planner
- Code walkthrough

The catalog also lists the new Auto tools and adds the missing Video and
Wide search shortcuts.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 229 tests, 0 failures
  (new `LocalCalculatorTest`, `LocalClockTest`, `AgentToolIntentsTest`,
  `StarterSkillsTest`, `PublicWebClientTest`).
- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.6.11 kryzz ai (debug).apk`
  (~78 MB). Manifest: `versionName=5.6.11`, `versionCode=76`.
