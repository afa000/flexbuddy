# FlexBuddy — second closed test

Google turned down the first production access application on October 24, 2026 and asked for 14 more days of closed
testing with real testers ([production-access.md](production-access.md) says why). This round fixes what Google could
not see the first time: updates to the app made because of tester feedback, and testers who use the app the way
drivers do.

Day 0 is the day version 3 is live on the closed track and the first tester task goes out. Apply no earlier than day
15, and only when every item in "Before applying" is true.

## What changes from the first round

| First round | This round |
|---|---|
| One update (version 2), on day 12 of a 38-day test | Version 3 on day 0, version 4 around day 5, version 5 around day 11 |
| Fixes shipped through Render only, invisible to Play | Each batch of fixes also ships as a closed-track release whose notes name the feedback |
| Testers partly from a tester-exchange app | Mostly delivery drivers, the app's real audience |
| 20 testers, no tasks after the opt-in | 25 invited, a short real-world task every four days |
| Feedback summarised from memory | Every report logged in [closed-test-feedback.md](closed-test-feedback.md) as it arrives |

## Before day 0 (about a week)

Recruiting and the version 3 review both take days, so start them together.

**Recruit (owner)**

- [ ] Invite 25 people: at least 15 who drive for Amazon Flex or another delivery app (drivers you know, your station,
      local driver groups), then friends and family. Use the invitation below.
- [ ] Keep testers from round 1 who really used the app; they're already opted in.
- [ ] Use tester-exchange apps only as a top-up, if at all, and say so in the application.
- [ ] Add each Play Store email to the `testers` list in Play Console, then send that person the opt-in link.
- [ ] Track opt-ins by tester number (T01, T02, ...) in a private note outside the repository. The repository only ever
      has tester numbers.

**Version 3 (owner builds and signs; an agent may prepare the files)**

- [ ] Raise `appVersionCode` to 3 and `appVersionName` to `"3"` in `twa-manifest.json`, then `bubblewrap update
      --skipVersionUpgrade` and restore the hand-drawn icons, as for version 2.
- [ ] Build, sign interactively with the upload key, and check the build on the emulator with a local test account.
- [ ] Upload to Closed testing - Alpha with the version 3 release notes below and send it for review. Reviews of
      closed releases can take a few days.
- [ ] Record the release in `CHANGELOG-android.md`.

**Check (owner, in Play Console)**

- [ ] The store listing, Data safety form and privacy policy still match the app.
- [ ] The app access instructions and the Play Reviewer sign-in still work. Read-only: never add data with it.
- [ ] Android vitals and the pre-launch report for version 2 show nothing blocking.

## The 14 days

| Day | Owner does | Testers do |
|---|---|---|
| 0 | Version 3 is live. Send the day 0 message. | Install or update, create an account, add this week's blocks, import one screenshot. |
| 1–3 | Answer every report within a day; log it. Fix what you can on the web. | Use it for their real blocks. |
| 4 | Send the day 4 message. Pick the fixes for version 4 and start the build. | Complete blocks with pay and miles, log an expense. |
| 5–6 | Upload version 4 with notes naming the fixes. Reply to the testers who reported them. | Update when Play offers it. |
| 8 | Send the day 8 message. Screenshot the Statistics and opted-in count. | Reports, a weekly goal, reminders. |
| 9–10 | Pick the fixes for version 5 and start the build. | Keep logging blocks. |
| 11 | Upload version 5 with notes naming the fixes. Reply to the reporters. | Update. |
| 12 | Send the day 12 message. | Taxes, backup, airplane mode, language. |
| 14 | Send the thank-you. Screenshot Statistics and the opted-in count again. | Stay opted in. |
| 15+ | Apply, once "Before applying" is all true. | Stay opted in until Google answers. |

**Every day (5 minutes):** check `flexbuddysupport@gmail.com` and Play Console's tester feedback, log each item, and
check the opted-in count. If it falls below 15, invite more people that day: a new tester's 14 days start when they
opt in.

**When something is fixed:** reply to the tester who reported it ("Thanks, that's fixed in version 4, out today").
Testers who see their feedback acted on keep using the app, and those replies are evidence for the application.

## Messages

Send in English or Spanish to match the tester. Fill in the opt-in link; never paste a private calendar link or
anyone's data into a group message.

**Invitation**

```text
Hi! I built FlexBuddy, an app for delivery drivers to track blocks, pay, miles, expenses and taxes. Before it
can go public on Google Play, Google needs drivers to test it for two weeks, and I'd really value your help.

What it takes: about 5 minutes when you finish a block, for two weeks. I'll send a short task every few days.

1. Send me the Google email you use for the Play Store on your Android phone.
2. When I say you're added, open this link on that phone and tap "Become a tester": <OPT-IN LINK>
3. Install FlexBuddy from the link on that page and keep it installed for at least 14 days.

Anything confusing or broken: tap "Send feedback" in the app, reply to me here, or email
flexbuddysupport@gmail.com. Every report gets an answer.
```

**Day 0**

```text
FlexBuddy version 3 is out. This week's task (10 minutes):
1. Create your account (Google sign-in works).
2. Add your scheduled blocks for this week.
3. Import one block from a Flex screenshot with the Import button, check the fields, and save it.
Tell me anything that was slow, confusing or wrong. "Send feedback" in the app fills in the details for you.
```

**Day 4**

```text
Thanks for testing! This round:
1. Mark your finished blocks as completed with your pay and miles.
2. Log one expense, like fuel, and link it to a block.
3. Check this week on Home: does the "earned" figure match what you expected?
Your reports so far are going into version 4 this week.
```

**Day 8**

```text
Halfway there! This round:
1. Open Reports and look at your last two weeks. Anything you wish it showed?
2. Set a weekly goal on Home.
3. Turn on reminders in Account > Reminders & calendar and allow notifications.
Version 4 fixed <WHAT CHANGED>, thanks to your reports.
```

**Day 12**

```text
Last task, for the parts of the app most people never try:
1. Account > Taxes: set it up and see the estimate.
2. Account > Backup & restore: download a backup.
3. Turn on airplane mode, open FlexBuddy, and add a block. Turn airplane mode off and check it saved.
4. Switch the language to Español and back.
Please stay opted in and keep the app installed for a few more days while Google reviews it.
```

**Thank-you (day 14)**

```text
That's the two weeks. Thank you! You reported <N> things and <M> of them are already fixed in the app.
Please keep FlexBuddy installed and stay opted in until it's public; I'll let you know when it is.
```

## Release notes

Play allows 500 characters per language. Write them for testers, and name the feedback a release answers.

**Version 3, English**

```text
Everything since version 2: Home built around your week, Reports with charts and best times to work, payout
checks, weekly goals, tax set-aside estimates and due-date reminders, adding blocks and expenses offline,
Spanish, Google sign-in, optional two-step sign-in, password reset, miles from odometer readings, missing-miles
reminders, larger tap targets, and more accurate, memory-safe screenshot import. Thanks to every tester who sent
feedback.
```

**Version 3, Spanish**

```text
Todo lo nuevo desde la versión 2: Inicio centrado en tu semana, Informes con gráficos y mejores horas para
trabajar, revisión de pagos, metas semanales, estimación de impuestos y recordatorios de fechas, añadir bloques y
gastos sin conexión, español, inicio con Google, verificación en dos pasos opcional, restablecer contraseña,
millas por odómetro, recordatorios de millas, botones más grandes e importación de capturas más precisa.
¡Gracias a cada tester!
```

**Versions 4 and 5:** "Fixed after tester feedback:" followed by each fix in the driver's words, for example "Reports
totals no longer cut off on small phones".

**Version 4, English.** These fixes came from the owner's own walkthrough as a driver on October 30, 2026 (FB-008,
FB-009 and FB-010), not from testers, so the notes don't credit testers. If tester reports arrive before the upload, log
them in [closed-test-feedback.md](closed-test-feedback.md) and name any that version 4 fixes under "Fixed after tester
feedback:".

```text
New in version 4: FlexBuddy warns you before importing a screenshot of a block you already logged, so it isn't counted
twice. Standard mileage now uses the IRS rate for each block's date (72.5¢ from January 2026, 76¢ from July). Reports
and Home now say when estimated net is too high because blocks have no miles.
```

**Version 4, Spanish**

```text
Novedades de la versión 4: FlexBuddy te avisa antes de importar una captura de un bloque que ya registraste, para que
no se cuente dos veces. El millaje estándar ahora usa la tarifa del IRS de la fecha de cada bloque (72.5¢ desde enero
de 2026 y 76¢ desde julio). Informes e Inicio ahora indican cuándo la ganancia neta estimada es demasiado alta porque
hay bloques sin millas.
```

## If something goes wrong

| Problem | What to do |
|---|---|
| A release is still in review on its planned day | Keep the fixes live on the web and ship them in the next version; don't skip a version. |
| Opted-in count drops | Invite more drivers the same day. Don't apply until 12 or more have 14 days in a row. |
| Little feedback | Ask one direct question in the next message ("What's the first thing you'd change?"), and ask in person. |
| A tester reports something you won't change | Log it with the reason. Google wants to see decisions, not only fixes. |
| A crash or ANR in Android vitals | Fix it before applying and mention the fix and version in the application. |

## Before applying

- [ ] Play Console says the closed-test requirements are met, with 12 or more testers opted in for 14 days in a row.
- [ ] Versions 3, 4 and 5 are on the closed track, each with notes naming tester feedback.
- [ ] The feedback log has every report, what changed, and the version that shipped it.
- [ ] Android vitals shows no crashes or ANRs, and the pre-launch report has nothing blocking.
- [ ] Screenshots from days 8 and 14 of Statistics and the opted-in count are saved privately.

**Writing the answers.** Use numbers and examples from the log and the release history, not general statements:

- **Recruitment:** how many testers, how many are delivery drivers, how you found them, and the role of any
  tester-exchange app.
- **Engagement:** the four tasks, what share of testers did each one, active devices from Statistics, and over how
  many days.
- **Feedback:** three or four real examples, each with the date, what was reported, what changed, and the version.
  Say how feedback was collected: in-app email, Play's tester feedback, in person.
- **Changes since the closed test:** versions 3, 4 and 5 with dates and what each changed.
- **Readiness:** vitals, the update history, and the automated tests that run before every release.

Save the submitted answers in [production-access.md](production-access.md) under "Second application".
