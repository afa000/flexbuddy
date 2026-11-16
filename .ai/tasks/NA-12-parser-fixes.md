# Task: NA-12-parser-fixes - Spanish tips and dates, and the right date on earnings lists

Status: Ready for implementation (owner asked for this spec on 2026-11-15; implementation waits for approval)
Risk: high (earnings figures: tips and the shift date drivers import)
Implementer: Claude Code (Opus)
Review: fresh Claude Code (Opus), Codex (Sol) or Antigravity (Gemini 4 Argon) session, review-task skill

## Objective

NA-12-public-tips measured the screenshot import on recent public Flex screens with tips above $0. It found
three parser gaps:
- Spanish drivers lose their tip.
- Spanish drivers get no date.
- A screenshot of the Earnings list can be saved with the wrong date.

Fix them in the shared library, so the website and the Android app both import these screens correctly.

## Files to read

- `AGENTS.md`; `.ai/tasks/NA-12-public-tips.md` and its implementation note (aggregate findings)
- `flexbuddy/import-core/src/main/java/com/angel/flexbuddy/importcore/ShiftScreenshotParser.java`:
  - `MONEY_LABEL_PATTERN` (lines 69-71) and `parseMoney` (around lines 255-310);
  - `NAMED_DATE_PATTERN` and `SPANISH_DATE_PATTERN` (lines 34-46) and `parseDate` (lines 148-176);
  - `parseTimes` (lines 178-205);
  - `SPANISH_WORDS` and `looksSpanish` (lines 85-130).
- `flexbuddy/src/test/java/com/angel/flexbuddy/service/ShiftScreenshotParserTest.java` (22 tests, including the
  Spanish date forms) and `flexbuddy/import-core/src/test/java/com/angel/flexbuddy/importcore/ImportCoreTest.java`
- `flexbuddy/src/test/java/com/angel/flexbuddy/service/OcrSampleAccuracyTest.java` (opt-in private baseline)
- `docs/import-core-releases.md` (how a version bump and release are done; 0.1.1 is the model)

## Current behavior (from the public check; aggregates only)

1. **Spanish tip label.** The current Spanish app shows the block breakdown as "Base", **"Consejos"** and "Importe
   total". `MONEY_LABEL_PATTERN` knows only `tips|propinas`, so the tip line is ignored and tips default to $0.
   Base pay is read correctly.
2. **Spanish date order.** The Spanish Block details header reads "jue, oct 22, 7:45 p.m. - 9:45 p.m.": an
   abbreviated weekday, then the month, then the day.
   - `NAMED_DATE_PATTERN` needs an English weekday.
   - `SPANISH_DATE_PATTERN` needs the day before the month.

   So no date is found. (`p.m.` times are already supported. That screen's time miss came from OCR dropping the
   colon in "9:45", which is out of scope here.)
3. **Earnings list date.** The Earnings screen lists "Payment sent" rows ("Payment sent $84" / "Wed, Aug 26") above
   the blocks ("Mon, Aug 24" / "5:45 PM - 7:15 PM" / "Base: $28" / "Tips: $0"). `parseDate` returns the first
   date in the text, so the payment date is used. Times, base pay and tips come from the first block, so the saved
   shift mixes two rows.

## Required behavior

1. **Tip label.** Add `consejos` as a tips label next to `tips|propinas`, with the same rules: the first labelled
   amount wins, and a missing tip still defaults to $0. Don't add it to `SPANISH_WORDS` unless a test shows
   numeric-date order stays the same for every existing case (US Spanish uses month-first padded dates).
2. **Spanish weekday, month, day.** Recognize a Spanish weekday abbreviation or name (lun, mar, mié/mie, jue, vie,
   sáb/sab, dom, and the full names, accents folded), then an optional comma, then a Spanish month, then the day.
   Examples: "jue, oct 22", "sáb., nov 8", "martes, oct 21".
   - "mar" as a weekday must not be taken as the month March: "mar, oct 21" is October 21.
   - Existing day-first forms ("13 de octubre", "13 MARTES OCTUBRE") behave exactly as today.
3. **Date from the same block as the times.** When the text has more than one date, prefer the date on the same
   line as the chosen time range, or on the line directly above it. Otherwise keep today's first-date rule. This
   must not change Block details screens, offer cards or the Spanish Programación card, where the first date is
   already the block's.
4. Results stay message keys and the same `ParsedShiftData` shape. No warning rules change.
5. Bump `import-core` to **0.1.2** and the server's pinned dependency with it, as in 0.1.1. Add a 0.1.2 note to
   `docs/import-core-releases.md`. Publishing stays a separate, owner-approved step after merge.

## Tests

- New synthetic `ShiftScreenshotParserTest` cases, using text in the layouts above and made-up values only:
  - a Spanish block detail with "Consejos", checking tips, base and date;
  - Spanish weekday-month-day forms, including "mar, oct 21";
  - an English Earnings list whose "Payment sent" row comes first, checking that date, times and pay all come from
    the first block;
  - and the reverse: a Block details screen with "Payment scheduled for Fri, Dec 26" below the header keeps the
    header date.
- All existing parser, warning, block-matching and import-core tests pass unchanged.
- `AndroidApiCompatibilityTest` passes: no API 34-only calls.
- Private FB-003 baseline (opt-in `OcrSampleAccuracyTest`, aggregates only) is unchanged or better: five-field
  842/853, six-field 897/908, all-six 50/50. Report any field that drops; a drop blocks the task.
- Public check (`.ai/artifacts/NA-12-public-tips`, opt-in, aggregates only): the Spanish capture's tips and date
  and the list capture's date are now correct. Every other field is unchanged or better.

## Out of scope

- OCR, preprocessing, Tesseract settings and colon-less times like "945 pm".
- Pending tips: no clean example yet.
- Parsing every row of an Earnings list.
- Android changes; re-pinning there is a separate FB task after 0.1.2 is published.
- Earnings arithmetic, warning rules, UI and catalogs.

## Acceptance criteria

- [ ] "Consejos" is read as tips; existing tips and propinas behavior is unchanged.
- [ ] Spanish weekday-month-day dates are read, and "mar" as a weekday is not March.
- [ ] Earnings-list screenshots take the date from the block whose times are used; single-block screens are
      unchanged.
- [ ] New synthetic tests fail without the change and pass with it; all existing tests pass unchanged.
- [ ] FB-003 private baseline is unchanged or better (aggregates recorded); public check improves as stated.
- [ ] import-core 0.1.2 is pinned in the server, with a releases-doc note; nothing is published.
- [ ] `verify.ps1 -TaskId NA-12-parser-fixes` results and skips are recorded in the implementation note.

## Validation

`.\scripts\verify.ps1 -TaskId NA-12-parser-fixes`, and the two opt-in accuracy runs above with private paths and
captured output. No browser check is needed: there is no UI change, and the website uses the same parser through
the server.

## Security and regression risks

A wrong tip or date changes a driver's earnings and hours. The date rule could pick a different date on screens
that work today; the FB-003 baseline and the existing tests guard that. Private screenshots, labels and OCR text
never go into Git, the PR, logs or chat.
