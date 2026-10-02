# Plan: a first-run setup card on Home, and a one-tap feedback link

Planned against `origin/main` at 89bde5d. Ship after `plan/ci` if
possible: CI's corrected `PostgresMigrationTest` counts migrations, so
the V22 added here needs no edit to that count. Shipped first, the old
hard-coded `5` is wrong either way.

## Where things stand

- A new account opens on Home with `$0.00 earned` and empty cards. Nothing
  tells a new tester what to do first.
- The **time zone** is already handled. `reportTimeZone()` in `app.js`
  (line 2240) saves the browser's zone silently on load and shows a
  toast. So the checklist doesn't need a time-zone step, even though my
  earlier list had one.
- These defaults already work for most drivers:
  - vehicle costs: `STANDARD_MILEAGE` at the default rate;
  - payout days: Tue and Fri, paid 1 day after.

  They can't be "detected" as set, so the card *shows* them with a
  Change link rather than asking the driver to tick them off.
- Feedback today: a plain `mailto:` in Account → Account & privacy ("Questions?
  Email …") and a "Contact support" button on `error.html`. Neither says
  which version or screen the driver was on.
- `PageController` already counts the driver's shifts for `backupDue`
  (`countByOwnerEmailIgnoreCase`, which excludes deleted shifts through
  `@SQLRestriction`).

## 1. Goal and out-of-scope

**Goal:**
- A brand-new driver sees a **Get set up** card at the top of Home with
  five rows:
  1. **Add your first block** (to do / done). It opens the + menu, which
     offers import, add by hand, and so on.
  2. **Set a weekly goal** (to do / done). It opens `/account#goals`.
  3. **Vehicle costs**, showing the current method, with a link to Change.
  4. **Payouts**, showing the current days, with a link to Change.
  5. **Taxes (optional)**, showing `Off` or the current set-aside
     percentage, with a link to Set up or Change. It's labelled as an
     estimate and not tax advice, and never counts towards "N of 2
     done".
- The card counts "N of 2 done". When both are done it says "You're set
  up" with a Done button.
- **Hide** or **Done** dismisses it for good, on every device, because
  it's stored on the server.
- Existing drivers who already have blocks never see it.
- A **Send feedback** link opens the driver's email app, addressed to
  `flexbuddysupport@gmail.com`, with:
  - the subject `FlexBuddy feedback`;
  - a body pre-filled with the app version, the current screen, the device
    type and today's date.

  It appears on Home (always, at the bottom), in the setup card, and in
  Account → Account & privacy. The error page's "Contact support" gets a
  pre-filled subject too.

**Out of scope:**
- A multi-page onboarding wizard or tour.
- A sample-data mode.
- Counting taxes as a step, or nudging a percentage. The taxes row only
  shows the setting and links to it; leaving it Off is a valid finished
  state.
- An in-app feedback form or storing feedback on the server.
- Screenshots in feedback emails.
- Changing any default setting value.

## 2. Data model and migration

New `src/main/resources/db/migration/V22__setup_checklist.sql`:

```sql
alter table app_users add column setup_dismissed_at timestamp(6) with time zone;
-- Drivers who already logged blocks are set up; only accounts without any see the card.
update app_users set setup_dismissed_at = now()
where exists (select 1 from shift where shift.owner_id = app_users.id);
```

The `exists` includes soft-deleted shifts on purpose: anyone who has ever
logged a block isn't new. Don't edit any existing migration.

`model/AppUser.java`: add `private Instant setupDismissedAt;` after
`lastBackupAt`. It's nullable with no annotation, like `lastBackupAt`.
The class uses Lombok `@Getter` and `@Setter`, so no accessors need
writing.

**Backup, restore and deletion:**
- `setup_dismissed_at` is screen state, not driver data, so it is **not**
  added to `BackupSettings`. The backup format stays version 4.
- Restore doesn't touch it. A backup restored into a fresh account makes
  "Add your first block" read done, because the server count is above 0;
  only the goal row remains.
- Account deletion removes the row with the user, so no change is needed.

## 3. Backend files

- **`service/AccountSettingsService.java`:** add

  ```java
  @Transactional
  public void dismissSetup(String email) {
      AppUser user = /* same lookup as the other update methods */;
      if (user.getSetupDismissedAt() == null) user.setSetupDismissedAt(Instant.now(clock));
  }
  ```

  It's idempotent: the first timestamp is kept. The service has no
  `Clock` today, so add the existing `Clock` bean to its constructor, the
  way `PageController` takes it. Update the test setup in
  `AccountSettingsServiceTest` to match.
- **`controller/AccountController.java`:** add, after `updateTimeZone`:

  ```java
  @PostMapping("/account/setup/dismiss")
  public ResponseEntity<Void> dismissSetup(Principal principal) {
      settingsService.dismissSetup(principal.getName());
      return ResponseEntity.noContent().build();
  }
  ```

  It needs sign-in and CSRF, which is the default.
- **`controller/PageController.java`**, in `shiftsPage`, inside the
  existing `ifPresent`:
  - compute `long shiftCount = shiftRepository.countByOwnerEmailIgnoreCase(user.getEmail());`
    once, and use it for both `backupDue` and the new attributes, so
    there's still only one count query;
  - `model.addAttribute("setupDue", user.getSetupDismissedAt() == null);`
  - `model.addAttribute("hasBlocks", shiftCount > 0);`
- `AccountSettingsResponse` is **not** changed: no new field and no
  constructor changes. The card gets its two flags from the page and the
  settings from the existing `GET /account/settings`.

## 4. Frontend files

### `templates/shifts.html`

- **Setup card:** in `#homeScreen`, between `.home-greeting` (ends at
  line 62) and `#goalCard`, add:

  ```html
  <article class="home-card home-setup" id="setupCard" th:if="${setupDue}"
           th:attr="data-has-blocks=${hasBlocks}" aria-labelledby="setupTitle">
      <div class="setup-heading">
          <h2 class="home-label" id="setupTitle">Get set up</h2>
          <span class="setup-progress" id="setupProgress"></span>
      </div>
      <ul class="setup-list" id="setupList"></ul>
      <div class="setup-actions">
          <button class="primary-button compact-button" id="setupDone" type="button" hidden>Done</button>
          <button class="text-button" id="setupHide" type="button">Hide</button>
          <a class="text-link" href="mailto:flexbuddysupport@gmail.com" data-feedback>Send feedback</a>
      </div>
  </article>
  ```

- **Home feedback link:** after the `.home-recent` article (ends at line
  151), still inside `#homeScreen`, add:

  ```html
  <p class="home-feedback"><a class="text-link" href="mailto:flexbuddysupport@gmail.com" data-feedback>Send feedback</a></p>
  ```

- **Scripts:** add these after `outbox.js` (line 869) and before
  `home.js`, in this order:

  ```html
  <script th:src="@{/js/account-sections.js(v=${buildId})}" defer></script>
  <script th:src="@{/js/feedback.js(v=${buildId})}" defer></script>
  <script th:src="@{/js/setup.js(v=${buildId})}" defer></script>
  ```

  `account-sections.js` is safe on this page: it touches nothing at load
  time.

### `templates/account.html`

- In the "Your data and terms" card (`legal-summary-card`), replace
  `<p class="legal-contact">Questions? Email <a href="mailto:flexbuddysupport@gmail.com">flexbuddysupport@gmail.com</a>.</p>`
  with:

  ```html
  <p class="legal-contact">Questions or ideas? <a class="text-link" href="mailto:flexbuddysupport@gmail.com" data-feedback>Send feedback</a> or email flexbuddysupport@gmail.com.</p>
  ```

- Add `<script th:src="@{/js/feedback.js(v=${buildId})}" defer></script>`
  after `account-sections.js` (line 345).

### `templates/error.html`

Give the "Contact support" link a pre-filled subject and a one-line
body. Build them server-side, because there's no JS on this page. Use no
`T(...)` static calls, which Thymeleaf's restricted mode can refuse:

```html
<a class="secondary-button error-support-link"
   th:href="|mailto:flexbuddysupport@gmail.com?subject=${#uris.escapeQueryParam('FlexBuddy problem report')}&body=${#uris.escapeQueryParam('What were you doing when this happened? (error ' + (status ?: 'unknown') + ')')}|"
   href="mailto:flexbuddysupport@gmail.com">Contact support</a>
```

### `static/js/account-sections.js`

- Split the method part out of `costs()` into
  `function costMethod(settings)`, which returns `'Actual expenses'` or
  `` `Standard mileage · ${rate}/mi` ``. `costs()` then calls it. Its
  output must stay byte-for-byte the same, because existing tests cover
  it.
- Export both: `window.flexbuddyAccountSections = {sectionForHash, summaries, costMethod, payouts};`.
  `payouts` is the existing function, now exported.

### `static/js/feedback.js` (new)

Pure helpers first, then wiring:

- `platformLabel(userAgent)` returns `'Android'` when the user agent
  matches `/Android/`, `'iPhone'` for `/iPhone/`, `'iPad'` for `/iPad/`,
  `'Windows'` for `/Windows/` and `'Mac'` for `/Macintosh/`, and
  `'Other'` otherwise.
- `screenLabel(pathname, search)` returns:
  - `'Account'` for `/account`;
  - otherwise the `screen` parameter mapped `dashboard`/`home` → `Home`,
    `reports` → `Reports`, `schedule` → `Schedule`, `import` → `Import`
    and `expenses` → `Expenses`;
  - `'Home'` when the parameter is absent;
  - `'Other'` for anything else.
- `localIsoDate(date)` builds `YYYY-MM-DD` from `getFullYear`,
  `getMonth` and `getDate`. **Never `toISOString()`.**
- `feedbackMailto({buildId, screen, platform, installed, date})` returns

  ```
  mailto:flexbuddysupport@gmail.com?subject=FlexBuddy%20feedback&body=<encoded>
  ```

  The body, before `encodeURIComponent`, is:

  ```
  What happened, or what would help?



  —
  App version: <buildId>
  Screen: <screen>
  Device: <platform> · <installed ? 'installed app' : 'browser'>
  Date: <YYYY-MM-DD>
  ```

  Use `\n` line breaks. Encode the subject and the body each with
  `encodeURIComponent`, not `URLSearchParams`, which writes `+` for
  spaces, and some mail apps show that literally.
- Wiring, which runs only when `document.addEventListener` exists:
  - **Delegated click:** a single `click` listener on `document` for
    `a[data-feedback]`. It sets `link.href` to a freshly built mailto
    just before the browser follows it, so the screen is the current one.
    Don't call `preventDefault`; the browser opens the link as normal.
  - **Build id:** from `document.currentScript?.src`'s `v` parameter,
    read once at load, or `'unknown'`.
  - **Installed:** `window.flexbuddyPwa?.isStandalone?.() ?? false`.
    `pwa.js` already exports it, and the Play app's Trusted Web Activity
    counts as standalone.
- Export `window.flexbuddyFeedback = {platformLabel, screenLabel, localIsoDate, feedbackMailto}`.

### `static/js/setup.js` (new)

- **Pure helper** `setupState({hasBlocks, settings})`, where `settings` may
  be null while loading, returns:

  ```js
  {
    rows: [
      {key: 'block', text: 'Add your first block', detail: 'Import a screenshot, add one by hand, or restore a backup in Account', done: hasBlocks, action: hasBlocks ? null : 'Add'},
      {key: 'goal', text: 'Set a weekly goal', detail: 'See your progress on Home each week', done: goalSet, action: goalSet ? null : 'Set goal'},
      {key: 'costs', text: 'Vehicle costs', detail: costMethod(settings) /* or 'Loading…' */, review: true, action: 'Change'},
      {key: 'payouts', text: 'Payouts', detail: payouts(settings) /* or 'Loading…' */, review: true, action: 'Change'},
      {key: 'taxes', text: 'Taxes (optional)', detail: taxDetail(settings), review: true, action: taxOn ? 'Change' : 'Set up'}
    ],
    doneCount, total: 2, complete: doneCount === 2,
    progress: complete ? "You're set up" : `${doneCount} of 2 done`
  }
  ```

  `goalSet` is `settings?.weeklyGoal != null || settings?.monthlyGoal != null`.
  A monthly goal counts as a goal. While `settings` is null, `goalSet` is
  false and the review rows say `Loading…`.

  `taxOn` is `settings?.taxSetAsidePercent != null`. Pure helper
  `taxDetail(settings)` returns:
  - `'Loading…'` while `settings` is null;
  - `'Off · an estimate to help you save, not tax advice'` when off;
  - `` `${Number(settings.taxSetAsidePercent)}% set aside · estimate, not tax advice` ``
    when on, for example `25% set aside · estimate, not tax advice`.

  Keep `taxDetail` local to `setup.js` rather than reusing
  `account-sections.js`'s `taxes()`. That one adds the reminder state and
  leaves out the not-advice wording this row needs. The taxes row never
  changes `doneCount`, `total` or `complete`.
- **Wiring** (only when `#setupCard` exists):
  - If `localStorage['flexbuddy-setup-hidden'] === '1'` (read inside
    try/catch), remove the card and retry the dismiss POST quietly when
    online, then return. This covers a Hide pressed while offline, and the
    service worker serving a cached `/` page that still has the card.
  - Fetch the settings with `apiFetch('/account/settings')`, which is
    cached offline through `DATA_PATHS`. Render once before the response
    and again after it.
  - **Rows:** each row is a `<li>` holding a `<button class="setup-row">`
    with:
    - a status mark: `✓` for done, `○` for to do, nothing for review rows;
    - the text, with the detail as `<small>`;
    - the action in `<b>`.

    Done rows have no button: a plain `<div class="setup-row is-done">`.
    Use `escapeHtml` from `app.js` at call time, the same way `home.js`
    does.
  - **Actions:**
    - `block` calls `window.flexbuddyQuickActions.open()`;
    - `goal` goes to `/account#goals`;
    - `costs` goes to `/account#costs`;
    - `payouts` goes to `/account#payouts-section`;
    - `taxes` goes to `/account#taxes`, which `sectionForHash` already
      maps to the Taxes section.

    Those three hashes already open the right section through
    `sectionForHash`.
  - When `complete`, show `#setupDone` and hide `#setupHide`.
  - **Dismissing** (Hide and Done do the same thing):
    1. Set the localStorage flag and remove the card at once.
    2. `POST /account/setup/dismiss` with `csrfHeaders()` through
       `apiFetch`.
    3. On success, clear the flag.
    4. On failure, keep the flag. The next online load retries.

    No toast: the card simply goes away. Move focus to `#homeGreeting`
    (add `tabindex="-1"` to that `h1` in the template) so keyboard focus
    isn't lost.
  - Export `window.flexbuddySetup = {setupState, update}`. `update({hasBlocks})`
    re-renders with a new `hasBlocks`.
- **`home.js` change:** in `load()`, after `renderRecent(...)`, call
  `window.flexbuddySetup?.update({hasBlocks: shifts.value.length > 0})`
  when `shifts.status === 'fulfilled'`. Adding the first block then ticks
  the row on the next Home refresh, with no full page reload. Add
  `flexbuddySetup` to the list of helpers in `home.js`'s header comment.

### `static/css/styles.css`

Add the following after the `.home-attention` rules (around line 2480).
Use the existing tokens only:

```css
.home-setup { background: var(--primary-soft); border-color: var(--cyan-dark); }
.setup-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.setup-progress { color: var(--muted); font-size: 13px; }
.setup-list { margin: 8px 0 0; padding: 0; list-style: none; }
.setup-row { width: 100%; min-height: 48px; padding: 6px 0; display: flex; align-items: center; gap: 12px; color: var(--text); background: none; border: 0; border-top: 1px solid var(--line-soft); font: inherit; text-align: left; cursor: pointer; }
.setup-list li:first-child .setup-row { border-top: 0; }
.setup-row > span { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.setup-row small { color: var(--muted); overflow-wrap: anywhere; }
.setup-row b { min-height: 44px; display: inline-flex; align-items: center; color: var(--cyan); font-size: 13px; white-space: nowrap; }
.setup-row.is-done { cursor: default; color: var(--muted); }
.setup-mark { width: 20px; flex: none; text-align: center; color: var(--cyan); }
.setup-actions { margin-top: 8px; display: flex; flex-wrap: wrap; align-items: center; gap: 4px 16px; }
.setup-actions > * { min-height: 44px; }
.home-feedback { margin: 4px 0 0; text-align: center; }
.home-feedback a, .legal-contact a[data-feedback] { min-height: 44px; display: inline-flex; align-items: center; }
```

Check the light theme. `--primary-soft` and `--cyan-dark` already have
light values; if `--cyan-dark` on the light surface fails contrast for
the border, use `var(--line)`.

### `static/sw.js`

Add `'/js/feedback.js'` and `'/js/setup.js'` to `VERSIONED_ASSETS`.
`account-sections.js` is already listed. `/account/settings` is already
in `DATA_PATHS`. The dismiss POST isn't intercepted.
`StaticAssetsTest` fails if a script tag isn't listed.

## 5. Ripple list

- **Shared records:** none of `AccountSettingsResponse`, `BackupShift`,
  `BackupSettings`, `AccountBackupFile` (v4), `BlockEvaluationResponse` or
  `ShiftResponse` changes.
- **New account data** (`setup_dismissed_at`): left out of the backup on
  purpose, ignored by restore, and removed by deletion (see section 2).
- **`PageController`:** still one count query. `backupDue` behaves exactly
  as before.
- **`home.js`:** one optional call, guarded with `?.`. Home works if
  `setup.js` fails to load.
- **`account-sections.js`:** now loaded on the main page too, and gains
  two exports with no behaviour change.
- **Service worker:** two new versioned assets, and the build id refreshes
  caches. A cached `/` shell can show a dismissed card offline, which the
  localStorage flag covers.
- **Phone layout:**
  - every action is at least 44px tall, and rows are at least 48px;
  - long details wrap (`overflow-wrap: anywhere`, `min-width: 0` on the
    text span), so nothing widens the page at 375px;
  - the card adds about 310px above the week card, for new drivers only.
- **Dates:** the feedback date is the device's local date and never comes
  from `toISOString()`. The server stamps `setup_dismissed_at` with the
  `Clock` as an instant.
- **Money and tax:**
  - The costs row shows the existing rate text.
  - The taxes row shows only the driver's own percentage, or Off, with
    "estimate, not tax advice" in its text.
  - No percentage is suggested or pre-filled, and no new figures are
    calculated.
  - Leaving taxes Off never blocks "You're set up".
- **The CI migration test:** with `plan/ci` shipped, V22 is picked up
  automatically. Add a V22 assertion (section 6).

## 6. Tests to add

- **`AccountSettingsServiceTest`:**
  - `dismissSetup` sets `setupDismissedAt` to the clock's instant.
  - A second call keeps the first timestamp.
- **`AccountControllerTest`:**
  - `POST /account/setup/dismiss` with a user and CSRF returns 204, and
    `dismissSetup` is called with the user's email.
  - Without CSRF it returns 403.
- **`PageControllerTest`:**
  - A user with `setupDismissedAt == null` and no shifts gets a page with
    `id="setupCard"` and `data-has-blocks="false"`.
  - A user with a dismissed setup gets a page without `setupCard`.
  - With 3 shifts, the page has `data-has-blocks="true"`.
  - The Home page always contains a `data-feedback` link to
    `mailto:flexbuddysupport@gmail.com`.
- **`AccountDeletionJpaTest`:** a user with `setupDismissedAt` set is
  deleted as before. This pins that the new column doesn't block deletion.
- **`PostgresMigrationTest`**, a new `assertSetupBackfill(schema)`. The
  legacy user inserted at V3 has a shift, so after migrating its
  `setup_dismissed_at` is not null. Insert a second user without shifts
  in `insertLegacyRow`; its `setup_dismissed_at` stays null. This pins the
  backfill rule.
- **`src/test/js/setup.test.js`**, new; load `account-sections.js` first,
  then `setup.js`, into the same context:
  - `setupState({hasBlocks: false, settings: null})` gives
    `0 of 2 done`, and the review rows show `Loading…`.
  - With settings `{weeklyGoal: 400, vehicleCostMethod: 'STANDARD_MILEAGE', mileageRate: 0.7, payoutDays: ['TUESDAY','FRIDAY'], payoutLagDays: 1}`
    and `hasBlocks: true`:
    - `complete` is true and `progress` is `You're set up`;
    - the costs detail is `Standard mileage · $0.70/mi`;
    - the payouts detail is `Tue & Fri · paid 1 day after`.
  - `monthlyGoal: 1600` alone counts as goal set.
  - The taxes row:
    - With `taxSetAsidePercent: null`, its detail is
      `Off · an estimate to help you save, not tax advice` and its action
      is `Set up`.
    - With `25`, its detail is
      `25% set aside · estimate, not tax advice` and its action is
      `Change`.
    - In both cases `total` stays 2. With block and goal done and tax
      Off, `complete` is true.
  - `ACTUAL_EXPENSES` shows `Actual expenses`.
- **`src/test/js/feedback.test.js`**, new:
  - `feedbackMailto` starts with
    `mailto:flexbuddysupport@gmail.com?subject=FlexBuddy%20feedback&body=`.
  - Its decoded body contains `App version: abc123`, `Screen: Reports`,
    `Device: Android · installed app` and `Date: 2026-10-02`, for
    `new Date(2026, 9, 2, 23, 30)`. That's late evening local time and
    must not roll to the next day.
  - The body contains no `+` from encoding.
  - `screenLabel('/', '?screen=dashboard')` is `Home`, `('/account', '')`
    is `Account`, and `('/', '?screen=zzz')` is `Other`.
  - `platformLabel` returns the expected label for a sample Android
    Chrome, iPhone Safari and Windows user agent.
- **`src/test/js/account-sections.test.js`:** add
  `costMethod({vehicleCostMethod: 'STANDARD_MILEAGE', mileageRate: 0.7})`
  equals `Standard mileage · $0.70/mi`. The existing costs-summary tests
  must pass unchanged.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

Start with a fresh account and no blocks.

### Desktop (1280×800)

1. Register a new account. Home shows **Get set up** above the week card,
   with:
   - `0 of 2 done`;
   - five rows: Add your first block / Set a weekly goal / Vehicle costs
     `Standard mileage · $0.70/mi` (or the current default rate) /
     Payouts `Tue & Fri · paid 1 day after` / Taxes (optional)
     `Off · an estimate to help you save, not tax advice` with
     **Set up**;
   - Hide and Send feedback underneath.
2. Click **Add**. The + menu opens. Add a block by hand with today's date,
   09:00–13:00, $80. Back on Home, the first row shows ✓ and the card says
   `1 of 2 done`, without a reload.
3. Click **Set goal**. Account opens with Earnings & costs unfolded. Set a
   weekly goal of $400, then go back to Home. The card says
   `You're set up` and shows **Done** instead of Hide.
4. Before clicking Done, click **Set up** on the taxes row. Account opens
   with Taxes unfolded. Set 25% and go back to Home. The row reads
   `25% set aside · estimate, not tax advice` with **Change**, and the
   count still says `You're set up`. (Leaving taxes Off also reaches
   `You're set up`, as step 3 shows.)
5. Click **Done**. The card disappears and focus lands on the greeting.
   Reload: it stays gone. Sign in on another browser: it's gone there
   too.
6. An existing account that had blocks before the deploy never shows the
   card.
7. Click **Send feedback** at the bottom of Home while on Reports
   (`?screen=reports`). The mail client opens a message to
   flexbuddysupport@gmail.com with:
   - subject `FlexBuddy feedback`;
   - a body ending with `App version: <the build id in the page's script URLs>`,
     `Screen: Reports`, `Device: Windows · browser` (or Mac) and
     `Date: <today>`.
8. Account → Account & privacy shows "Questions or ideas? Send feedback …".
   The link works the same way, with `Screen: Account`.
9. Open `/does-not-exist`. On the 404 page, **Contact support** opens an
   email with the subject `FlexBuddy problem report`.

### Phone (375×667 and 430×932 in dev tools, then a real Android phone in the Play app)

10. On a new account, the setup card fits with no horizontal scroll:
   `document.documentElement.scrollWidth` equals `innerWidth` at both
   widths.
11. Check sizes in dev tools:
    - each row is at least 48px tall;
    - Add, Set goal and Change are at least 44px tall;
    - Hide and Send feedback are each at least 44px tall.
12. At 375px, the payout and taxes details wrap within the card rather
    than pushing Change or Set up off-screen.
13. In the Play app on Android, tap **Send feedback**. Gmail opens a
    compose screen with the subject and body filled in, with
    `Device: Android · installed app`.
14. Turn on airplane mode and tap **Hide**. The card disappears. Close and
    reopen the app, still offline: the card stays hidden. Go back online
    and reopen: the card stays hidden, and on desktop, with the same
    account, it's gone too, which shows the queued dismiss reached the
    server.
15. In light mode, the card's border and text are readable. Compare with
    the Needs attention card.

## 8. Commit message

```
feat(home): add a setup card for new drivers and a feedback link

A new account opened on an empty Home with nothing saying what to do
first. New drivers now see a Get set up card at the top of Home: add
a first block, set a weekly goal, and check the vehicle cost method
and payout days that FlexBuddy assumes, and optionally turn on a tax
set-aside, shown as an estimate and not advice. The first two tick
themselves off from real data, and only they are counted. Hide or Done dismisses
it on the server so it stays gone on every device, and a dismissal
made offline is retried. Drivers who already logged blocks are marked
as set up by the migration and never see it.

Feedback took effort to send, so most of it never was. A Send
feedback link on Home, in the setup card and in Account opens an
email to the support address with the app version, screen, device
type and date filled in. The error page's support link now carries a
subject too. The time zone needs no step because the app already
saves the device's zone on its own.
```

## 9. Open questions

1. ~~Are the four rows right?~~ **Decided:** five rows. Taxes is an
   optional review row, never counted, labelled as an estimate and not
   advice.
2. ~~Should feedback be its own commit?~~ **Decided:** bundled with the
   setup card in one commit.
3. **Placement of the Home feedback link:** it's at the very bottom of
   Home. A link in the header would be more visible but adds a third
   round button next to the theme toggle and account. I'd keep the header
   as it is.
