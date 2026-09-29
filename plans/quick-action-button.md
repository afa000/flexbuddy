# Plan 16: Quick-action button

Planned against `origin/main` at `bfbecaf` (latest migration V17). Frontend only: no
migration, no backend change, no change to any shared record.

## 1. Goal and scope

One round button, reachable by thumb on every screen of the main page (`/`), that opens a
menu of the four things a driver does most:

1. **Import screenshot**: goes to the import screen and opens the file picker at once.
2. **Add shift**: opens the edit dialog in "new" mode, as a scheduled block on the Schedule
   screen and as a worked block everywhere else.
3. **Add expense**: goes to the Expenses screen with today's date and Fuel already set, and
   the cursor in Amount.
4. **Start block**: starts the next scheduled block when the server would allow it. Otherwise
   the row reads "No block to start" and is disabled.

Acceptance: from the dashboard, logging a fuel expense takes four interactions: tap the
button, tap Add expense, type the amount, tap Add expense (Save).

### Where the draft and the code disagree

These are settled here so the implementer doesn't have to guess:

- **The nav bar is visible at every width.** On desktop it is a floating 460px pill at the
  bottom centre. On phones (≤620px) it is a full-width 92px bar. So "bottom-right on desktop"
  has to keep clear of the pill, and "above the nav on phones" collides with the toast, which
  already sits at `bottom: calc(103px + env(safe-area-inset-bottom))` across the full width.
  Section 4 gives exact positions for each width.
- **The expense category is a `<select>`, not chips.** Its first option is Fuel, so the
  acceptance flow needs no category tap. Chips are out of scope.
- **"New" mode in the edit dialog is scheduled-only today.** The title, description, save
  label and toast all say "scheduled". Section 4 makes them follow the chosen status.
- **Start block already exists** (`schedule.js` `startBlock`, `POST /shifts/{id}/start`).
  The server allows a start only for a SCHEDULED block, from 2 hours before its start until
  its scheduled end, in the account's time zone (`ShiftService.startShift`). The menu uses
  the same window, measured with the schedule's server-anchored clock (`nowMs()`), not the
  device clock.

### Out of scope

- The account page (`/account`). It is a separate template with no nav bar.
- A "Finish block" row for a block already in progress (see open question 1).
- Pre-filling the last-used station, category chips, and any offline queueing of writes.
- Any change to the nav bar itself. It stays for browsing; the button is for creating.

## 2. Data model and migration

None. The latest migration stays V17 and no migration is added.

## 3. Backend

No change to services, controllers or DTOs. Every action uses an endpoint that already
exists:

- `POST /shifts` for Add shift. It already accepts COMPLETED with `details`; import uses it.
- `POST /shifts/{id}/start` for Start block.
- `POST /expenses` for Add expense, through the existing form.
- `GET /shifts/upcoming`, which the dashboard already loads through
  `window.flexbuddySchedule.refresh()`, for Start block eligibility.

## 4. Frontend

### 4.1 `templates/shifts.html`

Put this markup directly **after** `<nav class="mobile-nav">` and **before**
`<div class="toast" id="successToast">`. The CSS uses `.fab ~ .toast`, so the order matters,
and a test pins it.

```html
<button class="fab" id="quickActionButton" type="button" aria-label="Quick actions"
        aria-haspopup="menu" aria-expanded="false" aria-controls="quickActionMenu">
    <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14"/></svg>
</button>

<div class="modal-backdrop quick-actions-backdrop is-hidden" id="quickActionsSheet">
    <section class="action-sheet" id="quickActionMenu" role="menu" aria-label="Quick actions">
        <button class="action-sheet-row" id="qaImport" type="button" role="menuitem" data-online-only>
            <svg …upload icon, same path as #importNavButton…/>
            <span><strong>Import screenshot</strong><small>Read a block from the Flex app</small></span>
        </button>
        <button class="action-sheet-row" id="qaAddShift" type="button" role="menuitem" data-online-only>
            <svg …plus-in-calendar icon…/>
            <span><strong>Add shift</strong><small id="qaAddShiftHint">A block you worked</small></span>
        </button>
        <button class="action-sheet-row" id="qaAddExpense" type="button" role="menuitem" data-online-only>
            <svg …same path as #expensesNavButton…/>
            <span><strong>Add expense</strong><small>Fuel, tolls, parking</small></span>
        </button>
        <button class="action-sheet-row" id="qaStartBlock" type="button" role="menuitem" data-online-only>
            <svg …play icon: M8 5v14l11-7z…/>
            <span><strong id="qaStartLabel">Start block</strong><small id="qaStartHint"></small></span>
        </button>
        <p class="action-sheet-offline">Offline · adding is available when you are back online</p>
    </section>
</div>
```

Add `inputmode="decimal"` to `#expenseAmount` so the acceptance flow brings up the number pad.
Every other money input already has it.

Add `<script th:src="@{/js/quick-actions.js(v=${buildId})}" defer></script>` after
`swipe.js` and before `app.js`, following the "loaded before app.js, uses its helpers at call
time" pattern of `finish.js` and `payouts.js`.

### 4.2 New file `static/js/quick-actions.js`

An IIFE exposing `window.flexbuddyQuickActions = {open, close}`. It binds its listeners at
load and calls app.js globals (`elements`, `showImportScreen`, `openFilePicker`,
`showExpensesScreen`, `resetExpenseForm`, `openEditModal`, `toIsoDate`, `formatTime`,
`editingExpenseId`) only when clicked.

**Opening and closing**

- `open(focus = 'first')`:
  1. Call `refreshStartRow()`.
  2. Remove `is-hidden` from the sheet, add `modal-open` to `body`, and set
     `aria-expanded="true"`.
  3. Focus the first enabled row, or the last one for `focus = 'last'`.
- `close({restoreFocus = true} = {})` reverses this. It removes `modal-open` **before**
  focusing the button, because `body.modal-open .fab` is hidden and a hidden element cannot
  take focus.
- The button's `click` toggles the menu. `ArrowUp` on the button opens it with focus on the
  last row.
- Backdrop click (`event.target === sheet`) closes the menu.
- The menu's `keydown` handler:
  - ArrowDown / ArrowUp move between enabled rows and wrap. Home / End go to the first / last.
  - Escape closes and restores focus. It calls `event.stopPropagation()`, as `finish.js` does.
  - Tab closes without `preventDefault`, following the menu pattern.

**Actions.** Each closes the menu with `restoreFocus: false` first, except where noted.

- **Import** (`qaImport`): run `showImportScreen(false); openFilePicker();` synchronously in
  the click handler. The picker needs the user gesture, so there must be no `await` before
  it. Cancelling the picker leaves the driver on the import screen.
- **Add shift** (`qaAddShift`):
  - If `#scheduleScreen` is visible, call `window.flexbuddySchedule.add(fabButton)`. That is
    the existing `addScheduled` path: the selected day or the account's today, status
    SCHEDULED.
  - Otherwise, call
    `openEditModal({id: null, station: '', date: toIsoDate(new Date()), startTime: '', endTime: '', basePay: '', tips: 0, miles: null, status: 'COMPLETED'}, fabButton, {status: 'COMPLETED'})`.
  - Passing the button as the trigger means `closeEditModal` returns focus to it.
  - The hint `#qaAddShiftHint` reads "A scheduled block" on the Schedule screen and "A block
    you worked" elsewhere. Set it in `open()`.
- **Add expense** (`qaAddExpense`):
  1. `if (editingExpenseId === undefined) resetExpenseForm();` so an edit in progress is
     kept, not thrown away.
  2. `showExpensesScreen(false)`.
  3. `elements.expenseAmount.focus()`.
  4. `elements.expenseForm.scrollIntoView({block: 'center'})`.

  `resetExpenseForm` already sets the date with `toIsoDate(new Date())`, the device's local
  date, and resets the category to Fuel.
- **Start block** (`qaStartBlock`): `window.flexbuddySchedule.start(shift)` with the shift
  that `refreshStartRow()` stored. The toast and the dashboard reload come from `startBlock`.

**`refreshStartRow()`**

- When `window.flexbuddySchedule.startable()` returns a shift:
  - The label reads "Start block".
  - The hint reads `${station} · ${formatTime(start)}–${formatTime(end)}`.
  - The row is enabled, unless pwa.js disabled it for being offline. Check
    `window.flexbuddyPwa?.isOffline()`, the same guard `finish.js` uses.
- Otherwise:
  - The label reads "No block to start".
  - The hint reads "Blocks can start 2 hours before their start time".
  - The row gets `disabled`.
- It runs on every open, so the window is re-checked each time and nothing is polled.

**Hiding the button while typing (phones only)**

This only applies when `matchMedia('(pointer: coarse)').matches`. Toggle
`body.fab-suppressed` when either of these is true:

- An `input`, `select` or `textarea` outside the menu has focus. Listen for `focusin`, and
  for `focusout` re-check on the next animation frame.
- `window.innerHeight - visualViewport.height > 150`, checked on `visualViewport`'s
  `resize` event.

On desktop nothing hides, so tabbing through the filters never makes the button jump.

### 4.3 `static/js/schedule.js`

- Add `startable()`. It returns `state.upcoming.next` when the block:
  - has status `'SCHEDULED'`,
  - has no `details.actualStart`, and
  - satisfies `start − EARLIEST_START_MS ≤ nowMs() ≤ start + timeWorked·60000`.

  Otherwise, including when `state.upcoming` is not loaded yet, it returns `null`. This
  matches the server rule in `ShiftService.startShift`.
- Change `startBlock(shift, button)` to accept a missing button. Replace
  `button.disabled = …` with `if (button) button.disabled = …`. The Next up card still passes
  its button.
- Add `add(trigger)`, which calls `addScheduled(state.selected || today(), trigger)`.
- Export: `window.flexbuddySchedule = {show, refresh, changeStatus, startable, start: startBlock, add}`.

### 4.4 `static/js/app.js`: the edit dialog's "new" mode follows the status

- Add `newShiftCopy(status)`, which returns a title, description and toast:
  - SCHEDULED: title "Add scheduled shift". Description: the current text. Toast: "Shift
    scheduled" / "It is on your schedule and calendar feed."
  - Any other status: title "Add shift". Description: "Enter a block you already worked. It
    counts toward your earnings, hours, and miles." Toast: "Shift added" / "Your earnings
    history is up to date."
- `openEditModal`: in new mode, set the title and description from `newShiftCopy`. Leave edit
  mode unchanged.
- `applyEditStatusRules`: when `editMode === 'new'`, re-set the title from `newShiftCopy`, so
  switching the Status select from Completed to Scheduled retitles the dialog. Leave the
  description alone here, so the custom text `duplicateShift` writes survives.
- `saveEditLabel`: in new mode, return "Add scheduled shift" for SCHEDULED and "Add shift"
  for everything else.
- `saveEditedShift`: when `creating`, show the toast from `newShiftCopy(shift.status)`
  instead of the hard-coded "Shift scheduled".
- Nothing else changes. `duplicateShift` still opens as SCHEDULED, and all its text stays the
  same.

### 4.5 `static/css/styles.css`

Add a section `/* Quick-action button */` near the end, beside the touch-target rules.

**Button and menu**

- `.fab`:
  - `position: fixed; z-index: 35;` (above the nav's 30, below the toast's 40 and modals' 60)
  - `width: 56px; height: 56px; border-radius: 50%;`
  - `color: var(--primary-text); background: var(--cyan); box-shadow: var(--toast-shadow);`
  - A 26px SVG with a 2.2 stroke.
  - Hover and focus-visible states match `.primary-button`.
- `body.modal-open .fab, body.fab-suppressed .fab { display: none; }`
- `.quick-actions-backdrop`:
  - At ≥621px: `background: transparent; backdrop-filter: none;` The desktop menu is a
    popover, not a dialog.
  - At ≤620px it inherits the dimmed backdrop and the existing `place-items: end stretch`,
    which makes it a bottom sheet.
- `.action-sheet`:
  - At ≥621px: `position: fixed; right: 24px; bottom: calc(91px + env(safe-area-inset-bottom)); width: 300px;`
    That is 10px above the button. Surface, border and radius match `.edit-dialog`, with a
    6px padding.
  - At ≤620px: `width: 100%; border-radius: 14px 14px 0 0; padding: 8px 8px calc(12px + env(safe-area-inset-bottom));`
  - A slide-up entry animation, `transform: translateY(12px)` → `0` over .18s. The existing
    `prefers-reduced-motion` rule at line ~1832 already neutralises it.
- `.action-sheet-row`:
  - `min-height: 60px; width: 100%; display: grid; grid-template-columns: 28px minmax(0, 1fr); gap: 14px; align-items: center; padding: 0 14px; text-align: left; border-radius: 8px;`
  - `strong` is 15px. `small` is 12px, muted, with ellipsis on overflow. `minmax(0, 1fr)`
    keeps a long station name from widening anything.
  - `:disabled` uses `opacity: .5; cursor: default;`
- `.action-sheet-offline` shows only under `body.is-offline`, as a 12px muted line.

**Positions.** One table, so nothing overlaps:

| Width | Nav | Button | Toast |
|---|---|---|---|
| ≤620px | full-width bar, height `92px + safe` | `right: 16px; bottom: calc(104px + safe)` (12px above the bar) | `bottom: calc(172px + safe)` via `.fab ~ .toast` (12px above the button) |
| 621–659px | 460px pill, `bottom: 18px`, 70px tall, its right edge within 44px of the button | `right: 16px; bottom: calc(100px + safe)` (12px above the pill) | `bottom: calc(168px + safe)` |
| ≥660px | 460px pill | `right: 24px; bottom: calc(25px + safe)` (vertically centred on the pill, at least 20px from its right edge) | unchanged at `bottom: 105px` |

**Room at the end of the page**, so the last row, "Show 20 more" and the Save buttons can
scroll out from under the button:

- At ≤620px: `main { padding-bottom: calc(180px + env(safe-area-inset-bottom)); }`
  (was 112px) and `.schedule-screen { padding-bottom: calc(178px + env(safe-area-inset-bottom)); }`
  (was 110px).
- At 621–659px: `main { padding-bottom: 170px; }`

Nothing new is wider than its container. The button and menu are `position: fixed` inside
the viewport, so the page cannot widen.

### 4.6 `static/sw.js`

Add `'/js/quick-actions.js'` to `VERSIONED_ASSETS`. `DATA_PATHS` needs nothing, since no new
API path is read.

## 5. Ripple list

**Recurring rework items, checked one by one:**

| Item | Status |
|---|---|
| `AccountSettingsResponse` | Not touched. Field order and compatibility constructors are unchanged. |
| Backup format (`BackupShift`, `BackupSettings`, `AccountBackupFile`, format version 4) | Not touched. No new account data. |
| `BlockEvaluationResponse`, `ShiftResponse` | Not touched. No constructor call sites or tests change. |
| Backup, restore, account deletion | Nothing to add. The feature stores no data (not even `localStorage`). |
| `sw.js` `VERSIONED_ASSETS` | **Changes**: add `/js/quick-actions.js`. `DATA_PATHS` has no change. |
| Boxed request fields | No request DTO changes. |
| Phone layout | New controls are 56px and 60px tall, above the 44px minimum. Nothing widens the page (fixed positioning, `minmax(0, 1fr)`). The account page is untouched, so `.account-grid` `min-width: 0` is not affected. |
| Dates | The only date is `toIsoDate(new Date())`, the device's local date. `toISOString()` is never used. Start block eligibility uses the schedule's clock, which is anchored to the account time zone. |
| Money and tax wording | No figures shown. Nothing to label. |

**Existing code that changes:**

| File | What changes |
|---|---|
| `templates/shifts.html` | Button and menu markup, the new script tag, and `inputmode` on `#expenseAmount`. |
| `static/js/app.js` | `openEditModal`, `applyEditStatusRules`, `saveEditLabel`, the create toast in `saveEditedShift`, and the new helper `newShiftCopy`. |
| `static/js/schedule.js` | `startBlock` (the button becomes optional), new `startable` and `add`, and the export object. |
| `static/css/styles.css` | New section. `main` and `.schedule-screen` padding at ≤620px. `.toast` bottom at ≤659px through `.fab ~ .toast`. |
| `static/sw.js` | `VERSIONED_ASSETS`. |

**Existing tests that change:** none. No Java test covers these files today.

## 6. Tests to add

There is no JavaScript test harness, and adding one is out of scope. The two Java tests
below pin the markup and the service-worker rule; the manual checks in section 7 cover the
behaviour.

1. **`src/test/java/com/angel/flexbuddy/controller/PageControllerTest.java`**. Add
   `shiftsPageRendersTheQuickActionMenu`. It sends `GET /` with `user("angel@example.com")`.
   The existing `@MockitoBean`s are enough: `findByEmailIgnoreCase` returns
   `Optional.empty()` by default, so the page renders without a `currentUser`. It expects
   status 200 and a body that:
   - contains `id="quickActionButton"`, `aria-haspopup="menu"` and `aria-expanded="false"`;
   - contains `role="menu"` and each of `id="qaImport"`, `id="qaAddShift"`,
     `id="qaAddExpense"` and `id="qaStartBlock"`, each on an element with `role="menuitem"`
     and `data-online-only`. Check with a regex over each button's opening tag;
   - contains `/js/quick-actions.js?v=`;
   - has the index of `id="quickActionButton"` below the index of `id="successToast"`, which
     pins the DOM order that `.fab ~ .toast` relies on.
2. **New `src/test/java/com/angel/flexbuddy/StaticAssetsTest.java`** (plain JUnit, no Spring
   context). Add `everyPageScriptIsPrecachedByTheServiceWorker`:
   - Read `static/sw.js` from the classpath and pull out the `VERSIONED_ASSETS` array literal.
   - Read every `templates/*.html` through `PathMatchingResourcePatternResolver`.
   - For each `@{/js/<name>.js` found, assert `'/js/<name>.js'` is in that array.
   - Name the missing file and the template in the failure message.

   This pins the recurring "new JS file not in `VERSIONED_ASSETS`" rework. I checked it
   passes on today's `main`: all 14 script references across the templates are present.

The expected suite total is about 277 tests (275 today plus these 2).

## 7. Manual checks

Use `sh ./mvnw spring-boot:run` and Chrome DevTools device mode. On a phone, test with and
without the keyboard. Sizes: **375×667** (iPhone SE), **390×844**, **430×932**, **640×800**
(the 621–659px band), and **1280×800** desktop. Check both themes.

**Placement:**

1. At 390×844, on each of Dashboard, Schedule, Import and Expenses:
   - The button is 56×56px, 16px from the right edge, with its bottom edge 12px above the
     nav bar's top edge. Measure it in DevTools: `bottom` = 104px.
   - It never covers a nav item.
   - Scrolled fully down, the last element (Recently deleted on Dashboard and Expenses, the
     day panel on Schedule, the Review shift details panel on Import) sits fully above the
     button.
2. At 1280×800 the button's centre lines up vertically with the nav pill's centre, 24px from
   the right edge, with at least 20px between the pill and the button.
3. At 640×800 the button sits 12px above the pill, and a toast (add an expense to get one)
   appears above the button, not under it.
4. At 375×667, after any save, the toast's bottom edge is 12px above the button's top edge.
   Neither is covered.
5. At every width, `document.documentElement.scrollWidth === window.innerWidth`, with the menu
   both open and closed.

**Menu:**

6. At 390px, tapping the button opens a bottom sheet with four rows, each at least 60px tall,
   and a dimmed backdrop. The button is hidden while the sheet is open. Tapping the backdrop
   closes it and the button comes back.
7. At 1280px, clicking opens a 300px menu with its bottom edge 10px above the button, and the
   page is not dimmed. Escape closes it and focus is back on the button.
8. Keyboard only at 1280px: Tab to the button, then:
   - Enter focuses Import screenshot.
   - ArrowDown ×3 reaches Start block, or Add expense if Start is disabled.
   - ArrowDown once more wraps to the top.
   - End, then Home, jump to the ends.
   - Escape closes the menu with focus back on the button.
   - ArrowUp on the button opens the menu at the last enabled row.
9. With TalkBack or VoiceOver, the button is announced as "Quick actions, menu, collapsed"
   and then "expanded", and each row as a menu item.

**Actions:**

10. **Import screenshot**: the Import tab becomes active and the system file picker opens
    straight away. Cancelling leaves the driver on the import screen with nothing selected.
11. **Add shift** from Dashboard: the dialog is titled "Add shift", Status is Completed, Date
    is today's device date, and the save button reads "Add shift". Enter `TEST1`,
    9:00–13:00, $80, $10 tips and save. The toast reads "Shift added". The shift is in
    history with total $90.00 and 4h 0m. Close with Cancel and focus returns to the button.
12. In the same dialog, switching Status to Scheduled changes the title and button to "Add
    scheduled shift", and the tips field is disabled and set to 0.
13. **Add shift** from Schedule, with a day selected in the calendar: the dialog is titled
    "Add scheduled shift", the date is the selected day, and the toast after saving reads
    "Shift scheduled".
14. **Duplicate** from an existing shift still shows "A copy of … Check the date and pay,
    then add it." (a regression check).
15. **Add expense** from Dashboard at 390px:
    - The Expenses tab becomes active, Date is today, Category is Fuel, and the number pad
      opens with Amount focused.
    - Type `45.20` and tap Add expense. The toast reads "Expense added · Net earnings have
      been recalculated.", and the expense list's top row shows FUEL, today's date and
      $45.20.
    - That is exactly four interactions.
    - Start editing an existing expense, then open Add expense from the menu. The edit is
      kept, not cleared.
16. **Start block**:
    - Create a scheduled block starting 90 minutes from now. Open the menu: the row reads
      "Start block" with the hint "STATION · h:mm AM–h:mm PM". Tap it: the toast reads
      "Block started · Started at …" and the Schedule Next up card shows IN PROGRESS.
    - Open the menu again: the row reads "No block to start" and is disabled. The block has
      started, so it no longer qualifies.
17. With the only scheduled block 3 hours away, the row reads "No block to start", is
    disabled, and ArrowDown skips it.

**Keyboard and offline:**

18. At 390px with touch emulation on, focus a filter date field or the evaluate panel's
    Station field: the button disappears. Blur it and the button returns. On desktop with a
    mouse, focusing a filter field leaves the button in place.
19. On a real Android device in the TWA, open the edit dialog's Station field: the keyboard
    never covers a button, and Save stays reachable.
20. DevTools Offline, then tap the history panel's Refresh: all four rows are disabled, the "Offline · adding is
    available when you are back online" line shows, and ArrowDown moves nowhere. Back
    online, the rows re-enable (Start follows its own rule).
21. Reduced motion on: the sheet appears without sliding.

## 8. Suggested commit message

```
feat(ui): add a quick-action button for the four most common tasks

A round button now sits above the navigation on every screen of the
main page and opens a menu to import a screenshot, add a shift, add an
expense, or start the next block. On a phone it opens as a bottom
sheet; on a wider screen it opens as a small menu above the button. It
hides while a dialog is open and while the driver is typing on a
phone, so it never covers a Save button, and toasts and the end of
each page now leave room for it.

Add shift opens the existing dialog as a worked block, or as a
scheduled block on the Schedule screen, and the dialog's title, save
label and confirmation now follow the chosen status instead of always
saying scheduled. Start block uses the same window as the server, from
two hours before the start until the scheduled end, and says so when
no block qualifies.

A new test checks that every script a page loads is precached by the
service worker, which has been missed before, and the page test pins
the menu's markup and its accessible roles.
```

## 9. Open questions

1. **A block already in progress.** Once the next block has started, should the fourth row
   become **Finish block** and open the existing finish sheet? This plan leaves it as "No
   block to start". It would be about 10 more lines in `refreshStartRow` and the action, and
   it arguably fits "nothing important more than one tap away".
2. **Add shift outside Schedule** opens as a *worked* block (Completed, today). Is that the
   default you want, or should it always open as Scheduled, like the Schedule screen's
   button?
3. **Toast placement.** This plan lifts the toast above the button on phones, 172px from the
   bottom. The alternative is to leave the toast where it is and hide the button while a
   toast is showing. The toast carries Undo, so I lifted it rather than risk the button
   covering Undo. Is that right?
4. **Floating button or a fifth nav item.** A raised centre "+" inside the nav bar would
   avoid every overlap in the positions table, and it's the same thumb reach. The draft
   specifies a floating button, so that is what's planned. Say if you'd rather have the
   in-nav version.
5. **The account page.** Leave it without the button, as planned, or add it there too? That
   would mean moving the button into a shared Thymeleaf fragment.
