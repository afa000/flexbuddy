# FlexBuddy API v1

The JSON API the native Android app (`flexbuddy-android`) uses. It is the contract between the two repositories: every
server task that adds or changes an `/api/v1` endpoint updates this page in the same pull request, and app tasks build
against what this page says. The plan is [native-app-plan.md](native-app-plan.md).

Status: FB-005 implements password sign-in, code challenges, device sign-out and the account summary.
The API remains disabled by default.

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

Authentication endpoints do not use Idempotency-Key. A code is consumed once; after a lost sign-in response, restart
sign-in. The general retried-write convention above applies to later data endpoints.

While disabled, every method under `/api/v1` returns 404 before browser CSRF or sign-in handling, and API controllers,
the bearer security chain and cleanup job are absent. Schema creation and revocation remain active.
