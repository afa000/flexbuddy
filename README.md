# FlexBuddy

FlexBuddy is a shift and earnings tracker for delivery drivers. It can read shift details from screenshots with OCR, lets you review the detected information before saving it, and summarizes your work and earnings history.

## [Open the live app](https://flexbuddy.onrender.com)

The app is hosted on Render. The Android app is in closed testing on Google Play; see
[Install on your phone](#install-on-your-phone).

## Features

**Logging blocks**

- Import a block from a Flex screenshot (PNG or JPEG), including screenshots taken with Flex in Spanish, and review
  every field before saving. Share a screenshot to FlexBuddy from the phone's share sheet to import it directly.
- Add blocks by hand, with actual start and finish times, stops, packages, returns, and miles from odometer readings
- Plan scheduled blocks, then confirm them as completed, cancelled, or forfeited
- Log fuel, tolls, parking, maintenance, and other expenses, linked to a block or not
- Add blocks, expenses, and block details offline; they sync when the phone reconnects
- A quick-action button for the four most common tasks

**Seeing what you earned**

- Home is built around this week: earnings, a weekly or monthly goal, the next block, and the next payout
- Check each payout against what its blocks earned
- Reports with charts, filters, history, and the best times to work, by station, week, month, or year
- Gross and net earnings with standard-mileage or actual-expense vehicle costs
- A block value calculator that shows how much of an offer is surge
- Track your Flex standing next to forfeits, with a countdown to the forfeit deadline

**Taxes**

- An estimate of what to set aside, reminders before estimated tax due dates, and a printable year summary for a tax
  preparer. These are planning estimates, not tax advice.

**Schedule and reminders**

- Upcoming blocks, a live countdown to the next one, and a month calendar
- Subscribe to your schedule from Google Calendar or iOS Calendar, or get optional push reminders
- Reminders to log missing miles, which can be turned off

**Account and privacy**

- Sign in with a password or Google, with optional two-step sign-in by authenticator app or emailed code
- New accounts confirm their email with a six-digit code; forgotten passwords are reset by email
- Repeated failed sign-ins and sign-ups are limited
- English and Spanish, with a language setting
- Backups you can download and restore, a 30-day Recently deleted, and in-app account deletion
- Light and dark themes
- Every account sees only its own data

## Built with

- Java 21 and Spring Boot
- Spring MVC, Thymeleaf, and Spring Data JPA
- PostgreSQL
- Tess4J and Tesseract OCR
- HTML, CSS, and JavaScript
- Docker and Render

## Run locally

You need Java 21, PostgreSQL, and Tesseract OCR installed.

Create a PostgreSQL database named `flexbuddy`, then provide the connection settings as environment variables. The defaults are:

```text
DB_URL=jdbc:postgresql://localhost:5432/flexbuddy
DB_USER=flexbuddy
DB_PASSWORD=flexbuddy
```

From the application directory, start the app with:

```powershell
cd flexbuddy
.\mvnw.cmd spring-boot:run
```

Then open <http://localhost:8080>.

Email (sign-up codes, two-step codes, password resets) is optional locally. With no `FLEXBUDDY_MAIL_HOST` set, the app
starts and logs that mail isn't configured. Set `FLEXBUDDY_MAIL_LOG_LINKS=true` to have codes and reset links written
to the console instead; never set it in production. Google sign-in appears only when `FLEXBUDDY_GOOGLE_CLIENT_ID` and
`FLEXBUDDY_GOOGLE_CLIENT_SECRET` are set. [`.claude/launch.json`](.claude/launch.json) has ready-made local
configurations for both.

## Run the tests

From the repository root, one command runs both suites and writes a summary to `.ai/artifacts/<task-id>/`:

```powershell
.\scripts\verify.ps1 -TaskId FB-001
```

Or run the Java suite on its own:

```powershell
cd flexbuddy
.\mvnw.cmd test
```

GitHub Actions runs both suites, including the PostgreSQL migration test against Postgres 18, on every push to `main` and every pull request.

The PostgreSQL migration test is skipped unless a test database is configured. It creates
and removes its own isolated schema:

```powershell
$env:FLEXBUDDY_TEST_POSTGRES_URL = 'jdbc:postgresql://localhost:5432/flexbuddy'
$env:FLEXBUDDY_TEST_POSTGRES_USER = 'flexbuddy'
$env:FLEXBUDDY_TEST_POSTGRES_PASSWORD = 'flexbuddy'
.\mvnw.cmd -Dtest=PostgresMigrationTest test
```

The browser scripts have their own tests, run with Node 22 or later and no install step:

```powershell
cd flexbuddy
node --test "src/test/js/*.test.js"
```

They load the scripts from `src/main/resources/static/js` as they are, so a script that
needs the page at load time can't be tested this way; new scripts keep their logic in
functions they export.

## Deployment

The root [`render.yaml`](render.yaml) defines the Render web service and PostgreSQL database. Render deploys a push to `main` once its GitHub checks pass. A commit with a failing test is never deployed.

When the server or a page hits an error, the operator gets an email (`FLEXBUDDY_ALERT_TO`, once mail is configured).

## How changes are made

Work is planned as tasks in `.ai/tasks/`, built on `ai/<task-id>` branches, checked with `scripts/verify.ps1`, and
reviewed before merging. Claude Code, Antigravity, and a local model share this workflow; each task names the tool
that implements it, based on its risk. [`AGENTS.md`](AGENTS.md) holds the rules every coding agent follows, and
[`docs/agent-workflow.md`](docs/agent-workflow.md) explains the tools and the day-to-day loop.

The [driver experience improvement plan](docs/driver-experience-plan.md) turns the November 2026 emulator audit into
prioritized work, acceptance criteria, and validation requirements.

## Reporting API

Authenticated requests to `GET /shifts`, `GET /shifts/statistics`, and `GET /shifts/reports/earnings` accept the same optional `from`, `to`, `station`, `q`, and `status` filters. `status` takes a comma-separated list such as `scheduled,completed`, or `all`; history defaults to completed, cancelled, and forfeited shifts, while statistics, reports, and CSV export default to completed and cancelled. Shift history also accepts `sort` and `dir`; reports require `groupBy=station|week|month|year`. `GET /shifts/stations` returns the signed-in user's station choices.

`POST /shifts` requires a `status` (`COMPLETED`, `SCHEDULED`, `CANCELLED` or `FORFEITED`); a create without one is rejected rather than saved as completed. Shift create and update requests accept optional `miles`. Shift responses include mileage cost, earnings per mile, linked expenses, net pay, and net hourly rate. The statistics and grouped report endpoints include mileage, cash expenses, deductions, and net earnings. These figures are planning estimates and are not tax advice.

## Driver expenses API

All expense and settings endpoints require a signed-in session and are scoped to that account.

| Endpoint | Purpose |
|---|---|
| `GET /expenses` | Lists expenses. Accepts `from`, `to`, `station`, `q`, `category`, and `shiftId`. |
| `POST /expenses` | Creates an expense with date, category, amount, optional note, and optional owned shift id. |
| `PUT /expenses/{id}` | Updates an owned expense. |
| `DELETE /expenses/{id}` | Soft-deletes an owned expense and returns an undo batch header. |
| `GET /expenses/summary` | Returns filtered totals and category subtotals. |
| `GET /expenses/export.csv` | Downloads the filtered expense list as CSV. |
| `GET /expenses/trash` | Lists recently deleted expenses. |
| `POST /expenses/{id}/restore` | Restores one expense. |
| `POST /expenses/restore-batch/{batchId}` | Restores a delete batch for Undo. |
| `DELETE /expenses/trash/{id}` | Permanently deletes one trashed expense. |
| `DELETE /expenses/trash` | Permanently deletes all trashed expenses. |
| `GET /shifts/{id}/expenses` | Lists the expenses linked to an owned shift. |
| `GET /account/settings` | Returns the vehicle cost method and effective mileage rate. |
| `PUT /account/settings` | Saves `STANDARD_MILEAGE` or `ACTUAL_EXPENSES` and a mileage rate. |

## Shift status

Every shift has a status. Scheduled blocks never change earnings, hours, or net figures until they are confirmed.

| Status | Meaning | Earnings | Hours | Miles and linked expenses |
|---|---|---|---|---|
| `SCHEDULED` | An accepted block that has not happened yet | No | No | No |
| `COMPLETED` | Worked and paid | Base pay and tips | Yes | Yes |
| `CANCELLED` | Amazon cancelled the block | Cancellation pay entered as base pay | No | Yes |
| `FORFEITED` | You dropped or missed the block | No | No | Yes |

A shift keeps its id and linked expenses when its status changes. A completed, cancelled, or forfeited shift
cannot move back to scheduled; delete it and add the block again. Nothing completes automatically: a scheduled
block whose end time has passed waits on the Schedule screen for you to confirm it. Importing an earnings
screenshot that overlaps a scheduled block at the same station by 30 minutes or more offers to complete that
block instead of adding a duplicate.

## Schedule, calendar, and reminders API

| Endpoint | Purpose |
|---|---|
| `PATCH /shifts/{id}/status` | Changes the status, with optional `basePay`, `tips`, and `miles`. |
| `GET /shifts/upcoming?days=14` | Scheduled blocks by day, weekly totals, overlaps, the next block, and finished blocks awaiting confirmation. Dates use your saved time zone. |
| `GET /shifts/calendar?month=2026-09` | A per-day summary of one month for the calendar grid. |
| `GET /shifts/{id}.ics` | Downloads one shift as a calendar file with an alarm at your reminder lead time. |
| `GET /calendar/{token}.ics` | Your private subscription feed: every scheduled block plus the last 30 days. It needs no session, so anyone with the link can read it. |
| `POST /account/calendar-token` | Creates or regenerates the feed link. The old link stops working immediately. |
| `PUT /account/reminders` | Saves the time zone, reminder lead time (`30`, `60`, `120`, `720`, or `null` for off), and the confirmation nudge. |
| `PUT /account/time-zone` | Saves only the time zone. The dashboard sends the browser's zone when it differs. |
| `GET /push/public-key` | Reports whether push is configured and returns the VAPID public key. |
| `POST /push/subscriptions`, `DELETE /push/subscriptions` | Adds or removes this browser's push subscription. |

### Push reminders

Push is optional and off until both VAPID keys are set. Generate a key pair once and keep the private key secret:

```powershell
npx web-push generate-vapid-keys
```

Then set these environment variables on the server (on Render, under the web service's Environment settings):

```text
FLEXBUDDY_VAPID_PUBLIC_KEY=<public key>
FLEXBUDDY_VAPID_PRIVATE_KEY=<private key>
FLEXBUDDY_VAPID_SUBJECT=mailto:you@example.com
```

Reminders are best-effort: push delivery depends on the phone and browser, so the calendar feed is the more
reliable option. On iPhone, push requires iOS 16.4 or later and FlexBuddy installed to the home screen.

## Install on your phone

- **Android (Google Play):** FlexBuddy is in closed testing on Google Play. Testers join from an opt-in link, then
  install it from the Play Store. It is the same app as the website, in a full-screen wrapper (a Trusted Web
  Activity, see [`android/`](android/README.md)), and needs Android 7.0 or later.
- **Android (Chrome):** open the site, then use the Install button on Home or Account, or Chrome's menu > Install app.
- **iPhone (Safari):** tap Share, then Add to Home Screen.

The installed app opens full screen. After you have opened Home and the schedule online, they open offline with your
last synced data and a banner showing when it was synced. Blocks, expenses, and block details added offline wait on
the phone and sync when it reconnects; other changes need a connection. Signing out clears the cached data from the
device.

## Export, backup, and recovery

All of these require a signed-in session and only ever touch the caller's own data.

| Endpoint | Purpose |
|---|---|
| `GET /shifts/export.csv` | Downloads the filtered shift history as CSV. Accepts the same filters as `GET /shifts`. |
| `GET /account/backup` | Downloads a version 4 `flexbuddy-backup` JSON file with shifts and their status, mileage, expenses, settings, tax payments, payouts, Flex standing, and recently deleted items. It never contains the password hash. |
| `POST /account/restore/preview` | Multipart upload of a backup file (5 MB max). Returns what a restore would do — totals, new, existing, already in Recently deleted, duplicate backup rows, and invalid rows — and stages the file against a one-shot token. One staged backup per session; the token expires after 15 minutes. |
| `POST /account/restore` | Commits the staged restore. Takes the preview `token`, a `mode` of `MERGE` or `REPLACE`, `includeDeleted`, and `acknowledgeReplace`. `REPLACE` moves the current history to Recently deleted first and returns a batch id. |
| `POST /account/restore/{batchId}/undo` | Reverses a `REPLACE` restore: removes the inserted rows and restores the batch that was moved to Recently deleted. |
| `GET /shifts/trash` | Lists Recently deleted shifts. |
| `POST /shifts/{id}/restore` | Restores one shift out of Recently deleted. |
| `POST /shifts/restore-batch/{batchId}` | Restores every shift deleted in one batch — this backs the Undo action on a delete. |
| `DELETE /shifts/trash/{id}` | Permanently removes one shift. |
| `DELETE /shifts/trash` | Permanently removes everything in Recently deleted. |

Versions 1 to 3 remain restorable; version 1 and 2 shifts restore as completed. Version 2 and later restores remap expense-to-shift links and deduplicate both record types. Replace and Undo operate on shifts and expenses together.

Deleted shifts and expenses stay recoverable for **30 days**. A nightly job purges anything past that cutoff, and opening either trash listing purges expired rows.

## Built alongside

[**GitDigest**](https://github.com/afa000/gitdigest) is the other half of this pair: a Java command-line tool for Git analytics, changelogs, and release notes, where FlexBuddy is a web app that serves its own APIs. GitDigest uses FlexBuddy's commit history as one of its real-world test subjects.
