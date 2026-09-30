# Layout 4 of 4: the tidy-up pass

Planned against `origin/main` at `eef0595`, and **it assumes plans 1–3 have shipped**:
`reports-screen`, `home-screen` and `account-sections`. **No migration and no backend
change.**

## 1. Goal and scope

Plans 1–3 fix the structure. This pass removes the **visual noise that's left**, so every
screen reads the same way.

1. **One-line screen titles.** Schedule, Import and Expenses each open with a big banner:
   a small label, a large title and a sentence, for example "YOUR SCHEDULE / Schedule /
   Plan upcoming blocks, confirm the ones you finished…". Each becomes **one line**: a
   title, with the screen's main button on the right where it has one. Home and Reports
   already look like this after plans 1 and 2.
2. **Fewer small labels.** The page has 17 small uppercase labels above panel titles. Keep
   them only where they add information the title doesn't. Remove them where they repeat
   it. The full list is in section 4.
3. **Fewer repeated descriptions.** Cut the sentence under a panel title where it only
   restates the title. Keep instructions, limits and the money and tax disclaimers.
4. **The header,** as in the mockup:
   - **Left:** the FlexBuddy logo, which goes to Home. It replaces the menu icon, which went
     to the dashboard but looked like a menu.
   - **Centre:** "FlexBuddy", without the "Shift tracker" subtitle.
   - **Right:** the theme toggle, then a round **account button** showing the driver's
     initial, which goes to `/account`. It replaces the name text and the **sign-out
     button**. Sign-out moved to Account in plan 3.
5. **Expenses:** four tiles become **three** (Total, Fuel, Tolls & parking). The fourth,
   "Vehicle cost method", is a setting, not a total, so it becomes a one-line note under
   the expense list: "Vehicle costs use standard mileage at $0.70/mi · Change", linking to
   `/account#costs`.
6. **One spacing scale for phone panels.** At ≤620px, panels currently use padding of 18,
   20, 23, 24 or 29 px depending on the panel. It becomes **16px everywhere**, with gaps of
   8, 12 or 16px through new tokens.

### Out of scope

- Colours, fonts and the light theme's palette.
- Charts.
- Dialog content: the edit, finish, payout and conflict sheets keep their labels (see
  section 4).
- Login, register and legal pages.

## 2. Data model and migration

None.

## 3. Backend

None. The header's initial comes from `currentUser.displayName`, which is already in the
model.

## 4. Frontend

### Tokens (`styles.css`, `:root`, shared by both themes)

```css
--space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px; --space-6: 24px;
```

Use them **only in the rules this plan touches**. Don't sweep the whole stylesheet.

### Header (`templates/shifts.html`)

```html
<header class="topbar">
  <a class="brand-mark" href="/" id="homeLink" aria-label="FlexBuddy, go to Home">
    <img src="/icons/icon-192.png" alt="" width="36" height="36">
  </a>
  <strong class="brand-name">FlexBuddy</strong>
  <div class="header-actions">
    <button class="round-header-button theme-toggle" id="themeToggleButton" …unchanged…></button>
    <a class="round-header-button account-button" href="/account" aria-label="Account"
       th:text="${currentUser != null ? #strings.substring(currentUser.displayName, 0, 1) : 'A'}">A</a>
  </div>
</header>
```

- **Remove** `#dashboardButton`, `.brand` with its subtitle, `.account-name-link` and
  `#logoutForm`.
- **`#homeLink`:** its click handler runs `event.preventDefault(); showDashboard();`, so
  there's no page reload. The `href="/"` stays for middle-click and when scripts are off.
- **The initial:** make it upper case in CSS (`text-transform: uppercase`). An empty display
  name can't happen, because registration requires it, but guard with `'A'` anyway.

### `static/js/app.js`

- **Remove:**
  - `elements.dashboardButton` and its listener (line ~240);
  - `elements.logoutForm`, its listener (line ~238), and the `signOut` function. Sign-out
    now lives only in `account.js`'s `guardedSignOut` (plan 3).
  - **Check first** that nothing else calls `signOut`.
- **Add** `elements.homeLink` and its click handler.

### `templates/account.html`, the header

Make it consistent with the main page:

- **Left:** a back button, `aria-label="Back to Home"`, as set by plan 3.
- **Centre:** "FlexBuddy", **dropping** its "Account data" subtitle (line 32). The page's
  `<title>` also changes, from "Account data | FlexBuddy" to "Account | FlexBuddy".
- **Right:** the theme toggle only. The page is the account page, so it gets no account
  button.

### Screen titles

Replace each `header.screen-heading` with `<div class="screen-title">`:

| Screen | Before | After |
|---|---|---|
| Schedule | "YOUR SCHEDULE / Schedule / Plan upcoming blocks, confirm…", with the Add scheduled shift button below | `<h1>Schedule</h1>` and **"Add scheduled shift"** (`#addScheduledShiftButton`, unchanged, `compact-button`) on the same line |
| Import | "IMPORT A SHIFT / Add from screenshot / Upload a screenshot, review…" | `<h1>Import a shift</h1>`. Steps 1 and 2 already explain the flow. |
| Expenses | "DRIVER EXPENSES / Expenses / Track the costs behind each shift…" | `<h1>Expenses</h1>` |

**CSS:**

- `.screen-title { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; padding: var(--space-4) var(--space-4) var(--space-2); }`
- The `h1` is 24px (22px at ≤380px).
- The button doesn't shrink below 44px, and wraps under the title at ≤380px.

The existing heading ids stay on the new `h1`s (`schedule-title`, `import-title`,
`expenses-title`), so `aria-labelledby` keeps working.

### Small labels (the `.step-label` elements on the main page)

| Label (line at `eef0595`) | Title it sits over | Decision |
|---|---|---|
| NEEDS CONFIRMATION (229) | "Did you work these blocks?" | **Remove** |
| NEXT 14 DAYS (237) | "Upcoming blocks" | **Remove**, and rename the title to **"Next 14 days"** |
| CALENDAR (243) | the month name, for example "September 2026" | **Remove**. The grid makes it obvious. |
| STANDING (265) | "Your Flex standing" | **Remove** |
| STEP 1 / STEP 2 (311, 353) | "Upload a screenshot" / "Review shift details" | **Keep**: they give the order |
| BEFORE YOU ACCEPT (98) | "Evaluate a block" | **Keep**: it says when to use it |
| QUICK ADD (546) | "Log an expense" | **Remove** |
| EXPENSE HISTORY (558) | "Explore expenses" | **Remove**, and rename the title to **"Expense history"** |
| REPORTS (486) and HISTORY (575), now inside Reports after plan 1 | "Earnings breakdown" / "Shift history" | **Remove**. The tabs name them. |
| FILTERS (446) and LOG YOUR MILES (90) | | Already gone, in plans 1 and 2 |
| SHIFT DETAILS, PAYOUTS, SYNC, FINISH BLOCK (dialogs) | | **Keep**: in a dialog, the label names the kind of sheet |

Also remove the `.step-number` badges ("01" and "02") on the Import panels. They repeat
STEP 1 and STEP 2.

### Descriptions under panel titles

- **Remove:**
  - "Every shift matching the active filters." (history);
  - "Compare base pay, tips, hours, and hourly earnings." (reports);
  - "Log it from the Flex app when it changes. FlexBuddy never guesses your standing."
    becomes a **hint under the Standing form**, and the "never guesses" promise is kept.
- **Change:** "Link it to a shift when the cost belongs to one block." becomes the `field-hint`
  of the Shift select.
- **Keep:**
  - "Choose a PNG or JPEG screenshot up to 5 MB.";
  - "Check each value before adding the shift.";
  - every "estimate, not tax advice" line;
  - the payout sheet's explanation.

### Expenses tiles (`templates/shifts.html`, `.expense-stats`)

- **Keep three tiles:**
  - "Total" (`#expenseSummaryTotal`, `#expenseSummaryCount`);
  - "Fuel" (`#expenseFuel`);
  - "Tolls & parking" (`#expenseRoad`).
- At ≤620px they sit in **3 columns**: `grid-template-columns: repeat(3, minmax(0, 1fr))`,
  with compact 12px padding, 18px values and 11px labels.
- **Move the vehicle cost method** to a line under the expense history heading:

  ```html
  <p class="expense-cost-note">Vehicle costs use <span id="expenseCostMethod">standard mileage at $0.70/mi</span> · <a class="text-link" href="/account#costs">Change</a></p>
  ```

  The id is kept, and the code that fills it changes its text to lower-case phrasing:
  "standard mileage at $0.70/mi" or "actual expenses".

### Phone spacing (≤620px only)

Apply these to **these selectors only**:

- `.panel`, `.stat-card`, `.expense-form-panel`, `.expense-history-panel`,
  `.upcoming-panel`, `.calendar-panel`, `.standing-panel`, `.import-panel` and
  `.preview-panel`;
- the Home and Reports cards from plans 1 and 2 already use 16px.

The rules:

- panel padding `var(--space-4)`;
- gap between panels `var(--space-3)`;
- no border radius on full-bleed panels, as today;
- `.stat-card { min-height: 0; }` on phones. The fixed 142px leaves empty space in short
  tiles.

**Verify there's no regression** in the 44px tap rule. Padding changes mustn't shrink any
button.

### `static/sw.js`

No change. No new files.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format (v4), `BlockEvaluationResponse`, `ShiftResponse` | **Not touched.** |
| New account data | None. |
| `sw.js` | No change. |
| Boxed request fields | Not applicable. |
| Phone layout | This plan *is* phone layout. Keep 44px targets. Titles and buttons wrap at ≤380px. The header fits at 320px: 44px logo, name, 2 × 44px buttons. |
| Dates | None. |
| Money and tax wording | Disclaimers are kept. Only redundant sentences go. |

**Existing code that changes:**

| File | Change |
|---|---|
| `shifts.html` | Header, 3 screen titles, 9 labels removed, 2 titles renamed, step badges, descriptions, expense tiles and note |
| `account.html` | Header subtitle |
| `app.js` | `dashboardButton`, `logoutForm` and `signOut` removed; `homeLink`; expense cost note text |
| `styles.css` | Tokens, `.screen-title`, `.brand-mark`, `.account-button`, expense tiles, phone spacing. Remove `.brand small`, `.account-name` and `.screen-heading` rules **only if** nothing else uses them. The legal pages use `.auth-brand`, which is different. |

**Removed-elements checklist.** `app.js` looks its elements up once, at load, and several
listeners are attached without a null check. A removed element that is still referenced
throws at load and **stops the whole script**, which leaves the main page dead. For every
element this plan removes, delete its `elements` entry and every use, including the error
branch of `loadStatistics`:

| Removed | References to delete at `eef0595` |
|---|---|
| `#dashboardButton` | `elements.dashboardButton` (line 4) and its listener (line ~240) |
| `#logoutForm` | `elements.logoutForm` (line 166), its listener (line ~238), and `signOut` (line ~2158) |
| `.account-name-link` and `.brand` | No JavaScript refers to them. Check again at implementation time. |

**Tests that pin the old text or ids:** none at `eef0595`. A search of `src/test` for
`dashboardButton`, `logoutForm`, "YOUR SCHEDULE", "Explore expenses" and "Upcoming blocks"
finds nothing. Search again at implementation time, in case plans 1–3 added any.

## 6. Tests to add or change

**`controller/PageControllerTest.java`:**

1. `headerHasHomeLogoAndAccountButtonButNoSignOut`:
   - the body contains `id="homeLink"` and `class="round-header-button account-button"`,
     with the initial "A" for the stubbed user "Angel";
   - it **doesn't** contain `id="logoutForm"` or `id="dashboardButton"`.
2. `screenTitlesAreOneLine`:
   - there's **no** `class="screen-heading"`, and none of the texts "YOUR SCHEDULE",
     "IMPORT A SHIFT" or "DRIVER EXPENSES";
   - `<h1 id="schedule-title">Schedule</h1>` is present.
3. `onlyMeaningfulLabelsRemain`: the body contains "STEP 1", "STEP 2" and "BEFORE YOU
   ACCEPT", and doesn't contain "NEXT 14 DAYS", "QUICK ADD" or "EXPENSE HISTORY". The
   dialog labels "SHIFT DETAILS" and "FINISH BLOCK" are still present.
4. `expenseCostMethodIsANoteNotATile`: `id="expenseCostMethod"` sits inside
   `class="expense-cost-note"`, with a link to `/account#costs`.

**`AccountControllerTest`:**

5. The account header has no "Account data" subtitle.

No JavaScript tests change.

## 7. Manual checks

Use the local test account at **320×640**, **390×844** and **1280×800**, in **both themes**.

1. **Header.**
   - At 320px, all of it fits on one row with no overflow: the logo, "FlexBuddy", the theme
     toggle and "A".
   - The logo goes to Home without a reload. "A" opens Account.
   - There's no sign-out in the header. Sign-out is on Account, from plan 3.
2. **Titles.**
   - Schedule's first row is "Schedule" with **Add scheduled shift** beside it. At 320px
     the button wraps below the title, still at least 44px.
   - Import reads "Import a shift". Expenses reads "Expenses".
3. **Page heights at 390px,** measured with the same data:
   - Schedule is **at most about 2,700 px** (was 3,056);
   - Expenses is **at most about 2,650 px** (was 3,020);
   - Import is **at most about 1,250 px** (was 1,400).
4. **Labels.** Only STEP 1, STEP 2 and BEFORE YOU ACCEPT remain on screens. The dialogs keep
   theirs.
5. **Expenses.**
   - Three tiles on one row at 390px, with no overlapping text on a figure like "$1,234.56".
   - The cost note reads "Vehicle costs use standard mileage at $0.70/mi · Change", and
     Change opens Account → Earnings & costs.
6. **Spacing.** Panels on every screen line up at the same 16px inset on phones, and there's
   no empty space at the bottom of short tiles.
7. **Tap targets.** In DevTools, every button and link on Schedule, Expenses and Import is
   at least 44×44.
8. **Accessibility.**
   - Each screen has exactly one visible `h1`.
   - The logo link reads "FlexBuddy, go to Home". The account button reads "Account".
9. **No script errors.** With DevTools' console open, load `/` and `/account`, and open
   every screen. The console shows **no errors**.
10. **Before and after.** Take the same screenshots as in the design review. The full-page
   captures of Home, Reports, Schedule, Expenses and Account together should be **under
   half** of the original total (about 22,000 px at 390 wide).

## 8. Suggested commit message

```
style(ui): one-line titles, a simpler header and less repetition

Every screen now opens on a one-line title instead of a banner with a
label, a title and a sentence, and Schedule keeps its Add scheduled
shift button beside its title. The small uppercase labels over panel
titles are kept only where they add something, such as the import
steps, and sentences that repeated a title are gone, while every
estimate and tax notice stays.

The header shows the FlexBuddy logo, which goes home, the theme switch
and a round account button with the driver's initial; signing out now
happens on the account page. Expenses shows three totals in one row,
with the vehicle cost method as a note linking to its setting, and
panels on a phone share one 16-pixel inset.
```

## 9. Decisions

- **The header matches the mockup:** the logo on the left, the account initial on the right,
  and sign-out on Account. You approved the mockup.
- **My calls, so tell me if any is wrong:**
  - which labels stay (the import steps, "Before you accept", and the dialog labels);
  - moving the vehicle cost method off the Expenses tiles;
  - a 16px inset on phones.
