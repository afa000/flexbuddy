# Shared screenshot import rules

`com.angel:flexbuddy-import-core:0.4.0` is a plain Java 21 JAR with no runtime dependencies. It contains the
screenshot parser, immutable OCR/result types, confidence and warning rules, and block matching rules, and from
0.2.0 the earnings figures (below). Published 0.3.0 adds goal and planned-work preparation (FB-044). Published
0.4.0 adds the blocks and expenses CSV writers (FB-053).
The library performs no OCR, translation, persistence or network calls, and opens no files: the CSV writers write
only to the stream they are given.
Releases are listed in [docs/import-core-releases.md](../../docs/import-core-releases.md).

The native Android consumer targets API 26 (Android 8) and later. Don't use JDK methods Android lacks at that level unless D8
backports them; `AndroidApiCompatibilityTest` refuses `Stream.toList()` and `Matcher.results()` (API 34) and
`List.getFirst()` and the other sequenced-collection methods (API 35).

Pass the account's current local date explicitly in `ParseContext`; the library never reads the clock or the
device's locale. `parse(lines, year, context)` parses fields and year-rollover warnings; apply `ImportWarningRules`
with OCR mean confidence and the suggested scheduled status afterwards. The raw-text overload also applies warnings,
with confidence 100 and scheduled false, matching the server's existing convenience overload.

Warnings carry `code`, `severity`, `field` and `messageKey`. Translate at the consumer boundary. The server adapters
keep the website's existing value types and translated `message` fields. Android must map these keys to its own
English and Spanish resources.

`BlockMatchRules` compares plain date/time/station inputs. Consumers must first select only eligible, live blocks
belonging to the signed-in account. The server keeps its existing completed/scheduled filter and previous-day search
window. An equal start/end is a zero-minute interval for matching, as before; warning rules separately flag equal
times as overnight. This extraction preserves both behaviors.

## Earnings figures

`com.angel.flexbuddy.importcore.figures` holds the figures the website shows, so the app computes the same numbers
offline. The server's calculators delegate to it.

- `EarningsRules` decides what a block adds by status: a missing status means completed; base pay counts for
  completed and cancelled blocks, tips and hours only for completed ones, miles for every block except a scheduled
  one. **A scheduled block counts toward nothing until it is confirmed.** It also says when an expense waits for its
  block (the block is still scheduled and not deleted).
- `Block` and `Cost` are the plain inputs: a block's status, station, date, times, base pay, tips and miles; an
  expense's category, amount and date. They carry no ids or owner.
- `NetEarnings` (gross, costs, net and the per-hour, per-block and per-mile figures), `GoalProgress`, `PayPeriods`,
  `IrsMileageRates` (the dated rate table) and `MileageRates` (custom or IRS).
- `BlockOffers` is "Is this offer worth it?": `chooseSample` picks the station's last 90 days, then its whole
  history, then the account; `evaluate` estimates the offer and compares it with a usual block. A blank station is
  refused, so validate it first as the website does.

The consumer selects the data, always the signed-in account's live blocks and expenses only, and leaves out
expenses that wait for a scheduled block. Pass the account's own date as `today`; the library never reads the clock,
locale or time zone. Amounts stay `BigDecimal` with the website's scales and rounding. Tax figures stay on the
server, because they read payments and settings the phone may not have.

## Goal and planned-work preparation (published 0.3.0)

`GoalPreparation.historyStart(today)` exposes the inclusive 90-day window. Pass `fromHistory` the account's
completed/cancelled report `NetEarnings` for that window (including all countable costs) and the completed-only
block sample. It computes gross/net average pay, float-rounded average minutes and the four-decimal historical
net/gross share. No completed sample means null averages. Zero gross uses share one; negative or above-one shares
are preserved. `earned(period)` selects the same goal basis. `plannedPay(periodScheduled)` sums base pay only and
scales it by that share. **Pass its unrounded result into `GoalProgress.of`**: rounding first changes remaining,
pace and blocks-to-go. Scheduled period inputs include overdue rows; the caller selects the goal's dates.

`PlannedWork.of(upcomingCandidates, unconfirmedCandidates, now)` computes separate upcoming gross pay/count/minutes
and overdue confirmation count. The first list may come from today's date through today+6; only ends strictly
after now count. The second must include all scheduled dates through today, including old rows, and ends at/before
now need confirmation. Today may appear in both lists without double counting. Android can pass the same full
confirmed scheduled cache to both. Non-scheduled statuses are ignored; date/start/end/base pay are required for
eligible rows. End uses start plus shared duration, matching the server's overnight and equal-time behavior.
Previous-day overnight work does not become upcoming merely because it ends today. Scheduled tips count in neither
projection and never become confirmed earnings.

Both helpers use plain immutable values, retain no lists and perform no clock, zone, locale, framework or I/O
access. The consumer resolves the account-local date/time, scopes live rows to the account, selects periods and
filters expenses by date and shared waiting-link rules. Goal period planned pay and upcoming planned pay are
intentionally different. Existing 0.2.0 API signatures are unchanged. Server source adoption passes through its
existing user-scoped queries and response DTOs. Release tag `import-core-v0.3.0` points to source commit
`f4eb79d0d07c9902d71eead64f6195ab73ac1ec9`; run 44234045242 and its verified hashes are recorded in the release
guide. Android pinning, the full resolved-JAR audit and API26/current-API runtime checks remain the separate FB-046
gate. The source compatibility scan alone does not prove Android runtime compatibility.

## Blocks and expenses CSV (published 0.4.0)

`com.angel.flexbuddy.importcore.export` writes the website's two CSV downloads, byte for byte, so the app can build
the same files offline. The server's `ShiftCsvWriter` and `ExpenseCsvWriter` are thin adapters over it.

- `ShiftCsv.write(rows, output)` takes `ShiftCsvRow`s: every value a blocks row prints, already computed (id, date,
  station, times, minutes worked, pay, tips, total, rates, miles, mileage cost, linked expenses, net figures,
  created/updated instants, the status name, the recorded details and the late-forfeit flag). UTF-8 **with** a BOM,
  CRLF, a cell quoted only when it needs it, money at two decimals HALF_UP. Only the station is guarded against
  spreadsheet formulas. It flushes and leaves the stream open.
- `ExpenseCsv.write(rows, output)` takes `ExpenseCsvRow`s (date, category name, amount, note, block id, station).
  UTF-8 **without** a BOM, CRLF and every cell quoted. Any cell whose text starts with `=`, `+`, `@` or `-` gets a
  `'` prefix (so a negative amount does too), but a tab or CR is not guarded, and a cell with a line break after its
  first character is not prefixed either, because the check is `matches("^[=+@-].*")` and `.` stops at a line break.
  Keep that exact rule to match the website. It closes the stream.

These differences between the two files are the website's existing behavior and stay; the Javadoc spells out each
rule. The writers only format. **The consumer selects only the signed-in account's rows**, with the same filters as
the website (the blocks export defaults to completed and cancelled blocks), and computes the figures with the shared
calculators (`NetEarnings`, `EarningsRules`) first, so a scheduled block still counts toward nothing. Status and
category are passed as their names; the library reads no clock, locale or time zone. Existing 0.3.0 API signatures
are unchanged.

The golden files in `src/test/resources/com/angel/flexbuddy/importcore/export/` and the rows in the test class
`CsvExportFixtures` are the parity fixtures: the server's `CsvExportGoldenBytesTest` and the library's
`CsvExportTest` expect the same bytes. They are test sources, so they are **not** in the published JAR: the native
app copies the golden files and the fixture rows into its own export test. Git leaves those files' line ends alone
(`-text`).

## Building

From `flexbuddy/`, build and verify both modules without installing the artifact first:

```powershell
.\mvnw.cmd -B -ntp -f reactor/pom.xml verify
```

Before running the application directly, install the local library from source:

```powershell
.\mvnw.cmd -B -ntp -f reactor/pom.xml -pl :flexbuddy-import-core -am install
.\mvnw.cmd spring-boot:run
```

The repository's `scripts/verify.ps1` and CI run the reactor. Docker builds the same library from source inside the
existing `flexbuddy/` context, without package credentials. Its executable server JAR embeds the thin library.

The [manual publishing setup](../../docs/import-core-releases.md) keeps every release owner-approved. Published
0.3.0 and its source/hash evidence are recorded there. Android adoption remains a separate verification step.
Before choosing a phone OCR dependency, benchmark the same labelled screenshots and frozen parser version against
the server for all six fields, including tips. The existing server sample test measures five fields and cannot
alone establish that gate.

Android compatibility must be checked with the consuming app's Java 21/D8 toolchain and minimum SDK 26. The
library uses records, collection factories/copying, java.time and String.isBlank. Known unavailable calls such as
Stream.toList and Matcher.results were removed after 0.1.0 and are refused by the source compatibility test.
FB-046 must audit the entire resolved 0.3.0 JAR and run API26/current-API checks; source tests and publication do not
establish that runtime gate. This release-record task does not perform or claim those Android checks.
