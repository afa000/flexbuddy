# Native Android app plan

Status: Proposed; no implementation started

Source: owner's decision on November 11, 2026 to build a native app first, after the second production access application

Replace the Trusted Web Activity in `android/` with a native Kotlin and Jetpack Compose app, built in its own
repository, that talks to the existing Spring Boot server through a new JSON API. The website at
https://flexbuddy.onrender.com stays online and behaves exactly as it does today, for the owner's own use, internship
demos and every current driver, before, during and after the switch.

## Guardrails

- **The app lives in its own repository** (default name `flexbuddy-android`). App commits never touch this repository,
  its CI or Render.
- **The website does not change.** The only server work is additive: new endpoints under `/api/v1/**` on their own
  security chain, built on the existing services. No existing page, endpoint, cookie, CSRF rule or sign-in step
  changes. Every server task keeps the full existing test suite passing unchanged and adds a regression test for the
  website path it sits next to.
- **The API is off in production until the owner turns it on.** `/api/v1` and its security chain exist only when
  `FLEXBUDDY_API_ENABLED=true` (see NA-02). With it unset, the live server runs the same code paths as today and every
  `/api/v1` path answers 404 like any unknown path. Local runs and tests turn it on.
- **The TWA stays the Play app until the native app reaches parity** (NA-10). `android/` stays in this repository and
  is left alone until then, apart from normal TWA releases.
- **Same rules as today** in both repositories: one task per `ai/FB-xxx` branch, specs before code, the owner approves
  every push and merge, high-risk work by Claude Code, earnings rules, per-user scoping, both languages, and
  backup/restore untouched.
- **New dependencies need the owner's yes.** The list under "Decisions" is a proposal, not an approval.
- **Offline first, server as the record.** Whatever can run on the phone does (reading, reminders, figures, exports),
  so the app works with no signal and the server stays small as drivers join. The server stays the source of truth:
  the phone syncs to it, and it re-checks every write. Earnings and import logic is never written twice; it moves into
  the shared library (NA-12, NA-13), and the server's existing tests prove the website's results don't change.
- **Accessible on every screen.** Each app task includes: TalkBack labels on every control, text that scales to 200%
  without clipping, touch targets of at least 48 dp, meaning never shown by colour alone (such as missing miles),
  light and dark themes, and both languages. Each screen is checked with Android's Accessibility Scanner, and the Play
  pre-launch report's accessibility findings are read before release. The app stays usable on Android 8 phones with
  little storage: a small download, and cached data and waiting screenshots that clean themselves up.

## Two repositories, one task sequence

- **Server tasks** (`/api/v1` endpoints, the switch) live in this repository as today: spec in `.ai/tasks/`, branch
  `ai/FB-xxx`, `verify.ps1`, review packet, pull request.
- **App tasks** live in the app repository with the same layout (`.ai/tasks/`, `ai/FB-xxx` branches, its own
  `implement-task`, `prepare-review` and `review-task` skills). FB numbers are one sequence across both: check both
  repositories' `main` and remote `ai/FB-*` branches for the next free number.
- **A section that needs both** becomes two tasks: the server task first (it merges and deploys with the switch still
  off), then the app task that uses it. The app task's spec names the server task and the API it relies on.
- **The API contract** is written in `docs/api-v1.md` in this repository, updated by every server task: paths, request
  and response JSON, error codes. The app repository's `AGENTS.md` points to it on GitHub, so an app agent never needs
  this repository checked out.
- **This plan stays here**, since the risky work is on the server. The app repository's README links to it.
- **Local layout:** clone the app next to this one (`Desktop\flexbuddy-android`). The app's debug build talks to the
  server run from here with `.\mvnw.cmd spring-boot:run`.

## Decisions (defaults the plan assumes)

Change any of these before the first app task is specified.

| Question | Default | Why |
|---|---|---|
| Where the code lives | A new private GitHub repository, `flexbuddy-android`, owned by the same account | App work can't reach the web app, its CI or Render; a clean repository to show for internships |
| API in production | Off (`FLEXBUDDY_API_ENABLED` unset) until internal testing starts; the owner sets it in Render | Production keeps today's behavior exactly while the app is built |
| Package name | `com.angel.flexbuddy`, shipped as an update to the TWA listing | Keeps the listing, testers, reviews and production access. Debug builds use `applicationIdSuffix ".dev"` so they never replace the Play install |
| Minimum Android | API 26 (Android 8.0) | Covers nearly all drivers' phones; the TWA's 24 gains little |
| Stack | Kotlin, Compose, Navigation Compose, ViewModel, OkHttp + Retrofit + kotlinx.serialization, Room, WorkManager, DataStore. Manual dependency injection, no Hilt | Standard Jetpack set, few moving parts for a first native app |
| Token storage | DataStore, encrypted with an Android Keystore key | `EncryptedSharedPreferences` is deprecated |
| Screenshot reading | Server OCR through the API (the existing Tess4J path) at first; offline screenshots wait on the phone (NA-07). Reading on the phone comes in NA-12, with the server's own parser as a shared library | Keeps one parser and the FB-003 accuracy test set |
| Text | `strings.xml` in English and Spanish; the API returns error codes, not sentences | Same two languages as the website |
| Reminders | Local notifications scheduled by WorkManager from the driver's reminder settings | The website's Web Push (VAPID) can't reach a native app; Firebase Cloud Messaging is a new Google service and dependency, left for later |
| Offline | Room cache for reading, plus an outbox that replays writes with the existing `Idempotency-Key` header | The server already de-duplicates retried block and expense saves (`RequestIds`) |
| Sync | Every list endpoint takes `updatedSince` and returns changed and deleted records only, with an `ETag` so an unchanged answer is `304` with no body. Decided before NA-04 and written into `docs/api-v1.md` | Far less data on drivers' plans and less database work than reloading whole lists |
| Rarely used account features | Two-step setup, backup/restore, Google linking, sign-up and account deletion open the website in a Custom Tab at first | Keeps the riskiest flows in one place. Account deletion stays reachable from inside the app, as Play requires |

## Free testing tools

Checked November 11, 2026. Offers change, and Student Pack offers end when student verification lapses: confirm each at
sign-up, and if one asks for a card or a paid plan, stop and ask the owner. None of these adds a dependency to either
project.

| Tool | Cost | What it's for | Used in |
|---|---|---|---|
| Docker Desktop, `postgres:18` image | Free for personal and education use | Run `PostgresMigrationTest` locally by setting `FLEXBUDDY_TEST_POSTGRES_URL`, `_USER` and `_PASSWORD`, instead of only in CI. Every migration (V29 first) is checked before its pull request | NA-01, NA-02 |
| Mailpit (Docker image) | Free, open source | A local mail server with a web inbox. Point `FLEXBUDDY_MAIL_HOST` at it (port 1025) to see sign-up, two-step and reset emails in both languages as real messages. The app's SMTP settings assume Gmail (auth and STARTTLS on), so check it connects without a username before relying on it | NA-01, NA-02 |
| JetBrains student licence (IntelliJ IDEA Ultimate) | Student Pack, renewed yearly | Its HTTP client runs `.http` request files against the local or test server. Commit them next to `docs/api-v1.md` as runnable examples (sign in, get account, add a block), with no tokens or passwords in the files | NA-02 onward |
| Neon branches | Free with the test-server database | Keep a clean `seed` branch with made-up accounts; branch from it before a test session and delete the branch after | NA-11 |
| LambdaTest Live | Student Pack, 1 year | Real iPhones and Safari for website changes; the owner has no iPhone, and iPhone-only bugs have shipped before. Made-up data only | Any website task |
| GitHub Pro | Student Pack | 3,000 GitHub Actions minutes a month for private repositories instead of 2,000; Android builds use minutes quickly | NA-01 (app repository) |
| Play Console pre-launch report | Included with the developer account | Real-device crawl of every test-track upload for crashes, layout and accessibility. Google's docs disagree on whether internal testing gets one; check in the console | NA-10 |
| Firebase Test Lab, Spark plan | Free, no card: 15 runs a day, 10 virtual and 5 physical | Compose UI tests on phones the owner doesn't have, by hand or from the app repository's CI | NA-05 onward |
| Android Device Streaming | 30 free minutes a month | Drive a real Pixel or Samsung from Android Studio for a quick look; too few minutes for daily use | NA-03 onward |

Left out for now: Sentry (Student Pack) would need privacy policy and Data safety changes before it could go in a Play
build, and Android vitals already reports production crashes; BrowserStack overlaps with Test Lab.

## Delivery order

Sections are plan references (NA-xx), not task numbers. Promote one at a time into tasks from `TEMPLATE.md`, in the
repository where the code goes (see "Two repositories, one task sequence"). Split any section that won't fit in one
session.

| Phase | Sections | Outcome |
|---|---|---|
| 0 | NA-00, NA-01 | The base-pay import bug fixed; the app repository and both repositories' rules ready |
| 1 | NA-02, NA-11, NA-03 | A driver can sign in on the native app against a local server or the free test server (API still off in production) |
| 2 | NA-04, NA-05, NA-06 | Home, Schedule and blocks: read, add, edit, complete, also offline |
| 3 | NA-07, NA-12, NA-13, NA-08, NA-09 | Expenses and payouts, screenshot import and sharing (also offline), figures and tools that work offline, reports, taxes, settings |
| 4 | NA-10 | API turned on in production, parity check, internal and closed testing, then the Play update that replaces the TWA |

## NA-00 — Groundwork on the live app

Risk: high (earnings figures). Implementer: Claude Code.

- **FB-025, import misreads base pay.** A testers' report: a screenshot with total $90.50, base $72.00 and tips $18.50
  imported as base $90.50 plus tips. Read the Base, Total and Tips labels instead of taking the first dollar amount.
  Fix it on the website now: it affects the owner's own records, and the app will use the same parser.
- **FB-026, block hours vs actual hours** (also from testers) can follow; it isn't blocking.
- Commit the open edits to `android/closed-test-feedback.md` and `android/production-access.md` on their own docs
  branch, so they don't ride along with unrelated work.
- Optional: a `buildFilter` in `render.yaml` so docs-only commits here don't redeploy. App commits no longer come
  through this repository, so it isn't needed for the native app; it edits `render.yaml` and needs its own task and the
  owner's approval.

## NA-01 — Workflow for two repositories

Risk: low. Implementer: Claude Code. The owner creates the GitHub repository (or approves its creation with `gh`) and
its branch protection.

**In this repository:**

- **AGENTS.md:** "API changes go under `/api/v1` only, behind `FLEXBUDDY_API_ENABLED`; existing endpoints don't change.
  Update `docs/api-v1.md` with every API change." Name the app repository and say app code never goes here.
- **`docs/api-v1.md`:** created empty except for conventions (JSON, error codes, `Authorization: Bearer`, the
  `Idempotency-Key` header, 401 and 404 behavior).
- **`docs/agent-workflow.md`:** make the routing table match practice (since FB-011, the owner has handed every task to
  Claude Code with Opus) and add the two-repository loop. Compose screens are medium risk; anything touching sign-in,
  tokens, the outbox or earnings is high.
- **Claude Code hooks** in `.claude/settings.json`: refuse `git push` to `main` and any edit to an existing
  `V<n>__*.sql`, so those rules hold even if an instruction is missed.
- **Local Postgres and Mailpit:** add both to the setup in `docs/agent-workflow.md` (see "Free testing tools"), and
  have `verify.ps1` say in `summary.md` whether the PostgreSQL migration test ran or was skipped.
- Delete the stale local `claude/*` branches and `handoff-tools/` once the owner confirms nothing in them is needed.

**In the app repository:**

- A short `AGENTS.md` (and `CLAUDE.md` importing it) covering only the app: build commands, the `.dev` rule, the API
  contract link, dependency approval, both languages, no secrets or real driver data, and pushing to `main` needs the
  owner's approval (nothing deploys from it, but Play builds come from it).
- **Emulator rule:** never install a release-signed build of `com.angel.flexbuddy` on the emulator; it would replace
  the Play install signed in as the Play Reviewer account. Test with the `.dev` debug build against a local server at
  `http://10.0.2.2:8080`.
- `.ai/tasks/TEMPLATE.md`, the three skills adapted for Gradle, and a `scripts/verify.ps1` that runs
  `.\gradlew.bat test lintDebug` and writes `summary.md`.
- CI on pull requests and `main`: JDK 21, Gradle cache, unit tests, lint, required before merge. Claim GitHub Pro
  from the Student Pack first, for the extra Actions minutes.
- Keystores, passwords and signed builds stay out of this repository too; the owner signs.

## NA-02 — API foundation: the switch and token sign-in (FB-005, refreshed)

Risk: high (sign-in). Implementer: Claude Code. Repository: this one.

FB-005 on `ai/FB-005` is fully specified but was checked against `main` on October 25. Before implementing, update the
spec on its branch:

- **Add the switch.** A `flexbuddy.api.enabled` property (`FLEXBUDDY_API_ENABLED`, default `false`). When false, the
  `/api/v1` security chain, its filter, controllers and cleanup job are not registered at all
  (`@ConditionalOnProperty`), so production runs exactly today's security setup. Tests cover both settings: off,
  `/api/v1/account` is 404 and the website's chain is unchanged; on, the full FB-005 behavior.
- The migration becomes `V29__app_tokens.sql`; `V28__reset_pinned_mileage_rate.sql` is now taken. The tables are
  created even with the switch off and stay empty. That is the one change production sees.
- Recheck its "Current behavior" against today's `SecurityConfig`, `VerifiedLoginSuccessHandler`, `AttemptLimiter`,
  `SignInCompleter` and the FB-007 remember-me change.
- Keep its scope: stateless `/api/v1` chain, bearer tokens stored as hashes, sign-in with codes, sign-out,
  `GET /api/v1/account`. Write those endpoints into `docs/api-v1.md`.

## NA-03 — App skeleton and sign-in screens

Risk: high (tokens on the device). Implementer: Claude Code. Repository: the app.

- Gradle project with the wrapper committed, `debug` (`.dev`) and `release` (production) build types, and the theme
  and launcher icon from the TWA (copied, not linked). The debug build's server address is a build setting: the local
  server by default, the test server (NA-11) when chosen. Release builds can only reach production.
- Sign-in, code entry (app, emailed and recovery codes), resend, sign-out, and "token expired" returning to sign-in.
  Locked and wrong-code answers shown in the driver's language.
- Token kept encrypted, never logged; OkHttp logging off in release builds.
- Unit tests for the view models and the API client against a mock web server. Emulator check with the `.dev` build
  and a local test account.

## NA-04 — Read-only API and Home

Risk: medium (shows earnings; no calculation changes). Implementer: Claude Code for the API, Antigravity possible for
the screen.

- Server: `GET /api/v1/shifts`, `/api/v1/shifts/{id}`, `/api/v1/home` (or `/summary`), returning what the website's
  JSON endpoints return today, from the same services. Scoped to the token's account; another account's id answers 404.
- App: Home screen with this week's blocks, earnings, net with the missing-miles flags (FB-009, FB-013), goal progress.
  Scheduled blocks never count toward earnings, hours or net, same as the website.
- App: Room cache so Home and Schedule open offline with the last data and say how old it is.
- Server and app: the sync design from "Decisions" (`updatedSince`, deleted records, `ETag` and `304`) starts here,
  so every later list endpoint follows it. Deleted records come back as deletions within the 30-day Recently deleted
  window, so the phone drops them too.
- At first Home shows the figures the server computed, cached. NA-13 moves the arithmetic to the phone.

## NA-05 — Blocks: add, edit, complete

Risk: high (status decides earnings). Implementer: Claude Code.

- Server: `/api/v1` create, edit, status changes (start, complete, not worked, back to scheduled per FB-012), delete
  and restore within the 30-day window, all through `ShiftService` with the same validation and `Idempotency-Key`.
- App: screens for Schedule, a block, Finish (times, miles or odometer, tips) and the block review checklist (FB-023).
- App: an outbox. Writes made offline are saved locally and replayed by WorkManager with their idempotency key; a
  replay never adds a block twice. Test retries, double taps and a conflict with a change made on the website.

## NA-06 — Expenses and payouts

Risk: high (which expenses count toward net and tax). Implementer: Claude Code.

- Server: `/api/v1/expenses` and `/api/v1/payouts` over `ExpenseService` and `PayoutService`, keeping expense links to
  blocks (FB-017, FB-018).
- App: the screens, with the effect-on-net message (FB-014).

## NA-07 — Screenshot import and sharing

Risk: high (batch saves of earned blocks). Implementer: Claude Code.

- Server: `/api/v1/shifts/import-preview` (multipart), using `ShiftImportService` and the `ScreenshotReadGate` memory
  limits.
- App: pick or share one or several screenshots (an Android share intent replaces the TWA's web share target), review
  each, explicit worked / not-yet-worked choice (FB-011), duplicate warnings (FB-008), save as a batch (FB-021).
- App, **offline import (level 1)**: screenshots picked or shared with no connection are kept on the phone, in app
  storage only, and listed as "waiting for a connection". Once the app is online they are read by the server and
  reviewed one by one as usual. Nothing is saved without the driver's review, a waiting screenshot is deleted once it
  is saved or skipped, and all of them are deleted at sign-out. Reading fully offline is NA-12.

## NA-08 — Reports, taxes and standing

Risk: medium (read-only figures). Implementer: Claude Code or Antigravity.

- Server: read-only `/api/v1` endpoints over `ShiftReportService`, `TaxService` and `StandingService`.
- App: screens with date ranges stated (FB-016). CSV export can open the website's existing download.

## NA-09 — Settings and reminders

Risk: medium. Implementer: Claude Code or Antigravity.

- Server: language, time zone, goals, tax settings and reminder times through `/api/v1/account/...` over
  `AccountSettingsService`.
- App: local reminder notifications (WorkManager), with the Android 13 notification permission asked when first needed.
- App: links to the website for two-step setup, backup/restore, Google linking and account deletion.

## NA-10 — Turning it on, and the switch

Risk: high (production API and the Play app). Implementer: Claude Code builds and documents; the owner sets the
environment variable, signs and uploads.

- **Turn the API on.** The owner sets `FLEXBUDDY_API_ENABLED=true` in Render when internal testing starts. Before
  that, a local run with production-like settings confirms the website is unchanged with it on. Turning it off again
  is the rollback for the server; the website doesn't depend on it.
- A parity checklist: every everyday task in the TWA works natively, in both languages, at phone sizes, offline where
  the TWA worked offline.
- Read the Play pre-launch report for every test-track upload. Play builds talk to production, so give its crawler
  either no sign-in or a dedicated made-up account on production created for it, never a real driver's account, and
  delete what it adds.
- Internal testing track, then closed testing with the current testers, then production as a normal update:
  `versionCode` above the TWA's last, same package and upload key. Record each upload in the app repository's
  changelog, and the switch-over in `android/CHANGELOG-android.md` here.
- Keep `/.well-known/assetlinks.json` and both fingerprints; it costs nothing and lets the TWA come back.
- Rollback for the app: upload a TWA build with a higher `versionCode`. Keep `android/` buildable until the native app
  has been in production for a month.
- The website stays exactly as it is afterwards.

## NA-11 — A free test server

Risk: medium (a second public copy of the app; no production data or settings). Implementer: Claude Code writes the
task and the small server change; the owner creates the accounts and sets every secret.

FlexBuddy has no staging environment. This gives it one, at no cost, so `.dev` builds on the emulator or the owner's
phone can reach a real server with the API on, and risky server changes can be tried on a hosted copy before they reach
production. It comes after NA-02, since before the switch exists there is nothing to turn on.

**Hosting** (checked November 11, 2026; confirm both pricing pages at sign-up):

- **Server:** a Render free web service in the owner's existing workspace, built from the same Dockerfile. It sleeps
  after 15 minutes without traffic and takes about a minute to wake, has 512 MB of memory and a disk that is wiped on
  every restart. Created in the dashboard, not in `render.yaml`, so the production Blueprint doesn't change.
- **Database:** a Neon free Postgres database (about 0.5 GB, no card). Not Render's free database, which is deleted
  after 30 days plus a 14-day grace period. Choose Postgres 18 if Neon offers it, otherwise 17, and say so in the task.
- **If either asks for a card or a paid plan, stop** and ask the owner. The test server must cost nothing.

**Settings** (set by the owner in the dashboard, never in the repository, a log or chat):

- `FLEXBUDDY_API_ENABLED=true`.
- Its own `FLEXBUDDY_REMEMBER_ME_KEY` and `FLEXBUDDY_TWO_FACTOR_KEY`, freshly generated. Never copy production's.
- `DB_URL` set directly to Neon's JDBC address with `sslmode=require`, plus `DB_USER` and `DB_PASSWORD`. Leave
  `DATABASE_URL` unset: `docker-entrypoint.sh` rebuilds the address from it and would drop the SSL setting.
- No `FLEXBUDDY_MAIL_*` and no Google sign-in settings. With no mail host the app logs instead of sending mail
  (`MailConfig`), and `FLEXBUDDY_MAIL_LOG_LINKS=true` puts sign-up and sign-in codes in the Render log, so test
  accounts can be verified without a mailbox.
- `FLEXBUDDY_ALERT_TO` unset, so error alerts are logged rather than sent to the support inbox.

**The one server change** (its own server task, off by default, so production doesn't change):

- A `flexbuddy.test-server` property (`FLEXBUDDY_TEST_SERVER`, default `false`). When true, every page shows a banner
  in both languages ("Test server: made-up data only. Use flexbuddy.onrender.com for your real blocks.") and every
  response carries `X-Robots-Tag: noindex`, so no driver mistakes it for the real site and search engines don't list
  it. Tests cover both settings; with it off, pages and headers are exactly as before.

**Use:**

- Deploys follow `main` automatically, so the test server runs what production runs, with the API on. To try an
  unmerged server task, deploy its branch by hand, then switch back to `main`.
- Made-up accounts and figures only. No real driver data, no copies of the production database, and no real
  screenshots unless they're from the FB-003 sample set the owner already labeled.
- Keep a Neon `seed` branch with the made-up accounts. For a test session, branch from it and point `DB_URL` at the
  branch; delete the branch afterwards, so every session starts from the same data.
- Expect slow screenshot reading on 0.1 CPU and the one-minute wake. The app has to show a clear "server waking up"
  or timeout message anyway; this is where that gets tested.
- Play's internal and closed testing tracks (NA-10) use production with the API on, not this server: Play builds
  carry the real package name and talk to the real site.

## NA-12 — Reading screenshots fully offline

Risk: high (earnings figures and batch saves, read with a new engine). Implementer: Claude Code. Repositories: both.
Comes after NA-07; it can move to just after NA-10 if the owner would rather ship parity first.

The phone reads the screenshot, the driver reviews and saves it with no connection, and the save syncs later. The
website can't do this (its plan, `plan/on-device-ocr`, stopped because the parser would have to be copied into
JavaScript and drift). The app can, because Kotlin on Android runs the server's Java: checked November 11, 2026,
`ShiftScreenshotParser` (282 lines) and `ImportWarningRules` (114 lines) depend on nothing in Spring or the database
except a `@Component` annotation. `ScheduledShiftMatcher` reads the database, but its rule (blocks overlapping by at
least `MINIMUM_OVERLAP_MINUTES`) is small.

1. **Shared library (server task, this repository).** Move the parser, its result types (`OcrLine`, `OcrResult`,
   `ParseContext`, `ParsedField`, `ParsedShiftData`, `ConfidenceLevel`), `ImportWarningRules` and the overlap rule into
   a plain-Java Maven module with no Spring dependency. The server wraps them as beans and behaves exactly as before:
   `ShiftScreenshotParserTest`, `ImportWarningRulesTest`, `ScheduledShiftMatcherTest` and `OcrSampleAccuracyTest` pass
   unchanged, and nothing a driver sees changes. Text the rules produce must stay message keys, so the app shows its
   own `strings.xml` in both languages.
2. **Publishing it (server task).** Publish the module as a versioned artifact the app repository can use, by default
   GitHub Packages from this repository's CI. The owner approves the publishing setup; the app pins the version.
3. **Go / no-go on the phone's reader (app task, before any app code ships).** Measure on the FB-003 labelled sample
   set, as `plan/on-device-ocr` planned: the phone must give the same six fields (station, date, start, end, base pay,
   tips) as the server on at least as many screenshots. Candidates, each a new dependency the owner approves:
   - Tesseract on Android, the server's own engine and `eng.traineddata`, so its lines suit the parser best;
   - Google ML Kit text recognition, faster and bundled, but its lines differ, so the parser may need tuning (which
     then has to keep `OcrSampleAccuracyTest` passing on the server).
   No go means no offline reading; level 1 from NA-07 stays.
4. **Offline review and save (app task).** Read on the phone, run the shared parser and warning rules, check overlaps
   against the blocks cached on the phone (NA-04), and show the same review with the explicit worked / not-yet-worked
   choice (FB-011). Saving goes into the outbox (NA-05) with its `Idempotency-Key`. On sync the server runs its own
   duplicate check again; a clash answers `409` and the driver chooses, it never adds a second block.
5. **Privacy.** The screenshot never leaves the phone when it is read offline, and is deleted after save or skip, and at
   sign-out. The Play Data safety form and privacy policy are checked against this before release.

## NA-13 — Figures and tools that work offline

Risk: high (earnings, net and tax figures computed on the phone). Implementer: Claude Code. Repositories: both.
Comes after NA-12, which creates the shared library.

Moving this work to the phone won't lower the Render bill, which is a flat plan. It keeps the server small as drivers
join, and it makes the app useful with no signal and on a small data plan. Checked November 11, 2026:
`GoalProgressCalculator` (37 lines), `PayPeriodCalculator` (80), `IrsMileageRates` (57) and `MileageRates` (31) depend
on nothing but a `@Component` annotation. `NetEarningsCalculator` (68) and `BlockEvaluator` (226) take the database
entities `Shift` and `Expense`; `ShiftReportService` (429) and `TaxService` (258) also query the database.

1. **Shared figures (server task).** Add to the shared library the calculators above, with `NetEarningsCalculator` and
   `BlockEvaluator` changed to take plain inputs (a block's status, times, pay, tips and miles; an expense's category,
   amount and date) instead of entities. The server maps its entities to those inputs and returns exactly the same
   figures: the existing tests for each, plus the report and tax tests, pass unchanged. Scheduled blocks never count
   toward earnings, hours or net, in the library as on the website.
2. **Home and Reports offline (app task).** Compute this week's and each report's figures on the phone from the cached
   blocks and expenses, with the shared calculators. Each screen says when the data was last synced. The tax year
   summary keeps the server's figures, cached, because `TaxService` reads payments and settings the phone may not have
   in full; say so on the screen.
3. **Block evaluator offline (app task).** "Is this offer worth it?" runs on the phone with the shared `BlockEvaluator`
   and the driver's cached costs, so it works when the driver is deciding with poor signal.
4. **Exports offline (app task).** Build the blocks and expenses CSV on the phone with the shared writers
   (`ShiftCsvWriter`, `ExpenseCsvWriter`, moved into the library) and hand it to Android's share sheet. The columns
   and format match the website's download exactly; a test compares the two for the same data.
5. **Add a block to the calendar (app task).** Use Android's calendar insert screen instead of downloading the
   block's `.ics`. The private calendar feed (`/calendar/<token>.ics`) stays on the server, because Google Calendar
   subscribes to it; the app never logs or shares its link.
6. **Reminders on the phone (app task, with NA-09).** Block, missing-miles and tax due-date reminders are scheduled as
   local notifications from cached data. The server's `ReminderJob`, `TaxReminderJob` and Web Push stay for the
   website. To avoid two reminders for the same thing, the app tells the server, per device, that this phone handles
   reminders, and the server skips Web Push for that driver while a phone has checked in within the last 7 days.
   That flag is a small server change behind the API switch.

**Stays on the server:** sign-in, two-step codes, password reset and all email; the calendar feed; backup and
restore (older backup versions must stay restorable); the 30-day Recently deleted window; account deletion; and the
duplicate and validation checks every synced write goes through.

## Later, not in this plan

Background mileage tracking (Play's background location declaration), home-screen widgets, Google sign-in in the app
(needs a dependency the owner approves), Firebase push, account creation in the app, iOS.
