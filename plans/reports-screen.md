# Layout 1 of 4: a Reports screen

Planned against `origin/main` at `eef0595`. **No migration and no backend change.** This is
the first of four layout plans, all agreed from the clickable mockup on 2026-09-30:

1. **Reports (this plan).** Move analysis and history off the dashboard.
2. **Home.** Rebuild what's left of the dashboard around "this week".
3. **Account sections.** Fold the account page into sections.
4. **Tidy-up pass.** Title banners, headings and the header, across every screen.

Reports goes first because it leaves a shorter dashboard that still works on its own. Home
then rebuilds that dashboard.

## 1. Goal and scope

Today the dashboard is about 7½ phone screens long (6,238 px at 390 px wide, with 17
blocks). It holds a quick look at this week, a calculator, a full analytics report and the
complete history. This plan moves **the analysis and the history** to a new **Reports**
screen, with a new bottom bar:

**Dashboard · Schedule · Reports · Expenses**

**Import leaves the bottom bar.** It stays reachable through:

- the **+** button's "Import screenshot";
- sharing a screenshot from another app;
- the Android app's long-press "Import" shortcut (`?screen=import`, in `twa-manifest.json`);
- `/?screen=import` links.

**On Reports:**

- **One range button** replaces the filter panel. It reads, for example, **"This month ▾"**,
  followed by a one-line summary: "15 blocks · VEA7, DLV2, DAX5". The button opens a **range
  sheet** holding the existing presets, custom dates and station.
- **Two tabs, Charts and History.**
  - **Charts:** the stat tiles that follow the range, grouped under **Earnings, Time and
    Costs**, then the existing charts, "Best times to work" and the breakdown table,
    unchanged.
  - **History:** the existing shift history list, with sort, "Show 20 more", Export CSV,
    Refresh and Recently deleted, unchanged in behaviour.
- The range, tab and group-by are kept in the URL, so a reload returns to the same view.

**On the dashboard** after this plan: the title banner, install banner, backup reminder,
missing-miles panel, block calculator, and **only the six tiles that don't follow the
range**:

- this week's goal,
- next payout,
- set aside this week,
- rolling 7-day hours,
- planned for the next 7 days,
- forfeits this month.

Home (plan 2) redesigns these.

### Out of scope

- Redesigning the dashboard's remaining content (plan 2).
- Renaming "Dashboard" to "Home" (plan 2).
- Changing any chart.
- New figures.
- Schedule, Expenses and Account (plans 3 and 4).

## 2. Data model and migration

None.

## 3. Backend

None. `GET /shifts/statistics` already returns both the range figures and the fixed-window
ones in one response, and both screens keep calling it as today.

## 4. Frontend

### `templates/shifts.html`

**Bottom bar** (`nav.mobile-nav`):

- **Remove `#importNavButton`.**
- Add `#reportsNavButton` between Schedule and Expenses, with the label "Reports" and a
  bar-chart icon: `<path d="M5 20V10m7 10V4m7 16v-7"/>`.
- Keep four items, so the existing `repeat(4, …)` grid rules still apply.

**Screen markers.** Today every dashboard section carries `data-screen="dashboard"`, and
`showScreen` toggles them. Move these sections into **a new
`<section class="reports-screen is-hidden" id="reportsScreen" aria-labelledby="reports-title">`**,
placed after the dashboard tiles and before `#scheduleScreen`:

- `.filter-panel`, rebuilt below;
- `.reports-panel`, with the charts, heatmap and breakdown table, moved as a whole;
- `.history-panel`, moved as a whole.

The ids inside them stay the same, so `app.js`'s `elements` lookups keep working.

**Inside `#reportsScreen`, in order:**

1. **Heading:** `<h1 id="reports-title">Reports</h1>`, one line, with no eyebrow or
   description. That's the style plan 4 applies everywhere.
2. **Range row:**

   ```html
   <div class="range-row">
     <button class="range-chip" id="rangeChip" type="button" aria-haspopup="dialog" aria-controls="rangeSheet">This month ▾</button>
     <p class="range-summary" id="activeFilterSummary">Showing: All shifts</p>
   </div>
   ```

   - `#activeFilterSummary` **moves here** from the dashboard title banner, keeping its id.
   - `.results-summary` (`#resultsSummary`) also moves here, as the summary's second line.
3. **Tabs:** `<div class="report-tabs" role="tablist">` with two buttons,
   `id="chartsTab"` and `id="historyTab"`. Each has `role="tab"`, `aria-selected` and
   `aria-controls`. The panels are `<div role="tabpanel" id="chartsPanel">` and
   `<div role="tabpanel" id="historyPanel" hidden>`.
4. **`#chartsPanel`:**
   - **Three tile groups**, each an `<h2 class="tile-group-title">` over a
     `<div class="stats-grid report-stats">`. The existing `<article class="stat-card">`
     elements move here, **with their ids unchanged**:

     | Group | Tiles, with their existing ids | Second line |
     |---|---|---|
     | **Earnings** | "Earned" (`totalEarnings`) | "Base pay and tips" |
     | | "Est. net" (`netEarnings`) | `netMargin` |
     | | "Avg hourly" (`averageHourly`) | **Now the estimated net rate:** "$13.56/hr est. net", from `statistics.netHourlyRate` |
     | | "Base vs tips" (`baseTipsTotal`, with its split bar) | `tipsShare` |
     | **Time** | "Hours" (`totalTime`) | `timeWorkedDetail`, which keeps the on-the-clock detail |
     | | "Blocks" (`totalShifts`) | The range label |
     | | "Avg block" (`averagePay`) | "Pay per block" |
     | **Costs** | "Miles" (`totalMiles`) | `mileageCost` |
     | | **"Expenses"**, a new tile: `<strong id="expenseTotal">` shows `formatMoney(statistics.totalExpenses)` | "Cash spent in this range" |

     **Removed as tiles:**

     - The separate "Estimated net hourly" tile, whose rate now sits in Avg hourly.
     - `#hourlyBreakdown` (the base and tips hourly split), which is covered by Base vs
       tips.
     - `#netHourly` stays as the id of Avg hourly's second line, so `loadStatistics` changes
       as little as possible.

     Update the error branch of `loadStatistics` for the new element set.

   - Then `.reports-panel`, with the group-by control, charts, heatmap and table,
     **unchanged**.
5. **`#historyPanel`:** `.history-panel` unchanged, except that the **Sort** select and the
   direction button move into its heading, beside Export CSV. Sort only affects the list.

**The range sheet.** A new `.modal-backdrop#rangeSheet`, with an `.edit-dialog.range-dialog`
(`role="dialog"`, `aria-labelledby="rangeSheetTitle"`), which becomes a bottom sheet on
phones through the existing ≤620px rules. It contains the **existing controls, moved and
not re-created**:

- `.preset-row`, with the 8 preset chips;
- `#filterFrom` and `#filterTo`;
- `#filterStation`;
- `#clearFiltersButton`;
- `#filterError`.

It also gets a **Done** button (`#rangeDoneButton`, `primary-button`). Presets apply
straight away, as today, and don't close the sheet. Done, Escape and a tap on the backdrop
close it.

**Removed from the dashboard:**

- `.filter-panel`, whose content now lives in the sheet and the history heading;
- `#activeFilterSummary` in the title banner, which moved;
- the 9 range tiles, which move to Reports. They stay 9 there: "Estimated net hourly" is
  merged into Avg hourly, and a new Expenses tile is added.

**Kept on the dashboard:** `#goalCard`, `#payoutCard`, `#taxCard`, the rolling 7-day tile,
the planned-next-7-days tile and the forfeits tile, still in `.stats-grid` and with their
ids.

### New file `static/js/reports.js` (pure helpers, testable with `node --test`)

It exposes `window.flexbuddyReports = {rangeLabel, reportParams, parseReportParams}`. Add it
to the page's script tags **before `app.js`**, and to `VERSIONED_ASSETS`. It touches no page
elements at load.

- **`rangeLabel(filterState)`** returns the button text:
  - `all` gives "All time", `week` "This week", `month` "This month", `year` "This year",
    `30days` "Last 30 days", `payperiod` "This pay period", `lastpayperiod` "Last pay
    period";
  - `custom` with both dates gives "Sep 1 – Sep 15", with only a from date "From Sep 1",
    and with only a to date "Until Sep 15".
  - Build the dates with the local-date helpers. Never use `toISOString()`;
    `dates-guard.test.js` enforces this.
  - It always ends with " ▾".
- **`reportParams(filterState, groupBy, tab)`** returns the `URLSearchParams` for the
  Reports URL, **always including `screen=reports`**, plus `tab` when it's `history`.
- **`parseReportParams(search)`** is the inverse: it returns `{tab}`. The filter parsing
  stays in `readFilterState`.

### `static/js/app.js`

- **`elements`:** remove `importNavButton`. Add `reportsNavButton`, `reportsScreen`,
  `rangeChip`, `rangeSheet`, `rangeDoneButton`, `chartsTab`, `historyTab`, `chartsPanel` and
  `historyPanel`.
- **Navigation:**
  - Remove the `importNavButton` click listener (line ~229).
  - Add `reportsNavButton`, which calls `showReportsScreen()`.
  - `setActiveNavigation` lists `dashboard`, `schedule`, `reports` and `expenses`. On the
    import screen **no** item is active, and nothing carries `aria-current`.
- **`showScreen(name)`** also toggles `elements.reportsScreen` for `'reports'`.
- **Add `showReportsScreen(smooth = true, tab)`**:
  - `showScreen('reports')`;
  - select the tab from the argument, or from the URL through `parseReportParams`, or else
    `charts`;
  - `persistFilterState()`.
- **`persistFilterState()`:** write `flexbuddyReports.reportParams(...)` **only while
  Reports is visible**. Otherwise, write the current screen's plain URL (`/`, or
  `/?screen=<name>`) without filter params.
  - This fixes today's side effect, where saving a shift from the Schedule screen rewrote
    the URL to the dashboard's filter URL.
  - `readFilterState()` still reads `preset`, `from`, `to`, `station`, `sort` and `dir` from
    the URL, so a reload on Reports restores the range.
- **`openInitialScreen()`:** add `screen === 'reports'`, which calls
  `showReportsScreen(false)`.
  - Links that carry filter params **without** `screen` (old bookmarks such as
    `/?preset=month`) open **Reports**, since only Reports uses them. Test for `preset`,
    `from`, `to`, `station` or `groupBy` in `openInitialScreen`.
- **The range button and sheet:**
  - `rangeChip` opens the sheet: remove `is-hidden`, add `body.modal-open`, focus the
    active preset chip.
  - The sheet traps focus with the existing `trapFocus`.
  - Closing it returns focus to `rangeChip`.
  - After every filter change, which already runs `applyFilters()` and then
    `loadDashboard()`, update `rangeChip.textContent = flexbuddyReports.rangeLabel(filterState)`
    in `syncFilterControls()`.
- **Tabs:**
  - A click sets `aria-selected` and `hidden` on the panels, then `persistFilterState()`.
  - Arrow keys move between the two tabs (the WAI-ARIA tabs pattern).
  - "Show 20 more" and Recently deleted keep working in the History tab.
- **Labels and lines** in `loadStatistics` change to match the tile table:
  - "Blocks" instead of "Shifts logged", with its detail set to the range label (for
    example "This month") instead of "All-time total", which was wrong whenever a range was
    set;
  - `netHourly` now fills Avg hourly's second line;
  - `expenseTotal` becomes the Expenses tile's main figure.
- **`loadDashboard()` keeps loading everything,** including reports and history, as today.
  That keeps every "reload after save" call working unchanged. Making reports load only
  when opened is left for later; note it in a code comment.

### `static/js/quick-actions.js`

No change: "Import screenshot" already calls `showImportScreen(false)`.

### `static/css/styles.css`

- **`.reports-screen`:**
  - Same width and padding rules as `.schedule-screen`.
  - At ≤620px, `padding-bottom: calc(180px + env(safe-area-inset-bottom))`, the same as
    `main`, so the + button and the bottom bar never cover the last row.
- **`.range-row`:** `display: flex; flex-wrap: wrap; align-items: center; gap: 8px 12px;`
- **`.range-chip`:**
  - `min-height: 44px; padding: 0 16px; border-radius: 999px; border: 1px solid var(--cyan); background: var(--surface); color: var(--text); font-weight: 800;`
- **`.report-tabs`:**
  - `display: grid; grid-template-columns: 1fr 1fr; padding: 4px; border-radius: 999px; background: var(--surface-deep);`
  - The buttons are `min-height: 44px`.
  - The selected tab uses `background: var(--cyan); color: var(--primary-text);`.
- **`.tile-group-title`:** `margin: 18px 2px 8px; font-size: 13px; letter-spacing: .05em; text-transform: uppercase; color: var(--muted);`
- **`.report-stats`:** 4 columns at ≥900px and 2 columns below. At ≤620px the tiles use
  `min-height: 0; padding: 14px;`, so each group is compact.
- The `.filter-panel` rules become unused. **Delete them**, and check first that nothing
  else uses the class.

### `static/sw.js`

- `VERSIONED_ASSETS` gets `/js/reports.js`.
- No `DATA_PATHS` change.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format (v4), `BlockEvaluationResponse`, `ShiftResponse`, `ShiftStatisticsResponse` | **Not touched.** |
| New account data | None. |
| `sw.js` | `VERSIONED_ASSETS` gets `/js/reports.js`. `DATA_PATHS` unchanged. |
| Boxed request fields | Not applicable. |
| Phone layout | New controls are all at least 44px. The range sheet uses the existing bottom-sheet rules. Tables stay in their own scroll boxes. `.reports-screen` bottom padding clears the + button and the bottom bar. |
| Dates | `rangeLabel` formats custom dates from the local `YYYY-MM-DD` strings. No `toISOString`. |
| Money and tax wording | Tile labels keep "Est." on net figures. |

**Existing code that changes:**

| File | Change |
|---|---|
| `shifts.html` | Bottom bar, `#reportsScreen`, range sheet; sections moved; the dashboard loses the filter panel and 8 tiles |
| `app.js` | Navigation, `showScreen`, `showReportsScreen`, `persistFilterState`, `openInitialScreen`, range sheet, tabs, two tile labels |
| New `reports.js` | Pure helpers |
| `styles.css` | New rules; `.filter-panel` rules removed |
| `sw.js` | `VERSIONED_ASSETS` |

**Removed-elements checklist.** `app.js` looks its elements up once, at load, and several
listeners are attached without a null check. A removed element that is still referenced
throws at load and **stops the whole script**, which leaves the main page dead. For every
element this plan removes, delete its `elements` entry and every use, including the error
branch of `loadStatistics`:

| Removed | References to delete at `eef0595` |
|---|---|
| `#importNavButton` | `elements.importNavButton` (line 6), its click listener (line ~229), and its entry in `setActiveNavigation` |
| `#hourlyBreakdown` | `elements.hourlyBreakdown`, its assignment in `loadStatistics` (line ~865), and the error-branch list (line ~891) |
| The old "Estimated net hourly" tile | Nothing: `#netHourly` stays as Avg hourly's second line |

**Things that must keep working** (check these by hand, section 7):

- **Swipe to edit, delete or duplicate** on history rows (`swipe.js`, bound to
  `#historyList`, which keeps its id).
- **`?finish=<id>` links from push notifications**, which open the dashboard and then the
  finish sheet.
- The **offline outbox**'s "Pending" tag on history rows.
- **Import from a share.**
- **The quick-action button**, whose "Add expense" and "Start block" still work.

## 6. Tests to add or change

**`controller/PageControllerTest.java`:**

1. `bottomBarHasReportsAndNoImport`: the body contains `id="reportsNavButton"` and **not**
   `id="importNavButton"`. `id="qaImport"` is still there.
2. `reportsScreenHoldsTheRangeTabsAndHistory`:
   - the body contains `id="reportsScreen"`, `id="rangeChip"`, `id="rangeSheet"`,
     `role="tablist"`, `id="historyList"` and `id="earningsChart"`;
   - in the source order, `id="historyList"` comes after `id="reportsScreen"`, which proves
     the history moved into Reports.
3. `dashboardKeepsOnlyTheFixedWindowTiles`: `id="goalCard"`, `id="payoutCard"`,
   `id="taxCard"` and `id="rollingSevenDayTime"` appear **before** `id="reportsScreen"`,
   and `id="totalEarnings"` appears **after** it.
4. `/js/reports.js?v=` is present. `StaticAssetsTest` also checks it's precached.

**New `src/test/js/reports.test.js`** (loaded with `load-script.js`, with
`process.env.TZ = 'America/Los_Angeles'` on the first line):

5. `rangeLabel`: each preset gives its label with " ▾".
   - `{preset: 'custom', from: '2026-09-01', to: '2026-09-15'}` gives "Sep 1 – Sep 15 ▾".
   - from only gives "From Sep 1 ▾". to only gives "Until Sep 15 ▾".
6. `rangeLabel` near midnight: a custom range starting `2026-10-01` gives "Oct 1", never
   "Sep 30". That guards against UTC parsing.
7. `reportParams` always has `screen=reports`, has `tab=history` only for History, and
   keeps `preset`, `from`, `to`, `station`, `sort`, `dir` and `groupBy`.
8. `parseReportParams('?screen=reports&tab=history')` gives `{tab: 'history'}`. Anything
   else gives `{tab: 'charts'}`.

Existing Java and JavaScript tests must pass unchanged.

## 7. Manual checks

Use the local run from the design review: the app on a local Postgres, a test account with
about 17 worked blocks, 4 scheduled, 6 expenses, a $400 weekly goal and 25% tax. Check at
**390×844** and **1280×800**.

1. **Dashboard height.** On a phone the dashboard is **at most about 2,400 px** (it was
   6,238). It shows the banner, the reminders, the calculator and **six** tiles, with no
   filters, charts or history.
2. **Bottom bar.**
   - It reads Dashboard · Schedule · Reports · Expenses.
   - Each item is at least 44px, and the four fit at 375px.
   - On the import screen, reached from + and then "Import screenshot", **no** item is
     highlighted.
3. **Reports on a phone.**
   - The button reads "All time ▾", with the summary "17 blocks · …".
   - Tap it: a bottom sheet with the 8 presets, dates, station and Done.
   - Choose "This month": the button reads "This month ▾", the tiles and charts update, and
     the sheet stays open.
   - Done closes it, and focus returns to the button.
4. **Tabs.** History shows the full list, with Sort and the direction button in its heading.
   Switching tabs doesn't reload anything.
5. **Reload keeps the view.** On Reports → History with "This month" and sort "Total pay",
   reload the page. The same screen, tab, range and sort come back.
   - The URL has `screen=reports`, `tab=history` and `preset=month`.
6. **Other screens keep clean URLs.** Save a scheduled shift from the Schedule screen. The
   URL stays `/?screen=schedule` (before this change it became `/?from=…&preset=…`).
7. **Old bookmark.** `/?preset=month` opens **Reports** with "This month".
8. **Import stays reachable** through each route:
   - + → Import screenshot;
   - Android long-press on the app icon → Import;
   - sharing a screenshot from Google Photos;
   - `/?screen=import`.
9. **History behaviours:**
   - swipe a row to edit, delete (with Undo) and duplicate;
   - "Show 20 more";
   - Recently deleted, then restore;
   - Export CSV follows the range.
10. **Push link.** `/?finish=<id>` still opens the dashboard with the finish sheet.
11. **Desktop 1280.**
    - The Reports tiles form rows of 4.
    - The charts are as before.
    - The range sheet is a centred dialog.
    - `scrollWidth === innerWidth`.
12. **Keyboard.**
    - Tab to the range button, press Enter, then Tab through the sheet: focus stays inside.
    - Escape closes it.
    - The arrow keys switch the tabs.
13. **No script errors.** With DevTools' console open, load `/`, then open every screen
    and the range sheet. The console shows **no errors**.
14. **Offline.** Reports shows cached data, with the range sheet still usable. It reads
    cached responses, as the dashboard did.

## 8. Suggested commit message

```
feat(reports): move charts, filters and history to a Reports screen

The dashboard was doing four jobs at once and ran to seven phone
screens. Its charts, range filters, earnings tiles and full shift
history now live on a new Reports screen in the bottom bar, and the
dashboard keeps only the figures that do not depend on a range, so it
is about a third of the length.

On Reports a single button shows the chosen range, such as This month,
and opens a sheet with the same presets, dates and station as before.
The earnings figures are grouped into earnings, time and costs, and a
switch moves between the charts and the history, which keeps its
sorting, swipe actions, export and recently deleted list. The screen,
range and tab stay in the address, so a reload or an old bookmark
lands in the same place, and other screens no longer pick up the
filter settings in their address.

Import leaves the bottom bar and stays one tap away in the + button,
the share sheet and the app's shortcut.
```

## 9. Decisions (from the mockup review)

- **Home and Reports are separate screens.** The dashboard also gets tidied, which is plans 2
  and 4.
- **Bottom bar: Home (Dashboard until plan 2) · Schedule · Reports · Expenses.** Import moves
  to the + button.
- **Order: Reports, then Home, then Account sections, then the tidy-up pass.**

No open questions.
