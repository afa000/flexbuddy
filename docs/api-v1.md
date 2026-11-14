# FlexBuddy API v1

The JSON API the native Android app (`flexbuddy-android`) uses. It is the contract between the two repositories: every
server task that adds or changes an `/api/v1` endpoint updates this page in the same pull request, and app tasks build
against what this page says. The plan is [native-app-plan.md](native-app-plan.md).

Status: FB-005 implements password sign-in, code challenges, device sign-out and the account summary. FB-033 adds
read-only blocks and Home, with change-only sync. The API remains disabled by default.

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
- **Dates and times** are ISO 8601. Dates are the driver's local date (`2026-11-11`), times are local with no offset
  (`18:30`), and instants are UTC with `Z`. The driver's time zone comes from `GET /api/v1/account`.
- **Money** is a string with two decimals (`"72.00"`), never a floating-point number.
- **Earnings rules hold.** Scheduled blocks never count toward earnings, hours or net, same as the website.
- **One account per token.** No request body, path or query parameter can choose another account.
- **Status codes in data:** lower case, for example a block's `status` is `scheduled`, `completed`, `cancelled` or
  `forfeited`. Local times have no seconds (`18:30`). Decimals that aren't money (miles, a rate per mile, a percentage)
  are strings as stored, for example `"41.5"`.

## Sync

Added by FB-033. Every list endpoint follows this; `GET /api/v1/shifts` is the first.

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

Authentication endpoints do not use Idempotency-Key. A code is consumed once; after a lost sign-in response, restart
sign-in. The general retried-write convention above applies to later data endpoints.

While disabled, every method under `/api/v1` returns 404 before browser CSRF or sign-in handling, and API controllers,
the bearer security chain and cleanup job are absent. Schema creation and revocation remain active.
