# Task: NA-12-api26 - Make the shared import library run on Android 8 to 13

Status: Ready for implementation; owner chose a library fix (0.1.1) over core library desugaring on November 15, 2026
Risk: high (screenshot parsing feeds earnings figures; server results must not change)
Implementer: Claude Code (Opus), started from the FB-038 session on November 15, 2026
Review: fresh top-model session using review-task

## Objective

The native app (`afa000/flexbuddy-android`, FB-038, min SDK 26) pins `com.angel:flexbuddy-import-core:0.1.0`.
Its API scan of the release JAR against Android's api-versions.xml and AGP 9.5.0's D8 backport list found two JDK
methods that only exist on Android 14 (API 34) and that D8 does not backport: `Stream.toList()` and
`Matcher.results()`. On Android 8 to 13 every screenshot import would throw `NoSuchMethodError`. Release 0.1.1
replaces them with API-26-safe code that gives exactly the same results, so drivers on older phones can import.

Plan: docs/native-app-plan.md, NA-12. Follows NA-12-publish (0.1.0 published from tag `import-core-v0.1.0`,
commit 2057ab8).

## Files to read

- AGENTS.md; docs/import-core-releases.md; flexbuddy/import-core/README.md.
- flexbuddy/import-core/src/main/java/com/angel/flexbuddy/importcore/ImportWarningRules.java, `apply` (line 44).
- flexbuddy/import-core/src/main/java/com/angel/flexbuddy/importcore/ShiftScreenshotParser.java, `parse(String, ...)`
  (line 95) and `parseMoney` (line 252).
- ParsedShiftData.java (its compact constructor copies warnings with `List.copyOf`).
- flexbuddy/import-core/pom.xml, flexbuddy/pom.xml (pinned source-module dependency), flexbuddy/reactor/pom.xml.
- scripts/release/test_import_core.py (reads the real library POM version).
- Tests: import-core's ImportCoreTest, BlockMatchRulesTest; the server's ShiftScreenshotParserTest,
  ImportWarningRulesTest, ScheduledShiftMatcherTest, OcrSampleAccuracyTest.

## Current behavior

- `ImportWarningRules.apply` returns `warnings.stream().distinct().toList()`: duplicates removed, first occurrence
  order kept, unmodifiable (and then copied again by `ParsedShiftData`).
- `ShiftScreenshotParser.parse(String, ...)` splits raw text into trimmed, non-blank lines with `.toList()`.
- `parseMoney` collects every money label on a line with `matcher.results().toList()`.

## Required behavior

1. Replace the three calls with API-26-safe code that behaves identically: same elements, same order, same
   duplicates rule, unmodifiable where the old list was, and `MatchResult` snapshots taken through
   `matcher.toMatchResult()` in a `while (matcher.find())` loop.
2. Add a plain JUnit 5 test in import-core (no new dependency) that scans the module's main sources and fails on
   `.toList()` and `.results()` calls, explaining that Android below API 34 has neither and D8 does not backport them.
3. Bump the library to `0.1.1` and the server's pinned source-module dependency with it, as
   docs/import-core-releases.md step 2 requires. Keep the release-script tests in step with the real POM version.
4. Add a short 0.1.1 note to docs/import-core-releases.md and update the version in the import-core README.

## Out of scope

- Any other parser, warning, matching or earnings rule; the server's adapters, API contracts, catalogs, migrations.
- Tagging, pushing the tag, dispatching **Publish import core**, merging: the owner does those.
- The release workflow and scripts themselves (other than the test fixture's version), Render, Docker.
- Core library desugaring and anything in flexbuddy-android.

## Design references

None; no user-facing change.

## Acceptance criteria

- [ ] No `Stream.toList()` or `Matcher.results()` in import-core main sources; replacements are behaviour-identical.
- [ ] Guard test fails on either call and names the Android API 34 reason.
- [ ] Library and server dependency both at 0.1.1; release-script tests pass against the bumped POM.
- [ ] Releases doc has a 0.1.1 note; existing import-core and server tests pass unchanged.
- [ ] `.\scripts\verify.ps1 -TaskId NA-12-api26` results recorded in the implementation note.

## Validation

- `.\scripts\verify.ps1 -TaskId NA-12-api26` (Maven reactor and Node). Report PostgreSQL skip status.
- `python -B -m unittest discover -s scripts/release -p 'test_*.py'`.
- `OcrSampleAccuracyTest` locally if the private samples are present; report aggregate counts only.
- A `javap` scan of the built JAR for `Stream.toList` and `Matcher.results` references.
- Android runtime confirmation (API 26 emulator) belongs to the FB-038 follow-up after 0.1.1 is published.

## Security and regression risks

Parsed base pay and tips become earnings once the driver saves a block, so the parsing must stay identical; the
existing parser and warning tests are the regression net and must pass without edits. No driver data, secrets or
migrations are involved. Published versions and tags are immutable; this task publishes nothing.
