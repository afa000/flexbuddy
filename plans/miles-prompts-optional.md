# Plan: let drivers turn off the missing-miles prompts on Home

Planned against `origin/main` at 6e56508.

## What exists today

The miles reminders come in two kinds:

1. **Push reminder** ("Remind me to log miles 20 minutes after a block
   ends"). This is already optional and off by default: `remind_miles`
   is `false` (V12), and it's the checkbox `#remindMiles` in Account →
   Reminders & calendar. **Unchanged.**
2. **In-app prompts on Home.** These can't be turned off:
   - `finish.js` → `loadMissing()` runs on every dashboard load
     (`app.js` line 783). It calls `GET /shifts/missing-miles?days=7` and
     fills `#missingMiles` ("Blocks without miles") with **Add miles / No
     miles / Not now** for each block.
   - `home.js` → `attentionRows` then adds a "N blocks have no miles · Add
     miles" row to the **Needs attention** card.
   - "Not now" only snoozes **one block for 24 hours**, in localStorage, on
     that device. A driver who doesn't track miles per block, for example
     one who logs an annual total or uses another mileage app, sees these
     prompts after every block, forever.

## 1. Goal and out-of-scope

**Goal:**
- A setting in Account → Reminders & calendar, **"Show blocks without
  miles on Home"**, on by default.
- When it's off:
  - the Needs attention card never shows the miles row;
  - the "Blocks without miles" list never loads;
  - nothing is fetched from `/shifts/missing-miles`.
- The setting is stored on the server, so it applies on every device and
  in the Play app.
- It's included in backups and restored from them.
- The prompt list itself gets a **Stop asking** link to that setting, so
  drivers can find it.

**Out of scope:**
- The push miles reminder, which is already optional.
- The miles field in the Finish-block sheet and the edit dialog. That's
  data entry, not a reminder.
- Mileage figures in Reports and on the tax summary.
- The per-block "Not now" snooze, which stays as it is.
- Changing the Account → Reminders summary line. Its "miles on/off"
  still describes the push reminder.

## 2. Data model and migration

New `src/main/resources/db/migration/V23__ask_missing_miles.sql`:

```sql
-- Drivers can turn off the Home prompts for blocks without miles; everyone keeps them until they choose.
alter table app_users add column ask_missing_miles boolean not null default true;
```

Don't edit existing migrations. CI's `PostgresMigrationTest` counts
pending migrations, so V23 needs no count change.

`model/AppUser.java`, after `remindMiles`:

```java
/** Show recent blocks without miles on Home and in its Needs attention card. */
@Column(nullable = false)
private boolean askMissingMiles = true;
```

Lombok generates `isAskMissingMiles()` and `setAskMissingMiles(...)`.

**Backup, restore and deletion:**
- **Backup:** `BackupSettings` gains the field (see below). The format
  stays **version 4**, because the field is optional and older files
  simply don't have it.
- **Restore:** a `null` value keeps the account's current choice, the
  same rule `remindTax` uses.
- **Deletion:** it's a column on `app_users`, removed with the row. No
  change needed.

## 3. Backend files

### `dto/ReminderSettingsRequest.java`

- Append `Boolean askMissingMiles` as the **last** component of the
  record, with the comment `/** Optional; when absent the saved choice is kept. */`.
- **Keep the compatibility constructors.** Add one with the current five
  components (`timeZone, remindBeforeMinutes, remindConfirm, remindMiles,
  forfeitCutoffMinutes`) that passes `null` for the new field. Change the
  existing four-argument constructor to go through it. Existing callers
  and tests compile unchanged.

### `dto/AccountSettingsResponse.java` (shared record: keep the field order and the compatibility constructors)

- Append `boolean askMissingMiles` **after** `remindTax`, as the last
  component.
- Add a compatibility constructor with the current 17 components that
  passes `true`.
- Update the existing 16-argument constructor ("before tax due-date
  reminders") and the 4-argument constructor so they delegate with
  `false` for `remindTax`, as now, and `true` for `askMissingMiles`.
- Add a doc comment on the new 17-argument constructor: `/** The settings
  before the missing-miles prompts could be turned off. */`.

### `dto/BackupSettings.java` (shared record: keep the field order and the compatibility constructors)

- Append `Boolean askMissingMiles` after `remindTax`.
- Add a compatibility constructor with the current 14 components that
  passes `null`. The comment reads: `/** Backups made before the
  missing-miles prompts could be turned off; a missing choice keeps the
  account's current one. */`.
- Update the 13-argument and 2-argument constructors to pass `null` for
  the new last field.

### `service/AccountSettingsService.java`

- In `updateReminders` (line 63), after the `remindMiles` line, add
  `if (request.askMissingMiles() != null) user.setAskMissingMiles(request.askMissingMiles());`.
- In `response(...)` (line 123), pass `user.isAskMissingMiles()` as the
  new last argument.

### `service/AccountBackupService.java`

In the `new BackupSettings(...)` call (line 72), add
`user.isAskMissingMiles()` after `user.isRemindTax()`.

### `service/AccountRestoreService.java`

Inside the `file.version() >= 4` block (line 243), after the `remindTax`
line, add:

```java
if (file.settings().askMissingMiles() != null) owner.setAskMissingMiles(file.settings().askMissingMiles());
```

### `controller/PageController.java`

In `shiftsPage`, inside the existing `ifPresent`, add:

```java
model.addAttribute("askMissingMiles", user.isAskMissingMiles());
```

`/shifts/missing-miles` itself is unchanged. It still answers if called.

## 4. Frontend files

### `templates/shifts.html` (Home, line 116)

- Add the flag to the list container:

  ```html
  <div class="missing-miles" id="missingMiles" hidden th:attr="data-ask=${askMissingMiles}">
  ```

- In `.missing-miles-heading`, after `#missingMilesCount`, add:

  ```html
  <a class="text-link missing-miles-off" href="/account#reminders">Stop asking</a>
  ```

### `static/js/finish.js`

At the top of `loadMissing()` (line 279), after the `if (!section) return;`:

```js
// The driver turned these prompts off in Account; nothing is fetched and the attention card drops the row.
if (section.dataset.ask === 'false') {
    missing = 0;
    document.querySelector('#missingMilesList').replaceChildren();
    document.dispatchEvent(new CustomEvent('flexbuddy:attention'));
    return;
}
```

`home.js` needs no change. `renderAttention` reads `missingCount()`,
which is 0, so `attentionRows` leaves out the miles row and hides
`#missingMiles`. Update the comment above `loadMissing` to mention the
setting.

### `templates/account.html` (Reminders form, line 188)

Directly **before** the `#remindMiles` checkbox row, add:

```html
<label class="checkbox-row"><input id="askMissingMiles" type="checkbox"> Show blocks without miles on Home</label>
```

Change the existing `#remindMiles` label text to "Also send a push
reminder to log miles 20 minutes after a block ends". That makes the
difference between the two clear. Its id and behaviour don't change.

### `static/js/account.js`

- In `renderReminders` (line 365), add
  `document.querySelector('#askMissingMiles').checked = settings.askMissingMiles !== false;`.
  A cached response from before the deploy has no field, so it reads as
  on.
- In the reminders save body (line 403), add
  `askMissingMiles: document.querySelector('#askMissingMiles').checked,`.

### `static/css/styles.css`

`.missing-miles-heading` (line 2082) is a flex row with
`justify-content: space-between` and no wrapping. Adding a third child
would squeeze the title at 375px. Make two changes:

- Add `flex-wrap: wrap;` to `.missing-miles-heading`.
- After line 2084, add:

  ```css
  .missing-miles-heading .missing-miles-off { min-height: 44px; display: inline-flex; align-items: center; font-size: 13px; }
  ```

At 375px, the link must wrap below the title and count rather than widen
the card.

### `static/sw.js`

No change. No new script; `/account/settings` is already in `DATA_PATHS`.

## 5. Ripple list

- **Shared records:**
  - `AccountSettingsResponse` gains a last field, with compatibility
    constructors kept.
  - `BackupSettings` gains a last field, with compatibility constructors
    kept, and the format stays version 4.
  - `BlockEvaluationResponse`, `BackupShift`, `AccountBackupFile` and
    `ShiftResponse` are unchanged.
- **New account data** (`ask_missing_miles`): included in backup,
  restored when present, and deleted with the account.
- **Optional request field:** `askMissingMiles` is a boxed `Boolean`, and
  `null` keeps the stored value. An older cached Account page that posts
  without it doesn't switch the prompts back on.
- **Offline:** Home reads the flag from the page HTML. A cached `/` shell
  may show the old state until the next online load, which is acceptable
  for a preference.
- **The outbox and offline queue** are unaffected.
- **Phone layout:**
  - the new checkbox row uses `.checkbox-row`, which is already 44px
    tall;
  - the Stop asking link is 44px tall and wraps at 375px;
  - there's no page widening.
- **Push reminders:** completely independent. Turning the Home prompts off
  doesn't change `remindMiles`.

## 6. Tests to add

- **`AccountSettingsServiceTest`:**
  - A new user's settings have `askMissingMiles() == true`.
  - `updateReminders` with `askMissingMiles = false` stores false and
    returns false.
  - A later `updateReminders` with `null` keeps false.
- **`AccountControllerTest`:**
  - `PUT /account/reminders` with `{"timeZone":"America/New_York","remindConfirm":false,"askMissingMiles":false}`
    returns JSON with `"askMissingMiles":false`.
  - The same request without the field keeps the stored value.
- **`AccountBackupServiceTest`:** with the setting off, the backup's
  `settings.askMissingMiles` is `false`.
- **`AccountRestoreServiceTest`:**
  - Restoring a v4 file whose settings have `askMissingMiles: false` turns
    it off.
  - A v4 file without the field leaves the account's value unchanged:
    true stays true, and false stays false.
- **`PageControllerTest`:**
  - Home renders `id="missingMiles"` with `data-ask="true"` for a default
    user, and `data-ask="false"` after the setting is turned off.
  - The page contains the `Stop asking` link to `/account#reminders`.
- **`AccountSettingsResponse` and `BackupSettings` compatibility:** the
  existing tests that use the old constructors must compile and pass
  unchanged. Add one assertion that the 17-argument
  `AccountSettingsResponse` constructor yields `askMissingMiles() == true`.
- **`PostgresMigrationTest`:** add a check that after migrating, the
  legacy user's `ask_missing_miles` is `true`.
- **`src/test/js/home.test.js`:** already pins that `attentionRows` with
  `missingMiles: 0` has no miles row. No change needed.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

Set this up first: an account with two completed blocks from this week
without miles.

### Desktop (1280×800)

1. On Home, the Needs attention card shows "2 blocks have no miles · Add
   miles". Opening it shows both blocks and a **Stop asking** link.
2. Click **Stop asking**. Account opens with Reminders & calendar
   unfolded. "Show blocks without miles on Home" is ticked, and below it
   the push checkbox reads "Also send a push reminder…".
3. Untick it and click **Save reminders**. Go back to Home:
   - there's no miles row, and if nothing else needs attention, no Needs
     attention card at all;
   - the Network tab shows no request to `/shifts/missing-miles`.
4. Sign in on a second browser: the prompts are off there too.
5. Download a backup. In the JSON, `settings.askMissingMiles` is `false`.
   Turn the setting back on, restore the backup, and it's off again.
6. Turn it back on and save. The miles row and list come back on the next
   Home load.
7. With push miles reminders on and the Home prompts off, a block ending
   without miles still sends the push reminder after 20 minutes. They're
   independent.

### Phone (375×667 and 430×932, then a real iPhone and Android phone)

8. In the miles list heading, "Stop asking" is at least 44px tall. At
   375px it sits on its own line under the title if it doesn't fit, with
   no sideways scroll (`document.documentElement.scrollWidth === innerWidth`).
9. In Account → Reminders & calendar, the new checkbox row is at least
   44px tall, and its label wraps without widening the card.
10. In the Play app on Android, turning the setting off and reopening the
    app hides the prompts.

## 8. Commit message

```
feat(reminders): let drivers turn off the missing miles prompts

Home asked about every recent block without miles, in the Needs
attention card and a list of blocks, and Not now only put one block
off for a day on one device. A driver who does not log miles per
block saw the prompts after every block with no way to stop them.

Account now has a Show blocks without miles on Home setting, on by
default. With it off, Home neither loads nor shows the prompts, and
the list itself links to the setting. The choice is stored with the
account so it applies on every device, and it is kept in backups and
restored from them. The push reminder to log miles stays a separate
choice and is unchanged.
```

## 9. Open questions

1. ~~Default for new accounts?~~ **Decided:** on, as planned.
2. **The confirm prompts:** you only asked about miles. The Needs
   attention card's "N blocks to confirm" row and the Schedule's confirm
   strip stay as they are. The same setting pattern could cover them
   later if testers ask.
