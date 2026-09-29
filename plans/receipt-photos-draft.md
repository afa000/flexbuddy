# Plan 11: Receipt photos

## Goal

Attach a photo of the receipt to a fuel, toll, parking, or maintenance expense from the
phone camera, view it later, and include it in backups. Optionally read the total from
the receipt with the existing OCR.

## Builds on

- `Expense` entity and the expenses screen.
- `ScreenshotPreprocessor` and `TesseractScreenshotTextExtractor` for optional amount
  reading.
- `AccountBackupService` v2 format.

## Design

- **Storage decision first.** Render's free web service has an ephemeral disk, so files
  cannot live on the filesystem. Two workable options:
  1. **Database bytes** (`bytea`) with a hard per-image cap of 400 KB after client-side
     resizing. Simplest, no new service, and Render's free Postgres allows 1 GB. At 400 KB
     a driver logging 300 receipts a year uses 120 MB.
  2. **Object storage** (Cloudflare R2 or Backblaze B2, S3-compatible, free tiers) with
     presigned URLs. More setup, unlimited growth, keeps the database small.
  Recommendation: start with option 1 behind a `ReceiptStore` interface so option 2 can
  replace it without touching controllers.
- **Client-side resize** with a canvas to a 1600 px long edge, JPEG quality 0.8, before
  upload. That is enough to read a receipt and keeps uploads fast on mobile data.
- **Privacy**: receipts are served only to their owner through an authenticated endpoint;
  no public URLs, no caching by the service worker.

## Data and migration

`Vn__receipts.sql`: `receipt (id, owner_id, expense_id unique references expense on delete
cascade, content_type varchar(40), size_bytes int, width int, height int, data bytea,
created_at)`. Index on `expense_id`. Soft-deleting an expense keeps the receipt; purging
the expense cascades.

## API

- `POST /expenses/{id}/receipt` multipart (`receipt` file, JPEG or PNG, 400 KB cap after
  resize; server rejects larger with a clear message).
- `GET /expenses/{id}/receipt` returns the image with `Cache-Control: private, no-store`.
- `DELETE /expenses/{id}/receipt`.
- Optional `POST /expenses/receipt-preview`: runs OCR and returns candidate totals
  (largest currency amount on the receipt, plus any line starting with "Total") with
  confidence, for the quick-add form to prefill the amount.
- `ExpenseResponse`: `hasReceipt`.
- Backup v3: receipts embedded base64 in `expenses[].receipt` when the backup is requested
  with `includeReceipts=true`; default off so the JSON stays small. Restore accepts both.

## Frontend

- Quick-add form: a camera button (`<input type="file" accept="image/*" capture="environment">`)
  that shows a thumbnail, resizes in a worker-free canvas step, and uploads after the
  expense is created. With OCR preview enabled, the amount field fills and gets a
  confidence badge like the import form.
- Expense rows: a small receipt icon; tapping opens a full-screen viewer with pinch zoom
  (native image behaviour inside a scrollable overlay) and a Delete photo action.
- Account page backup card: "Include receipt photos" checkbox with the size estimate.

## Tests

- `ReceiptServiceTest`: size cap, content-type check, owner check on read, cascade on purge.
- `ExpenseControllerTest`: upload, fetch with no-store header, 404 for another user's expense.
- `ReceiptAmountParserTest`: total detection on three sample receipt texts.
- Manual: camera capture on Android and iOS, upload on slow 3G throttling, viewer zoom.

## Acceptance

- A photographed fuel receipt uploads under 400 KB, appears on the expense within a second,
  and is only retrievable while signed in as its owner.
- Backups without receipts are the same size as today; with receipts they include every
  photo and restore them.

## Risks

- Database growth on the free tier. The account page shows "Receipts: 38 MB" and the store
  interface leaves the door open to object storage.
- OCR on receipts is noisier than on app screenshots; the amount prefill is a suggestion
  with a confidence badge, never auto-saved.
