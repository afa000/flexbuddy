# Plan 06b: Offline queue, the outbox in the browser

Planned against `origin/main` at `0292b67`. **It depends on 06a**
(`plans/offline-queue-server.md`), which must ship first: the `Idempotency-Key` header,
`expectedUpdatedAt`, the 409 bodies and `GET /csrf`. No migration.

## 1. Goal and scope

A driver with no signal, or a signal too weak to use, can still do three things, and they
reach the server once the app is open with a connection. Each is sent exactly once, even
if the app was closed in between.

1. **Add a shift**: the edit dialog in new mode, from Quick actions or "Add scheduled
   shift".
2. **Add an expense**: the expense form in add mode.
3. **Finish a block, or add its details**: the finish sheet's actual times, odometer, miles,
   stops, packages and returns, and completing a scheduled block.

What the driver sees:

- The save "succeeds" with the toast **"Saved offline · will sync"**.
- A **Waiting to sync** strip lists what's queued, on every screen.
- When a sync finishes, the toast reads **"All changes synced"** and the dashboard reloads.
- If a queued finish meets a block that was changed on another device, a **conflict sheet**
  shows both versions and asks which one to keep. It never silently overwrites.

**Nothing destructive can be queued.** Deletes, restores, edits in the full dialog, status
changes other than finishing, Start block, import, and everything on the account page stay
online-only, as today.

### How this implements the draft, and where it differs

- **The drain runs in the page, not in the service worker.** It runs:
  - when the app opens,
  - on the `online` event and the app's own `flexbuddy:online` event,
  - when the app comes back to the foreground,
  - and on a backoff timer while items are waiting.

  **No Background Sync.** Chrome in the Android app supports it, but:
  - the worker would need its own copy of the IndexedDB, CSRF and error logic;
  - it would race the page over the rotating remember-me cookie;
  - and the only case it adds is "phone reconnects while the app is closed".

  With page-only draining, those items sync seconds after the driver next opens the app.
  See open question 1.
- **No fake rows with negative ids.** Queued creates aren't injected into history or the
  statistics, which are computed on the server and would be wrong. They're listed in the
  Waiting to sync strip. A queued finish marks its shift's history row "Pending".
  Consequences:
  - An expense can never be linked to a queued shift: queued shifts never appear in the
    Shift dropdown.
  - A queued shift can't be finished before it syncs: it has no id yet.
- **Conflicts: choose a whole version, not a field-by-field merge.** Per-field merging of
  times and odometer readings invites nonsense combinations. See open question 3.
- **"Pending" and "sync" wording** is used throughout. The draft's account-page line is
  kept, with Sync now and Discard.

### Out of scope

- Queueing edits (`PUT /shifts/{id}`), deletes, status changes other than the finish sheet,
  Start block, imports and uploads.
- Background Sync.
- Showing queued creates in totals, charts or goals.
- Syncing across devices while offline.
- A JavaScript test harness (open question 5).

## 2. Data model and migration

No database change. Browser storage uses **IndexedDB** database `flexbuddy-outbox`,
version 1, with one object store, `items`, keyed by `id`.

| Field | Meaning |
|---|---|
| `id` | UUID from `crypto.randomUUID()`. It is also the `Idempotency-Key`. It is made **once, before the first network attempt**, so a request that reached the server but lost its response is deduplicated when replayed. |
| `accountId` | From `<meta name="flexbuddy-account">` when queued. Items are only ever sent when `GET /csrf` returns the same `accountId`. |
| `kind` | `shift-create`, `expense-create` or `shift-finish` |
| `method`, `url` | For example `POST /shifts`, `POST /expenses`, `PATCH /shifts/42/status` |
| `body` | The exact JSON object the form would have sent |
| `shiftId`, `expectedUpdatedAt` | For `shift-finish` only: the shift's `updatedAt` when the sheet was opened |
| `summary` | `{title, detail}`, for the strip. For example `{title: "Fuel · $45.20", detail: "Sep 29"}`, `{title: "Add shift · VEA7", detail: "Sep 29 · 9:00 AM–1:00 PM"}` or `{title: "Finish · VEA7", detail: "Sep 29 · 42.0 mi"}` |
| `createdAt` | `Date.now()` |
| `state` | `pending`, `conflict` or `failed` |
| `error`, `current` | The failure message, or the server's current shift for a conflict |

- **At most 50 items.** A 51st save shows "Too many changes are waiting to sync. Connect to
  send them before adding more." and nothing is queued.
- **If IndexedDB is unavailable** (for example a private window), saving behaves as today
  and shows the offline error.

## 3. Backend

The only change is one meta tag, rendered by the existing controllers.

- In `templates/shifts.html` and `templates/account.html`, in `<head>`:
  `<meta name="flexbuddy-account" th:content="${currentUser != null ? currentUser.id : ''}">`.
- `PageController` and `AccountController` already put `currentUser` in the model. The shell
  pages are cached network-first, so the tag is also there offline.

Everything else is 06a.

## 4. Frontend

### New file `static/js/outbox.js`

An IIFE exposing `window.flexbuddyOutbox = {submit, drain, list, discard, discardAll, count, resolveConflict, pendingShiftIds, clear}`.
Load it on both pages, after `pwa.js` and before the other scripts (`finish.js`, `app.js`,
`account.js` and so on).

**`submit(kind, {method, url, body, summary, shiftId, expectedUpdatedAt})`** returns
`{sent: Response}` or `{queued: true}`, and throws for HTTP errors as today.

1. `id = crypto.randomUUID()`.
2. If `window.flexbuddyPwa.isOffline()` or `!navigator.onLine`, go to step 4.
3. Otherwise, send through `apiFetch` with `Idempotency-Key: id`, the CSRF headers the page
   already has, and an `AbortController` timeout of **20 s**.
   - A normal response goes back to the caller, whether 2xx or an error such as a 400
     validation message. The caller handles it exactly as today.
   - A network failure goes to step 4: a `TypeError`, an `AbortError` from the timeout, or
     the error `apiFetch` throws when offline.
   - `expectedUpdatedAt` is **not** sent on this online attempt, as 06a specifies.
4. **Queue:** put the item with `state: 'pending'`, dispatch `flexbuddy:outbox`, and start
   the backoff timer. Return `{queued: true}`.

**`apiFetch` change** (`app.js`): the `throw new Error('You are offline…')` becomes an error
with `error.offline = true`, so `submit` can recognise it without matching message text.
`account.js` has its own `apiFetch`, but no queueable form, so it doesn't change.

**`drain()`**

- Guarded by `navigator.locks.request('flexbuddy-outbox', {ifAvailable: true}, …)`, so two
  tabs never drain at once. Where Web Locks is missing, use a module-level flag.
- No-op when there are no `pending` items for this account.

1. `GET /csrf` with `fetch` directly, **not `apiFetch`**, because `apiFetch` sends a 401 or
   403 to the login page. Use `cache: 'no-store'`.
   - If the response was redirected to `/login`, or is 401 or 403: set the signed-out state,
     which makes the strip read "Sign in to finish syncing 2 changes" with a Sign in link to
     `/login`. Stop.
   - A network error or 5xx: stop, and retry on the next trigger.
2. Items whose `accountId` differs from the `/csrf` `accountId` are **never sent**. The strip
   shows them as "From another account" with Discard only.
3. For each `pending` item, oldest first, send `fetch(url, {method, headers: {'Content-Type': 'application/json', [headerName]: token, 'Idempotency-Key': id}, body})`
   with a 20 s timeout. A `shift-finish` body gets `expectedUpdatedAt` added. Then:

   | Response | What happens |
   |---|---|
   | 2xx | Delete the item, and count it as synced |
   | 409 `{"code":"DUPLICATE"}` | Delete it. It was already delivered. Count it as synced. |
   | 409 `{"code":"CONFLICT"}` | Set `state: 'conflict'` and `current`. Continue. |
   | 403 | Fetch `/csrf` once more and retry this item once. A second 403 sets signed-out and stops. |
   | Redirect to `/login`, or 401 | Signed out. Stop. |
   | 400 or 404 | Set `state: 'failed'`, with `error` set to the response text cut to 200 characters. Continue. A 404 means the shift was deleted elsewhere. |
   | 5xx, network error, timeout | Stop. Keep the rest for the next trigger. |

4. When something synced:
   - Show the toast "All changes synced", or "2 changes synced · 1 needs your choice".
   - Call `loadDashboard()`, and `loadExpenses()` if the Expenses screen is visible, through
     the globals in `app.js`. On the account page, just refresh the line.
   - Dispatch `flexbuddy:outbox`.

**Triggers**

- `DOMContentLoaded` plus 1 s, if online.
- The window's `online` event and the app's `flexbuddy:online` event.
- `visibilitychange` to visible.
- After every `submit` that queued.
- A timer while `pending` items exist and the page is visible: 30 s, 60 s, 120 s, then every
  300 s. It resets after a success.

**Other functions**

- `resolveConflict(id, choice)`:
  - `'mine'` sets `expectedUpdatedAt = current.updatedAt` and `state = 'pending'`, then drains.
    The same `id` is kept, which is safe: 06a only records `lastRequestId` for a change it
    applied.
  - `'saved'` deletes the item.
- `pendingShiftIds()` returns a `Set` of `shiftId`s with a queued finish. It's cached in
  memory and refreshed on `flexbuddy:outbox`.
- `clear()` deletes the database. It's called on sign-out.

### Saving through the outbox

**`app.js`, `saveEditedShift` when `creating`:**

- Call `flexbuddyOutbox.submit('shift-create', {method: 'POST', url: '/shifts', body: shift, summary})`.
- On `{queued}`: `closeEditModal()`, then the toast "Saved offline · will sync" with the
  message `${station} on ${formatDate(date)} will be added when you're back online.` Don't
  call `loadDashboard`.
- On `{sent}`: continue with the existing response handling.
- The edit path (`PUT`) is unchanged.

**`app.js`, `saveExpense` when not editing:** the same, with `kind: 'expense-create'` and the
message `Fuel · $45.20 will be added when you're back online.` It calls
`resetExpenseForm()` as after a normal add. Editing an expense is unchanged, and online-only.

**`finish.js`, `save`:**

- Call `submit('shift-finish', {method: 'PATCH', url: /shifts/${id}/status, body, shiftId: id, expectedUpdatedAt: finished.updatedAt, summary})`.
- On `{queued}`: `close()`, then the toast "Saved offline · will sync" with
  `${station} on ${date} will be updated when you're back online.`

### Letting these controls work offline

Today `pwa.js` disables every `form button[type="submit"]` and every `[data-online-only]`
while offline.

| File | Change |
|---|---|
| `pwa.js` | The selector becomes `[data-online-only], form:not([data-queueable]) button[type="submit"], #dropZone`. |
| `shifts.html` | `data-queueable` on `#editForm`, `#expenseForm` and `#finishForm`. Remove `data-online-only` from `#addScheduledShiftButton`, `#qaAddShift` and `#qaAddExpense`. Keep it on `#qaImport`, `#qaStartBlock` and `#completeScheduledButton`. |
| `app.js`, `openEditModal` | In **edit** mode while offline, disable `#saveEditButton` with the title "Available when you are back online", as `pwa.js` would. New mode stays enabled. Re-check on `flexbuddy:online`. |
| `app.js`, expense editing | The same: when `editingExpenseId` is set and the app is offline, disable `#saveExpenseButton`. |
| `schedule.js`, `bindActions` | Actions `completed` and `finish` are no longer marked online-only. `start`, `cancelled` and `forfeited` stay online-only. |
| `schedule.js`, day panel | The "Add scheduled shift" button is no longer disabled offline. |
| `finish.js`, missing-miles rows | "Add miles" is no longer online-only. "No miles" stays online-only, because it's a separate write that isn't queued. |
| `quick-actions.js`, `refreshStartRow` | Only `#qaImport` and the start row follow `offline`. Add shift and Add expense stay enabled. |

### Waiting to sync strip, on every screen of `/`

In `shifts.html`, directly after `#offlineBanner` and outside the `data-screen` sections:

```html
<section class="outbox-strip" id="outboxStrip" aria-labelledby="outboxTitle" hidden>
  <div class="outbox-heading">
    <strong id="outboxTitle">Waiting to sync · 2</strong>
    <button class="text-button" id="outboxSyncButton" type="button">Sync now</button>
  </div>
  <ul class="outbox-list" id="outboxList"></ul>
</section>
```

- One row per item: `summary.title`, `summary.detail`, and a tag.
  - **Pending**: in `--cyan`.
  - **Needs your choice**: in `--warning`, with a **Choose** button that opens the conflict
    sheet.
  - **Couldn't sync**: in `--error`, with the error text and **Discard**.
  - **From another account**: Discard only.
- **Discard** on a pending item asks first, through `openConfirm`: "Discard this change? It
  hasn't been sent and can't be recovered."
- The signed-out state replaces the heading with "Sign in to finish syncing 2 changes" and a
  **Sign in** link.
- **Sync now** is disabled offline.
- The strip is hidden when the list is empty. It re-renders on `flexbuddy:outbox`.

**Pending tag on history rows:** in `renderShifts`, when `flexbuddyOutbox.pendingShiftIds()`
has the row's id, add `<span class="pending-tag">Pending</span>` after the station.

### Conflict sheet

A new `.modal-backdrop#conflictModal` with an `.edit-dialog`, following the finish modal's
markup, handlers and focus trap.

- **Heading:** "This block changed on another device", then the station and date.
- **Table** (`.breakdown-scroll > table.conflict-table`), with columns Field, Your offline
  change, Saved now. Rows cover only the fields the queued body sets: Status, Started,
  Finished, Odometer start, Odometer end, Miles, Stops, Packages, Returns, Base pay.
  - Values that differ are bold.
  - Times go through `formatTime`, and money through `formatMoney`.
- **Buttons:** **Keep saved version** (secondary), which discards, and **Use my change**
  (primary), which resends against the saved version.
- This is not a field-by-field merge (open question 3).

### Sign-out and account page

- **`app.js`, `signOut`:** if `flexbuddyOutbox.count()` for this account is more than 0,
  `openConfirm('2 changes haven't synced', 'Signing out now discards them. Stay signed in to let them sync first.', …, 'Sign out and discard')`.
  On accept: `clear()`, then the existing `clearUserData()` and submit. Otherwise stay.
- **`pwa.js`, `clearUserData`:** also call `window.flexbuddyOutbox?.clear()`, so every path
  that wipes cached data wipes the queue too.
- **`account.html`**, Data card (near the backup status):
  - `<p class="outbox-status" id="outboxStatus" hidden>2 changes waiting to sync</p>`
  - **Sync now** and **Discard all**. Discard all asks first: "Discard 2 unsynced changes?
    This can't be undone."
  - Load `outbox.js`.
  - `account.js` has no queueable forms, and the account page has no sign-out button at
    `0292b67`. Sign-out only happens from the main page, so the guard above is the only one
    needed.

### CSS (`styles.css`)

- `.outbox-strip`:
  - Same width rules as `.offline-banner`, as a panel with `--warning-border` when anything
    needs attention and `--surface` otherwise.
  - At ≤620px: no radius, 18px side padding.
- `.outbox-list li`: `display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px;`.
  Long notes wrap. Buttons are at least 44px (`.text-button` already is).
- `.pending-tag` copies `.edited-tag`, in `--cyan`.
- `.conflict-table td:nth-child(n+2)` is right-aligned, with `b` for differences. The table
  is inside `.breakdown-scroll`, so it scrolls on a phone.

### Service worker

- Add `'/js/outbox.js'` to `VERSIONED_ASSETS`. `StaticAssetsTest` fails otherwise.
- No new `DATA_PATHS`: `/csrf` must never be cached.
- **No `sync` event handler.**

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, `ShiftResponse`, `BlockEvaluationResponse` | **Not touched.** |
| Backup format (v4) | **Not touched.** The queue lives only on the device. It's never backed up, and it's cleared on sign-out. |
| New account data | None on the server. Device data is cleared by `clear()` on sign-out and on `clearUserData`. |
| `sw.js` | `VERSIONED_ASSETS` gets `/js/outbox.js`. `DATA_PATHS` has no change. |
| Boxed request fields | No new request fields. 06a's `expectedUpdatedAt` is only sent on replay. |
| Phone layout | The strip and conflict sheet use `minmax(0, …)` and `.breakdown-scroll`. Every new control is at least 44px. |
| Dates | The queued body is exactly the form's values: the device's local dates via `toIsoDate`, as today. `createdAt` is only used for ordering. |
| Money and tax wording | Not applicable. |

**Existing code that changes:**

| File | Change |
|---|---|
| `shifts.html` | Meta tag, strip, conflict modal, `data-queueable` attributes, `data-online-only` removals, script tag |
| `account.html` | Meta tag, status line and buttons, script tag |
| `app.js` | `apiFetch` (the `offline` flag), `saveEditedShift`, `saveExpense`, `openEditModal`, expense edit gating, `renderShifts` tag, `signOut` |
| `finish.js` | `save`, and the missing-miles row gating |
| `schedule.js` | `bindActions` and the day panel button |
| `quick-actions.js` | `refreshStartRow` |
| `pwa.js` | Offline selector, `clearUserData` |
| `account.js` | The outbox status line and its two buttons |
| `styles.css` | New section |
| `sw.js` | `VERSIONED_ASSETS` |

**Java tests that change:** none. Tests are added below.

## 6. Tests

There's no JavaScript test harness, and adding one is open question 5. To keep the logic
testable later, `outbox.js` puts the drain's decision in a pure function,
`classify(status, redirectedToLogin, body)`, which returns
`'delete' | 'conflict' | 'failed' | 'retry-token' | 'signed-out' | 'stop'`.

**Java tests to add** (about 4):

1. `PageControllerTest.shiftsPageCarriesTheAccountIdAndTheOutbox`:
   - The body contains `<meta name="flexbuddy-account" content="42">`, with
     `findByEmailIgnoreCase` stubbed to a user with id 42.
   - It contains `id="outboxStrip"`, `id="conflictModal"` and `/js/outbox.js?v=`.
   - `#editForm`, `#expenseForm` and `#finishForm` carry `data-queueable`.
   - `#qaAddShift` and `#qaAddExpense` **don't** carry `data-online-only`, but `#qaImport`
     does.
2. `PageControllerTest.theAccountMetaIsEmptyWithoutAUser`: `findByEmailIgnoreCase` returns
   empty, and the content is `""`.
3. `AccountControllerTest.accountPageCarriesTheAccountIdAndTheOutboxScript`, if the account
   page render is tested there. Otherwise add it to whichever test renders `/account`.
4. `StaticAssetsTest` needs no change. It covers `outbox.js` automatically.

## 7. Manual checks

These need Chrome DevTools, a real Android phone with the Play build, or both.

**A. Basic offline add.**

1. Open the dashboard at 390×844. In DevTools, go Offline.
2. Quick actions, then Add expense: type 45.20 and save.
   - The toast reads "Saved offline · will sync".
   - The strip reads "Waiting to sync · 1", with "Fuel · $45.20 · Sep 29 · Pending".
   - The expense list and totals are unchanged.
3. Add a shift: TEST1, today, 9:00–13:00, $80, $10 tips. The strip now reads 2.
4. Go back online. Within about 2 seconds:
   - the toast reads "All changes synced";
   - the strip hides;
   - history shows TEST1 with $90.00;
   - the Expenses screen shows one $45.20 fuel row.

**B. Closed app.** Repeat A in airplane mode on the phone, then **force-close** the app,
turn airplane mode off, and reopen. Both sync within a few seconds of opening, **once
each**. Check the history and expense list for duplicates.

**C. Lost response (lie-fi).**

1. With DevTools throttling set to a custom profile with 25 s latency, add an expense. After
   20 s it queues.
2. Set the network back to normal. It syncs.
3. There is **one** row, even though the first request probably reached the server. That is
   06a's idempotency at work.

**D. Finish offline, then a conflict.**

1. On the phone, open a scheduled block's Finish sheet offline. Enter the odometer (1200.0
   to 1242.5) and save. The row gets a "Pending" tag.
2. On a laptop, open the same block and change its end time. Save.
3. Bring the phone online.
   - The strip reads "Needs your choice".
   - **Choose** shows Finished and Odometer rows, with the differing values bold.
   - **Use my change**: the block shows 42.5 mi and the phone's times.
4. Repeat, choosing **Keep saved version**: the laptop's values stay, and the item is gone.

**E. Expired session.**

1. Queue an item offline.
2. Sign out on another tab, which clears the remember-me token for that series. Or delete
   the session and remember-me cookies in DevTools.
3. Go online. The strip reads "Sign in to finish syncing 1 change". After signing in, it
   syncs.

**F. A different account.** Queue an item as user A. Sign out and choose **Sign out and
discard**: the item is gone. Also try with the guard bypassed in DevTools (clear the session
without the page): sign in as B. A's item shows "From another account", is never sent, and
can be discarded.

**G. Nothing destructive offline.**

- Offline, with a history row open: Delete, Duplicate and Save changes (edit mode) are
  disabled.
- On the Schedule: Start, Cancelled and Forfeited are disabled. Completed and Finish block
  are enabled.
- "No miles" is disabled. Import, account settings, backup and restore are disabled.

**H. Phone layout** at 375×667 and 430×932:

- The strip with 3 items, one of them with a 255-character note, wraps inside the screen,
  and `scrollWidth === innerWidth`.
- The conflict sheet's table scrolls inside its box.
- Every button is at least 44px.

**I. The limit.** Queue 50 items offline (a quick loop in the console with `submit`). The
51st save shows "Too many changes are waiting to sync…".

## 8. Suggested commit message

```
feat(sync): add shifts, expenses and block details while offline

Adding a shift, adding an expense and saving a block from the finish
sheet now work without a connection. The change is kept on the phone,
the toast says it was saved offline, and a Waiting to sync strip lists
what is queued on every screen. When the app is open with a connection
again, the changes are sent in order, each exactly once even if the app
was closed or a response was lost, and the dashboard reloads.

A queued finish carries the version of the block it was made against.
If the block was changed on another device in the meantime, nothing is
overwritten: the strip asks the driver to choose, and a sheet shows
both versions side by side. A change the server refuses stays in the
strip with the reason until it is discarded, and an expired session
keeps the queue until the driver signs in again.

Only these three kinds of change can be queued. Deleting, restoring,
editing in the full dialog, starting a block and everything on the
account page still need a connection. Signing out with changes waiting
asks first, and queued changes are never sent under a different
account.
```

## 9. Open questions

1. **Page-only sync.** Queued changes sync when the app is next open with a connection, not
   while it is closed. Is that acceptable, or do you want Background Sync added later as a
   separate plan?
2. **Add scheduled shift offline.** It's a create, so this plan allows it. Should queueing be
   limited to worked blocks and expenses?
3. **Conflict choice.** The driver keeps one version or the other, not a field-by-field mix.
   Is that right?
4. **The limit of 50 items.** Too low or too high for a long day without signal?
5. **A JavaScript test harness.** The outbox is the most logic-heavy script in the app.
   Should a follow-up plan add `node --test` (built into Node, with no dependencies) for the
   pure functions, such as `classify`, without touching the browser code?
