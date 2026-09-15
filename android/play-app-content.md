# FlexBuddy — Play Console App content answers

Draft for Stage 9, prepared September 14, 2026 from the code, privacy policy, terms, and the
Play-installed build. Recheck each answer against the live app before submitting, and update this
file, the privacy policy, and the Data safety form together whenever data handling changes.

Sources checked:

- Data models: `AppUser`, `Shift`, `Expense`, `PushSubscription`, `ReminderLog`, and the
  `persistent_logins` remember-me table.
- Dependencies (`pom.xml`): Spring Boot, PostgreSQL, Flyway, Tess4J (on-server OCR), and web-push.
  No ads, analytics, crash-reporting, or third-party SDKs.
- Templates and static files: no external scripts, trackers, or fonts.
- Android build: `androidbrowserhelper` 2.6.2 only. The Play-installed APK requests
  `POST_NOTIFICATIONS`, its own receiver permission, and `CHECK_LICENSE` (added by Play). No
  `AD_ID` permission.
- Screenshot import: images are processed in memory by Tess4J and never stored.
- Account deletion (`AccountService.deleteAccount`): removes expenses and shifts including trash,
  reminder history, push subscriptions, remember-me tokens, and the account.

## Already complete

| Declaration | Status |
|---|---|
| Sign in details (App access) | Done: reviewer account credentials and instructions entered |
| Store listing | Done |

## Privacy policy

| Question | Answer |
|---|---|
| Privacy policy URL | `https://flexbuddy.onrender.com/privacy` |

## Ads

| Question | Answer |
|---|---|
| Does your app contain ads? | **No, my app does not contain ads** |

## Advertising ID

| Question | Answer |
|---|---|
| Does your app use advertising ID? | **No** |

The release APK does not declare `com.google.android.gms.permission.AD_ID`, and nothing in the app
reads an advertising ID.

## Content ratings (IARC questionnaire)

| Question | Answer |
|---|---|
| Email address | `flexbuddysupport@gmail.com` |
| Category | **All other app types** (a productivity/utility app, not a game, social, news, or entertainment app) |
| Violence, blood, or gore | No |
| Sexuality or nudity | No |
| Profanity or crude humor | No |
| Drugs, alcohol, or tobacco | No |
| Gambling or simulated gambling | No |
| Hateful or extremist content or symbols | No |
| Users can interact or exchange content with other users | **No**: each account's data is private to that user |
| Shares the user's physical location with other users | No |
| Allows purchases of digital goods | No |
| Is a web browser or search engine | No: the app opens only `flexbuddy.onrender.com` |

Expected result: the lowest age rating in each region (for example ESRB Everyone, PEGI 3).

## Target audience and content

| Question | Answer |
|---|---|
| Target age groups | **18 and over** only |
| Could the store listing unintentionally appeal to children? | **No** |

FlexBuddy is built for independent delivery drivers, who must be adults. Selecting only 18 and over
keeps the app outside the Families policy.

## Data safety

### Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes**: the app loads only the HTTPS origin, and push payloads are encrypted |
| Which account creation methods does your app support? | **Username and password** (email address and password) |
| Link for users to request account and data deletion | `https://flexbuddy.onrender.com/delete-account` |
| Can users request deletion of some data without deleting their account? | Optional; left unanswered. Answering Yes requires a second URL describing partial deletion, and `/delete-account` covers only full account deletion. Individual shifts and expenses can already be deleted in the app, so add a section to that page before answering Yes |

Data is **not shared**. Render (hosting and database) and browser push services (which deliver
encrypted notifications) act as service providers on FlexBuddy's behalf, which Play does not count
as sharing.

### Data types

For every type below: **Collected: Yes. Shared: No.** Purpose is **App functionality** unless
another purpose is listed.

| Play category | Data type | Ephemeral? | Required or optional | Purposes | What it is |
|---|---|---|---|---|---|
| Personal info | Name | No | Required | App functionality, Account management | Display name entered at registration |
| Personal info | Email address | No | Required | App functionality, Account management | Sign-in email |
| Financial info | Other financial info | No | Required | App functionality | Base pay, tips, cancellation pay, expense amounts, mileage rate, and vehicle-cost settings |
| Photos and videos | Photos | **Yes** | Optional | App functionality | Screenshots uploaded for shift import; processed in memory and discarded |
| Files and docs | Files and docs | No | Optional | App functionality | Backup files uploaded for restore; held in the server session for up to 15 minutes before import |
| App activity | Other user-generated content | No | Required | App functionality | Shift dates, times, stations, statuses, miles, and expense notes |
| Device or other IDs | Device or other IDs | No | Optional | App functionality | Browser push endpoint, keys, and user agent, stored only when reminders are enabled |

Photos are declared but processed ephemerally, so Play does not show them on the store listing
preview; that is expected.

Do **not** select these types: approximate or precise location (station names are typed by the
user, and the app never reads device location), contacts, calendar (the app provides a calendar
feed but never reads the device calendar), messages, audio, health and fitness, web browsing,
app interactions, in-app search history, installed apps, crash logs, diagnostics, or other app
performance data. FlexBuddy has no analytics or crash reporting.

Passwords are stored only as one-way hashes and remember-me tokens are authentication state; Play
has no data type for either.

### Security practices

| Question | Answer |
|---|---|
| Data encrypted in transit | Yes |
| Users can request data deletion | Yes (in-app, and on the web by signing in at `flexbuddy.onrender.com`) |
| Committed to the Play Families policy | Not applicable: the app is not for children |
| Independent security review | No |

## Government apps

| Question | Answer |
|---|---|
| Is your app developed by or on behalf of a government? | **No** |

## Financial features

| Question | Answer |
|---|---|
| Select the financial features your app provides | **My app doesn't provide any financial features** |

FlexBuddy only records earnings and expenses that the user enters. It does not hold or move
money, offer loans or credit, provide earned wage advances, process payments, trade assets, or
give financial advice (the terms say so). If Google asks for more detail during review, choose
**Other** and describe it as "Personal record-keeping of delivery shift earnings, mileage, and
expenses entered by the user; no money is held, moved, lent, or invested."

## Health apps

| Question | Answer |
|---|---|
| Health features in your app | **My app does not have any health features** |

## Store settings

| Field | Answer |
|---|---|
| App or game | App |
| Category | **Productivity** |
| Tags | Up to five of Play's offered tags closest to shift tracking, planning, and expense tracking |
| Email address | `flexbuddysupport@gmail.com` |
| Website | `https://flexbuddy.onrender.com` |
| Phone number | Leave blank |

Productivity is preferred over Finance so the app is not presented as a financial service.

## Privacy policy follow-ups (recommended before production)

These do not block the declarations above, but reviewers compare the policy with the Data safety
form:

1. Name the developer as it appears on Google Play (**Angel2458**) in the privacy policy, next to
   the contact email.
2. State that push reminders are delivered through the browser's push service (for example
   Google's for Chrome) with encrypted contents.
3. State that uploaded backup files are held briefly in the server session to preview a restore and
   then discarded.
