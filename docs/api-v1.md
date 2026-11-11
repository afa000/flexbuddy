# FlexBuddy API v1

The JSON API the native Android app (`flexbuddy-android`) uses. It is the contract between the two repositories: every
server task that adds or changes an `/api/v1` endpoint updates this page in the same pull request, and app tasks build
against what this page says. The plan is [native-app-plan.md](native-app-plan.md).

Status: conventions only. No endpoint exists yet; FB-005 (token sign-in) adds the first ones.

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

None yet.
