# FlexBuddy agent instructions

These rules apply to every coding agent working in this repository. Read this file before making changes.

## Project

FlexBuddy is a shift and earnings tracker for delivery drivers, live at https://flexbuddy.onrender.com.

- `flexbuddy/` is the Spring Boot app: Java 21, Spring Boot 4, Spring MVC, Thymeleaf, Spring Data JPA, Flyway,
  PostgreSQL 18, Tess4J/Tesseract OCR. Run Maven commands from this directory.
- `flexbuddy/src/main/resources/static/js/` holds the browser scripts; their Node tests are in `flexbuddy/src/test/js/`.
- `flexbuddy/src/main/resources/i18n/` holds the English (`messages.properties`) and Spanish (`messages_es.properties`)
  text catalogs.
- `android/` is a Trusted Web Activity wrapper around the production site, built with Bubblewrap. It contains no app
  logic of its own.
- `render.yaml` defines production on Render.

## How work is organised

- Each task has a specification in `.ai/tasks/<task-id>.md` (ids `FB-001`, `FB-002`, ...; see `TEMPLATE.md`) and its own
  branch `ai/<task-id>`. The specification is committed on that branch before implementation starts.
- Design references for a task go in `.ai/design/<task-id>/`.
- Repeatable procedures live in `.agents/skills/`: `implement-task` to build a task, `prepare-review` to get it ready for
  a pull request. If your tool does not load skills, read the `SKILL.md` and follow it.
- `.ai/implementations/<task-id>.md` is the handoff report between agents and `.ai/reviews/<task-id>.md` is the review
  packet. Both are local files, ignored by Git.
- Only one agent edits a working tree at a time.

## Before changing anything

1. Read the task you were given, then only the files it touches and their existing tests.
2. Follow the patterns already in the code. Keep business logic in services, not controllers or templates.
3. Make the smallest change that does the task. Do not touch unrelated code, CI, `render.yaml` or the Android signing
   setup unless the task says to.
4. Do not add frameworks or dependencies without asking.

## Deployment: pushing to main is a release

Render deploys every push to `main` once the GitHub checks pass. There is no staging environment.

- Never push, merge or force-push without the owner's explicit approval for that push.
- Work on a feature branch, not directly on `main`.
- Never edit production data, Render settings or environment variables.
- `FLEXBUDDY_TWO_FACTOR_KEY` must never be regenerated: it would lock out every driver who uses an authenticator app.

## Testing

Run what your change touches and report every check you did not run. Never claim a check passed unless you ran it.

From the repository root, `.\scripts\verify.ps1 -TaskId <task-id>` runs both suites and writes a summary to
`.ai/artifacts/<task-id>/`. To run them one at a time, from `flexbuddy/`:

```powershell
.\mvnw.cmd -B -ntp verify
node --test "src/test/js/*.test.js"
```

- Run the Node tests (Node 22+, no install) whenever a change touches `static/js`. They are separate from Maven on
  purpose.
- Scripts must not take a date from `toISOString()`: it is UTC and gives tomorrow's date on a US evening. A test fails on
  it. A line that counts UTC days on purpose ends with `// utc-day`.
- Scripts are tested as they are loaded from disk, so keep logic in exported functions, not code that needs the page at
  load time.
- The PostgreSQL migration test is skipped locally unless `FLEXBUDDY_TEST_POSTGRES_URL`, `_USER` and `_PASSWORD` are
  set. CI runs it against Postgres 18 on every pull request. Say so if you did not run it.
- User-facing text goes in both catalogs, English and Spanish, never hard-coded in a template or script. A new key goes
  in the same place in both files, in the same comment group. Spanish keeps the English `{0}` arguments and markup, and
  a message with arguments writes an apostrophe as `''`. `SpanishMessagesTest` checks all of this.
- For UI changes, run the app (`.\mvnw.cmd spring-boot:run`, then http://localhost:8080) and check the page you changed,
  at phone width too.

## Data, security and money

- Every query and endpoint is scoped to the signed-in user. Never return or change another user's data.
- Keep existing authentication, authorization and rate-limit checks.
- Database changes go in a new Flyway migration in `flexbuddy/src/main/resources/db/migration/` (next number after the
  newest `V<n>__*.sql`). Never edit a migration that already exists; it has run in production.
- Preserve earnings rules: scheduled shifts never count toward earnings, hours or net figures until confirmed.
- Preserve backup/restore compatibility (older backup versions must stay restorable) and the 30-day Recently deleted
  window.
- Never print, log or commit secrets, private calendar feed links (`/calendar/<token>.ics`) or real drivers' data. Use
  test accounts and made-up data in screenshots.

## Android and the emulator

- The Android emulator has the Play Store build of FlexBuddy installed. It is signed in to **production** as the Play
  Reviewer account. Use it for read-only checks only (launch, offline start, cached screens). Never add, edit, delete or
  queue anything there. Test anything that writes data against `localhost:8080` with a local test account.
- In the Play Store listing, the Uninstall button under "Installed on all devices" removes the app from the owner's
  real phone. Never press it.
- Keystores, passwords and signed builds never go in the repository, scripts, logs or chat. Signing is done by the
  owner in an interactive terminal.
- The package name `com.angel.flexbuddy` and the upload key must not change.
- A wrapper change means editing `android/twa-manifest.json`, raising `appVersionCode`, and recording the Play upload in
  `android/CHANGELOG-android.md`.
- `/.well-known/assetlinks.json` must keep both certificate fingerprints (upload key and Play app signing).

## Commits

- Conventional style, lowercase, saying what changed for the driver: `feat(scope): ...`, `fix(scope): ...`,
  `docs(android): ...`, `chore(deploy): ...`.
- No AI attribution: no `Co-Authored-By` lines for an AI and no "Generated with" lines, in commits or pull requests.
- Do not commit `.claude/worktrees/`, `.idea/`, `plans/`, `handoff-tools/`, local logs or build output.

## Handing off

When you stop, whether finished or not, write `.ai/implementations/<task-id>.md` with:

- what changed and which files
- the checks you ran and their actual results, and the ones you did not run
- what is still unfinished, and any security or database concerns

Do not call a task done while any part of it is unfinished.
