# FlexBuddy — Google Play store listing draft

Draft for Stage 7. Every statement below describes functionality that exists in the current
release. Recheck it against the live app before submitting, and update it with the privacy
policy and Data safety form if features or data handling change.

## App details

| Field | Value | Limit |
|---|---|---|
| App name | `FlexBuddy: Shift Tracker` (24 characters) | 30 |
| Short description | `Track delivery shifts, earnings, mileage, and expenses in one private place.` (76 characters) | 80 |
| Category | Productivity (alternative: Finance) | — |
| Contact email | `flexbuddysupport@gmail.com` | — |
| Website | `https://flexbuddy.onrender.com` | — |
| Privacy policy | `https://flexbuddy.onrender.com/privacy` | — |
| Account deletion | `https://flexbuddy.onrender.com/delete-account` | — |

The handoff guide suggested `FlexBuddy – Delivery Shift Tracker`, but that is 34 characters and
Play limits app names to 30.

## Full description

```text
FlexBuddy is a private tracker for independent delivery drivers. Log every block, see what you
really earned after costs, and plan the week ahead.

TRACK EVERY SHIFT
• Add shifts from a screenshot: FlexBuddy reads the station, date, times, and pay, and you review
  every value before saving.
• Record base pay, tips, and miles for each block.
• Filter and sort your history by date, station, pay, hours, or hourly rate.

SEE YOUR REAL EARNINGS
• Totals for earnings, hours worked, average pay per shift, and hourly rate.
• Estimated net earnings after mileage and expenses, using standard mileage or actual costs.
• Reports by station, week, month, or year with charts and tables.
• Export your shift and expense history to CSV.

PLAN YOUR SCHEDULE
• Add scheduled blocks, then mark them completed, cancelled, or forfeited.
• A countdown to your next block, a 14-day list, and a monthly calendar.
• Cancelled blocks can record cancellation pay without adding hours.
• Subscribe to your schedule from your phone's calendar, or turn on optional reminders.

LOG EXPENSES
• Fuel, tolls, parking, maintenance, and other costs, optionally linked to a shift.

YOUR DATA STAYS YOURS
• A private account; your history is visible only to you.
• Recently deleted items can be restored for 30 days.
• Download a full backup at any time and restore it later.
• Delete your account and its data from inside the app.
• No ads and no analytics.

Works with a connection; your most recent dashboard and schedule stay viewable when you are
briefly offline.

Earnings, mileage, and net figures are estimates for your own planning and are not tax or
financial advice.

FlexBuddy is an independent app and is not affiliated with, endorsed by, or sponsored by Amazon
or any delivery platform.
```

## Graphics

| Asset | File | Required size | Status |
|---|---|---|---|
| App icon | `android/store_icon.png` | 512 × 512 PNG | The F-and-outline mark redrawn from the provided icon sheet; full-bleed, opaque; Play applies its own corner mask |
| Feature graphic | `android/feature_graphic.png` | 1024 × 500 PNG/JPEG | Original FlexBuddy branding with the new app icon |
| Phone screenshots | `android/store-screenshots/*.png` | 2–8, 16:9 or 9:16, 320–3840 px sides | 1242 × 2208 (exactly 9:16), 24-bit PNG |
| 7-inch tablet screenshots | `android/store-screenshots/tablet-7/*.png` | Up to 8, 16:9 or 9:16, 320–3840 px sides | 1224 × 2176 (exactly 9:16), 24-bit PNG |
| 10-inch tablet screenshots | `android/store-screenshots/tablet-10/*.png` | Up to 8, 16:9 or 9:16, 1080–7680 px sides | 1080 × 1920 (exactly 9:16), 24-bit PNG |

The icon was redrawn as vector shapes from the provided icon sheet on September 14, 2026, so every
size (Play, web app manifest, maskable, Android launcher, splash, and notification) is sharp and
consistent. The Android launcher, splash, and notification icons only reach devices in a new app
bundle (version code 2); the web icons update with the next deploy.

Tablet screenshots use the same capture as the phone set: a 612 × 1088 viewport at 2x for 7-inch
tablets and an 810 × 1440 viewport at 1.33x for 10-inch tablets, both in portrait so the app's
tablet layout fills the frame.

The screenshots were captured on September 14, 2026 from the Play-installed build signed in to the
reviewer account with its sample data. They show the app's web content at a 414 × 736 phone viewport
through Chrome DevTools, without the Android status bar. The update toast and backup reminder were
hidden because they are transient prompts.

Suggested upload order:

1. `01-dashboard.png`: earnings, shifts, hours, and the next scheduled block.
2. `03-schedule.png`: the next block with its countdown, offered pay, and actions.
3. `06-history.png`: shift history with pay, hours, miles, and a forfeited block.
4. `04-expenses.png`: expense totals and the vehicle cost method.
5. `02-earnings-report.png`: the weekly earnings breakdown.
6. `05-import.png`: the screenshot import step (optional).

A screenshot of the import review with parsed fields is not included: it would need a sample
screenshot image, and it must not show another app's screen.

Do not show third-party logos, other apps' screens, or real personal data in any screenshot.
