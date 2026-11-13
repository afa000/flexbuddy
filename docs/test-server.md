# The hosted test server

A second, public copy of FlexBuddy for native-app debug builds and for trying server changes before they reach
production. It runs the same Docker image with the native API on, holds made-up accounts and figures only, says so on
every page, and asks search engines not to index it. It is set up and run by the owner; agents never create resources,
set secrets, deploy or change its settings. Plan: [NA-11](native-app-plan.md#na-11--a-free-test-server). Server
change: FB-032.

The banner and the `X-Robots-Tag: noindex` header identify the copy. They are not access controls: anyone with the
address can open it, so sign-in, per-driver scoping and rate limits apply exactly as in production.

## What the code does

| Setting | Effect |
|---|---|
| `FLEXBUDDY_TEST_SERVER=true` | Every HTML page (signed-out, signed-in, legal, error, tax print view and the offline page) starts with the banner "Test server: made-up data only. Use flexbuddy.onrender.com for your real blocks." in the visitor's language. Every response, including static files, redirects, `304`s, `HEAD`s, error pages and the API's `401`/`404`, carries `X-Robots-Tag: noindex`. |
| unset or `false` (production) | Nothing changes: no banner, no header, and `/offline.html` is the original static file. |

The switch is independent of `FLEXBUDDY_API_ENABLED`: neither turns the other on. Pages the service worker keeps (`/`,
`/account`, `/offline.html`) are cached with the banner in them, so a test install still shows it offline. Code:
`flexbuddy/src/main/java/com/angel/flexbuddy/testserver/`, `templates/fragments/test-server.html`,
`templates/offline.html` (test server only).

## 1. Check the cost before creating anything

The test server must cost nothing. Free compute alone does not prove that.

1. Read both pricing pages on the day: <https://render.com/docs/free> and <https://neon.com/pricing>. On November 13,
   2026 Neon Free listed 1 GB of storage per project, 100 CU-hours per project per month and 10 branches. Record what
   the pages say on the day you set up:

   | Item | Limit offered | Checked on |
   |---|---|---|
   | Render free instance hours (shared by the whole workspace) | | |
   | Render free bandwidth and build minutes | | |
   | Neon Free storage / compute / branches | | |

2. In the Render dashboard, check the workspace's current use. Free instance hours are shared by every free service in
   the workspace. The production service is on the paid Starter plan, so it does not use them, but any other free
   service does.
3. The production workspace has a payment method. Bandwidth or build minutes over the free amount can be billed there.
   Look for a spending limit or usage alert in Billing and turn on whatever exists. If you cannot confirm that going
   over a free limit stops the service instead of charging the card, stop and decide before going further.
4. When creating each resource, pick **Free** explicitly. Add no paid add-ons, no extra disk and no automatic upgrade.
5. If either service asks for a new card, a trial that turns into a paid plan, or a paid plan, **stop**. Write down what
   it asked for in `.ai/implementations/FB-032.md` and leave the hosted setup pending. The code side of FB-032 does not
   depend on it.

## 2. Create separate resources

Neither resource is connected to production in any way. Never restore, copy or branch production data into them.

**Database (Neon):** create a new Neon project on the Free plan, in the AWS region nearest Render's Ohio region (US East
(Ohio), `aws-us-east-2`, if offered). Choose PostgreSQL 18 if it is offered; otherwise choose the newest version and
record it here: `PostgreSQL version: ____`. The project's default branch is `main`; rename or create a branch called
`seed` for the clean made-up data (step 6).

**Web service (Render):** in the dashboard (not `render.yaml`, which stays production's alone), New, Web Service:

| Field | Value |
|---|---|
| Repository | this repository |
| Branch | `main` |
| Runtime | Docker |
| Dockerfile path | `./flexbuddy/Dockerfile` |
| Docker build context | `./flexbuddy` |
| Instance type | Free |
| Region | Ohio (same as production, near the database) |
| Health check path | `/actuator/health` |
| Auto-deploy | After CI checks pass (production's `checksPass`) |
| Environment groups | none; do not link production's |

## 3. Environment variables

Set these in the test service's Environment tab only. Values never go in the repository, a log, a screenshot or chat.
Names and placeholders only here:

| Name | Value |
|---|---|
| `FLEXBUDDY_TEST_SERVER` | `true` |
| `FLEXBUDDY_API_ENABLED` | `true` |
| `FLEXBUDDY_PUBLIC_URL` | `https://<test-service>.onrender.com` (no trailing slash), so emailed and calendar links stay on the test host |
| `DB_URL` | `jdbc:postgresql://<neon-host>/<database>?sslmode=require` (the pooled or direct host from Neon's connection details, written as JDBC) |
| `DB_USER` | Neon role name |
| `DB_PASSWORD` | Neon role password |
| `FLEXBUDDY_REMEMBER_ME_KEY` | new random value, test server only |
| `FLEXBUDDY_TWO_FACTOR_KEY` | new random value, test server only |
| `FLEXBUDDY_MAIL_LOG_LINKS` | `true` (see step 4) |

- **Leave `DATABASE_URL` absent.** `docker-entrypoint.sh` rebuilds `DB_URL` from it and drops `?sslmode=require`.
- **Generate both keys fresh**, in your own terminal, once each, and paste them straight into the dashboard. For
  example, in Git Bash: `openssl rand -base64 48`. Never copy, show or reuse production's keys, and never rotate production's. Keep the test keys stable too:
  changing `FLEXBUDDY_TWO_FACTOR_KEY` locks out every test account that uses an authenticator app.
- `FLEXBUDDY_CLIENT_ADDRESS_HEADER` and `FLEXBUDDY_CLIENT_ADDRESS_HOPS` stay unset. Render's proxy is the same as
  production's, so the defaults (the right-most `X-Forwarded-For` entry, one trusted hop) are right. Check the hourly
  `client address:` log line after the first visits; change them only if that line shows Render's own address instead
  of yours, and never to trust a header the visitor can set.
- `PORT` is set by Render. No VAPID keys: Web Push stays off.

## 4. Mail and sign-in identity

- Leave every `FLEXBUDDY_MAIL_*` SMTP setting (`HOST`, `PORT`, `USERNAME`, `PASSWORD`, `FROM`) and both
  `FLEXBUDDY_GOOGLE_CLIENT_ID` / `_SECRET` absent. With no mail host, `MailConfig` uses the logging mailers, and Google
  sign-in does not appear.
- `FLEXBUDDY_MAIL_LOG_LINKS=true` sets `flexbuddy.mail.log-links` (mapped explicitly in `application.properties`,
  default `false`). The logging mailers then write sign-up codes, two-step codes and password-reset links to the Render
  log (`email code requested; mail is not configured. Code: ...`). Only the owner reads them, in the Render log viewer,
  for made-up accounts. Agents never print or copy a code or link into an artifact or chat.
- With no mail host, **no mail leaves the server at all**: not account mail and not error alerts. Error alerts are still
  created, and `LoggingErrorAlertMailer` writes `error alert (mail not configured): <subject>` to the log. Leaving
  `FLEXBUDDY_ALERT_TO` unset does not do this: the address falls back to the support inbox. It is the missing mail host
  that keeps alerts in the log.

## 5. Deploying

- The service follows `main` and deploys a commit after its GitHub checks pass, like production. It runs what
  production runs, with the API on.
- To try an unmerged task, the owner changes the service's branch to that task's approved branch and deploys it by hand,
  then switches back to `main` (with a database compatible with `main`, step 6). No agent pushes, merges, deploys or
  changes the service.
- After each deploy, record it here, with non-secret identifiers only:

  | Date | Commit | Branch | Neon branch | Notes |
  |---|---|---|---|---|
  | | | | | |

## 6. Repeatable made-up data

- Made-up accounts and figures only (for example `driver1@example.test`). No real driver data, screenshots of real
  drivers, production backups or copies of the production database. Use synthetic screenshots with made-up data for
  OCR checks, including when selecting samples from FB-003; labeling a real screenshot does not make it synthetic.
- **Seed branch.** Deploy `main` against the Neon `seed` branch once, so Flyway creates the schema. Register the
  made-up accounts through the site, add their made-up blocks, then record the baseline: the `main` commit and the
  newest `flyway_schema_history` version. Do not use the `seed` branch for test sessions after that.

  | Seed baseline commit | Newest Flyway version | Recorded on |
  |---|---|---|
  | | | |

- **Session branch.** Before testing, create a Neon branch from `seed` (for example `session-2026-11-20`), point `DB_URL`
  at it and redeploy. Test, then point back and delete the branch.
- **Migrations.** A task that adds a migration runs only against a disposable branch made for it, never against `seed`.
  When the service goes back to `main`, point it at a branch whose schema matches `main` (the `seed` branch, or a fresh
  branch of it). Never downgrade Flyway, edit `flyway_schema_history`, or reuse a branch that is ahead of `main`. When
  `main` gains a migration, rebuild the seed: branch from `seed`, deploy `main` on it so the migration runs, check it,
  and make that the new `seed`. Update the baseline table.
- **Deleting a branch.** First check in Render that no service's `DB_URL` points at it.
- **After any reset**, earlier sessions, remember-me cookies and native-app tokens belong to a database that no longer
  exists. Sign in again on the browser and the app; clear site data if a page loops.

## 7. Smoke check

After the first deploy, and after any settings change. Record results here; keep passwords, codes and tokens private.

| Check | How | Result |
|---|---|---|
| HTTPS | open `https://<test-service>.onrender.com`; the browser shows a valid certificate | |
| Health | `/actuator/health` returns `{"status":"UP"}` | |
| Banner, both languages | `/login` and `/login?lang=es` show the banner; signed-in pages, `/privacy`, a 404 page and the tax print view show it too | |
| Noindex | `curl -sI https://<test-service>.onrender.com/login` shows `X-Robots-Tag: noindex`; so does `curl -sI .../css/styles.css` | |
| Web sign-up and sign-in | register a made-up account; read its code in the Render log; sign in, sign out, sign in again | |
| API sign-in | `POST /api/v1/auth/sign-in` with the made-up account (`docs/api-v1.md`), then `GET /api/v1/account` with the token | |
| Cached shells and offline fallback | follow the offline procedure below; both cached shells and the fallback show the banner | |
| Cold start | after 15+ minutes idle, time the first request until the page loads | |
| Memory and screenshot reading | import a few synthetic screenshots containing only made-up data; note time per screenshot and the memory graph in Render's Metrics tab | |

**Offline procedure (test origin only):**

1. Sign in with a made-up account, visit `/` and `/account` online, and wait until the service worker controls the page.
   In browser developer tools, confirm the current `flexbuddy-pages-*` cache contains both routes and the current
   `flexbuddy-static-*` cache contains `/offline.html`.
2. Switch the browser offline and navigate to `/` and `/account`. Each cached shell must show the test-server banner.
3. While still offline, delete only the `/account` entry from the current `flexbuddy-pages-*` cache in developer tools.
   Keep the static cache and its `/offline.html` entry. Navigate to `/account` again: the offline fallback must show the
   banner and Retry button. Do not use an arbitrary unvisited route: the service worker handles page navigation only
   for `/` and `/account`.
4. Restore the connection and visit `/account` to cache its shell again. Repeat for the other language, confirming the
   cached fallback's language before disconnecting; it retains the language it had when cached at worker installation.

- Render Free currently sleeps after 15 minutes without traffic and takes about a minute to wake. Do not add keep-alive
  pings to stop it sleeping.
- Free instances are small (512 MB at the time of writing). If start-up or screenshot reading fails or runs out of
  memory, record it as a limitation. Do not move to a paid instance.
- The disk is wiped on every restart and deploy. Nothing the app needs is stored there.

## 8. Handing it to the app

- Record the test origin for NA-03: `Test origin: https://<test-service>.onrender.com` (not secret). Also record the
  cold-start time measured above.
- Debug (`.dev`) builds may offer it as a server choice. Play and release builds keep using production only.
- The app's "server waking up" and timeout messages belong to NA-03 or the app task that first needs them.
- Never test against the emulator's Play Store install for this: it is signed in to production.

## Status

Hosted setup: **pending**, owner-operated. Until steps 1 to 7 are done and recorded above, the hosted test server is not
ready.
