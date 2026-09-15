# FlexBuddy — closed test plan (Stage 10)

Google requires personal developer accounts to run a closed test with **at least 12 testers opted in
continuously for at least 14 days** before applying for production access. Confirm the current rule
on the Play Console dashboard, which tracks the tester count.

## Setup

| Item | Value |
|---|---|
| Track | Closed testing - Alpha |
| Release | Version code 1, promoted from Internal testing (the same Play-signed bundle) |
| Countries / regions | All available, so no invited tester is blocked |
| Tester list | A Play Console email list of the testers' Google account emails |
| Feedback | `flexbuddysupport@gmail.com` |
| Opt-in link | Shown on the track's Testers tab after the release is published |

The new launcher, splash, and notification icons ship later in version code 2. Updating the release
does not restart the 14-day period; testers leaving the test does.

## Recruiting

- Invite **15 or more** people so one or two dropouts do not drop the count below 12.
- Each tester needs an Android phone and the Google account email they use for the Play Store.
- Collect those emails and add them to the tester list before sending the opt-in link.
- Track who has opted in; the dashboard shows the current opted-in count.

## Invitation message

```text
Hi! I'm testing FlexBuddy, an app I built for delivery drivers to track shifts, earnings, mileage,
and expenses. Google requires 12 testers for 14 days before it can go public, and I'd really
appreciate your help.

What to do:
1. Send me the Google account email you use for the Play Store on your Android phone.
2. After I add you, open this link on that phone and tap "Become a tester":
   <OPT-IN LINK>
3. Install FlexBuddy from the Play Store link on that page.
4. Create an account and try it (checklist below).
5. Keep the app installed and stay opted in for at least 14 days.

Questions or bugs: flexbuddysupport@gmail.com, or use "Send feedback" on the Play Store page.

Thank you!
```

## Tester checklist

Ask testers to try these at least once and report anything confusing or broken:

1. Create an account and sign in with "Keep me signed in" checked.
2. Close and reopen the app; it should stay signed in.
3. Add a scheduled shift, then mark it completed with pay and miles.
4. Import a shift from a screenshot and review the fields before saving.
5. Log an expense and link it to a shift.
6. Check the dashboard totals and the earnings report.
7. Switch between light and dark themes.
8. Turn on airplane mode, reopen the app, and confirm recent data is viewable but editing is disabled.
9. Optional: turn on reminders in Account and allow notifications.
10. Download a backup from Account.

Testers should not enter real passwords from other services or any sensitive personal data beyond
what the app needs.

## During the test

- Check feedback and the Play Console pre-launch report and Android vitals every few days.
- Ship web-only fixes through Render; they reach testers without a new release.
- For Android shell changes, increment `versionCode`, rebuild, sign interactively, upload to the
  closed track, and record the release in `CHANGELOG-android.md`.

## Before applying for production

Play asks about the closed test when you apply. Keep notes on:

- How testers were recruited and how many stayed opted in.
- The feedback received and what changed in response.
- Why the app is ready for production.
