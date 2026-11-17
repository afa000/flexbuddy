# Shared screenshot import rules

`com.angel:flexbuddy-import-core:0.2.0` is a plain Java 21 JAR with no runtime dependencies. It contains the
screenshot parser, immutable OCR/result types, confidence and warning rules, and block matching rules, and from
0.2.0 the earnings figures (below). It performs no OCR, translation, persistence, file access or network calls.
Releases are listed in [docs/import-core-releases.md](../../docs/import-core-releases.md).

The native Android app runs it from API 26 (Android 8). Don't use JDK methods Android lacks at that level unless D8
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
  history, then the account; `evaluate` estimates the offer and compares it with a usual block.

The consumer selects the data, always the signed-in account's live blocks and expenses only, and leaves out
expenses that wait for a scheduled block. Pass the account's own date as `today`; the library never reads the clock,
locale or time zone. Amounts stay `BigDecimal` with the website's scales and rounding. Tax figures stay on the
server, because they read payments and settings the phone may not have.

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

The [manual publishing setup](../../docs/import-core-releases.md) keeps first publication owner-operated; no package
has been published by this change. Android integration remains separate. Before choosing a phone OCR dependency,
benchmark the same labelled screenshots and frozen parser version against the server for all six fields, including tips. The
existing server sample test measures five fields and cannot alone establish that gate.

Android compatibility must be checked with the consuming app's Java 21/D8 toolchain and minimum SDK 26. The existing
parser uses Java collection factories/copying, records, `String.isBlank`, `Stream.toList` and `Matcher.results`.
The app integration must verify desugaring/API support for these calls on API 26 before release; this server task
does not claim an Android runtime check. No additional platform APIs are introduced by the extraction.
