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

## Deployment

The root [`render.yaml`](render.yaml) defines the Render web service and PostgreSQL database. Pushes to `main` are automatically deployed through the connected Render Blueprint.

## Reporting API

Authenticated requests to `GET /shifts`, `GET /shifts/statistics`, and `GET /shifts/reports/earnings` accept the same optional `from`, `to`, `station`, and `q` filters. Shift history also accepts `sort` and `dir`; reports require `groupBy=station|week|month|year`. `GET /shifts/stations` returns the signed-in user's station choices.

## Export, backup, and recovery

All of these require a signed-in session and only ever touch the caller's own shifts.

| Endpoint | Purpose |
|---|---|
| `GET /shifts/export.csv` | Downloads the filtered shift history as CSV. Accepts the same filters as `GET /shifts`. |
| `GET /account/backup` | Downloads a `flexbuddy-backup` JSON file with the account profile plus active and recently deleted shifts. It never contains the password hash. |
| `POST /account/restore/preview` | Multipart upload of a backup file (5 MB max). Returns what a restore would do — totals, new, existing, already in Recently deleted, invalid rows — and stages the file against a one-shot token. One staged backup per session; the token expires after 15 minutes. |
| `POST /account/restore` | Commits the staged restore. Takes the preview `token`, a `mode` of `MERGE` or `REPLACE`, `includeDeleted`, and `acknowledgeReplace`. `REPLACE` moves the current history to Recently deleted first and returns a batch id. |
| `POST /account/restore/{batchId}/undo` | Reverses a `REPLACE` restore: removes the inserted rows and restores the batch that was moved to Recently deleted. |
| `GET /shifts/trash` | Lists Recently deleted shifts. |
| `POST /shifts/{id}/restore` | Restores one shift out of Recently deleted. |
| `POST /shifts/restore-batch/{batchId}` | Restores every shift deleted in one batch — this backs the Undo action on a delete. |
| `DELETE /shifts/trash/{id}` | Permanently removes one shift. |
| `DELETE /shifts/trash` | Permanently removes everything in Recently deleted. |

Deleted shifts stay recoverable for **30 days**. A nightly job purges anything past that cutoff, and opening the trash listing purges the caller's own expired rows.

