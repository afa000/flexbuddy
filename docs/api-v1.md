# FlexBuddy API v1

The JSON API the native Android app (`flexbuddy-android`) uses. It is the contract between the two repositories: every
server task that adds or changes an `/api/v1` endpoint updates this page in the same pull request, and app tasks build
against what this page says. The plan is [native-app-plan.md](native-app-plan.md).

Status: FB-005 implements password sign-in, code challenges, device sign-out and the account summary. FB-033 adds
read-only blocks and Home, with change-only sync. NA-05 adds block writes for the native outbox, NA-06 expenses and
payouts, NA-07 screenshot import. The API remains disabled by default.

## Availability

- Everything lives under `/api/v1/**`, on its own stateless security chain. The website's pages and JSON endpoints are
  separate and don't change.
- The API exists only when `FLEXBUDDY_API_ENABLED=true`. While it is unset, as in production until the owner turns it
  on, every `/api/v1` path answers `404` like any unknown path.
- Local server: `http://localhost:8080`, or `http://10.0.2.2:8080` from the Android emulator. Test server and
  production addresses go here when each has the API turned on.

## Conventions

- **JSON in and out**, UTF-8, `Content-Type: application/json`, except screenshot uploads (`multipart/form-data`).
- **Signing in.** A token from the sign-in endpoints goes in `Authorization: Bearer <token>` on every other request.
  Tokens start with `fbt_`. Never log a token or the `Authorization` header, on either side.
- **No cookies, no CSRF, no redirects.** A browser's session or remember-me cookie never authenticates an `/api/v1`
  request.
- **Errors are codes, not sentences:** `{"error": "<code>"}`, plus fields some codes add (such as
  `retryAfterMinutes`). The app shows its own text in the driver's language.
- **Status codes:**
  - `400`: the request can't be accepted as sent; the code says why (for example `invalid`, with a `fields` object
    naming each bad field, or FB-005's `code_wrong`)
  - `401`: missing, unknown or expired token: `{"error": "unauthorized"}`
  - `404`: unknown path, an id that belongs to another account, or the API is turned off
  - `409`: a conflict the driver must resolve, such as a block already logged
  - `429`: locked or rate-limited: `{"error": "locked", "retryAfterMinutes": n}`
- **Retried writes.** Every create or change sends an `Idempotency-Key` header with a new UUID per action. A retry with
  the same key never adds a second record. This is the header the website's offline outbox already uses.
  NA-05 block writes and NA-06 expense and payout writes require it, including delete and restore. UUIDs are
  case-insensitive. Keys belong to the account, across its devices and every API write endpoint: a key used for a block
  can't be used again for an expense or a payout. Reusing a key with a different action, id or bound request body
  returns `409` `idempotency_conflict`. JSON whitespace and property order do not matter; retain the original values
  when retrying. Failed actions do not consume a key. Successful acknowledgements persist across subsequent changes,
  deletion, permanent deletion and backup restores, until account deletion; simultaneous retries apply only once.
  A screenshot import uses one key for the screenshot's preview and its one save (see
  `POST /api/v1/shifts/import-preview`).
- **Dates and times** are ISO 8601. Dates are the driver's local date (`2026-11-11`), times are local with no offset
  (`18:30`), and instants are UTC with `Z`. The driver's time zone comes from `GET /api/v1/account`.
- **Money** is a string with two decimals (`"72.00"`), never a floating-point number.
- **Earnings rules hold.** Scheduled blocks never count toward earnings, hours or net, same as the website.
- **One account per token.** No request body, path or query parameter can choose another account.
- **Status codes in data:** lower case, for example a block's `status` is `scheduled`, `completed`, `cancelled` or
  `forfeited`. Local times have no seconds (`18:30`). Decimals that aren't money (miles, a rate per mile, a percentage)
  are strings as stored, for example `"41.5"`.

## Sync

Added by FB-033. Every list endpoint follows this; `GET /api/v1/shifts` is the first, `GET /api/v1/expenses` (NA-06)
the second. Each list has its own cursor; never send one list's cursor to another. A permanent removal or backup
restore on the account makes both lists full.

The one exception is `GET /api/v1/payouts`: its figures are worked out from blocks and settings when asked, so it has
no `updatedSince` and every answer is the whole list. It still has an `ETag`, so an unchanged answer is `304`.

- **First sync:** send no `updatedSince`. The answer has `"full": true` and every record. Replace the phone's copy.
- **Later syncs:** send `updatedSince=<cursor>` with the `cursor` from the previous answer. The answer has
  `"full": false`, the records changed since then, and `deleted`: the ids of records deleted since then. Upsert the
  changed ones by id and remove the deleted ones. A record deleted and then restored comes back as changed.
- **The cursor is opaque.** It is the time of the latest change on the account, encoded; store it as given. A cursor
  this server didn't write answers `400` `invalid`. It never chooses an account or a record.
- **Overlap.** Changes from the 5 minutes before the cursor are sent again, so a save that committed late isn't missed.
  The same record can arrive twice; upserting by id makes that harmless. The records at the cursor itself are always
  among them.
- **Full again.** The answer is `"full": true` (replace everything) when the cursor is more than 29 days old (Recently
  deleted records are purged after 30), or when the account's records were removed for good or replaced since then
  (permanent delete, emptying Recently deleted, a backup restore or undoing one). After such a removal, answers stay
  full until a later change moves the cursor more than 5 minutes past it.
- **`ETag` and `304`.** Every read answer has a strong `ETag`. Send it back as `If-None-Match`; if nothing changed the
  answer is `304` with no body. Answers carry `Cache-Control: private, no-cache`. An unchanged account gives the same
  cursor and body, so a phone that polls gets `304`s.
- No paging yet: a full answer holds every record.
- **After a write:** an acknowledgement is `{"id":41}`, not a block snapshot or sync cursor. Keep the previous sync
  cursor and fetch changes with `GET /api/v1/shifts?updatedSince=<cursor>` (or a full sync if none). A delayed retry
  acknowledges the original successful action without applying it again, even if the block has since changed or been
  deleted; the acknowledgement does not promise the block still exists. Do not put an acknowledgement into the block
  cache or advance a cursor from a write response. Delete appears in `deleted`; restore appears in `shifts`.

## Endpoints

Each server task adds a section here in this shape:

```text
### METHOD /api/v1/path

Added by FB-xxx. Token required: yes | no.

Request: JSON fields, which are required.
Responses: each status, its JSON, and when it happens.
```

### POST /api/v1/auth/sign-in

Added by FB-005. Token required: no.

Request: `{"email":"driver@example.com","password":"<password>","deviceName":"My phone"}`.
Email and password are required; deviceName is optional, nullable and at most 80 characters.
Email is at most 254 characters. Password is at most 72 UTF-8 bytes (BCrypt's limit).
Requests must use `Content-Type: application/json`.

- 200, verified email and no second factor:
  `{"status":"signed_in","token":"<fbt_…>","expiresAt":"2026-12-12T12:00:00Z","account":{…}}`.
- 200, email verification required:
  `{"status":"code_required","purpose":"verify_email","challenge":"<fbc_…>","expiresAt":"2026-11-12T12:10:00Z"}`.
- 200, second factor required: same challenge shape with `purpose:"two_factor"` and
  `method:"app"` or `method:"email"`. An email code is sent for the email method.
- 401 `invalid_credentials`: wrong email/password or a Google-only account without a password.
  Set a password through the website's password reset flow before using the app.
- 429 `locked` with `retryAfterMinutes`: the same email/IP login lock or email-send limits used by the website.
- 400 `invalid`: invalid fields or malformed JSON. Submitted credentials are never echoed.

A device token has 256 random bits and starts with `fbt_`. It is returned once; the server stores only SHA-256.
Send it in the Authorization header, never in a URL, and store it using Android's protected credential storage.
The persisted expiry slides to 30 days from a request, updated at most hourly. The stored deadline is authoritative,
so expiry can be up to one hour earlier than exactly 30 days since the latest request. An expired token cannot slide.

### POST /api/v1/auth/code

Added by FB-005. Token required: no. Request: `{"challenge":"<fbc_…>","code":"<code>"}`.
Both fields are required; code accepts six-digit app/email codes or a recovery code, including spaces/dashes.
The challenge is a separate 256-bit secret, stored hashed, valid for ten minutes and usable once.
A new password sign-in replaces that account's previous pending app challenges.

- 200: the same signed_in response as sign-in; a recovery code adds `recoveryCodeUsed:true`.
- 200: if an unverified account also has second factor enabled, a new two_factor challenge follows successful email
  verification. Complete this second challenge before expecting a token.
- 400 `code_wrong` or `code_expired`: the entered code failed, or its email code expired.
- 401 `challenge_expired`: unknown, consumed, replaced or expired challenge. Restart password sign-in.
- 429 `locked` with `retryAfterMinutes`: a shared login lock. The challenge is discarded.

Correct password authentication does not clear email failures until the entire sign-in succeeds.
Wrong passwords count against both login buckets; wrong codes count against the email bucket.
Successful sign-in does not clear the connection's failures.

### POST /api/v1/auth/code/resend

Added by FB-005. Token required: no. Request: `{"challenge":"<fbc_…>"}`.

- 204: a replacement email code was sent. Earlier codes for this purpose stop working.
- 400 `invalid`: an app-code challenge has no email to resend.
- 401 `challenge_expired`: restart sign-in.
- 429 `locked` with `retryAfterMinutes`: shared email/IP login or send limit.

Resending does not extend the challenge's ten-minute lifetime.

### POST /api/v1/auth/sign-out

Added by FB-005. Token required: yes. No body. Returns 204 and revokes only the token used for this request.
Reusing a revoked token returns 401 `unauthorized`.
Password reset and account deletion revoke every app token and pending challenge, even with the API disabled.
Tokens and challenges are excluded from backups. Expired credentials are purged daily while the API is enabled.

### GET /api/v1/calculation-settings

Added by FB-042 for NA-13 offline figures. Token required: yes. Read-only; no account selector or write endpoint.

200 (made-up example):

```json
{
  "timeZone": "America/New_York",
  "vehicleCostMethod": "standard_mileage",
  "customMileageRate": null,
  "weeklyGoal": "600.00",
  "monthlyGoal": null,
  "goalBasis": "gross",
  "payoutDays": ["tuesday", "friday"],
  "payoutLagDays": 1
}
```

This is the complete eight-field response. It maps the existing website settings service, including its defaults:

- `timeZone`: the account's configured time-zone ID, defaulting to `America/New_York` when absent. If a legacy or
  corrupt value is invalid, the consumer must fall back to `America/New_York`, as server calculations do through
  `UserTimeService`. These are local dates in that zone, not UTC dates or the device's zone.
- `vehicleCostMethod`: `standard_mileage` (default) or `actual_expenses`.
- `customMileageRate`: decimal string with the saved rate's precision, or null for IRS mode. For example, a
  custom rate is `"0.655"`; an explicit zero remains a decimal string, not null. IRS mode always returns null,
  never today's effective IRS rate. The shared figures library supplies the IRS rate for each block's date.
- `weeklyGoal`, `monthlyGoal`: money strings with two decimal places, or null when that goal is unset.
- `goalBasis`: `gross` (default) or `net`.
- `payoutDays`: lowercase weekday codes in Monday-to-Sunday order. The existing settings parser supplies
  `tuesday` and `friday` when no stored day can be parsed.
- `payoutLagDays`: integer, the existing stored/default lag (default 1).

No private calendar feed link, sign-in/security settings, tax settings or reminder preferences are returned.
Reading does not save settings or generate a calendar token. Existing bearer-token authentication runs before
conditional requests; a web session alone cannot authorize this endpoint.

Strong `ETag` and `Cache-Control: private, no-cache` follow Sync. Matching `If-None-Match` returns 304 with no body;
a represented setting change returns the complete body with a new tag. Unrelated settings and the passage of
time alone do not change the response. No `updatedAt` or delta cursor is supplied.

The app must cache the body, ETag and its own last successful-check time **per account**; either 200 or 304 advances
that check time. A changed settings tag requires recalculation even if block/expense cursors are unchanged.
Settings, blocks and expenses are independent reads, not an atomic snapshot. The app must coordinate refreshes
and use the oldest relevant successful-check time when describing calculation freshness; it must not present a
partially refreshed result as fully synced. Cached figures may be stale offline. This contract does not replace
the server's source-of-truth validation or let a client infer settings from computed Home totals.

### GET /api/v1/tax/summary

Added by FB-050 for NA-08 and NA-13. Token required: yes. The bearer token selects the account.

Request: optional `year` query parameter, an integer from 2000 through 2100 inclusive. When omitted, the year
defaults to the current year in the account's time zone.

- 200 returns the complete summary. Money fields are two-decimal strings, `percent` is a decimal string in
  percentage points (for example, `"25.50"` for 25.5%), and dates are ISO local dates.
  - `year` is the selected integer tax year. `netYearToDate` is net earnings for the entire selected calendar
    year, using the same blocks, expenses and settings as the website.
  - `reserveToDate` is the estimated reserve based on the driver's configured percentage applied to positive
    annual net earnings. `paid` is the total of all recorded payments for the year. `remaining` is the
    reserve less that total, floored at zero.
  - `thisWeekNet` is net earnings for the current Monday through Sunday in the account's time zone, even when
    a different tax year is selected. `thisWeekSetAside` is the estimate based on the configured percentage
    and positive net for that week.
  - `nextDueDate` is the first configured due date in the selected year's schedule on or after the account's
    current date. It is null after the final due date passes.
  - `quarters` contains four entries in estimated-payment period order. Each has the integer `quarter` (1–4),
    inclusive period dates `from` and `to`, configured `dueDate`, period net earnings `net`, estimated
    `setAside` based on the configured percentage and positive period net, and `paid` for payments assigned
    to that quarter. Payments without a quarter count toward annual `paid` only.
  - With no configured percentage, `percent`, `reserveToDate`, `remaining`, `thisWeekSetAside` and every
    quarter's `setAside` are null. A saved zero percentage yields zero estimates. The payload contains payment
    totals only.
- 304 has an empty body when an authenticated `If-None-Match` matches the current representation. Both 200 and
  304 send a strong `ETag` and `Cache-Control: private, no-cache`. A changed representation returns 200 with a
  new ETag. Store the summary, its ETag and the last successful fetch or revalidation time per account and
  selected tax year. Either 200 or 304 advances that time. The summary has no delta cursor.
- 400 returns `{"error":"invalid","fields":{"year":"invalid"}}` for a malformed or out-of-range year.
- 401 returns `{"error":"unauthorized"}` when the bearer token is missing or invalid, including requests with
  only a website session.
- 404 when the API is disabled (see Availability).

### GET /api/v1/standing

Added by FB-057 for NA-08. Token required: yes. Read-only; the bearer token selects the account.

Request: optional `days` query parameter, a base-10 integer from 7 through 365 inclusive. When omitted, `days`
defaults to 90.

200 (made-up example):

```json
{
  "from": "2026-08-23",
  "to": "2026-11-20",
  "current": {
    "recordedOn": "2026-10-14",
    "level": "fair",
    "note": "After late forfeit"
  },
  "entries": [
    {"recordedOn": "2026-06-01", "level": "great", "note": null},
    {"recordedOn": "2026-10-14", "level": "fair", "note": "After late forfeit"}
  ],
  "events": [
    {"date": "2026-10-10", "startTime": "09:00", "station": "DXX1", "kind": "late_forfeit"},
    {"date": "2026-10-18", "startTime": "14:30", "station": "VEA7", "kind": "forfeited"},
    {"date": "2026-11-02", "startTime": "18:00", "station": "BDL4", "kind": "cancelled"}
  ]
}
```

- `from` and `to` are inclusive ISO local dates in the account's time zone (`to` is today in that zone; `from` is
  `to` minus `days - 1`).
- `current` is the account's latest logged standing entry overall (which may have `recordedOn` earlier than `from`),
  or `null` when nothing has ever been logged.
- `entries` starts with the carry-in entry in force on `from` (the latest entry with `recordedOn` before `from`,
  keeping its real `recordedOn` date), if any, followed by entries logged from `from` through `to` inclusive in
  ascending `recordedOn` order. Empty (`[]`) when no entry applies.
  - `level` is a lowercase standing code: `fantastic`, `great`, `fair`, or `at_risk`. Standing is only what the driver
    logged; the server never calculates it.
  - `note` is the trimmed driver note, or `null` when unset or blank.
- `events` lists non-deleted forfeited and cancelled blocks with `date` from `from` through `to` inclusive, ordered
  oldest first (`date` ascending, then `startTime` ascending). Empty (`[]`) when none exist. Scheduled, completed,
  and soft-deleted blocks are excluded.
  - `date` is an ISO local date (`YYYY-MM-DD`); `startTime` is a local time with no seconds (`HH:mm`); `station` is
    the block's station string.
  - `kind` is a lowercase event code: `forfeited`, `late_forfeit`, or `cancelled`.
- 304 has an empty body when an authenticated `If-None-Match` matches the current representation. Both 200 and 304
  send a strong `ETag` and `Cache-Control: private, no-cache`. Any change to the returned standing entries, in-window
  block events, or the account's local date returns 200 with the updated body and a new ETag. Store the response, its
  ETag, and the last successful fetch or revalidation time per account and window (`days`). Either 200 or 304 advances
  that time. The response has no delta cursor.
- 400 returns `{"error":"invalid","fields":{"days":"invalid"}}` for a malformed or out-of-range `days` value.
- 401 returns `{"error":"unauthorized"}` when the bearer token is missing or invalid, including requests with only a
  website session.
- 404 when the API is disabled (see Availability).

### GET /api/v1/account

Added by FB-005. Token required: yes.

200: `{"email":"driver@example.com","displayName":"Test Driver","language":"es","timeZone":"America/New_York","emailVerified":true,"twoFactorMethod":"app"}`.

`language` is `en`, `es` or null (follow device); `twoFactorMethod` is `app`, `email` or null (off).
This is also the account shape in a successful sign-in response. The token chooses the account.
Missing, malformed, unknown, expired or revoked tokens return 401 `unauthorized`, without a redirect.
Browser sessions and remember-me cookies cannot authenticate these endpoints.

### GET /api/v1/shifts

Added by FB-033. Token required: yes. Query: optional `updatedSince` (see Sync).

200:

```json
{
  "full": false,
  "cursor": "djE6MTc2MzA0NjAwMDow",
  "shifts": [
    {
      "id": 41, "station": "DXX1", "date": "2026-11-10", "startTime": "18:30", "endTime": "21:30",
      "status": "completed", "statusChangedAt": null, "lateForfeit": false,
      "basePay": "72.00", "tips": "18.50", "totalPay": "90.50", "earnedPay": "90.50",
      "timeWorked": 180, "hourlyRate": "30.17", "miles": "41.5", "earningsPerMile": "2.18",
      "details": {
        "actualStart": "18:25", "actualEnd": "21:10", "actualMinutes": 165, "actualHourlyRate": "32.91",
        "finishedEarlyMinutes": 20, "odometerStart": null, "odometerEnd": null,
        "stops": 38, "packages": 52, "returns": 1, "minutesPerStop": "4.3", "returnsRate": "1.9"
      },
      "createdAt": "2026-11-10T23:40:12Z", "updatedAt": "2026-11-11T01:02:03Z"
    }
  ],
  "deleted": [38, 40]
}
```

- Blocks in Recently deleted are not in `shifts`; they appear in `deleted` on a later sync. Order: date, start time, id.
- `timeWorked` and `actualMinutes` are minutes. `earnedPay` is what counts toward earnings (zero for scheduled and
  forfeited blocks). Scheduled blocks never count toward earnings, hours or net.
- A block carries only figures that depend on the block itself. Net, vehicle cost, linked expenses and the forfeit
  deadline also depend on expenses and settings, which don't change the block, so a sync wouldn't refresh them; Home has
  the net totals.
- 400 `invalid`: an unreadable cursor. 401 `unauthorized`: as for every endpoint.

### GET /api/v1/shifts/{id}

Added by FB-033. Token required: yes.

200 with one block in the same shape. 404 `{"error":"not_found"}` for an id that doesn't exist, belongs to another
account or is in Recently deleted. 400 `invalid` for an id that isn't a number. `ETag`/`304` as in Sync.

### Block-write validation and conflicts

Added by NA-05. Every write below requires a bearer token and a UUID `Idempotency-Key`. JSON bodies use the same
lower-case status codes, ISO dates/local times and string decimals as reads. Every change to an existing block also
requires `expectedUpdatedAt`, copied from the block the driver reviewed; DELETE carries it as a query parameter.
Keep the exact request and key in the outbox until acknowledged. Resolve a conflict with the current block and
submit the revised action with a fresh key.

- `400 {"error":"invalid","fields":{"station":"invalid"}}`: missing/invalid fields, with property paths from
  the website's validation (`details.stops`, for example). Missing versions use `"expectedUpdatedAt":"required"`;
  missing, blank or malformed keys use `"Idempotency-Key":"invalid"`.
- `400 {"error":"invalid","reason":"error.shift.scheduledNoTips"}`: a website business rule refused the action.
  `reason` is a stable website message key, never a translated sentence. Malformed JSON, dates, times, statuses or
  ids return `400 {"error":"invalid"}`. Submitted values are never echoed in validation errors.
- `409 {"error":"conflict","current":{...}}`: `expectedUpdatedAt` differs from the stored version at microsecond
  precision. `current` has the GET block shape. No changes are saved. A successful retry is acknowledged before
  checking this version, so the original action cannot conflict with itself or overwrite a later website change.
- `409 {"error":"idempotency_conflict"}`: this account already used the key for a different successful action.
- `404 {"error":"not_found"}`: unknown id, another account's block, or a deleted block except on restore. A restore
  also returns 404 for a live block or an expired deletion. A successful old retry remains acknowledged in these cases.
- `401 {"error":"unauthorized"}`: missing, invalid, expired or revoked bearer token; website cookies do not count.

Validation, status transitions, earnings and details calculations all use `ShiftService`, as the website does.
Scheduled blocks never count toward earnings. Start, complete and not-worked are different actions: starting only
records an actual start time; completing makes the block count as earned; not-worked removes the worked details.

### POST /api/v1/shifts

Added by NA-05. Token required: yes.

Request:

```json
{
  "station": "DXX1", "date": "2026-11-13", "startTime": "10:30", "endTime": "13:30",
  "basePay": "72.00", "tips": "0.00", "status": "scheduled",
  "miles": null, "details": null
}
```

Required: station (nonblank, at most 255 characters), date, startTime, endTime, nonnegative basePay and tips, and
status. Completed and scheduled blocks need positive basePay. Scheduled blocks cannot have tips or miles; cancelled
and forfeited blocks cannot have tips. Miles is optional, nonnegative, at most seven integer digits and one decimal.

Optional `details` has `actualStart`, `actualEnd`, `odometerStart`, `odometerEnd`, `stops`, `packages`, `returns`.
Odometers are nonnegative, at most eight integer digits and one decimal; the end cannot precede the start. Without
explicit miles, both odometers fill in the miles from their difference. Route counts are integers from 0 to 9999;
returns cannot exceed packages. Actual end needs actual start and a completed block. A scheduled block may have
actualStart but no actualEnd, route counts or odometer readings. All website details rules apply.

201 `{"id":41}` with `Location: /api/v1/shifts/41`, including a retry of the same successful create. Other responses
are listed under Block-write validation and conflicts. No duplicate-detection heuristic is added: distinct keys can
create distinct blocks with identical values, as on the website.

### PUT /api/v1/shifts/{id}

Added by NA-05. Token required: yes.

Request: the create fields plus required `expectedUpdatedAt`, for example `"2026-11-13T15:20:00Z"`. Station, date,
times, basePay and tips are required replacement values. Status is optional and keeps the saved status when absent.
Absent/null miles clears miles. Absent/null details keeps saved details, subject to status rules; supplying details
replaces every details field, so null/absent fields inside it clear saved values. 200 `{"id":41}`.

Directly editing or changing a completed, cancelled or forfeited block to scheduled is refused by the website's
rules. For a completed block mistakenly marked worked, use not-worked below.

### PATCH /api/v1/shifts/{id}/status

Added by NA-05. Token required: yes.

Request: required `status` and `expectedUpdatedAt`; optional `basePay`, `tips`, `miles`, `details`.
For example `{"status":"completed","tips":"8.50","expectedUpdatedAt":"2026-11-13T15:20:00Z"}`.
200 `{"id":41}`.

Completing keeps offered pay and saved tips unless supplied. Moving to cancelled/forfeited starts pay at zero unless
supplied, and clears tips unless supplied (nonzero tips are invalid). Missing miles preserves the saved distance
except for scheduled; omitted details preserves valid saved details. Existing website rules decide all transitions.
There is no separate complete endpoint: this status operation completes a scheduled block.

### POST /api/v1/shifts/{id}/start

Added by NA-05. Token required: yes. Request: `{"expectedUpdatedAt":"2026-11-13T15:20:00Z"}`.
200 `{"id":41}`. Records now, to the minute in the account's time zone, as actualStart and clears actualEnd. The block
stays scheduled. Only allowed from 15 minutes before its start through its end, and only for scheduled blocks.

### POST /api/v1/shifts/{id}/not-worked

Added by NA-05. Token required: yes. Request: `{"expectedUpdatedAt":"2026-11-13T15:20:00Z"}`.
200 `{"id":41}`. Only completed blocks qualify. Returns the block to scheduled, retaining offered base pay and linked
expenses while clearing tips, miles, actual times, odometers, route counts and late-forfeit judgement (FB-012).

### DELETE /api/v1/shifts/{id}

Added by NA-05. Token required: yes. No body. Required query: `expectedUpdatedAt=<ISO instant>` (URL-encode it).
204 with no body, including successful retries. Soft-deletes the block into Recently deleted; linked expenses keep
the website's behavior. It appears in `deleted` on the next change-only sync.

### POST /api/v1/shifts/{id}/restore

Added by NA-05. Token required: yes. Request: `{"expectedUpdatedAt":"2026-11-13T15:20:00Z"}`.
200 `{"id":41}`. Restores only this account's deleted block when deleted less than 30 days ago. At exactly 30 days,
restoration is refused even if the daily purge has not run. Use the saved block's updatedAt: delete and restore move
the sync change time without changing the website's updatedAt. The restored block arrives in `shifts` on sync.
No permanent-delete, empty-trash or batch-restore API is added by NA-05.

### POST /api/v1/shifts/import-preview

Added by NA-07. Token required: yes, plus `Idempotency-Key`. Reads one block screenshot with the website's import
(same parser, warnings, status suggestion and duplicate check) and returns what the website's review shows. It saves
nothing and keeps nothing: no copy of the screenshot, its text or the values read is stored, cached or logged.

Request: `multipart/form-data` with one file part, `screenshot`. PNG or JPEG, at most 5 MB (6 MB for the whole
request), at most 12,000 px a side and 30 megapixels. One screenshot per request; send a batch one at a time. Other
parts are ignored. Check the type and the 5 MB size on the phone before sending, as the website does: the server
answers an oversize upload with `413`, but on a large one a proxy or client may see the connection close first, so
treat a dropped connection on an upload over 5 MB as `too_large` rather than retrying it.

**The screenshot's key.** Create one UUID per screenshot when it is picked or shared, send it on its preview, and save
that screenshot with the same key, whichever save the driver chooses:

- add it as a block: `POST /api/v1/shifts`, with the status the driver chose (worked or not yet worked);
- or, for a `scheduled_match`, complete that scheduled block: `PATCH /api/v1/shifts/{id}/status` to `completed`,
  with that block's `expectedUpdatedAt`.

Keys are shared across the account's writes, so once one of the two has landed the other answers
`409 idempotency_conflict`, and a screenshot's pay can never be counted twice, even after an unanswered save. If a
preview's key was already used by a successful block write (any block action, in practice this screenshot's save),
the answer is `409 already_saved` with that block's id and the image isn't read again: drop the waiting screenshot. A preview itself uses up no key. There is no server check for duplicates inside a
batch: compare with the blocks this batch already saved, as the website does.

200, with `Cache-Control: no-store`:

```json
{
  "meanConfidence": 87,
  "lines": [{"index": 0, "text": "Windsor (DCY1)"}, {"index": 1, "text": "Thursday, 11/12"}],
  "rawText": null,
  "shifts": [{
    "station": {"value": "DCY1", "level": "high", "confidence": 95, "lineIndex": 0},
    "date": {"value": "2026-11-12", "level": "high", "confidence": 93, "lineIndex": 1},
    "startTime": {"value": "04:00", "level": "medium", "confidence": 72, "lineIndex": 2},
    "endTime": {"value": "07:30", "level": "medium", "confidence": 72, "lineIndex": 2},
    "basePay": {"value": "72.00", "level": "high", "confidence": 90, "lineIndex": 4},
    "tips": {"value": "18.50", "level": "high", "confidence": 85, "lineIndex": 5},
    "suggestedStatus": "completed",
    "warnings": [{"code": "medium_confidence", "severity": "info", "field": "startTime"}],
    "duplicates": [{"shiftId": 41, "kind": "scheduled_match"}]
  }]
}
```

- `meanConfidence` (0-100) is the website's read-quality bar.
- `lines` are the text read, in order, as the website's "View extracted text" panel shows it. `rawText` is the
  fallback text, sent only when `lines` is empty, otherwise null.
- Each field has `value` (null when not read), `level` (`high`, `medium`, `low` or `missing`), `confidence`
  (0-100, null when not read) and `lineIndex` (the line it came from, to highlight; null when there is none). The
  website shows a badge on a field unless it is `high` and at least 90: "Missing", or "N% read".
- `suggestedStatus` is `completed` or `scheduled` (a block that starts after now in the account's time zone). It is a
  hint: the driver chooses.
- `warnings` are codes, never sentences: `poor_image`, `tips_exceed_base`, `missing_field`, `low_confidence`,
  `medium_confidence`, `future_date`, `old_date`, `overnight`, `long_shift`, `short_shift`, `pay_out_of_range`.
  `severity` is `error`, `warning` or `info`; `field` names the field, or is null.
- `duplicates` are this account's completed or scheduled blocks the screenshot overlaps by 30 minutes or more:
  `exact` or `likely` (already logged), or `scheduled_match` (a scheduled block this completed screenshot most likely
  finishes). Look the block up in the synced cache or with `GET /api/v1/shifts/{id}`.
- `shifts` is an array to match the website; it always has one entry today.

Not returned: the file name, type and size, line positions, per-line confidence, the year the parser assumed,
the source line range and translated messages.

The read text is screenshot content: keep the answer in memory for the review only, never in the outbox, a database or
logs.

Errors (every one with `Cache-Control: no-store`; none repeats the screenshot, its text or its file name):

- `400 {"error":"invalid","reason":"error.import.<key>"}`: `noScreenshot`, `unsupportedType` (not PNG or JPEG),
  `notReadableImage`, `couldNotRead`, `tooLargeSides`, `tooLargeMegapixels`.
- `400 {"error":"invalid","fields":{"Idempotency-Key":"invalid"}}`: missing or malformed key.
- `400 {"error":"invalid"}`: not a multipart request, or a malformed multipart body.
- `409 {"error":"already_saved","id":41}`: this key was already used by a successful write to that block.
- `409 {"error":"idempotency_conflict"}`: this key was used for an expense or payout write.
- `413 {"error":"too_large"}`: over the 5 MB file or 6 MB request limit. The limit is checked before the token, so
  an oversize upload without a token also gets `413`, not `401`; nothing is decoded or read either way.
- `429 {"error":"busy_account"}` with `Retry-After` (seconds): this account already has a preview being read. At most
  one per account at a time, per server instance.
- `503 {"error":"busy"}`: another screenshot (any account) is being read and this one waited 20 seconds.
- `500 {"error":"read_failed"}`: the reader failed.
- `401`, and `404` while the API is off.

Reading is slow on the hosted server: one screenshot at a time across all drivers, and the first request after a quiet
spell may wait for the server to wake. Send previews one at a time, allow at least a minute, and back off on `429` and
`503`.

### GET /api/v1/home

Added by FB-033. Token required: yes. No parameters: the server works out today from the account's time zone.

200:

```json
{
  "today": "2026-11-13",
  "week": {"from": "2026-11-09", "to": "2026-11-15", "totalShifts": 2, "totalEarnings": "170.50", "...": "..."},
  "lastSevenDays": {"from": "2026-11-07", "to": "2026-11-13", "...": "..."},
  "goals": {"basis": "gross", "week": null, "month": {"goal": "2000.00", "earned": "640.50", "...": "..."}},
  "missingMiles": [37, 41]
}
```

- `week` is Monday to Sunday containing `today`; `lastSevenDays` ends on `today`. Both have the website's statistics
  for those dates: `totalShifts`, `totalBasePay`, `totalTips`, `totalEarnings`, `averagePayPerShift`,
  `totalTimeWorked`, `averageHourlyEarnings`, `averageHourlyBasePay`, `averageHourlyTips`, `averageBasePerShift`,
  `averageTipsPerShift`, `tipsShareOfEarnings`, `averageShiftMinutes`, `totalMiles`, `mileageCost`, `totalExpenses`,
  `totalDeductions`, `netEarnings`, `netHourlyRate`, `earningsPerMile`, `netMargin`, `expensesByCategory` (category
  code to money), `vehicleCost`, `outOfPocket`, `netPerShift`, `vehicleCostMethod`, `mileageRate`,
  `rollingSevenDayMinutes`, `scheduledShifts`, `scheduledMinutes`, `expectedPay`, `needsConfirmation`,
  `cancelledShifts`, `forfeitedShifts`, `forfeitedThisMonth`, `timedShifts`, `clockedMinutes`, `clockedHourlyRate`,
  `averageFinishedEarlyMinutes`, `lateForfeitedShifts`, `lateForfeitedThisMonth` and `blocksWithoutMiles`. Money is a
  string; minutes and counts are numbers.
- `goals` is the weekly and monthly goal progress on the driver's basis; a goal that isn't set is `null`. Each has
  `goal`, `earned`, `planned`, `remaining`, `percent`, `onTrack`, `blocksToGo`, `averageBlockPay`,
  `averageBlockMinutes`, `periodStart` and `periodEnd`.
- `missingMiles` lists the ids of completed blocks from the last 7 days with no miles entered, newest first.
- Scheduled blocks never count toward these figures. `ETag`/`304` as in Sync.

### GET /api/v1/expenses

Added by NA-06. Token required: yes. Query: optional `updatedSince` (see Sync).

200:

```json
{
  "full": false,
  "cursor": "djE6MTc2MzA0NjAwMDow",
  "expenses": [
    {
      "id": 17, "date": "2026-11-12", "category": "fuel", "amount": "12.50", "note": "Fuel stop", "shiftId": 41,
      "createdAt": "2026-11-12T23:40:12Z", "updatedAt": "2026-11-12T23:40:12Z"
    }
  ],
  "deleted": [12]
}
```

- Categories: `fuel`, `toll`, `parking`, `maintenance`, `other`. `shiftId` is null for an expense not linked to a block.
- Expenses in Recently deleted are not in `expenses`; they appear in `deleted` on a later sync. A restored expense
  comes back in `expenses`. Order: date, id.
- **Which expenses count toward net.** An expense carries only what is stored on it. Whether it counts depends on its
  block, and a block change doesn't send the expense again, so the phone decides from its block cache, with the
  website's rule:
  - not linked (`shiftId` null): counts, by its own date;
  - linked to a block that is `scheduled`: does not count yet; it counts once the block is completed, cancelled or
    forfeited;
  - linked to a block that is in Recently deleted, or to an id the phone doesn't have (a block removed for good):
    counts by its own date, as if not linked, and has no station.
  Home and the website already apply this rule to their totals.
- 400 `invalid`: an unreadable cursor. `ETag`/`304` as in Sync.

### GET /api/v1/expenses/{id}

Added by NA-06. Token required: yes. 200 with one expense in the same shape. 404 `not_found` for an id that doesn't
exist, belongs to another account or is in Recently deleted. `ETag`/`304` as in Sync.

### Expense writes

Added by NA-06. Every write requires a bearer token and a UUID `Idempotency-Key`, and follows the rules under
Block-write validation and conflicts: the same `400 invalid` with `fields`, `409 conflict` with `current` (here in
the GET expense shape), `409 idempotency_conflict`, failed writes don't use up a key, and a successful retry is
acknowledged before the version is checked. Validation and linking use the website's `ExpenseService`.

- `POST /api/v1/expenses` with `{"date":"2026-11-12","category":"fuel","amount":"12.50","note":"Fuel stop",
  "shiftId":41}`. Required: date, category, a positive amount with at most two decimals. Optional: note (at most 255
  characters, trimmed; blank clears it) and shiftId. 201 `{"id":17}` with `Location: /api/v1/expenses/17`, also for a
  retry, and also if a website create already used the same key.
- `PUT /api/v1/expenses/{id}` with the same fields plus required `expectedUpdatedAt`. Every field is replaced; absent
  `note` or `shiftId` clears it. Keeping the same `shiftId` keeps the link even if that block is now in Recently
  deleted. 200 `{"id":17}`. If the expense's `shiftId` names a block the phone doesn't have (removed for good, so the
  server has already cleared the link), send `shiftId` null; sending the old id answers `404` with `field: shiftId`.
- `DELETE /api/v1/expenses/{id}?expectedUpdatedAt=<ISO instant>`: 204. Moves the expense to Recently deleted; it
  appears in `deleted` on the next sync.
- `POST /api/v1/expenses/{id}/restore` with `{"expectedUpdatedAt":"..."}`: 200 `{"id":17}`. Only within 30 days of
  deletion; at exactly 30 days it is refused even before the purge runs. Use the expense's saved `updatedAt`: delete and
  restore don't change it.
- `404 {"error":"not_found"}`: unknown id, another account's expense, or a deleted one except on restore (a restore
  also answers 404 for a live expense or an expired deletion).
- `404 {"error":"not_found","field":"shiftId"}`: `shiftId` names a block that isn't this account's or is in Recently
  deleted, and isn't the expense's current link.

No permanent-delete, empty-trash or batch-restore API is added.

### GET /api/v1/payouts

Added by NA-06. Token required: yes. Query: optional `count`, how many pay periods to return, 1 to 26 (default 2).
Always a full answer (see Sync); `ETag`/`304` as in Sync.

200:

```json
{
  "nextPayout": {
    "payoutDate": "2026-11-17", "from": "2026-11-13", "to": "2026-11-16", "blocks": 1, "earned": "72.00",
    "scheduledBlocks": 1, "scheduledPay": "80.00", "received": null, "difference": null, "note": null,
    "status": "upcoming", "updatedAt": null
  },
  "periods": [
    {
      "payoutDate": "2026-11-13", "from": "2026-11-10", "to": "2026-11-12", "blocks": 2, "earned": "170.50",
      "scheduledBlocks": 0, "scheduledPay": "0.00", "received": "170.50", "difference": "0.00", "note": "Bank",
      "status": "matched", "updatedAt": "2026-11-13T16:02:00Z"
    }
  ]
}
```

- The website's pay periods, newest first, from the account's payout days and lag. `status` is `upcoming`,
  `unchecked`, `matched`, `short` or `over`. `received`, `difference` and `note` are null until a payout is recorded.
- `updatedAt` is when the payout was last recorded, or null with nothing recorded. Send it back as `expectedUpdatedAt`.
- Scheduled blocks never count toward `earned`; they show in `scheduledBlocks` and `scheduledPay`.
- 400 `invalid`: `count` outside 1 to 26 or not a number.

### PUT /api/v1/payouts/{payoutDate}

Added by NA-06. Token required: yes, plus `Idempotency-Key`. Request:
`{"amount":"170.50","note":"Bank","expectedUpdatedAt":"2026-11-13T16:02:00Z"}`. `expectedUpdatedAt` is the period's
`updatedAt`; leave it out or null when nothing was recorded. Amount is 0 to 99999.99 (0 for a payout that never
arrived); note is optional, at most 255 characters. Recording again replaces the amount and note.
200 `{"payoutDate":"2026-11-13"}`, also for a retry.

- `400 {"error":"invalid","reason":"error.payout.notYet"}`: the payout date is after today in the account's time zone.
- `400 {"error":"invalid","reason":"error.payout.notPayoutDay"}`: the date isn't one of the account's payout days.
- `400 invalid` with `fields`, a missing or bad key, or a date that can't be read.
- `409 {"error":"conflict","current":{...}}`: the recorded payout changed since the phone saw it. `current` is
  `{"payoutDate","amount","note","updatedAt"}`, or null when nothing is recorded any more. `409 {"error":"conflict"}`
  without `current` if the website recorded the same payout at the same moment; reload and try again.
- `409 idempotency_conflict`: the key was used for another write.

### DELETE /api/v1/payouts/{payoutDate}

Added by NA-06. Token required: yes, plus `Idempotency-Key`. Query: `expectedUpdatedAt=<ISO instant>` (absent when
nothing was recorded). 204. Forgets what was recorded, so the payout shows as unchecked again; nothing recorded is not
an error. The same `409` answers as above. A retried record after the removal is acknowledged and doesn't bring it back.

Authentication endpoints do not use Idempotency-Key. A code is consumed once; after a lost sign-in response, restart
sign-in. The general retried-write convention above applies to later data endpoints.

While disabled, every method under `/api/v1` returns 404 before browser CSRF or sign-in handling, and API controllers,
the bearer security chain and cleanup job are absent. Schema creation and revocation remain active.
