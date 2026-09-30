# Layout 3 of 4: fold the account page into sections

Planned against `origin/main` at `eef0595`. **No migration and no backend change.** This plan
doesn't depend on plans 1 and 2, but it ships after them in the agreed order.

## 1. Goal and scope

Today the account page is about **8½ phone screens** (7,241 px at 390 px wide). It's
**11 cards, one after another**, each with its own label, title, description and Save
button, and settings are mixed in with data actions.

This plan groups those 11 cards into **six sections that start folded**. The first screen
becomes a short list, where each row shows the section's name and its current state.
Tapping a row opens it, with the existing forms inside.

| # | Section (`id`) | Cards inside, unchanged | One-line summary when folded |
|---|---|---|---|
| 1 | **Earnings & costs** (`costs`) | Net earnings settings; Earnings goals (`#goals`) | "Standard mileage · $0.70/mi · Goal $400/wk" |
| 2 | **Taxes** (`taxes-section`) | Tax set-aside (`#taxes`) | "25% set aside · reminders on", or "Off" |
| 3 | **Payouts** (`payouts-section`) | Payout schedule (`#payouts`) | "Tue & Fri · paid 1 day after" |
| 4 | **Reminders & calendar** (`reminders`) | Reminders, including the calendar feed | "1 hour before · confirm on · miles off" |
| 5 | **Backup & restore** (`backup`) | Download all account data; Restore from a backup | "Last backup: Sep 14", or "Never backed up" |
| 6 | **Account & privacy** (`account`) | **Sign out** (new); Signed-in devices; Your data and terms; Delete account | The display name and email |

**Also:**

- **Replace the title banner** ("Account · Export and recovery · Keep a copy of your
  history…") with one line: **"Account"**, then "Angel · angel@example.com" in muted text.
- **Add a Sign out button** in section 6, using the same guarded sign-out as the main page
  (see section 4). Plan 4 then removes sign-out from the main page's header.
- **Deep links open their section.** `/account#taxes`, `#goals`, `#payouts` and `#backup`,
  and any section id, open the right section and scroll to it. Existing links already use
  `#goals` and `#taxes`, and Home (plan 2) links to `#backup`.
- **One section can be open at a time on phones**, so the page stays short. On desktop
  several can be open.

### Out of scope

- Changing any form, field, validation or save behaviour inside the cards.
- New settings.
- The main page's header (plan 4).
- Light-theme restyling beyond using the existing tokens.

## 2. Data model and migration

None.

## 3. Backend

None. `AccountController`'s `/account` model already provides `currentUser`, with
`displayName` and `email`, and `currentUser.lastBackupAt`.

## 4. Frontend

### `templates/account.html`

**Header block.** Replace `section.hero.account-hero` with:

```html
<header class="account-heading">
  <h1>Account</h1>
  <p th:text="${currentUser.displayName + ' · ' + currentUser.email}">Angel · angel@example.com</p>
</header>
```

**Sections.** Replace `section.account-grid` with a list of six `<details>`:

```html
<div class="settings-sections">
  <details class="settings-section" id="costs">
    <summary>
      <span class="settings-section-title">Earnings &amp; costs</span>
      <span class="settings-section-summary" data-summary="costs">…</span>
      <svg class="settings-section-chevron" aria-hidden="true">…</svg>
    </summary>
    <div class="settings-section-body">
      …the existing "Net earnings settings" article, then the existing "Earnings goals" article (id="goals")…
    </div>
  </details>
  …the other five sections, following the table…
</div>
```

**Inside each section body:**

- **The cards move as they are,** with every `id`, `form` and button unchanged, so
  `account.js`, `tax.js` and the outbox line from 06b keep working.
- **Card labels:** remove each card's small label (`<p class="step-label">…</p>`), because
  the section title replaces it. Keep each card's `<h2>`, **demoted to `<h3>`** so the
  heading order stays h1 → section title → card title.
- **Section titles:** the `summary` text is not a heading. Give each `<details>`
  `aria-labelledby` pointing at its title span. **Don't** put an `<h2>` inside `<summary>`,
  since some screen readers mis-announce headings there.
- **Descriptions:** keep them where they explain a choice, for example the tax disclaimer,
  which must stay by the rules for money figures. Cut them where they repeat the title.
  Specifically:
  - **"Net earnings settings":** keep its first sentence only.
  - **"Download all account data":** keep it.
  - **"Your data and terms":** keep its links, and drop the heading-level sentence.
  - **The tax card:** keep its full text, including "These are estimates, not tax advice".

**Section 6, Account & privacy:**

- **New, at the top:**

  ```html
  <form id="accountSignOutForm" th:action="@{/logout}" method="post">
    <button class="secondary-button" type="submit">Sign out</button>
  </form>
  ```

- Then the existing Signed-in devices card, Your data and terms card and Delete account
  card, in that order. Delete account stays last and keeps its danger styling.

**Header bar:** the back button currently reads "Back to dashboard". Change its
`aria-label` to "Back to Home", to match plan 2.

### New file `static/js/account-sections.js` (pure helpers, testable with `node --test`)

It exposes `window.flexbuddyAccountSections = {sectionForHash, summaries}`. Add it to
`account.html`'s scripts **before `account.js`**, and to `VERSIONED_ASSETS`. It touches no
page elements at load.

- **`sectionForHash(hash)`** maps `#goals` → `costs`, `#taxes` → `taxes-section`,
  `#payouts` → `payouts-section`, `#backup` → `backup`, and any section id to itself.
  Anything else returns `null`.
- **`summaries(settings, extra)`** returns `{costs, taxes, payouts, reminders, backup, account}`
  strings, built from the `/account/settings` JSON the page already loads.
  `extra = {lastBackupAt, displayName, email, remindTax}`.
  - **costs:**
    - "Standard mileage · $0.70/mi", or "Actual expenses";
    - plus " · Goal $400/wk" when there's a weekly goal, else " · Goal $1,600/mo" when
      there's only a monthly goal, else nothing.
  - **taxes:** "25% set aside", plus " · reminders on" when `remindTax` is true. With no
    percentage, "Off".
  - **payouts:** the day names joined with " & " (for example "Tue & Fri"), plus " · paid N
    day(s) after". N is `payoutLagDays`; use "day" when it's 1 and "days" otherwise.
  - **reminders:**
    - the lead time as "30 min" / "1 hour" / "2 hours" / "12 hours" before, or "No
      reminder before blocks";
    - then " · confirm on" or " · confirm off";
    - then " · miles on" or " · miles off".
  - **backup:** "Last backup: Sep 14", formatted in the device's locale from the stored
    instant, or "Never backed up".
  - **account:** "Angel · angel@example.com".

  Money is formatted with `Intl.NumberFormat('en-US', {style: 'currency', currency: 'USD'})`,
  trimming ".00", as the dashboard does for whole goals.

### `static/js/account.js`

- **After each render function** (`renderSettings`, `renderPayouts`, `renderGoals`,
  `renderReminders`, the tax loader and the backup status), **and after every successful
  save**, call `updateSectionSummaries()`. It writes
  `flexbuddyAccountSections.summaries(latestSettings, extra)` into each
  `[data-summary]` span. A save updates the summary immediately, and the section stays
  open.
- **Opening sections:**
  - **On load and on `hashchange`,** open the section `sectionForHash(location.hash)`
    returns (`details.open = true`), then scroll the **card** named in the hash, or the
    section itself, into view with `block: 'start'`.
  - **On phones** (`matchMedia('(max-width: 620px)')`), opening one section closes the
    others. Use the `toggle` event, and ignore it while applying a hash.
  - **When nothing is in the hash,** all sections start closed.
- **Sign out on the account page.** `account.js` already guards "Sign out everywhere"
  (`#signOutEverywhereForm`, line ~301): if the outbox has changes, it asks with
  `window.confirm("N changes haven't synced… Sign out anyway?")`, clears the outbox, then
  submits. **Turn that into one function, `guardedSignOut(form)`, and use it for both
  forms**: the new `#accountSignOutForm` and `#signOutEverywhereForm`.
  - Keep the `window.confirm` wording as it is. Don't add a dialog.
  - **Also call `await window.flexbuddyPwa?.clearUserData()`** before submitting. The main
    page's sign-out clears the device's cached data, but the existing "sign out everywhere"
    here doesn't, so a second person signing in on the same phone could briefly see cached
    figures. This fixes it for both forms.

### `static/css/styles.css`

Add a section `/* Account sections */`.

- **`.account-heading`:** `padding: 20px 16px 8px;`. The `h1` is 26px. The `p` is muted,
  14px, and **wraps**: `overflow-wrap: anywhere`, because long email addresses must not
  widen the page.
- **`.settings-sections`:** `display: grid; gap: 10px; width: min(760px, 100%); margin: 0 auto; padding: 0 16px calc(40px + env(safe-area-inset-bottom));`.
  It's a single column at every width, and a readable 760px on desktop.
- **`.settings-section`:** a surface card with a border and a 12px radius, with
  `min-width: 0`, as for the account grid children.
- **`summary`:**
  - `display: grid; grid-template-columns: minmax(0, 1fr) 20px; gap: 2px 10px; min-height: 64px; padding: 12px 16px; cursor: pointer; list-style: none;`
  - The title is 16px bold, and the summary is 13px muted with ellipsis, both in the first
    column. The chevron sits in the second column and rotates 180° when open.
  - `summary::-webkit-details-marker { display: none; }`
  - `:focus-visible` gets the existing focus ring.
- **`.settings-section-body`:**
  - `padding: 0 16px 16px; display: grid; gap: 14px;`
  - The cards inside **lose their own panel border and background**
    (`.settings-section .data-card { border: 0; background: none; padding: 0; }`), so
    they're not a card inside a card.
  - A 1px `var(--line-soft)` divider sits between two cards in the same section.
- The danger styling of Delete account is kept.
- Remove `.account-grid` rules only if nothing else uses them. The `min-width: 0` rule
  carries over to `.settings-section`.

### `static/sw.js`

`VERSIONED_ASSETS` gets `/js/account-sections.js`. `/account` stays a shell page, cached as
today.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format (v4), `BlockEvaluationResponse`, `ShiftResponse` | **Not touched.** They're only read. |
| New account data | None. |
| `sw.js` | `VERSIONED_ASSETS` gets `/js/account-sections.js`. |
| Boxed request fields | Not applicable. The forms and requests are unchanged. |
| Phone layout | Summary rows are at least 64px. Forms keep their 44px controls. The email wraps. `.settings-section` has `min-width: 0`, as the account grid children needed. |
| Dates | The backup date is formatted from the stored instant with `toLocaleDateString`. No `toISOString`. |
| Money and tax wording | The tax card keeps its full "estimates, not tax advice" text. Summaries show the driver's own settings only. |

**Existing code that changes:**

| File | Change |
|---|---|
| `account.html` | Header block, six `<details>` wrapping the unchanged cards, labels removed, `h2` → `h3`, sign-out form |
| New `account-sections.js` | Pure helpers |
| `account.js` | Summaries after render and save, hash opening, one-open-on-phones, the shared `guardedSignOut` (which now also clears cached data) |
| `styles.css` | New section; `.account-grid` and `.account-hero` removed if unused |
| `sw.js` | `VERSIONED_ASSETS` |

**Links into the account page that must land correctly** (section 7): `/account#goals`
(the goal card's "Change goals"), `/account#taxes` (the tax tile), `/account#backup` (Home's
attention row), and `/account` from the header's name link.

## 6. Tests to add or change

**`controller/AccountControllerTest.java`.** It already renders `/account` at lines ~196
and ~211, so add these alongside:

1. `accountPageHasSixFoldedSections`:
   - the body contains `id="costs"`, `id="taxes-section"`, `id="payouts-section"`,
     `id="reminders"`, `id="backup"` and `id="account"`, **each on a `<details` element**,
     and none with `open`;
   - the old anchors `id="goals"`, `id="taxes"` and `id="payouts"` are still present.
2. `accountPageHasASignOutForm`: `id="accountSignOutForm"`, posting to `/logout`, with the
   CSRF field.
3. `theTitleBannerIsGone`: the body doesn't contain "Export and recovery". It does contain
   the display name and email line.
4. `/js/account-sections.js?v=` is present. `StaticAssetsTest` also checks it's precached.

**New `src/test/js/account-sections.test.js`:**

5. `sectionForHash`: `#goals` gives `costs`; `#taxes` gives `taxes-section`; `#backup`
   gives `backup`; `#reminders` gives `reminders`; `#nope` and `''` give `null`.
6. `summaries.costs`:
   - standard mileage with `mileageRate` 0.7 and weekly goal 400 gives "Standard mileage ·
     $0.70/mi · Goal $400/wk";
   - actual expenses with only a monthly goal of 1600 gives "Actual expenses · Goal
     $1,600/mo".
7. `summaries.taxes`: 25 with `remindTax` true gives "25% set aside · reminders on"; `null`
   gives "Off".
8. `summaries.payouts`: `['TUESDAY', 'FRIDAY']` with lag 1 gives "Tue & Fri · paid 1 day
   after", and lag 2 gives "… 2 days after".
9. `summaries.reminders`: 60, true, false gives "1 hour before · confirm on · miles off";
   `null` gives "No reminder before blocks · …".
10. `summaries.backup`: `null` gives "Never backed up". An instant gives "Last backup: " plus
    a locale date. Run it with `TZ=America/Los_Angeles`, and check that
    `2026-09-15T02:00:00Z` shows **Sep 14**, the local date.

## 7. Manual checks

Use the local test account at 390×844 and 1280×800.

1. **Length.** On a phone, with every section closed, the page is **at most about
   1,000 px** (it was 7,241). The first screen shows the heading and all six rows with their
   summaries.
2. **Summaries match the forms.** Open each section and compare, for example "Standard
   mileage · $0.70/mi · Goal $400/wk".
3. **Saving updates the summary.** In Taxes, change 25 to 30 and save. The row reads "30%
   set aside" straight away, and the section stays open.
4. **One open at a time on phones.** Open Taxes, then Payouts: Taxes closes. On desktop both
   stay open.
5. **Deep links:**
   - `/account#goals` opens Earnings & costs and scrolls to the goals card;
   - `/account#taxes` opens Taxes;
   - `/account#backup` opens Backup & restore;
   - from the main page, the goal card's "Change goals" link lands on the goals form.
6. **Everything inside still works:**
   - save each form once;
   - the calendar feed link's "Create calendar link";
   - push reminders on and off;
   - download a backup;
   - restore preview and undo;
   - sign out everywhere;
   - the delete account form's validation.
7. **Sign out:**
   - it signs out and lands on `/login?logout`;
   - with a queued offline change (06b), the browser asks "1 change hasn't synced.
     Signing out discards it. Sign out anyway?" first, and Cancel keeps you signed in;
   - "Sign out everywhere" asks the same, and now also clears cached data;
   - after signing out, Cache Storage has no `flexbuddy-data` entries, just as when signing
     out from the main page.
8. **Long email.** Use a 60-character email: it wraps, and `scrollWidth === innerWidth`.
9. **Keyboard and screen reader.**
   - Tab reaches each summary row, and Enter or Space opens it.
   - The chevron isn't announced.
   - The headings run h1 "Account", then each card's h3.
10. **Offline.** The page opens from the cache, and the summaries fill from cached settings.
    Save buttons follow the existing offline rules.

## 8. Suggested commit message

```
feat(account): fold the account page into six sections

The account page was eleven cards in a row and ran to eight phone
screens. It now opens on a short list of six sections, earnings and
costs, taxes, payouts, reminders and calendar, backup and restore, and
account and privacy, each showing its current setting in one line, such
as 25% set aside or Last backup: Sep 14. Tapping a section opens the
same forms as before, and saving updates its line straight away. On a
phone one section is open at a time, so the page stays short.

Links to a setting from elsewhere in the app, such as Change goals or
the backup reminder on Home, open the right section. The title banner
is replaced by the driver's name and email, and the page gains a Sign
out button, which checks for changes that have not synced yet, as
signing out from the main screen does.
```

## 9. Decisions

These are my calls, **so tell me if any is wrong:**

- **Six sections,** grouped as in the table. "Earnings goals" sits under Earnings & costs,
  not Payouts.
- **One open at a time on phones,** several on desktop.
- **Sign out moves to section 6** here. The header's sign-out button is removed in plan 4,
  not here, so it's never missing in between.
