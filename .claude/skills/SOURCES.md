# Vendored skills

Copied from upstream so every Claude Code session on this repo loads them.
Each skill folder keeps its upstream LICENSE.

| Skill folder(s) | Upstream | Commit | License |
|---|---|---|---|
| `brag`, `brag-slim` | https://github.com/latent-spaces/brag (`skills/`) | `c893c5ed52aed84e3e2ee56787de869fccdae6b0` | MIT |
| `impeccable` (+ `../agents/impeccable-*.md`) | https://github.com/pbakaus/impeccable (`.claude/`) | `9d715cc4f5564a990ca8345abfdd5df6dc9b41c8` | Apache 2.0 |
| `design-taste-frontend`, `gpt-taste`, `image-to-code`, `redesign-existing-projects`, `high-end-visual-design`, `full-output-enforcement`, `minimalist-ui`, `industrial-brutalist-ui`, `stitch-design-taste`, `imagegen-frontend-web`, `imagegen-frontend-mobile`, `brandkit` | https://github.com/Leonxlnx/taste-skill (`skills/`) | `ce26fc25c0e5e8cab638f883de62d9a86ee5e45b` | MIT |

Notes:

- taste-skill folders are named after each skill's `name:` field, matching `npx skills add`. The legacy `design-taste-frontend-v1` was left out.
- Impeccable's design-detector hooks are not enabled. Turn them on per machine with `/impeccable hooks on`.
- `impeccable/scripts/impeccable` downloads a checksum-verified engine binary from the upstream GitHub releases on first run.
- To update, re-copy the folders from a newer upstream commit and bump the table.
