# Driver experience improvement plan

Status: Proposed backlog; no implementation started

Source: November 5, 2026 emulator audit and a targeted check of the current repository

Make everyday phone use faster and prevent an accepted block from being mistaken for earned money. This plan covers all nine audit findings and all three feature ideas. It separates observed problems from improvements to features that already exist.

The audit exercised the installed Android app. Repository code may differ from that build. Reproduce each finding against the implementation branch before making changes. Local audit notes and screenshots are ignored under `.ai/implementations/driver-audit-2026-11-05.md` and `.ai/artifacts/driver-audit-2026-11-05/`; they are supporting evidence, not prerequisites for using this plan. Do not commit real screenshot contents or driver figures.

## Delivery order

These DX references are plan sections, not allocated FB task numbers. Promote one bounded section at a time into `.ai/tasks/<task-id>.md` using `TEMPLATE.md`. Check main and remote task branches for the next free ID; create its `ai/<task-id>` branch and commit the specification before implementation. The owner must explicitly approve each push or merge.

| Wave | Sections | Outcome |
|---|---|---|
| 1 | DX-01, DX-02, DX-03 | Explicit completion decisions, safe status correction, trustworthy net labels |
| 2 | DX-04, DX-06, DX-08, DX-09 | Clear expense effects, local-only forfeit wording, report coverage, recognizable recovery rows |
| 3 | DX-05, DX-07 | Shorter phone flows and phone-appropriate import instructions |
| 4 | DX-10, DX-11, DX-12 | Multiple imports, validated Android sharing, and a clearer post-block review |

Do not hold small copy fixes for a later feature release. DX-11's existing-share verification can happen earlier; any batch-sharing enhancement follows DX-10. DX-05 should be split by screen if it exceeds one implementation session. One agent edits each working tree at a time.

## Existing work to preserve

- **FB-008:** duplicate import warnings. The audit saw the warning and its Open it shortcut work. Extend that behavior for batches; do not replace it.
- **FB-009:** missing-mileage counts and explanations already exist in the checked code. DX-03 improves coverage, visibility, and the correction shortcut without recalculating earnings.
- **FB-010:** dated mileage-rate work is separate. These improvements must use the effective account cost method/rate, not introduce another rate table or copy a rate from the audit. Check its implementation/merge state before editing overlapping files.
- **Android sharing already exists:** `share_target` in the web manifest, `shareTarget` in the TWA manifest, a service-worker inbox, and a server fallback for `/share-import`. DX-11 is validation and targeted repair/discoverability work, not a new integration from scratch.
- **Post-block fields already exist:** `finish.js` supports actual times, miles/odometer, route details, and missing-mileage reminders. DX-12 connects existing workflows and identifies remaining gaps.
- Existing task headers say Ready for implementation even where corresponding behavior is visible. Verify branch/history state rather than treating that label as proof work is absent.

## DX-01 — Require an explicit completion decision on imports

Priority: High. Risk: high, because the chosen status controls earnings. Implementer: Claude Code (Sonnet).

**Observed:** a past Schedule Details screenshot defaulted to Completed · worked and paid, although the screenshot established only an accepted block. `ShiftImportService.suggestStatus` currently infers scheduled/completed from date/time and defaults a missing date to completed.

**Plan:** keep extracted values and status suggestions, but require an explicit worked/not-yet-worked decision before saving an import as completed. A date or OCR-quality score must not establish completion or payment. Keep the current status model for this first task; payment tracking is addressed separately in DX-12. Do not silently reclassify existing records.

**Read:** `ShiftImportService.suggestStatus`, `ShiftCandidate`, `ShiftImportPreviewResponse`, import handlers in `static/js/app.js`, `templates/shifts.html` import form, `ShiftController`, create-request validation, `ShiftImportServiceTest`, `ShiftControllerTest`, and existing duplicate tests. Search both catalogs for `import.*` and the relevant `js.app.*` import keys.

**Acceptance:**

- [ ] Past, future, and unreadable-date screenshots all require an intentional completion choice; changing the screenshot resets that choice.
- [ ] Choosing scheduled saves no earned pay, hours, or net contribution; choosing completed requires the driver's confirmation.
- [ ] Cancelling, navigating away, OCR failure, and duplicate detection create no record automatically.
- [ ] The authoritative save path enforces the agreed acknowledgement contract without breaking supported manual creation, offline writes, or older clients. Document compatibility before implementation.
- [ ] English and Spanish distinguish accepted, worked, and payment verification; no new paid/unpaid field is implied by copy alone.

Validation: Java import/save tests, Node state-transition tests, and a local phone-width import with a schedule screenshot, an earnings screenshot, an empty-offers screenshot, and a missing-date screenshot. Use labeled source values when claiming OCR accuracy.

## DX-02 — Correct a block saved with the wrong completed status

Priority: High. Risk: high, because status changes affect earnings and saved data. Implementer: Claude Code (Sonnet). Depends on the completion wording/contract from DX-01.

**Observed:** Scheduled is disabled when editing a completed block. This is also a server restriction in `ShiftService.applyRequest`, not just a disabled option.

**Plan:** add a deliberate correction path for an incorrectly completed block. Do not merely enable the option or remove the server guard. Before implementation, specify how tips, entered miles, actual times, route details, linked expenses, and payout reconciliation behave. Show the data affected and require confirmation; retain recoverable information or provide an undo path. Keep ordinary cancel/forfeit/reschedule rules intact.

**Read:** `ShiftService.applyRequest`/`applyDetails`/status updates, `ShiftStatus`, `Shift`, update/status DTOs and controller, edit and undo handlers in `app.js`, `outbox.js`, `PayoutService`, backup/restore services, `ShiftServiceTest`, `ShiftControllerTest`, `ShiftStatusTest`, `PayoutServiceTest`, and existing restore tests. Search catalog keys `error.shift.*CannotReschedule` and edit/status messages.

**Acceptance:**

- [ ] A driver can intentionally correct an accidental completed status without deleting/recreating the block.
- [ ] Scheduled records are removed from earned pay, hours, counted miles, net, and dependent report/payout views consistently.
- [ ] Linked expenses and detail fields follow the documented preservation/clearing policy; no silent data loss or cross-user changes.
- [ ] Cancelling confirmation leaves the record unchanged; repeat/stale/offline attempts cannot apply a correction twice.
- [ ] Existing backup versions remain restorable. Any schema change uses a new migration and receives migration validation.

Validation: service/controller tests covering affected aggregates, details, ownership, stale requests, undo if provided, and backup/restore; local phone walkthrough before/after correction. Do not change a real user's record to test this.

## DX-03 — Make missing-mileage warnings consistent and actionable

Priority: High. Risk: medium if labels/navigation only; elevate to high if formulas change. Implementer: Antigravity for the bounded UI task. Builds on FB-009.

**Plan:** show an understandable incomplete-estimate label wherever affected net or net/hour appears: Home, Reports cards, and History rows. Add an Add miles shortcut to the existing edit/finish flow. Use the existing missing-mileage signal and distinguish `null` (unknown) from explicit zero.

**Read:** `home.js`, report/statistics/history rendering in `app.js` and `reports.js`, `finish.js`, `ShiftStatisticsResponse`, `NetEarningsResult`, `ShiftReportService`, `styles.css`, `shifts.html`, FB-009, `home.test.js`, `reports.test.js`, `NetEarningsCalculatorTest`, and `ShiftReportServiceTest`. Search `blocksWithoutMiles`, `js.home.blocksNoMiles.*`, and net-caption keys.

**Acceptance:** unknown miles are clearly flagged beside every affected net figure; the shortcut opens the correct owned block; explicit zero and actual-expenses mode follow the established rules; entering miles clears the warning; formulas and existing monetary results are unchanged. Check contrast, keyboard focus, English, and Spanish on a phone.

## DX-04 — Explain what a saved expense does to estimated net

Priority: Medium. Risk: medium for feedback only. Implementer: Antigravity.

**Observed:** every expense save uses `js.account.netRecalculated`, including fuel under standard mileage, which the page says does not separately lower net.

**Plan:** use category- and cost-method-aware guidance beside category/amount and in the save confirmation. Explain that vehicle costs are covered by mileage where applicable. Do not imply that every expense lowers net, and do not change the calculation.

**Read:** expense form/render/save functions in `app.js`, `ExpenseCategory`, `VehicleCostMethod`, `ExpenseService`, `NetEarningsCalculator`, expense form in `shifts.html`, `ExpenseServiceTest`, `NetEarningsCalculatorTest`, and JS test patterns. Search `js.app.expenseAdded`, `js.app.expenseUpdated`, `js.account.netRecalculated`, and `js.app.costStandard`/`costActual` in both catalogs.

**Acceptance:** guidance matches the calculator for every category under both methods; changing category/method refreshes the explanation; confirmation accurately describes save versus net impact; edit/delete/restore preserve the calculation rules. Use meaningful table-driven rule tests and local fuel/toll examples.

## DX-05 — Reduce scrolling and control overlap on phones

Priority: Medium. Risk: medium. Implementer: Antigravity. Split into Schedule, Import, and Expenses tasks as needed.

**Plan:** compact the phone header and empty Schedule panels; reduce the upload card height; make expense history accessible without passing the full form; keep save actions reachable and prevent fixed navigation/the floating plus from obscuring interactive content. Preserve comfortable tap targets and current desktop behavior. Avoid adding a UI dependency.

**Read:** `styles.css`, `shifts.html`, `schedule.js`, `home.js`, screen navigation/import/expense/modal code in `app.js`, and existing layout-related Node checks.

**Acceptance:**

- [ ] At 360px, 390px, and 430px widths, all fields and actions remain reachable with the keyboard open, in landscape, and with enlarged text.
- [ ] Empty Schedule reaches useful calendar content sooner; expense history has a clear direct route; import review and save remain obvious.
- [ ] Fixed controls do not cover focused fields, validation messages, or the last actionable row. No horizontal scrolling is introduced.
- [ ] Modal focus, back/cancel behavior, screen-reader labels, safe-area spacing, desktop layout, and Spanish wrapping remain usable.

Validation: run locally with synthetic empty/populated data; compare before/after phone screenshots and required scroll steps. Use manual visual checks for CSS changes; do not add tests that only assert CSS source text.

## DX-06 — Say that forfeiting here only updates FlexBuddy

Priority: Medium. Risk: low for localized copy only. Implementer: OpenCode + local model.

**Plan:** label the action as marking/recording a forfeit and explicitly state in normal and late-forfeit confirmations that FlexBuddy does not cancel the Amazon Flex block. Tell the driver to handle that in Amazon Flex. Preserve deadline calculations, late flags, and status transitions.

**Read:** `schedule.js` confirmation functions, related `shifts.html` actions, and `js.schedule.forfeit*`/late-forfeit keys in both catalogs. Search existing `calendar.test.js` and schedule coverage before adding any tests.

**Acceptance:** both confirmation variants make the scope clear in English/Spanish; cancel makes no change; confirmation records only the local tracker status; no Amazon integration or external cancellation action is added. Validate locally without forfeiting a real block.

## DX-07 — Use phone-appropriate import instructions

Priority: Low. Risk: low. Implementer: OpenCode + local model. Coordinate with DX-05 and DX-10.

**Plan:** use Choose screenshot/Choose from photos on phones and concise guidance about supported Flex screens and formats. Preserve useful drag-and-drop instructions on desktop. Show synthetic examples or descriptions; do not publish Dropbox screenshots or imply that an empty-offers screen contains an importable block.

**Read:** import section of `shifts.html`, upload handlers in `app.js`, responsive CSS, and `import.dropYourScreenshotHere`, `import.orClickToBrowseYour`, and format-help keys in both catalogs.

**Acceptance:** phone copy uses touch language; formats/size limits match actual validation; the chooser remains keyboard accessible; examples distinguish accepted schedule details from earnings evidence; desktop drag/drop still works.

## DX-08 — Clarify the dates included in a report

Priority: Medium. Risk: medium for captions only. Implementer: Antigravity.

**Observed:** All time displayed first/last shift dates even when an expense outside that interval affected net.

**Plan:** distinguish the selected reporting period from the dates of shifts found within it. Explain expense-date inclusion and export scope using the existing filters. Do not expand/restrict data or change arithmetic as a side effect of a wording task.

**Read:** `reports.js`, report-scope text in `app.js`, `ShiftReportService`, `ShiftFilter`, `ExpenseFilter`, shift/expense export paths, `ShiftReportServiceTest`, `ShiftCsvWriterTest`, and `reports.test.js`. Locate current scope-message keys by searching the renderer, then inspect those keys in both catalogs.

**Acceptance:** All time, custom ranges, no-shift/expense-only periods, and station filters have unambiguous captions; an expense outside the displayed shift-date span is not implied to be excluded; UI/export descriptions match existing behavior; original totals and filters are unchanged. Verify with synthetic shifts and an expense on a different date.

## DX-09 — Identify deleted expenses before restoring them

Priority: Low. Risk: medium for a bounded recovery-list UI; deletion behavior remains unchanged. Implementer: Antigravity.

**Plan:** include the escaped note and available linked-station context in Recently deleted expense rows, alongside category, amount, and date. Handle long notes and missing/deleted linked shifts without creating a new query per row.

**Read:** `loadExpenseTrash` in `app.js`, `ExpenseResponse`, `ExpenseService` trash mapping, expense trash markup in `shifts.html`, relevant CSS, `ExpenseServiceTest`, and `ExpenseControllerTest`.

**Acceptance:** similar deleted expenses can be distinguished; notes render as text rather than executable markup; ownership scoping and the 30-day window are unchanged; restore/delete controls remain distinct and usable on phones. Validate with two similar synthetic expenses carrying different notes. Any change to deletion/purge semantics becomes a separate high-risk task.

## DX-10 — Review and import several screenshots

Priority: Feature. Risk: high, because retries and batch saves can duplicate earned records. Implementer: Claude Code (Sonnet). Depends on DX-01; preserve FB-008 duplicate handling.

**Plan:** add a bounded multi-file selection queue with per-file processing, review, duplicate handling, retry, skip, and cancellation. Reuse the existing upload validation and server read gate. Process OCR sequentially initially rather than multiplying memory load. Set a batch-count limit in the task specification and retain the existing per-file size/type limits.

**Read:** screenshot input/import state in `app.js`/`shifts.html`, `import-duplicates.js`, `ShiftImportService`, `ScreenshotReadGate`, import DTOs/controller, `outbox.js`, `ShiftImportServiceTest`, `ScreenshotReadGateTest`, `import-duplicates.test.js`, and `outbox.test.js`.

**Acceptance:**

- [ ] Every file has a visible result and explicit review/completion decision; an invalid file does not discard other reviews.
- [ ] Duplicates are checked against saved history and earlier items in the same batch; a retry or double tap cannot create the same reviewed entry twice.
- [ ] Partial success identifies exactly what saved; cancellation stops further work and leaves already-saved records intact and visible.
- [ ] Navigating away or losing connectivity does not silently save/retry unreviewed files. Match the existing online-only OCR behavior and document any supported draft recovery.
- [ ] Synthetic batch tests cover mixed valid/invalid files, repeated images, changed pay on an existing block, expired previews, server-busy responses, and interruption during save.

Prefer existing single-file APIs where safe. Any idempotency endpoint or persistence change needs explicit service-level design, ownership checks, and compatibility tests before implementation.

## DX-11 — Validate and improve existing Android sharing

Priority: Feature validation. Risk: medium; Android work stays with Claude Code (Sonnet). Batch sharing depends on DX-10 only if added.

**Plan:** test the current share route from Photos and Dropbox before changing it. Verify warm/cold launch, cancellation, signed-out entry, unavailable service worker, stale inbox cleanup, and unsupported files. Add visible in-app guidance if drivers cannot discover sharing. Make targeted fixes only for reproduced failures; do not rebuild a feature already present.

**Read:** `static/manifest.webmanifest`, `sw.js` share-inbox functions, shared-import handling in `app.js`, `PageController.sharedScreenshotWithoutServiceWorker`, related page/security tests, `pwa-language.test.js`, `android/twa-manifest.json`, generated Android manifest/configuration, and `android/README.md`.

**Acceptance:** a supported shared image reaches the existing review flow with no automatic record creation; signed-out/fallback paths clearly ask the driver to sign in/reselect as necessary; stale shared files and sign-out cannot expose a previous user's image; cancel creates no record. Validate current single-image support first and document multi-image behavior rather than claiming it works.

If a wrapper change is required, update `twa-manifest.json`, raise `appVersionCode`, and record the owner-managed Play upload in `android/CHANGELOG-android.md`. Preserve the package name, upload key, and both asset-link fingerprints. Signing/upload remain owner actions; no keystores or signed builds enter Git. Use localhost/test fixtures for write checks; do not treat the audit's session-specific production-write permission as blanket authorization for future implementation sessions.

## DX-12 — Connect the existing post-block checklist

Priority: Feature. Risk: medium for navigation/checklist UI, high if payment persistence or earnings semantics change. Implementer: Antigravity for the UI-only task; Claude Code (Sonnet) for any separate financial/schema task. Depends on DX-01 and DX-03; coordinate layout with DX-05.

**Plan:** expose a concise Review this block entry point linking actual finish time, miles/odometer, tolls/parking, and payout review. Reuse `finish.js`, expense-to-shift links, and payout reconciliation. Make incomplete, explicitly zero, and optional/skipped information distinct. Do not introduce another definition of completed or paid inside UI code.

**Read:** `finish.js`, `home.js`, relevant edit/expense handlers in `app.js`, `ShiftService` missing-mileage/details logic, `PayoutService` and controller, `shifts.html`, `ShiftServiceTest`, `PayoutServiceTest`, `PayoutControllerTest`, and `home.test.js`.

**Acceptance:** the checklist opens the correct owned block; saving one section preserves other details; scheduled work contributes nothing to earnings until confirmed; explicit zero miles resolves the existing reminder correctly; a toll can be linked without re-entering the block; payment review leads to the existing payout flow. Checklist use is optional and introduces no new notification subscription automatically.

Decision before promoting a payment task: determine whether existing payout-level verification is sufficient. If independent per-block paid/unpaid tracking is needed, specify its relationship to worked status, reports, reconciliation, migrations, and old backups in a separate high-risk FB task. Do not equate completed with verified payment by default.

## Shared validation and release requirements

For every promoted implementation task:

1. Add targeted Java tests for service/endpoint changes and meaningful Node tests for changed exported browser behavior. Run Node tests whenever `static/js` changes; they are separate from Maven.
2. Run `.\scripts\verify.ps1 -TaskId <task-id>` from the repository root, then read its `summary.md`. Record actual results and every skipped check in the handoff. Run the PostgreSQL migration test when a migration is added; otherwise explicitly state it was not run if unavailable locally.
3. Run the app locally and validate affected pages at phone width, in English and Spanish, using synthetic data. New text belongs in matching catalog groups; preserve placeholders/markup and message-format apostrophe escaping. Never use `toISOString()` to obtain a local calendar date.
4. Retest single import/duplicates, scheduled exclusion from earnings/hours/net, missing versus explicit-zero miles, both expense methods, owner scoping, and recovery when touched. Preserve old backup compatibility and the 30-day recovery window.
5. Use existing services for business rules; controllers/templates must not become a second calculation layer. Do not add frameworks/dependencies without asking. Do not edit production data, Render settings, or credentials as part of implementation validation.
6. Obtain a fresh Claude Code (Opus) review with `review-task`, then the owner's explicit approval for the push/merge. A push to main is a production release. Keep each change independently reviewable and revertible; avoid a single PR for this entire plan.

The audit did not verify offline/reconnect, notifications, Spanish, backup/restore, export contents, or a second screen size. Those are planned validation requirements, not passed checks. Planning itself changes no executable code and does not establish any implementation test result.

## Completion checklist for this plan

- [ ] Each of DX-01 through DX-12 has a promoted task or a documented disposition after baseline verification.
- [ ] All accepted fixes/features pass their task-specific checks and fresh review.
- [ ] Regression checks use synthetic data; no private audit screenshots or driver figures are committed.
- [ ] Final phone walkthrough repeats the driver loop: accepted block → explicit completion decision → actual time/miles → expenses → earnings/payout review → recover a mistaken deletion.
- [ ] Owner-approved releases and any Android upload are recorded; no unfinished or untested work is labelled complete.
