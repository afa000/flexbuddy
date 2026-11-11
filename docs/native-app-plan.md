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
| Screenshot reading | Server OCR through the API (the existing Tess4J path) | Keeps one parser and the FB-003 accuracy test set. On-device ML Kit can come later |
| Text | `strings.xml` in English and Spanish; the API returns error codes, not sentences | Same two languages as the website |
| Reminders | Local notifications scheduled by WorkManager from the driver's reminder settings | The website's Web Push (VAPID) can't reach a native app; Firebase Cloud Messaging is a new Google service and dependency, left for later |
| Offline | Room cache for reading, plus an outbox that replays writes with the existing `Idempotency-Key` header | The server already de-duplicates retried block and expense saves (`RequestIds`) |
| Rarely used account features | Two-step setup, backup/restore, Google linking, sign-up and account deletion open the website in a Custom Tab at first | Keeps the riskiest flows in one place. Account deletion stays reachable from inside the app, as Play requires |

## Delivery order

Sections are plan references (NA-xx), not task numbers. Promote one at a time into tasks from `TEMPLATE.md`, in the
repository where the code goes (see "Two repositories, one task sequence"). Split any section that won't fit in one
session.

| Phase | Sections | Outcome |
|---|---|---|
| 0 | NA-00, NA-01 | The base-pay import bug fixed; the app repository and both repositories' rules ready |
| 1 | NA-02, NA-11, NA-03 | A driver can sign in on the native app against a local server or the free test server (API still off in production) |
| 2 | NA-04, NA-05, NA-06 | Home, Schedule and blocks: read, add, edit, complete, also offline |
| 3 | NA-07, NA-08, NA-09 | Expenses and payouts, screenshot import and sharing, reports, taxes, settings |
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
- CI on pull requests and `main`: JDK 21, Gradle cache, unit tests, lint, required before merge.
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
- Expect slow screenshot reading on 0.1 CPU and the one-minute wake. The app has to show a clear "server waking up"
  or timeout message anyway; this is where that gets tested.
- Play's internal and closed testing tracks (NA-10) use production with the API on, not this server: Play builds
  carry the real package name and talk to the real site.

## Later, not in this plan

Background mileage tracking (Play's background location declaration), home-screen widgets, Google sign-in in the app
(needs a dependency the owner approves), Firebase push, account creation in the app, iOS.
