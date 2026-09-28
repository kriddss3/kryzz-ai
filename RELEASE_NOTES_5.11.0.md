# Kryzz AI 5.11.0

Public **v1.1.2**. Agent Auto rework, phases 1–4, shipped together. versionCode `84`.

## Agent

- Live plan card (`update_plan`). Planning rounds do not consume the step budget.
- Time, calculate, weather, and file creation are offered every round.
- `fetch_url` is allowlisted to links the user shared, search results from this chat, or a site the user named.
- Search intent is narrower. Follow-up replies carry recent citations and file names.
- One review pass on substantial answers. Trivial turns skip it.
- Per-turn cost cap, default $0.25, up to $2. Hitting the cap still writes a final answer.
- Context compaction after 60k characters of older search and page results.
- Step budget 4–24 (default 16). Voice stays on one round.

## Quality

- Fast / Balanced / Max on the Agent composer.
- Prompt prefix is stable so compatible providers can cache it.
- Agent default `google/gemini-3.8-flash`. Max default `anthropic/claude-sonnet-5`.
- Composer warns when the selected model cannot call tools.

## Files and activity

- One Markdown model for DOCX, XLSX, and PDF.
- DOCX: heading styles, real lists, bordered tables, hyperlinks.
- XLSX: typed numbers, percentages, booleans, formulas, frozen header, autofilter, `### Sheet: Name` for extra sheets.
- PDF: system fonts (Unicode, including CJK), page numbers. Standard-14 writer remains the fallback.
- Work log under each agent answer, built from stored tool rows.
- Activity chips name the query, page, or place. Concurrent calls get their own chips.
