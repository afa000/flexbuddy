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

Authenticated requests to `GET /shifts`, `GET /shifts/statistics`, and `GET /shifts/reports/earnings` accept the same optional `from`, `to`, `station`, and `q` filters. Shift history also accepts `sort` and `dir`; reports require `groupBy=station|week|month|year`. `GET /shifts/stations` returns the signed-in user's station choices.

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

## Export, backup, and recovery

All of these require a signed-in session and only ever touch the caller's own data.

| Endpoint | Purpose |
|---|---|
| `GET /shifts/export.csv` | Downloads the filtered shift history as CSV. Accepts the same filters as `GET /shifts`. |
| `GET /account/backup` | Downloads a version 2 `flexbuddy-backup` JSON file with shifts, mileage, expenses, settings, and recently deleted items. It never contains the password hash. |
| `POST /account/restore/preview` | Multipart upload of a backup file (5 MB max). Returns what a restore would do — totals, new, existing, already in Recently deleted, duplicate backup rows, and invalid rows — and stages the file against a one-shot token. One staged backup per session; the token expires after 15 minutes. |
| `POST /account/restore` | Commits the staged restore. Takes the preview `token`, a `mode` of `MERGE` or `REPLACE`, `includeDeleted`, and `acknowledgeReplace`. `REPLACE` moves the current history to Recently deleted first and returns a batch id. |
| `POST /account/restore/{batchId}/undo` | Reverses a `REPLACE` restore: removes the inserted rows and restores the batch that was moved to Recently deleted. |
| `GET /shifts/trash` | Lists Recently deleted shifts. |
| `POST /shifts/{id}/restore` | Restores one shift out of Recently deleted. |
| `POST /shifts/restore-batch/{batchId}` | Restores every shift deleted in one batch — this backs the Undo action on a delete. |
| `DELETE /shifts/trash/{id}` | Permanently removes one shift. |
| `DELETE /shifts/trash` | Permanently removes everything in Recently deleted. |

Version 1 backups remain restorable. Version 2 restore remaps expense-to-shift links and deduplicates both record types. Replace and Undo operate on shifts and expenses together.

Deleted shifts and expenses stay recoverable for **30 days**. A nightly job purges anything past that cutoff, and opening either trash listing purges expired rows.
