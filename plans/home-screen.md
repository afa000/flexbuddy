# Layout 2 of 4: the Home screen

Planned against `origin/main` at `eef0595`, and **it assumes plan 1, `plans/reports-screen.md`,
has shipped**. **No migration and no backend change.**

This plan builds the Home screen from the agreed mockup
(`flexbuddy-home-reports-mockup.html`, 2026-09-30), with the order and figures confirmed:
this week first, then the next block and payout, then needs attention.

## 1. Goal and scope

After plan 1, the dashboard holds a title banner, three reminder banners, the calculator
and six fixed-window tiles. This plan replaces all of that with **Home**, about **1½ phone
screens**, in this order:

1. **Greeting line:** "Hi, Angel" and today's date. It replaces the "Shift earnings" title
   banner.
2. **This week** (the headline card, with a cyan top border):
   - the label "This week · Mon Sep 28 – Sun Oct 4";
   - a **goal ring** with the percentage, and the big figure **"$93.50 of $400"**, or just
     "$93.50 earned" when there's no goal;
   - a sentence under it from the existing `goalSentence`, for example "Earned so far · your
     4 scheduled blocks add $336";
   - **three small figures:**
     - **Last 7 days**: hours, pay and blocks, for example "12h 0m · $375.00 · 4 blocks";
     - **Est. net / hr** for this week, labelled "estimate";
     - **Set aside** this week, labelled "tax estimate".
3. **Next block** and **Next payout**, side by side on phones, and in the right-hand column
   on desktop.
   - The next-block card uses the Schedule's "Next up" content, including Start block,
     Finish block and the forfeit deadline.
4. **Needs attention.** One card, with only the rows that apply, **hidden when empty**:
   - "N blocks to confirm", which goes to Schedule, where the confirm strip is;
   - "N blocks have no miles", which expands the existing missing-miles list in place;
   - "No backup in 30 days", which goes to Account (`/account#backup`), and only shows when
     the server's `backupDue` is true. `#backup` is added by plan 3 (account sections).
     Until plan 3 ships, the link opens the account page at the top, which is expected, not
     a bug;
   - "Install FlexBuddy", with the existing install and not-now logic, only in a browser,
     never in the Play app.
5. **Evaluate a block:** the existing calculator, unchanged and still folded.
6. **Recent blocks:** the last **3** worked, cancelled or forfeited blocks, in compact
   rows, then **"See all blocks in Reports"**, which goes to Reports → History.

**Also:**

- The bottom bar's first item is renamed **Home**, with a house icon.
- `?screen=home` works as an alias for the dashboard.

**Two of today's six fixed-window tiles don't appear on Home** (see the decisions in
section 9):

- **Forfeits this month**, with its "Log standing" link, moves to the **Schedule** screen,
  right above the Standing card, where forfeits and standing belong together.
- **Planned for the next 7 days** is dropped. Schedule already shows "Week of … · 16h 0m
  planned · $336.00 expected", and the week card's sentence mentions scheduled pay.

### Out of scope

- The header buttons (sign-out and the menu icon), and title banners on other screens:
  plan 4.
- Account: plan 3.
- New figures or endpoints.

## 2. Data model and migration

None.

## 3. Backend

None. Every figure comes from existing endpoints:

| Figure | Source |
|---|---|
| This week earned, goal, ring, sentence | `GET /shifts/goals` (`week.earned`, `week.goal`, `week.percent`, `onTrack`), as today |
| This week earned **when there's no goal** | `GET /shifts/statistics?from=<Mon>&to=<Sun>`, using `totalEarnings` |
| Est. net / hr this week | the same call's `netHourlyRate` |
| Last 7 days | `GET /shifts/statistics?from=<today−6>&to=<today>`, using `totalTimeWorked`, `totalEarnings` and `totalShifts` |
| Blocks to confirm | `needsConfirmation` from either statistics call. It doesn't depend on the range. |
| Set aside | `GET /tax/summary`, using `thisWeekSetAside`, as `loadTaxTile` does now |
| Next payout | `GET /shifts/pay-periods`, as `loadPayPeriods` does now |
| Next block | the schedule's `GET /shifts/upcoming` (`state.upcoming.next`), already loaded by `flexbuddySchedule.refresh()` |
| Missing miles | `finish.js`'s `loadMissing()`, as now |
| Recent blocks | `GET /shifts?sort=date&dir=desc`. Take the first 3 whose status isn't SCHEDULED. |

**Dates:** the week (Monday to Sunday) and the last 7 days are built from the **device's
local date**, with `flexbuddyCalendar.weekStart` and `addDays`. Never use `toISOString`. The
server applies the account's time zone to what it returns.

**Recent blocks and the full list:** the list endpoint returns every block, with no limit
parameter. That's fine at closed-test sizes. Add a limit later, if the history grows large,
as a separate change.

## 4. Frontend

### `templates/shifts.html`, the dashboard area

**Remove** these, which are replaced below:

- `.hero` (`#page-title`, the slogan, `#nextShiftLink`). `#activeFilterSummary` already
  moved in plan 1.
- The standalone `.install-banner`, `.backup-nudge` and `#missingMiles` sections. Their
  content moves into the attention card, and **the ids inside are kept**, so `pwa.js`,
  `finish.js` and `app.js` keep finding them.
- The `.stats-grid` with the six fixed-window tiles.

**Add**, all with `data-screen="dashboard"`:

```html
<section class="home" id="homeScreen" data-screen="dashboard" aria-labelledby="homeGreeting">
  <div class="home-greeting"><h1 id="homeGreeting" th:text="${currentUser != null ? 'Hi, ' + currentUser.displayName : 'Hi'}">Hi</h1>
       <span id="homeToday"></span></div>

  <article class="home-week" id="goalCard" data-state="none">            <!-- keeps the id loadGoals uses -->
    <span class="home-label" id="homeWeekLabel">This week</span>
    <div class="home-week-top">
      <svg class="goal-ring">…existing ring markup, ids goalRingFill kept…</svg>
      <div><strong class="home-week-amount" id="goalProgress">—</strong>
           <p class="home-sub" id="goalSentence"></p></div>
    </div>
    <div class="home-figures">
      <div><span>Last 7 days</span><b id="rollingSevenDayTime">—</b><small id="lastSevenDetail"></small></div>
      <div><span>Est. net / hr</span><b id="weekNetHourly">—</b><small>estimate</small></div>
      <div><span>Set aside</span><b id="taxWeekAmount">—</b><small id="taxWeekDetail">tax estimate</small></div>
    </div>
    <p class="home-month" id="goalMonth"></p> <a class="text-link" id="goalLink" href="/account#goals">Set a goal</a>
  </article>

  <div class="home-pair">
    <article class="home-card home-next" id="homeNextBlock" aria-live="polite"></article>
    <article class="home-card" id="payoutCard">…existing payout content: nextPayoutAmount, nextPayoutDetail, payoutCheckButton…</article>
  </div>

  <article class="home-card home-attention" id="homeAttention" hidden aria-labelledby="homeAttentionTitle">
    <h2 class="home-label" id="homeAttentionTitle">Needs attention</h2>
    <ul class="attention-list" id="attentionList"></ul>
    <div id="missingMiles" hidden>…existing missing-miles heading and #missingMilesList, unchanged…</div>
    <div id="installBanner" hidden>…existing install content and ids…</div>
  </article>

  <details class="panel evaluate-panel" id="evaluatePanel">…unchanged…</details>

  <article class="home-card" aria-labelledby="recentTitle">
    <h2 class="home-label" id="recentTitle">Recent blocks</h2>
    <div class="recent-list" id="recentList"></div>
    <button class="text-link recent-all" id="recentAllButton" type="button">See all blocks in Reports</button>
  </article>
</section>
```

**Ids that keep working unchanged:** `goalCard`, `goalRingFill`, `goalProgress`,
`goalSentence`, `goalMonth`, `goalLink`, `payoutCard`, `nextPayoutAmount`,
`nextPayoutDetail`, `payoutCheckButton`, `taxWeekAmount`, `taxWeekDetail`,
`rollingSevenDayTime`, `missingMiles`, `missingMilesList`, `missingMilesCount`,
`installBanner`, `installBannerButton`, `dismissInstallBanner` and `evaluatePanel`. So
`loadGoals`, `loadPayPeriods`, `loadTaxTile`, `finish.js` and `pwa.js` keep working, with
only their show and hide logic changed (below).

**Backup reminder.** The server's `th:if="${backupDue}"` now sets an attribute instead of
rendering a banner: `<article … id="homeAttention" th:attr="data-backup-due=${backupDue}">`.

**Schedule screen:** add the **forfeits** figure above the Standing card, as a compact
stat line:

```html
<p class="schedule-forfeits"><span id="forfeitsMonth">0</span> forfeits this month · <span id="forfeitsDetail"></span> <span class="standing-pill is-hidden" id="standingPill" data-level=""></span> <button class="text-link" id="standingLink" type="button">Log standing</button></p>
```

It keeps the ids, so `loadStatistics` and `standing.js` keep working. **`#standingPill` must
come too:** `standing.js` calls `setPill(el.pill, …)` without a null check (line ~52), so
leaving the pill behind would throw on every load. It also puts the current standing next
to the forfeit count, which is where you'd want it.

**The planned-next-7-days tile** (`#plannedWeek` and `#plannedWeekDetail`) is removed.
Delete its two lines in `loadStatistics`.

**Bottom bar:** `#dashboardNavButton` gets the label **Home** and a house icon:
`<path d="M4 11 12 4l8 7v8a1 1 0 0 1-1 1h-5v-6h-4v6H5a1 1 0 0 1-1-1v-8Z"/>`. Its id stays.

### New file `static/js/home.js`

It exposes `window.flexbuddyHome = {load, weekRange, lastSevenRange, attentionRows, recentBlocks}`.
Add it to the page's script tags before `app.js`, and to `VERSIONED_ASSETS`. The pure
helpers touch no page elements.

- **`weekRange(todayIso)`** returns `{from, to, label}`: Monday to Sunday containing today,
  and a label like "Mon Sep 28 – Sun Oct 4", from `flexbuddyCalendar`.
- **`lastSevenRange(todayIso)`** returns `{from: today − 6, to: today}`.
- **`attentionRows({needsConfirmation, missingMiles, backupDue, installable})`** returns the
  rows, in that order, each with its text and action:
  - a confirm count of **1** reads "1 block to confirm", otherwise "N blocks to confirm";
  - the same for miles.
- **`recentBlocks(shifts, n = 3)`** keeps statuses COMPLETED, CANCELLED and FORFEITED, keeps
  the order, and returns the first `n`.

**`load()`**, called from `loadDashboard()` in `app.js` in place of the old tile code, runs
these in parallel with `Promise.allSettled`:

1. the statistics call for this week, which fills `weekNetHourly` and holds
   `needsConfirmation`. When `/shifts/goals` has no weekly goal, `goalProgress` shows
   "`$X` earned".
2. the statistics call for the last 7 days, which fills `rollingSevenDayTime` with
   `formatMinutes(totalTimeWorked)` and `lastSevenDetail` with "$375.00 · 4 blocks".
3. `/shifts?sort=date&dir=desc`, which renders `recentList`.

Then `renderAttention()`. One failed call shows "—" in its own figure and doesn't blank the
rest.

**Recent rows** use a compact version of the history row:

- the date badge;
- the station, with duration and miles, for example "3h 30m · 39 mi";
- the total, with the gross hourly rate, for example "$26.71/hr".

A tap opens the existing edit dialog (`openEditModal(shift, row)`), as the history does.
No swipe actions here.

**`recentAllButton`** calls `showReportsScreen(true, 'history')` from plan 1.

**The next-block card.** `schedule.js` gets **`renderNextInto(container)`**, which renders
the same content as `renderNextUp`, including the countdown, the forfeit deadline, Start
block and Finish block, and the offline gating from 06b, into any container:

- `renderNextUp()` becomes `renderNextInto(el.nextUp)`;
- Home calls `window.flexbuddySchedule.renderNextInto(homeNextBlock)` after every
  `refresh()`.

Keep **one countdown timer per container**, so the Schedule card's timer and Home's don't
clear each other. That's `state.countdownTimers`, a `Map` keyed by container.

**Needs attention** (`renderAttention`) combines:

- `needsConfirmation`;
- the missing-miles count, from a new `window.flexbuddyFinish.missingCount()` that returns
  the number `loadMissing` found;
- `data-backup-due`;
- `pwa.js`'s install state.

If the list is empty, the card is `hidden`. **The missing-miles row** toggles the
`#missingMiles` block open inside the card. **The install row** toggles `#installBanner`
inside the card.

### `static/js/app.js`

- **`loadDashboard()`:** call `window.flexbuddyHome?.load()`. The tax, pay-period and goal
  loaders stay.
- **`loadStatistics`** (the Reports range call) stops writing the fixed-window figures:
  `rollingSevenDayTime`, which is now written by Home, and the planned tile, which is
  removed. It keeps writing `forfeitsMonth` and `forfeitsDetail`, whose elements are now on
  the Schedule screen.
- **`openInitialScreen()`:** `screen === 'home'` behaves like no screen.
- **`showDashboard()`:** fill `#homeToday` with today's date,
  `toLocaleDateString(undefined, {weekday: 'short', month: 'short', day: 'numeric'})`.

### `static/js/finish.js` and `static/js/pwa.js`

- **`finish.js`:**
  - `loadMissing()` no longer shows or hides `#missingMiles` itself. It stores the count and
    dispatches `flexbuddy:attention`.
  - Add `missingCount()`.
- **`pwa.js`:** the install banner's visibility is set by the attention card: dispatch
  `flexbuddy:attention` when the install state changes, and let `home.js` decide. The "Not
  now" dismissal and its storage key stay as they are.

### `static/css/styles.css`

Add a section `/* Home */`.

- **`.home`:** `display: grid; gap: 12px;`. At **≥900px**, use
  `grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr)`, with:
  - the week card and recent blocks in the left column;
  - next block, payout, attention and the calculator in the right column.

  Place them with explicit `grid-column` and `grid-row` values, so the **order in the page
  source stays the phone order**.
- **`.home-week`:** a surface card with `border-top: 4px solid var(--cyan)` and 16px
  padding.
  - `.home-week-amount` is 30px, weight 800.
  - `.home-figures` is `display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px;`.
    Each figure sits on `var(--surface-deep)` with a 10px radius. The values are 17px bold,
    and the labels and details are 11–12px muted.
- **`.home-pair`:** `grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr)` on phones. At
  ≥900px it becomes one column inside the right-hand column.
- **`.home-attention`:** `background: var(--warning-surface); border-color: var(--warning-border);`.
  Its rows are `min-height: 48px` with a divider, and the action link uses `--warning`, at
  least 44px tall.
- **`.recent-list`:** rows `grid-template-columns: 44px minmax(0, 1fr) auto; min-height: 58px;`.
- **`.schedule-forfeits`:** 14px muted text, with the Log standing link at least 44px.
- Remove the now-unused `.hero`, `.install-banner` and `.backup-nudge` layout rules, but only
  where no other screen uses them. **Check** that `.hero` isn't used on Account.
- At ≤620px, `.home` has 14px side padding, and its bottom padding clears the + button and
  the bottom bar, as `main` does.

### `static/sw.js`

`VERSIONED_ASSETS` gets `/js/home.js`.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format (v4), `BlockEvaluationResponse`, `ShiftResponse`, `ShiftStatisticsResponse`, `GoalsResponse` | **Not touched.** |
| New account data | None. |
| `sw.js` | `VERSIONED_ASSETS` gets `/js/home.js`. |
| Boxed request fields | Not applicable. |
| Phone layout | Every row and link is at least 44px. The figures use `minmax(0, …)` columns. The bottom padding clears the + button. No page widening. |
| Dates | The week and last-7-days ranges come from the device's local date through `flexbuddyCalendar`. `#homeToday` uses `toLocaleDateString`. No `toISOString`. |
| Money and tax wording | "Est. net / hr" says "estimate", and "Set aside" says "tax estimate". The payout card keeps its "estimate" wording. |

**Existing code that changes:**

| File | Change |
|---|---|
| `shifts.html` | Dashboard area rebuilt, forfeits line on Schedule, planned tile removed, nav label |
| New `home.js` | Home loading, rendering and pure helpers |
| `app.js` | `loadDashboard`, `loadStatistics`, `openInitialScreen`, `showDashboard` |
| `schedule.js` | `renderNextInto`, and per-container countdown timers |
| `finish.js` | `loadMissing` visibility, `missingCount` |
| `pwa.js` | Attention event for the install state |
| `styles.css` | Home section; old banner rules removed |
| `sw.js` | `VERSIONED_ASSETS` |

**Removed-elements checklist.** `app.js` looks its elements up once, at load, and several
listeners are attached without a null check. A removed element that is still referenced
throws at load and **stops the whole script**, which leaves the main page dead. For every
element this plan removes, delete its `elements` entry and every use, including the error
branch of `loadStatistics`:

| Removed | References to delete at `eef0595` |
|---|---|
| `#nextShiftLink` | `elements.nextShiftLink` (line 140) and **its click listener (line ~231), which has no null check**. `schedule.js`'s `renderHints` already guards it with `if (el.nextShiftLink)`, so it's safe. |
| `#plannedWeek`, `#plannedWeekDetail` | `elements` (lines 141–142), `loadStatistics` (lines ~881–882) **and** its error-branch list (lines ~893–894) |
| `#rollingSevenDayTime`, moved into the week card | Nothing: the id is kept. Only `loadStatistics` stops writing it, and `home.js` writes it instead. |
| `.hero`, `.install-banner`, `.backup-nudge` wrappers | No JavaScript refers to the wrappers. The ids inside are kept. |

**Things that must keep working** (section 7):

- Start block and Finish block from Home's next-block card.
- The payout sheet from "Payout history".
- The goal toast "Weekly goal reached".
- The missing-miles "Add miles" and "No miles" buttons, including offline.
- The install flow in a browser.
- `?finish=<id>` push links.
- `?screen=evaluate` (the Android shortcut to the calculator, if one is added later).
- The quick-action button.

## 6. Tests to add or change

**`controller/PageControllerTest.java`:**

1. `homeReplacesTheTitleBannerAndTiles`:
   - the body contains `id="homeScreen"`, `id="goalCard"`, `id="homeNextBlock"`,
     `id="homeAttention"`, `id="recentList"` and `id="evaluatePanel"`;
   - it **doesn't** contain "Track every block, hour, and dollar", `id="plannedWeek"` or
     `class="hero"` in the dashboard area.
2. `theGreetingUsesTheDisplayName`: with a stubbed user named "Angel", the body contains
   "Hi, Angel".
3. `backupDueIsAnAttributeOnTheAttentionCard`: with more than 10 shifts and no backup, the
   attention card carries `data-backup-due="true"`.
4. `forfeitsMovedToSchedule`: `id="forfeitsMonth"` appears after `id="scheduleScreen"`.
5. `theFirstNavItemReadsHome`

**New `src/test/js/home.test.js`** (with `process.env.TZ = 'America/Los_Angeles'`):

6. `weekRange('2026-09-30')` gives from `2026-09-28` and to `2026-10-04`, with the label
   "Mon Sep 28 – Sun Oct 4". Sunday `2026-10-04` gives the same week.
7. `lastSevenRange('2026-10-01')` gives from `2026-09-25` and to `2026-10-01`. Check it
   across the month boundary, and across a daylight-saving change:
   `lastSevenRange('2026-11-03')` starts on `2026-10-28`.
8. `attentionRows`:
   - with everything off, `[]`;
   - with confirm 1, miles 3 and backup true, 3 rows in order, reading "1 block to confirm"
     and "3 blocks have no miles";
   - install true adds the install row last.
9. `recentBlocks` skips SCHEDULED, keeps the order, and returns at most 3.

**`src/test/js/reports.test.js`** (from plan 1): unchanged.

## 7. Manual checks

Use the same local test account as plan 1: 17 blocks, 4 scheduled, a $400 goal, 25% tax, at
390×844 and 1280×800.

1. **Length.** On a phone, Home is **at most about 1,500 px** (plan 1 left about 2,400). The
   first screen, without scrolling, shows the greeting, the whole **This week** card, and
   the top of Next block and Payout.
2. **This week.**
   - The ring shows 23%, with "$93.50 of $400".
   - Last 7 days reads "12h 0m · $375.00 · 4 blocks". Check it against Reports with a custom
     range for the same 7 days.
   - Set aside matches the Taxes card's "this week".
3. **No goal.** Remove the weekly goal. The card reads "$93.50 earned" with no ring
   percentage, and the link reads "Set a goal".
4. **Next block.**
   - It shows VEA7 with the countdown.
   - Within 2 hours of the start, Start block appears here **and** on Schedule, and tapping
     it on Home updates both.
   - The countdowns on both screens keep ticking. Switch screens and wait 1 minute.
5. **Needs attention:**
   - it's hidden with nothing due;
   - with 2 completed blocks without miles, it shows "2 blocks have no miles", and tapping
     the row opens the list with Add miles and No miles;
   - with a block to confirm, tapping the row opens Schedule's confirm strip;
   - the backup row appears for an account with more than 10 blocks and no backup;
   - in desktop Chrome (not the Play app), the install row appears, and "Not now" hides it
     and keeps it hidden.
6. **Recent blocks.** Three rows, newest first, with no scheduled blocks. A tap opens the
   edit dialog. "See all blocks in Reports" opens Reports on the History tab.
7. **Schedule.** The forfeits line sits above the Standing card, and "Log standing" scrolls
   to the form.
8. **Bottom bar.** The first item reads Home, with the house icon.
9. **Desktop 1280.**
   - Two columns: the week card and recent blocks on the left; next block, payout,
     attention and the calculator on the right.
   - `scrollWidth === innerWidth`.
10. **Offline.** Home shows the cached figures. The Start block, Finish and miles buttons
    follow 06b's rules.
11. **No script errors.** With DevTools' console open, load `/` and open every screen. The
    console shows **no errors**. The standing pill beside the forfeits line shows the current
    standing after one is logged.
12. **Screen reader.** The greeting is the page's `h1`. The attention and recent sections
    have headings. The ring is `aria-hidden`, and the amount is text.

## 8. Suggested commit message

```
feat(home): turn the dashboard into a Home screen built around this week

The first screen now answers the questions a driver opens the app
with. A greeting replaces the title banner, and a single This week card
shows what has been earned against the weekly goal, with the last seven
days' hours and pay, the estimated net hourly rate and the tax set-aside
beside it. The next block, with its start and finish buttons, and the
next payout follow, then one Needs attention card that lists only what
is due: blocks to confirm, missing miles, an overdue backup or the
install prompt, and disappears when there is nothing to do.

The block calculator stays folded below, and the last three blocks
close the screen with a link to the full history in Reports. The
forfeits count moves to the Schedule screen beside the standing card,
the planned-hours tile goes because Schedule already shows it, and the
bottom bar's first item is now called Home.
```

## 9. Decisions

These are from the mockup review, plus two calls I made that need your confirmation. **Say
if either is wrong.**

- **Order:** this week, then next block and payout, then needs attention, then the
  calculator, then recent blocks. (You confirmed this.)
- **The week card's three figures:** last 7 days, est. net / hr, set aside. (You confirmed
  this.)
- **My call:** forfeits this month moves to Schedule, beside Standing, instead of taking a
  place on Home.
- **My call:** the planned-next-7-days tile is dropped, since Schedule already shows it.
- **Recent blocks shows 3.** The mockup showed 3.
