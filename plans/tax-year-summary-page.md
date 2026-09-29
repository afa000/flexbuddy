# Plan 14c: Printable tax year summary

Planned against `origin/main` at `7b431cc` (latest migration V18). **No migration.**

This is the second of the two parts plan 14 left for later. The due-date reminders are
planned separately, in `plans/tax-due-date-reminders.md`. The two plans don't depend on each
other and can ship in either order.

## 1. Goal and scope

Give the driver a page for a chosen tax year that they can print, or save as PDF, for a
tax preparer. It carries the same numbers as the existing year CSV, **to the cent**, plus
the four estimated-payment periods, the payments recorded, and **every expense line for
the year**. Every tax figure is labelled as an estimate, and the page carries the Taxes
card's disclaimer. It prints in **landscape** and shows the driver's display name.

- The account page's Taxes card gets **"View printable summary"** next to "Download year
  summary (CSV)", for the selected year.
- The page has a **Print** button, which calls `window.print()`. This matters because the
  Android app (TWA) has no browser menu to print from.
- A `@media print` block hides the header, buttons and theme colours, and prints black on
  white in landscape.

### Out of scope

- Generating a PDF on the server.
- Emailing or sharing the summary.
- Any new figures: the monthly rows are exactly the CSV's columns, and the expense list is
  the same expenses the monthly totals already count.
- Offline use: the page is network-only, like the CSV.
- A print style for any other page.

## 2. Data model and migration

None.

## 3. Backend

### One source for the CSV and the page

Today `TaxService.writeRow` computes each row and writes it straight to CSV. Pull the
computation out so both outputs use it.

**New record `dto/TaxYearRow.java`** (it goes in no shared record, backup or response):

```java
public record TaxYearRow(String label, int shifts, BigDecimal basePay, BigDecimal tips, BigDecimal gross,
        BigDecimal miles, BigDecimal mileageRate, BigDecimal mileageCost, BigDecimal fuel, BigDecimal tolls,
        BigDecimal parking, BigDecimal maintenance, BigDecimal other, VehicleCostMethod vehicleCostMethod,
        BigDecimal vehicleCost, BigDecimal totalDeductions, BigDecimal net) {}
```

**New record `dto/TaxYearExpense.java`:**

```java
public record TaxYearExpense(LocalDate date, ExpenseCategory category, BigDecimal amount,
        String station, String note) {}
```

`station` is the linked block's station, or null.

**New record `dto/TaxYearReport.java`:**

```java
public record TaxYearReport(int year, LocalDate generatedOn, String displayName,
        VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
        List<TaxYearRow> months, TaxYearRow total, TaxSummaryResponse summary,
        List<TaxYearExpense> expenses) {}
```

`summary` is the existing `TaxSummaryResponse` for the same year. It carries the percentage,
the quarters (period, due date, net, estimated set-aside, paid) and the payments.

**`service/TaxService.java`:**

- Add `@Transactional(readOnly = true) public List<TaxYearRow> yearRows(String email, int year)`.
  It calls `checkYear` and returns 13 rows: `YYYY-MM` for months 1 to 12, then the year
  total labelled `YYYY`. Each row is computed exactly as `writeRow` does now, from the same
  `shiftService.findFiltered(... ShiftFilter.report(from, to, null, null))`,
  `expenses(email, from, to)` and `calculator.calculate(...)`, with the same
  `getEarnedBasePay` and `getEarnedTips` sums.
- Rewrite `writeYearCsv` to write `CSV_HEADERS`, then one line per `yearRows` row using the
  existing `money(...)` and `toPlainString()` formatting. **The CSV bytes don't change**,
  and a test pins that.
- Remove `writeRow`.
- Add `@Transactional(readOnly = true) public TaxYearReport yearReport(String email, int year)`.
  It builds the rows from `yearRows`, the display name from the user,
  `generatedOn = userTime.today(email)` (the account's time zone), the method and rate from
  `settingsService.get(email)`, `summary(email, year)`, and the expenses:
  - Use the existing private `expenses(email, Jan 1, Dec 31)`, the same set the monthly
    totals count. Deleted expenses are already excluded by `@SQLRestriction`, and expenses
    linked to a block that hasn't happened yet are left out as in the reports.
  - Sort by date, then id.
  - Map each to a `TaxYearExpense`, with `station` from `expense.getShift()` when linked.
  - So for each category, the listed amounts add up to the year total row's column for that
    category, to the cent. A test pins this.

  Two notes:
  - `TaxService` already has `settingsService` and `userTime`. The display name needs the
    user: use `AppUserRepository`, which **adds a constructor parameter**, or pass
    `principal`'s display name in from the controller. **Prefer passing it from the
    controller**, so the constructor and `TaxServiceTest:60` don't change. The signature
    then becomes `yearReport(String email, String displayName, int year)`.
  - The year total is computed over the whole year, as the CSV does today, not summed from
    the months. That keeps rounding identical to the current CSV.

### New page controller: `controller/TaxPageController.java`

A `@Controller`, because `TaxController` is a `@RestController` and returns JSON.

```java
@GetMapping("/tax/year-summary")
public String yearSummary(Principal principal, @RequestParam(required = false) Integer year, Model model)
```

- Year defaults to `userTime.today(email).getYear()`, as `TaxController.year` does.
- Look up the display name with `userRepository.findByEmailIgnoreCase(email)`, as
  `PageController` does. Fall back to the email if there is none.
- Put `report` in the model and return `"tax-summary"`.
- A year outside 2000–2100 throws `InvalidFilterException`. `GlobalExceptionHandler` already
  maps that to 400 with the message.
- Security needs no change. The path is not public, so an anonymous request redirects to
  `/login`.

## 4. Frontend

### New template `templates/tax-summary.html`

It uses the same head as `account.html`: the theme script, the `theme-color` metas, and the
`styles.css?v=${buildId}` link.

**Structure.** `<body class="print-page">` wraps a `<main class="print-layout">`, which
contains, in order:

1. **Header** (`.print-header`):
   - "FlexBuddy" and a **Back to account** link (`/account#taxes`).
   - A **Print** button, `id="printButton"`, `primary-button`, 44px or taller.
   - Both are hidden in print.
2. **Title block:**
   - "Tax year summary · 2026", then the driver's display name.
   - "Generated Sep 29, 2026" (`generatedOn`, formatted `MMM d, yyyy`).
   - "Vehicle costs: Standard mileage at $0.700/mile" or "Actual expenses".
3. **Estimate notice** (a `.notice`, printed with a border). The same wording as the Taxes
   card: estimates from the driver's own numbers and chosen percentage, not tax advice.
   Also: "Figures match the CSV download for the same year."
4. **Monthly table** (`<table class="breakdown-table print-table">`, with a caption):
   - One row per month and the year total row in `<tfoot>`, with the same 17 columns and
     order as the CSV.
   - Headers in words, for example "Mileage cost" or "Est. net".
   - Money right-aligned, formatted `$1,234.56` with `#numbers.formatDecimal(value, 1, 'COMMA', 2, 'POINT')`.
     The CSV keeps its plain format.
   - Wrapped in `<div class="breakdown-scroll">`, the existing positioned scroll box, so on
     a phone it scrolls inside the box instead of widening the page.
5. **Estimated payments table.** Columns: Period (`Jan 1–Mar 31`), Due, Est. net, Est.
   set-aside, Paid. When `summary.percent` is null, the set-aside column reads "—" and a
   line says "No set-aside percentage chosen." Also wrapped in `.breakdown-scroll`.
6. **Payments recorded:** a simple list of date, period, amount and note, or "No payments
   recorded."
7. **Expenses** (`<table class="breakdown-table print-table print-expenses">`, with a
   caption):
   - Columns: Date, Category (Fuel, Toll, Parking, Maintenance, Other), Block (the station,
     or "—"), Note, Amount.
   - One row per expense in date order.
   - A `<tfoot>` with one subtotal row per category that has expenses, then a total. These
     equal the year row's fuel, tolls, parking, maintenance and other columns.
   - Empty state: "No expenses recorded for 2026."
   - Wrapped in `.breakdown-scroll`. Notes wrap (`white-space: normal; min-width: 12ch`),
     unlike the numeric tables.
8. **Footer line:** "FlexBuddy · figures are estimates from your own records."

**Print button.** An inline script at the end of the body, the same style as the theme
script:

```html
<script>document.querySelector('#printButton').addEventListener('click', () => window.print());</script>
```

It adds no new JS file, so `VERSIONED_ASSETS` doesn't change. `StaticAssetsTest` only checks
`th:src` scripts.

### `templates/account.html`, Taxes card

In `.tax-year-row`, after `#taxCsvLink`, add:

```html
<a class="secondary-button compact-button" id="taxPrintLink" href="/tax/year-summary">View printable summary</a>
```

### `static/js/tax.js`

Where the CSV link's `href` is set (line ~43), also set
`el.print.href = /tax/year-summary?year=${encodeURIComponent(year)}` and add `print` to
`el`. `tax.js` is already in `VERSIONED_ASSETS`.

### `static/css/styles.css`

Add a section `/* Printable tax summary */`.

**Screen:**

- `.print-layout { width: min(1160px, calc(100% - 32px)); margin: 0 auto; padding: 24px 0 48px; }`
- `.print-header { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }`
- `.print-table td, .print-table th { white-space: nowrap; }` and right-aligned numbers via
  `.print-table .num { text-align: right; }`. The year total row is bold, with a top border.
- At ≤620px: `.print-layout { width: 100%; padding: 16px 16px 32px; }`. The tables scroll
  inside `.breakdown-scroll`. Buttons stack full width.

**The first `@media print` block in the app:**

```css
@page { size: landscape; margin: 12mm; }
@media print {
    html, body, .print-page { background: #fff !important; color: #000 !important; }
    .print-header, .mobile-nav, .toast, .offline-banner { display: none !important; }
    .print-layout { width: 100%; padding: 0; }
    .breakdown-scroll { overflow: visible !important; }
    .print-table { font-size: 9pt; border-collapse: collapse; width: 100%; }
    .print-table th, .print-table td { border: 1px solid #999; padding: 3px 5px; color: #000; }
    .notice { border: 1px solid #000; background: none; color: #000; }
    tr, .print-section { break-inside: avoid; }
    thead { display: table-header-group; }   /* repeat headers when the expense list runs over pages */
    a { color: #000; text-decoration: none; }
}
```

It uses the page's own classes, so printing other pages is only affected by the generic
hide rules for nav and toast.

### `static/sw.js`

No change. `/tax/year-summary` is a navigation outside `SHELL_PAGES`, so the service worker
leaves it alone, and it isn't a data path. No new JS file.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse` | **Not touched.** |
| Backup format (`BackupShift`, `BackupSettings`, `AccountBackupFile`, v4) | **Not touched.** No new account data. |
| `BlockEvaluationResponse`, `ShiftResponse` | **Not touched.** |
| `TaxSummaryResponse` | **Not touched.** It is only read into the new report. |
| Backup, restore, deletion | Nothing to add. The feature stores nothing. |
| `sw.js` | No change. |
| Boxed request fields | Only `Integer year`, which is optional and defaults to the current year in the account's time zone. |
| Phone layout | Both tables sit in `.breakdown-scroll`. Buttons are at least 44px. No page widening. The account card gets one more button in the existing wrapping `.tax-year-row`. |
| Dates | `generatedOn` and the default year come from the account's time zone. `tax.js` already builds the year list. |
| Money and tax wording | "Est." on set-aside and net columns, the notice at the top, and the footer line. |

**Existing code that changes:**

| File | Change |
|---|---|
| `TaxService` | + `yearRows` and `yearReport`. `writeYearCsv` rewritten over `yearRows`. `writeRow` removed. **Constructor unchanged.** |
| `account.html` | + one link |
| `tax.js` | + one `href` update |
| `styles.css` | + section and the print block |

**Records:** `TaxYearRow`, `TaxYearExpense` and `TaxYearReport` are new and only
constructed in `TaxService` and the new tests.

**Existing tests that must keep passing unchanged** (they pin the CSV bytes):

- `TaxControllerTest.theYearCsvDownloadsForTheCurrentYearByDefault`
- `TaxServiceTest.theYearCsvHasAMonthPerRowAndAYearTotalThatAddsUp`

## 6. Tests to add

Expect about 9 new tests.

**`service/TaxServiceTest.java`** (add)

1. `yearRowsGiveTwelveMonthsAndAYearTotal`: labels run `2026-01` to `2026-12`, then `2026`.
   With the existing fixture, the June row's gross, miles, mileage cost and net match the
   values the CSV test already expects for June.
2. `theCsvIsExactlyTheYearRowsFormatted`: build the CSV into a `ByteArrayOutputStream`. Parse
   it line by line and compare each field with the matching `yearRows` value formatted with
   `setScale(2, HALF_UP).toPlainString()`. The BOM and `\r\n` line endings are still there.
3. `yearReportCarriesTheSummaryAndTheGeneratedDateInTheAccountZone`: with the clock at
   `2026-09-30T02:00:00Z` and the zone `America/Los_Angeles`, `generatedOn` is
   `2026-09-29`. `summary().year()` is 2026, and `total()` equals `yearRows(...).getLast()`.

4. `yearReportListsTheYearsExpensesInDateOrderAndTheyAddUpToTheYearRow`: the fixture has
   fuel on Jun 3 (linked to a VEA7 block), parking on Sep 9, a deleted toll, and a fuel
   expense linked to a still-scheduled block. The list has the two counted expenses in date
   order, with `station` "VEA7" on the first and null on the second. For each category, the
   list's sum equals `total()`'s column.
5. `aYearWithNoExpensesHasAnEmptyList`

**New `controller/TaxPageControllerTest.java`** (`@WebMvcTest(TaxPageController.class)`,
`@Import(SecurityConfig.class)`, mocking `TaxService`, `UserTimeService`,
`AppUserRepository`, `ShiftRepository` and `Clock`, as `PageControllerTest` does)

6. `theSummaryPageRequiresSignIn`: an anonymous GET redirects to `/login`.
7. `rendersTheYearWithTheEstimateNoticeAndPrintButton`: stub a report with one June row
   (gross `1234.5`) and a total. The body contains:
   - "Tax year summary · 2026"
   - `$1,234.50`
   - `id="printButton"`
   - `window.print()`
   - the estimate notice sentence
   - "Figures match the CSV download"
   - the display name
   - one stubbed expense row: "Fuel", "VEA7", `$45.20`.
8. `defaultsToTheCurrentYearInTheAccountTimeZone`: `userTime.today` returns 2026-09-29, and
   the service is asked for 2026.
9. `aYearOutOfRangeIsABadRequest`: the service throws `InvalidFilterException` for
   `?year=1999`, which gives 400.

**`controller/PageControllerTest.java` or `AccountControllerTest`** (optional, if the account
page render is already tested): the account page contains `id="taxPrintLink"`.

## 7. Manual checks

**Setup:** tax percentage 25; a few 2026 blocks in June and September, including tips and
miles; a $45.20 fuel expense linked to a June block; a $12.00 parking expense on its own; a
deleted toll; one recorded payment of $500 for Q3.

1. **Account page at 1280×800, Taxes card.**
   - "View printable summary" sits next to the CSV button, both at least 44px tall.
   - Change the year selector to 2025: the link's href becomes `/tax/year-summary?year=2025`.
2. **Open the page for 2026.**
   - The title reads "Tax year summary · 2026", with your name and "Generated" plus today's
     date.
   - The monthly table has 12 rows and a bold 2026 total.
   - The estimated-payments table lists the four periods: Jan 1–Mar 31 due Apr 15 2026,
     Apr 1–May 31 due Jun 15, Jun 1–Aug 31 due Sep 15, and Sep 1–Dec 31 due Jan 15 2027.
   - Q3 shows $500.00 paid.
   - The Expenses table lists the fuel ($45.20, with the June block's station) and the
     parking ($12.00, Block "—"), but not the deleted toll. The Fuel subtotal is $45.20,
     Parking $12.00 and Total $57.20, equal to the year row's fuel and parking columns.
   - The driver's display name is under the title.
3. **Match to the cent.**
   - Download the 2026 CSV.
   - For June and the year total, every figure on the page equals the CSV field. Only the
     `$` and thousands separators differ.
   - The year total's net equals the dashboard with the "This year" preset.
4. **Print preview** (Chrome desktop, then Print in the Android app on a real device).
   - Landscape, white background, black text.
   - No header, Print button or Back link.
   - The monthly table fits the page width without cutting off columns.
   - Rows aren't split across pages. If the expense list runs onto a second page, its
     header row repeats.
   - Saving as PDF gives a readable file.
5. **Phone at 375×667 and 430×932.**
   - `document.documentElement.scrollWidth === window.innerWidth`.
   - Both tables scroll sideways inside their own boxes.
   - The Print and Back buttons are full width and at least 44px.
6. **Dark theme on screen.** The page follows the theme, and printing is still black on white.
7. **No percentage set.** The set-aside column shows "—" with the note "No set-aside
   percentage chosen." The monthly table is unaffected.
8. **Signed out.** Opening `/tax/year-summary` goes to the login page.
9. **CSV unchanged.** Byte-compare the downloaded 2026 CSV before and after the change on
   the same data. It's identical.

## 8. Suggested commit message

```
feat(taxes): add a printable year summary for a tax preparer

The Taxes card now links to a printable summary for the chosen year,
with the same month-by-month figures and year total as the CSV
download, the four estimated-payment periods with their due dates,
estimated set-aside and payments recorded, the payments themselves, and
every expense for the year with a subtotal for each category.
A Print button opens the print dialog, which the Android app has no
menu for, and the page prints black on white in landscape without the
buttons.

The CSV and the page are now built from one list of rows, so they
cannot drift apart, and the CSV itself is unchanged. Every tax figure is
labelled as an estimate from the driver's own numbers, with the same
notice as the Taxes card, and the tables scroll inside their own boxes
on a phone.
```

## 9. Decisions

All questions are settled. Nothing is left open.

- **Expense lines are included.** Every counted expense for the year is listed, with
  category subtotals that equal the year row.
- **The driver's display name is shown** under the title.
- **Landscape.** `@page { size: landscape }` stays. The table isn't split into portrait
  halves.
