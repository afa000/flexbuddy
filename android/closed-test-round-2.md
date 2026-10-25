# FlexBuddy — second closed test

Google turned down the first production access application on October 24, 2026 and asked for 14 more days of closed
testing with real testers ([production-access.md](production-access.md) says why). This round fixes what Google could
not see the first time: updates to the app made because of tester feedback, and testers who use the app the way
drivers do.

## What changes from the first round

| First round | This round |
|---|---|
| One update (version 2), on day 12 of a 38-day test | A release on day 0 and at least two more during the 14 days, each answering feedback |
| Web fixes shipped through Render only, invisible to Play | Web fixes still ship through Render, and each batch goes out with a new closed-track release whose notes say what changed |
| Testers partly from a tester-exchange app | Mostly delivery drivers, the app's real audience |
| 20 testers | 25 invited so dropouts don't matter; at least 12 must stay opted in for 14 days in a row |
| Feedback summarised from memory | A dated feedback log, kept as feedback arrives |

## Releases

Play only counts what reaches the closed track. FlexBuddy is a Trusted Web Activity, so most changes are web changes,
but each batch of feedback fixes still goes out as a new version so testers get an update and Play sees one.

For each release:

1. Raise `appVersionCode` (and `appVersionName`) in `twa-manifest.json`, and make any real wrapper change the
   feedback calls for.
2. Build with Bubblewrap. The owner signs it in an interactive terminal (never an agent, never in a script).
3. Upload to Closed testing - Alpha with release notes in English and Spanish that name the feedback, for example
   "Fixed: the Reports totals cut off on small phones (reported by testers)".
4. Record it in `CHANGELOG-android.md` and link the feedback log entries it answers.

Planned releases:

- **Day 0, version 3:** everything shipped since version 2, listed in the release notes: offline adding, Spanish,
  Google and two-step sign-in, password reset, reminders, goals, payout checks, tax estimates and the screenshot import
  fixes.
- **Around day 5, version 4:** fixes for the first week's feedback.
- **Around day 11, version 5:** fixes for the second week's feedback.

## Recruiting

- Invite 25 people. Aim for at least 15 who drive for Amazon Flex or another delivery app: drivers you know, people
  at your station, local driver groups. Friends and family can fill the rest.
- Don't rely on tester-exchange apps. If you use one, they're a top-up, not the main group, and the application
  should say so honestly.
- Every tester sends the Google account email they use for the Play Store, is added to the `testers` list, then opts
  in with the link on that phone. Track who has opted in. Only testers who stay opted in for 14 days in a row count,
  and one who leaves and rejoins starts their 14 days again.

## Keeping testers engaged

Send a short message on days 0, 4, 8 and 12. Each one asks for something drivers really do, and ends with "reply
with anything confusing or broken":

- **Day 0:** create an account, add this week's scheduled blocks, import one block from a Flex screenshot.
- **Day 4:** mark completed blocks with pay and miles, log fuel or another expense, check the week on Home.
- **Day 8:** look at Reports for the last two weeks, set a weekly goal, turn on reminders.
- **Day 12:** set up tax estimates, download a backup, try the app in airplane mode, switch the language.

This covers the features few testers tried in the first round (taxes, backups) and spreads use over the whole 14 days.

## Feedback log

Keep `android/closed-test-feedback.md` as feedback arrives, one line per item:

```text
| Date | Tester | Channel | What they reported | What changed | Version |
| 2026-10-28 | T07 | email | ... | ... | 4 |
```

- Use a tester number, never a name or email.
- Channels: the in-app "Send feedback" link, `flexbuddysupport@gmail.com`, Play's private feedback, in person.
- Note feedback you decided not to act on, and why.

## Before applying again

Check in Play Console:

- At least 12 testers opted in for 14 days in a row (the dashboard shows when this is met).
- Versions 3, 4 and 5 are on the closed track.
- Android vitals shows no crashes or ANRs, and the pre-launch report has no blocking issues.
- If sign-in is required for review, the Play Reviewer test account still works (read-only; never add data through it).

Then answer with specifics from the feedback log and the release history:

- **Recruitment:** how many testers, how many are delivery drivers, how they were found.
- **Engagement:** what testers did, over how many days, and which features they used, from the day 0–12 tasks and
  what they sent back.
- **Feedback:** three or four real examples, each with what was reported, what changed, and the version that shipped
  it.
- **Changes since the closed test:** by version, with dates.
- **Readiness:** the vitals, the update history and the tests that run before every release.

Save the submitted answers in [production-access.md](production-access.md) under a new heading, as the first
application was.
