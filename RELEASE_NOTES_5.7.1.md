# Kryzz AI 5.7.1

Auto file-deliverable fix: asking the Agent (Auto) for an Excel / Word / PDF file
now reliably produces the file. Version bumped to `5.7.1` (version code `79`).

## The problem

Three defects stacked into "Auto crashes when I ask for an Excel file, and it
can't make DOCX or PDF":

- **Strict artifact typing.** `validateArtifact` accepted only the exact strings
  `document` / `spreadsheet` / `database`. Asked for "an excel file", models
  naturally answer with `type: "excel"` / `"xlsx"` / `"csv"` (or `"docx"`,
  `"pdf"` for documents) — every one of those was a hard validation rejection.
  The tool call bounced, the model burned tool rounds retrying, and the turn
  died without a deliverable.
- **PDF did not exist.** `pdf` was not in the Auto keyword gate, not in the
  `create_artifact` enum, and there was no `OutputKind.PDF` or PDF generator —
  the model was never even given a way to make one.
- **Prose-only answers ended file requests.** When the artifact keyword gate
  fired but the model wrote the table/document out as chat text, the turn
  finished with no file. On top of that, models frequently wrap the CSV in a
  ```` ```csv ```` fence or emit a Markdown table, which previously landed in
  the .xlsx as garbage rows; and the binary conversion + disk write ran
  unguarded on the main thread.

## The fixes

- **Type aliases** (`domain/ArtifactKinds.kt`): `excel` / `xlsx` / `xls` /
  `csv` / `sheet` / `workbook` → spreadsheet, `doc` / `docx` / `word` /
  `markdown` / `report` → document, `sql` / `sqlite` / `db` → database,
  `pdf` → PDF. Applies to Auto and to the dedicated capabilities (a Spreadsheet
  maker call typed `excel` is accepted now too).
- **Real PDF output**: new `OutputKind.PDF`, a minimal valid PDF writer in
  `OfficeFileGenerator.pdf()` (standard-14 Helvetica, WinAnsi-sanitised text,
  word wrap, multi-page, heading styles), `pdf` added to the Auto
  `create_artifact` enum, the keyword gate (`pdf`, `word document`, bare
  `excel`, `workbook`, …), the output-card icon, and the export/import
  portability path (exports as `.txt` since the binary isn't portable).
- **Content normalisation**: one wrapping code fence is stripped from artifact
  content, and a Markdown table supplied as spreadsheet content is converted to
  real CSV (separator rows dropped, cells re-escaped) before XLSX generation.
- **One-shot artifact nudge**: a new high-precision `userWantsArtifactCreated`
  gate (creation phrasing only — "make an excel file", "turn this into a pdf";
  format questions like "what is a pdf used for" do NOT arm it). When it fired
  and the first model round is prose-only, Auto re-asks once with a dedicated
  `ARTIFACT_NUDGE_DIRECTIVE` telling the model to call `create_artifact` instead
  of pasting content into chat. Gated on the plan actually offering the tool;
  one nudge maximum, then the model's answer is accepted (v5.7 contract
  preserved).
- **Crash-proof materialise**: `GeneratedOutputStore.materialize` is now
  `suspend` on `Dispatchers.IO`, and if binary conversion ever throws, the raw
  content is saved with its plain-text extension (`.md` / `.csv` / `.txt`) so
  the user still gets a file instead of a dead tool call.

The v5.7 loop contract is otherwise untouched: calls returned → run them, text
→ final answer, one terminal no-tools round, XML recovery off on the terminal
round.

## Tests

- New `ArtifactKindsTest` (7 tests): alias resolution, fence stripping,
  Markdown-table→CSV conversion, CSV passthrough.
- `OfficeFileGeneratorTest` extended: valid PDF header/trailer, text escaping,
  multi-page pagination, blank/non-Latin input.
- `AgentTurnPolicyTest` extended: artifact gate nudges a prose-only first
  answer exactly once; calls still win; no behaviour change without the gate.
- `AgentToolIntentsTest` extended: format requests offer the tool; only
  creation phrasing arms the nudge.
- `:app:testDebugUnitTest` — BUILD SUCCESSFUL, 259 tests, 0 failures.

## Build

Built with the Temurin 17 + Android SDK 35 toolchain.

- `:app:assembleDebug` — BUILD SUCCESSFUL, `v5.7.1 kryzz ai (debug).apk`.
  Manifest: `versionName=5.7.1`, `versionCode=79`.
