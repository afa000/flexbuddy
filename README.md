# FlexBuddy

FlexBuddy is a shift and earnings tracker for delivery drivers. It can read shift details from screenshots with OCR, lets you review the detected information before saving it, and summarizes your work and earnings history.

## [Open the live app](https://flexbuddy.onrender.com)

The app is hosted on Render. Because it uses a free web service, the first request after a period of inactivity may take up to about 50 seconds while the service wakes up.

## Features

- Import shift details from PNG or JPEG screenshots
- Create a private account and sign in securely
- Review and correct OCR results before saving
- View, edit, and delete only your own saved shifts
- Track total earnings, base pay, tips, time worked, and average earnings
- Log mileage and fuel, toll, parking, maintenance, or other driver expenses
- Compare gross and net earnings using standard-mileage or actual-expense vehicle costs
- Filter and search history by date or station, then sort by pay, hours, or hourly rate
- Compare earnings by station, ISO week, month, or year with accessible charts and tables
- Plan scheduled blocks, then confirm them as completed, cancelled, or forfeited
- See upcoming blocks, a live countdown to the next one, and a month calendar
- Subscribe to your schedule from Google Calendar or iOS Calendar, or get optional push reminders
- Install the app on Android or iOS and open the last synced dashboard and schedule offline
- Switch between light and dark themes
- Store shift data in PostgreSQL

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

## Run the tests

```powershell
cd flexbuddy
.\mvnw.cmd test
```

The PostgreSQL migration test is skipped unless a test database is configured. It creates
and removes its own isolated schema:

```powershell
$env:FLEXBUDDY_TEST_POSTGRES_URL = 'jdbc:postgresql://localhost:5432/flexbuddy'
$env:FLEXBUDDY_TEST_POSTGRES_USER = 'flexbuddy'
$env:FLEXBUDDY_TEST_POSTGRES_PASSWORD = 'flexbuddy'
.\mvnw.cmd -Dtest=PostgresMigrationTest test
```

## Deployment

The root [`render.yaml`](render.yaml) defines the Render web service and PostgreSQL database. Pushes to `main` are automatically deployed through the connected Render Blueprint.

## Reporting API

Authenticated requests to `GET /shifts`, `GET /shifts/statistics`, and `GET /shifts/reports/earnings` accept the same optional `from`, `to`, `station`, `q`, and `status` filters. `status` takes a comma-separated list such as `scheduled,completed`, or `all`; history defaults to completed, cancelled, and forfeited shifts, while statistics, reports, and CSV export default to completed and cancelled. Shift history also accepts `sort` and `dir`; reports require `groupBy=station|week|month|year`. `GET /shifts/stations` returns the signed-in user's station choices.

Shift create and update requests accept optional `miles`. Shift responses include mileage cost, earnings per mile, linked expenses, net pay, and net hourly rate. The statistics and grouped report endpoints include mileage, cash expenses, deductions, and net earnings. These figures are planning estimates and are not tax advice.

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

Reminders are best-effort. Render's free instance sleeps when idle, so a reminder can arrive late or, if the
instance wakes after the block starts, not in time to help. The calendar feed does not depend on the server being
awake and is the reliable option. On iPhone, push requires iOS 16.4 or later and FlexBuddy installed to the home
screen.

## Install on your phone

- **Android (Chrome):** open the app, then use the Install button on the dashboard or account page, or Chrome's
  menu > Install app.
- **iPhone (Safari):** tap Share, then Add to Home Screen.

The installed app opens full screen. After you have opened the dashboard and schedule online, they open offline
with your last synced data and a banner showing when it was synced; changes are disabled until you reconnect.
Signing out clears the cached data from the device.

## Export, backup, and recovery

All of these require a signed-in session and only ever touch the caller's own data.

| Endpoint | Purpose |
|---|---|
| `GET /shifts/export.csv` | Downloads the filtered shift history as CSV. Accepts the same filters as `GET /shifts`. |
| `GET /account/backup` | Downloads a version 3 `flexbuddy-backup` JSON file with shifts and their status, mileage, expenses, settings, and recently deleted items. It never contains the password hash. |
| `POST /account/restore/preview` | Multipart upload of a backup file (5 MB max). Returns what a restore would do — totals, new, existing, already in Recently deleted, duplicate backup rows, and invalid rows — and stages the file against a one-shot token. One staged backup per session; the token expires after 15 minutes. |
| `POST /account/restore` | Commits the staged restore. Takes the preview `token`, a `mode` of `MERGE` or `REPLACE`, `includeDeleted`, and `acknowledgeReplace`. `REPLACE` moves the current history to Recently deleted first and returns a batch id. |
| `POST /account/restore/{batchId}/undo` | Reverses a `REPLACE` restore: removes the inserted rows and restores the batch that was moved to Recently deleted. |
| `GET /shifts/trash` | Lists Recently deleted shifts. |
| `POST /shifts/{id}/restore` | Restores one shift out of Recently deleted. |
| `POST /shifts/restore-batch/{batchId}` | Restores every shift deleted in one batch — this backs the Undo action on a delete. |
| `DELETE /shifts/trash/{id}` | Permanently removes one shift. |
| `DELETE /shifts/trash` | Permanently removes everything in Recently deleted. |

Version 1 and 2 backups remain restorable, and their shifts restore as completed. Version 2 and later restores remap expense-to-shift links and deduplicates both record types. Replace and Undo operate on shifts and expenses together.

Deleted shifts and expenses stay recoverable for **30 days**. A nightly job purges anything past that cutoff, and opening either trash listing purges expired rows.
