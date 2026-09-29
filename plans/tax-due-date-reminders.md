# Plan 14b: Estimated-tax due-date reminders

Planned against `origin/main` at `7b431cc` (latest migration V18, so this adds **V19**).

Most of plan 14 has already shipped in `377f4fb`: the set-aside percentage, the dashboard
tile, the Taxes card, payments, quarter buckets and the year CSV. That commit left two
things for later: **due-date push reminders**, which this plan covers, and a **printable
year summary**, which gets its own plan.

## 1. Goal and scope

A driver who opts in gets two push notifications for each estimated-tax due date: one
**7 days before** and one **on the day**. Each is sent once. They go out at the first job run
after **9:00 in the account's time zone**. Each one names the quarter and gives that
quarter's estimated set-aside and what the driver recorded as paid. Every figure is labelled
as an estimate, not tax advice.

The due dates come from the existing `flexbuddy.tax.due-dates` property
(`04-15,06-15,09-15,01-15`). January's date belongs to the previous tax year's fourth
quarter, as `TaxService.dueDate` already works out.

### Out of scope

- Moving a date that falls on a weekend or holiday. The dates are exactly as configured,
  and a moved deadline is a property change, as plan 14 intended.
- State estimated-tax dates.
- Email or SMS.
- Reminders for past due dates the job missed. There is no catch-up: only today's date
  matches.
- The printable summary (a separate plan).

## 2. Data model and migration

**New file `V19__tax_reminders.sql`.** Don't edit any existing migration.

```sql
alter table app_users add column remind_tax boolean not null default false;

create table tax_reminder_log (
    owner_id bigint not null references app_users(id) on delete cascade,
    due_date date not null,
    kind varchar(16) not null,
    sent_at timestamp(6) with time zone not null,
    primary key (owner_id, due_date, kind),
    constraint chk_tax_reminder_log_kind check (kind in ('WEEK_BEFORE', 'DUE_DAY'))
);
```

The existing `reminder_log` can't be reused. Its key is `(shift_id, kind)`, with a foreign
key to `shift`, and a tax reminder belongs to an account and a date, not to a shift.

**Model changes:**

- `model/AppUser.java`: add `private boolean remindTax;`, with Lombok accessors as for
  `remindMiles`.
- New `model/TaxReminderKind.java`: enum `WEEK_BEFORE, DUE_DAY`.
- New `model/TaxReminderLog.java`: `@Entity @Table(name = "tax_reminder_log")` with
  `@IdClass(TaxReminderLog.Key.class)`, following `ReminderLog`'s shape:
  - `@Id Long ownerId`
  - `@Id LocalDate dueDate`
  - `@Id @Enumerated(STRING) @Column(length = 16) TaxReminderKind kind`
  - `@Column(nullable = false) Instant sentAt`
  - A nested `Key` with `@EqualsAndHashCode`, implementing `Serializable`.

  It stores the owner as a plain id rather than a relation, as `ReminderLog` stores
  `shiftId`, so claiming a reminder never loads the user.

## 3. Backend

### New repository: `repository/TaxReminderLogRepository.java`

`JpaRepository<TaxReminderLog, TaxReminderLog.Key>` with:

```java
@Modifying
@Query("delete from TaxReminderLog log where log.ownerId = :ownerId")
int deleteAllByOwnerId(@Param("ownerId") Long ownerId);
```

### `repository/AppUserRepository.java`

Add `List<AppUser> findByRemindTaxTrue();`.

### `service/TaxService.java`

Add one public method that wraps the existing private logic, and change nothing else:

```java
/** The configured due date of a tax year's quarter (1–4); Q4's falls in the following January. */
public LocalDate dueDate(int taxYear, int quarter)   // delegates to the private dueDate(year, quarter - 1)
```

To avoid overload confusion, rename the private one to `dueDateAt(int year, int index)` and
update its single caller in `summary`. The constructor doesn't change, so
`TaxServiceTest:60` is untouched.

### New job: `service/TaxReminderJob.java`

This is a new class, not a method on `ReminderJob`, so `ReminderJob`'s constructor and
`ReminderJobTest` don't change.

- **Constructor:** `AppUserRepository`, `TaxReminderLogRepository`, `TaxService`,
  `PushService`, `UserTimeService`, `Clock`.
- **Constants:** `static final int SEND_HOUR = 9;` and
  `DateTimeFormatter DAY = ofPattern("EEE MMM d", Locale.ENGLISH)`, the same pattern as
  `ReminderJob`.
- **Schedule:** `@Scheduled(cron = "0 12 * * * *") @Transactional public int sendTaxReminders()`,
  which runs at minute 12 of every hour.

For each run:

1. If `!pushService.isConfigured()`, return 0.
2. `Instant now = Instant.now(clock)`.
3. For each `AppUser owner : userRepository.findByRemindTaxTrue()`:
   1. `LocalDateTime local = LocalDateTime.ofInstant(now, userTime.zone(owner))`. If
      `local.getHour() < SEND_HOUR`, skip this owner.
   2. `LocalDate today = local.toLocalDate()`.
   3. For `taxYear` in `{today.getYear() - 1, today.getYear()}` and `quarter` in 1–4, take
      `due = taxService.dueDate(taxYear, quarter)`. The kind is:
      - `DUE_DAY` if `today.equals(due)`,
      - `WEEK_BEFORE` if `today.equals(due.minusDays(7))`,
      - otherwise nothing, so move on.
   4. Claim `(owner.getId(), due, kind)`: if a log row exists, skip; otherwise save one with
      `sentAt = now`. This is the same claim-then-send as `ReminderJob.claim`, so at most
      one of each is ever sent.
   5. Build the message (below) and call `pushService.send(owner, message)`. Count it as
      sent.
4. Return the count.

**Message.** The figures come from `taxService.summary(owner.getEmail(), taxYear)`, using
`quarters().get(quarter - 1)`.

- **Title:**
  - `WEEK_BEFORE`: `"Estimated tax due " + DAY.format(due)`, for example "Estimated tax due
    Tue Sep 15".
  - `DUE_DAY`: `"Estimated tax due today · Q" + quarter`.
- **Body when the percentage is set** (the quarter's `setAside` is not null):
  `"Q3 estimate to set aside: $412.50 · $300.00 recorded as paid. An estimate from your own numbers, not tax advice."`
  Money uses `$` plus `setScale(2, HALF_UP).toPlainString()`, like `ReminderJob.money`.
- **Body when no percentage is set:**
  `"Q3 payment is due. Choose a set-aside percentage in FlexBuddy to see an estimate."`
- **URL:** `/account#taxes`.
- **Tag:** `"tax-" + taxYear + "-q" + quarter + (kind == WEEK_BEFORE ? "-week" : "-due")`.

**Why this is safe**

- Only an exact date match sends, and only from 9:00 local. So a driver never gets a
  reminder at night, and never one for a date already past.
- The hourly run means a restart during the day still sends later that day.

### Settings: the switch (boxed, so null keeps the stored value)

**`dto/ReminderSettingsRequest.java`**

- Add a 6th component, `Boolean remindTax`, documented as "Optional; when absent the saved
  choice is kept."
- Keep the existing 4-component constructor, now passing `null, null`.
- Add a 5-component compatibility constructor
  `(timeZone, remindBeforeMinutes, remindConfirm, remindMiles, forfeitCutoffMinutes)` that
  passes `null` for `remindTax`. That keeps `AccountSettingsServiceTest:75` compiling
  unchanged.

**`service/AccountSettingsService.updateReminders`**

Add `if (request.remindTax() != null) user.setRemindTax(request.remindTax());`.

**`dto/AccountSettingsResponse.java`** (a flagged record)

- **Append** `boolean remindTax` as the **17th and last** component, after
  `calendarFeedPath`. Don't reorder anything.
- Add a 16-component compatibility constructor with the current signature, delegating with
  `false`. That keeps `TaxServiceTest:171` compiling unchanged.
- Update the 4-component compatibility constructor to pass `false` at the end. It already
  delegates to the canonical constructor, so this is one more argument.
- In `AccountSettingsService.response(user)`, add `user.isRemindTax()` as the last argument.

### Backup, restore and account deletion

**`dto/BackupSettings.java`** (a flagged record)

- **Append** `Boolean remindTax` as the 14th and last component.
- Add a 13-component compatibility constructor with the current signature, passing `null`.
- Update the 2-component constructor to pass one more `null`.
- The format version stays **4**. A backup without the field restores it as null, and null
  keeps the account's current setting.

**`service/AccountBackupService.create`**

The canonical `new BackupSettings(…)` at line ~72 gets `user.isRemindTax()` as the 14th
argument.

**`service/AccountRestoreService`**

Inside the `file.version() >= 4` block, next to the tax percentage, add
`if (file.settings().remindTax() != null) owner.setRemindTax(file.settings().remindTax());`.

The log isn't backed up. Neither is `reminder_log`, since a sent reminder is not account
history.

**`service/AccountService.deleteAccount`**

- Inject `TaxReminderLogRepository`, added last, which changes the constructor.
- Call `taxReminderLogRepository.deleteAllByOwnerId(ownerId)` right after
  `standingEntryRepository.deleteAllByOwnerId(ownerId)`.

The foreign key cascades in Postgres, but the H2 test schema is built from the entities
without that cascade. So the explicit delete is what `AccountDeletionJpaTest` depends on.

## 4. Frontend

### `templates/account.html`, Reminders card (line ~137)

- Rename the heading from "Shift reminders" to **"Reminders"**. The card now covers more
  than shifts.
- After the `#remindMiles` row, add:

  ```html
  <label class="checkbox-row"><input id="remindTax" type="checkbox"> Remind me a week before and on each estimated tax due date</label>
  ```

  `.checkbox-row` already meets the 44px rule.

### `templates/account.html`, Taxes card (`#taxes`)

Under the next-due-date line, add one sentence:

> Due-date reminders are **on**/**off** · change them in Reminders.

Give it `id="taxReminderState"`, with "Reminders" as an in-page link to the reminders card.
Add `id="reminders"` to the reminders `article` if it has no id.

### `static/js/account.js`

- In `renderReminders(settings)`:
  - Set `#remindTax` checked from `Boolean(settings.remindTax)`.
  - Set the state word in `#taxReminderState` to "on" or "off".
- In the reminder form submit, add `remindTax: document.querySelector('#remindTax').checked`
  to the JSON body.

`tax.js` needs no change.

### CSS and service worker

- No new CSS classes are needed. Check at 375px that the longer checkbox label wraps inside
  the card. The account grid children already have `min-width: 0`.
- `sw.js` doesn't change: there's no new JS file and no new cached API path.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse` | **Changes.** One component is appended last, so field order is kept. A new 16-component compatibility constructor is added, and the 4-component one passes `false`. |
| `BackupSettings` | **Changes.** One component is appended last. A new 13-component compatibility constructor is added, and the 2-component one passes `null`. |
| `BackupShift`, `AccountBackupFile` (format version 4) | Not touched. Version stays 4. |
| `BlockEvaluationResponse`, `ShiftResponse` | Not touched. |
| New account data | `remind_tax` is backed up and restored through `BackupSettings`. `tax_reminder_log` is deleted with the account and isn't backed up (see 3). |
| `sw.js` | No change. |
| Boxed request fields | `ReminderSettingsRequest.remindTax` is a `Boolean`, where null keeps the stored value. |
| Phone layout | One checkbox row and one sentence, both wrapping in `min-width: 0` cards. |
| Dates | The send hour and "today" come from `userTime.zone(owner)`. No browser dates. |
| Money and tax wording | Every figure in the push body says "estimate" and "not tax advice". The Taxes card's existing disclaimer is unchanged. |

**Constructor and record call sites:**

| Call site | Change |
|---|---|
| `AccountSettingsService.response` | Canonical `AccountSettingsResponse`, + `user.isRemindTax()` |
| `TaxServiceTest:171` (16 components) | Unchanged; binds to the new compatibility constructor |
| `PayoutServiceTest:127`, `BlockEvaluatorTest:225` (4 components) | Unchanged |
| `AccountBackupService:72` | Canonical `BackupSettings`, + `user.isRemindTax()` |
| `AccountRestoreServiceTest:312` (2 components) | Unchanged |
| `AccountSettingsServiceTest:75` (5-component `ReminderSettingsRequest`) | Unchanged; binds to the new compatibility constructor |
| `AccountService` constructor | + `TaxReminderLogRepository`, last. **`AccountServiceTest` uses `@InjectMocks`, so add a `@Mock TaxReminderLogRepository`**, or deletion hits a NullPointerException. `AccountDeletionJpaTest` gets it from the JPA slice. |
| `TaxService` | Constructor unchanged. The private `dueDate` is renamed to `dueDateAt`, with one caller. |
| `ReminderJob`, `ReminderJobTest` | Not touched. |

## 6. Tests to add or change

Expect about 17 new tests, taking the suite from about 310 to about 327.

**New `service/TaxReminderJobTest.java`** (Mockito)

Setup:

- A real `UserTimeService` over a fixed `Clock`.
- A mocked `TaxService` whose `dueDate(y, q)` is answered from a small table for 2025–2027,
  matching the configured dates.
- One owner in `America/Los_Angeles` with `remindTax = true`.
- `summary` returns quarters where Q3 has `setAside` 412.50 and `paid` 300.00.
- A helper `job(String instant)`, like `ReminderJobTest.job`.

Tests:

1. `sendsTheWeekBeforeReminderAtNineLocalOnce`: at `2026-09-08T15:12:00Z` (08:12 PDT)
   nothing is sent. At `2026-09-08T16:12:00Z` (09:12 PDT) one message is sent. Verify
   exactly `new PushMessage("Estimated tax due Tue Sep 15", "Q3 estimate to set aside: $412.50 · $300.00 recorded as paid. An estimate from your own numbers, not tax advice.", "/account#taxes", "tax-2026-q3-week")`.
   A second run at 17:12Z sends nothing, because the log row exists.
2. `sendsTheDueDayReminderOnTheDay`: at `2026-09-15T16:12:00Z` the title is "Estimated tax
   due today · Q3" and the tag is `tax-2026-q3-due`.
3. `januaryDueDateUsesTheFourthQuarterOfThePreviousYear`: at `2027-01-08T17:12:00Z`
   (09:12 PST) the message is for Q4 with tag `tax-2026-q4-week`, and `summary` was asked
   for 2026.
4. `withoutAPercentageTheBodyAsksForOne`: the quarter's `setAside` is null, and the body is
   "Q3 payment is due. Choose a set-aside percentage in FlexBuddy to see an estimate."
5. `usesTheAccountTimeZoneForTheDayAndHour`: at `2026-09-08T00:12:00Z` it's 09:12 on Sep 8
   for an owner in `Asia/Tokyo`, so it sends. The same instant is 17:12 on Sep 7 in Los
   Angeles, so nothing is sent for the Los Angeles owner.
6. `nothingOnOtherDays`: at `2026-09-10T18:12:00Z` nothing is sent and nothing is logged.
7. `doesNothingWhenPushIsNotConfigured`: `verifyNoInteractions(userRepository, logRepository, taxService)`.

**`service/TaxServiceTest.java`** (add)

8. `dueDateMapsEachQuarterToItsConfiguredDate`: for 2026 the dates are Apr 15, Jun 15 and
   Sep 15 2026, and Jan 15 **2027**.

**`service/AccountSettingsServiceTest.java`** (add)

9. `updateRemindersTurnsTaxRemindersOnAndKeepsThemWhenOmitted`: a 6-component request with
   `TRUE` gives `remindTax()` true. A following 5-component request (`remindTax` null)
   leaves it true.

**`controller/AccountControllerTest.java`** (add)

10. `updateReminders_acceptsTheTaxReminderSwitch`: a PUT with `"remindTax":true` returns 200.
    Capture the request passed to `settingsService.updateReminders` and assert
    `remindTax()` is `TRUE`. Also assert the JSON of the stubbed response contains
    `"remindTax":true`.

**`service/AccountBackupServiceTest.java`** (change)

11. Set `user.setRemindTax(true)` and assert `backup.settings().remindTax()` is `TRUE`.

**`service/AccountRestoreServiceTest.java`** (add)

12. `restoreSetsTaxRemindersFromAVersionFourBackup`: settings with `remindTax = true`
    restore to true.
13. `aBackupWithoutTheTaxReminderFieldKeepsTheCurrentChoice`: the owner has `true`, and a
    13-component `BackupSettings` (null field) keeps it `true`.

**`service/AccountServiceTest.java`** (change)

14. Add the `@Mock`, and in the `InOrder`, verify
    `taxReminderLogRepository.deleteAllByOwnerId(42L)` right after the standing repository.

**`service/AccountDeletionJpaTest.java`** (change)

15. Save one `TaxReminderLog` for the owner, and assert `rowCount("tax_reminder_log")` is
    zero after deletion.

**`repository/…` (a new `@DataJpaTest`, or add to an existing one)**

16. `findByRemindTaxTrueReturnsOnlyOptedInAccounts`
17. `theTaxReminderLogIsKeyedByOwnerDateAndKind`: after saving `(owner, Sep 15 2026,
    WEEK_BEFORE)`, `existsById` is true for that key and false for the same owner and date
    with `DUE_DAY`. The job's claim relies on this.

`PostgresMigrationTest` (it only runs with `FLEXBUDDY_TEST_POSTGRES_URL` set) validates V19
against the entities. Run it once locally before shipping, if possible.

## 7. Manual checks

The push part needs VAPID keys configured locally, as for the existing reminders. Without
them, the job returns 0 and only the settings checks apply.

1. **Account page at 1280×800.**
   - The card heading reads "Reminders".
   - Tick "Remind me a week before and on each estimated tax due date" and save. The toast
     confirms, and after a reload the box is still ticked.
   - The Taxes card reads "Due-date reminders are on · change them in Reminders", and the
     link scrolls to the Reminders card.
2. **Save the form from an older cached `account.js`** (or send a PUT without `remindTax`
   from DevTools). The choice stays on.
3. **Trigger the job.**
   - Temporarily set the account time zone so local time is past 9:00, and set
     `flexbuddy.tax.due-dates` so one date is exactly 7 days from today. Restart.
   - Within the hour (or by calling the job from a test), one notification arrives. Its
     title is "Estimated tax due <Day Mon d>", and its body gives that quarter's estimate
     and paid amounts.
   - Tapping it opens `/account#taxes`.
   - No second notification arrives in the following hours.
   - Reset the property afterwards.
4. **With no set-aside percentage** the body reads "… Choose a set-aside percentage in
   FlexBuddy to see an estimate."
5. **Phone at 375×667 and 430×932.**
   - The new checkbox label wraps inside the card, with its tap target at least 44px.
   - `document.documentElement.scrollWidth === window.innerWidth` on `/account`.
6. **Backup and restore.**
   - Download a backup. `"settings"` contains `"remindTax": true` and `"version"` is `4`.
   - Turn the reminders off, then restore in Merge mode. They're on again.
7. **Account deletion.** On a throwaway account that has received a tax reminder, delete
   the account. `tax_reminder_log` has no rows for it.

## 8. Suggested commit message

```
feat(taxes): remind drivers of estimated tax due dates

Drivers can now ask for a push reminder a week before and on each
estimated tax due date. Each reminder names the quarter and gives the
estimated set-aside for it and what has been recorded as paid, from the
driver's own numbers, and says it is an estimate and not tax advice.
Without a set-aside percentage it asks for one instead of showing a
figure.

Reminders go out once each, from 9 in the morning in the account's time
zone, and only on the exact day, so a driver never gets one at night or
for a date that has passed. The due dates are the same configured dates
the Taxes card uses, with January's belonging to the previous year's
fourth quarter. The switch sits with the other reminders, and the Taxes
card says whether it is on.

The choice is carried in backups, an older backup keeps the current
choice, and deleting the account removes the record of sent reminders.
```

## 9. Open questions

1. **Where the switch lives.** This plan puts it in the Reminders card, with the other push
   reminders, and a status line on the Taxes card. Would you rather it sat in the Taxes
   card itself, where the draft put it?
2. **Send time.** Is 9:00 local right, or should it follow the driver's existing reminder
   lead time? Nothing else in the app has a "morning" setting.
3. **Weekend and holiday dates.** The IRS moves a due date that lands on a weekend or
   holiday to the next business day. This plan keeps the configured dates exactly, which
   is what the Taxes card shows today. Should both move, or is updating the property each
   year enough?
4. **Heading rename.** Is renaming "Shift reminders" to "Reminders" all right? It's
   cosmetic, but it changes a label testers have seen.
