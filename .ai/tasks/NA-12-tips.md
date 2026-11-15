# Task: NA-12-tips - Six-field screenshot benchmark baseline

Status: Ready for implementation (owner authorized server scoring and private tips labelling on 2026-11-15)
Risk: high (private screenshot labels and accuracy evidence for earnings import)
Implementer: Codex (Sol), assigned by owner
Review: fresh top-model session using review-task

## Objective
Unblock FB-038's measured Android reader decision by scoring tips and all-six-correct screenshots in the server's
existing opt-in OcrSampleAccuracyTest. Nothing in the shipped parser, earnings logic or API changes.

## Files to read
- AGENTS.md, .agents/skills/implement-task/SKILL.md, scripts/verify.ps1
- .ai/tasks/NA-12.md (shared library and current accuracy-test behavior)
- flexbuddy/src/test/java/com/angel/flexbuddy/service/OcrSampleAccuracyTest.java
- flexbuddy/src/main/java/com/angel/flexbuddy/service/{TesseractScreenshotTextExtractor,ScreenshotPreprocessor}.java
- flexbuddy/pom.xml (existing test configuration), docs/native-app-plan.md NA-12 step 3
- Android .ai/tasks/FB-038.md and its implementation handoff
- Private FB-003 labels and original screenshots only for local labelling/benchmarking, never copied into Git or output

## Required behavior
- Add optional tips CSV column after pay; preserve compatibility with existing five-field labels.
- Compare base pay and tips with BigDecimal.compareTo; preserve other exact comparisons and blank-label skips.
- Report each field's checked/correct counts and fully-labelled/all-six-correct screenshot counts. A partial label
  must never count as all-six correct. Count processed images after label filtering, not all directory entries.
- Add synthetic unit coverage for comparison and completeness accounting; no private fixtures.
- Keep existing parser, OCR configuration, fixed 2026-10-21 context and year, and optional sample filtering unchanged.
- Keep per-image reports and raw text local and ignored. Console summaries contain aggregates only. Capture native
  benchmark output into ignored artifacts; never echo private values or filenames.
- Owner authorizes the implementer to label tips by inspecting original screenshots, preserving the other five
  columns and recording ambiguous/unreadable tips as unlabelled rather than guessing. Back up the private CSV locally.
- Run the server baseline and Android candidates on exactly the same labelled corpus. Only aggregate results leave
  ignored local artifacts. No production operations, dependencies, push or PR.

## Acceptance criteria
- [ ] Six-field scoring and fully-labelled/all-six counts, with five-field compatibility.
- [ ] Synthetic scoring tests pass.
- [ ] Private tips labels reviewed and limitations recorded without disclosing values or source names.
- [ ] verify.ps1 -TaskId NA-12-tips results/skips and baseline aggregates recorded in implementation note.

## Validation
Run existing wrapper tests and scripts/verify.ps1; PostgreSQL migration test may remain explicitly skipped if no
local test DB is configured. Run opt-in OcrSampleAccuracyTest with private env paths and captured output; compare
its aggregate six-field report with FB-038. No browser test needed for test-only changes.

## Security and regression risks
Private labels or screenshots leaking into Git/log output; partial labels overstating accuracy; floating-point
money comparison; accidentally changing parser rules to improve a candidate; modifying production or the separate
Android checkout. No application data, schema, releases or package publishing changes.
