# Plan: run the tests on every push and deploy only after they pass

Planned against `origin/main` at 89bde5d. Ship after
`plan/mail-health-check`, because the CI run should start from a
healthy app.

## Where things stand

- `main` auto-deploys to Render on every push, and nothing runs the tests
  first.
- Locally on 89bde5d, `mvn test` takes 55 s: 441 tests pass and 1 is
  skipped. The JS tests take 0.4 s, with 45 passing.
- The 1 skipped test is `PostgresMigrationTest`, which runs only when
  `FLEXBUDDY_TEST_POSTGRES_URL` is set. **It is broken today.** I ran it
  against Postgres 16 and it failed with `expected: 5 but was: 18` at
  line 55, because the migration count was hard-coded when V8 was the
  latest. With the count corrected, it passes. With all 10 entities added
  to its schema validation (it checks only 5 today), it also passes.
  CI will run this test, so it has to be fixed in the same commit.
- `flexbuddy/mvnw` is committed without the executable bit (mode 100644).
  CI calls it through `sh`, which avoids touching the file mode.
- `render.yaml` is synced through a connected Blueprint (README,
  "Deployment"), so a field added there reaches the service.

## 1. Goal and out-of-scope

**Goal:**
- A GitHub Actions workflow runs on every push to `main` and on every
  pull request. It runs the Java tests, including the Postgres migration
  test against a real Postgres 18 (Render's version), and the JS tests.
- Render deploys a `main` commit only after those checks pass. A push that
  breaks a test never reaches drivers. The previous version stays live,
  and the red check in GitHub shows why.

**Out of scope:**
- Building the Docker image in CI. The image compiles Leptonica and
  Tesseract from source, which takes many minutes per run. Render already
  builds it, and a failed image build there leaves the previous version
  live.
- Android builds, lint and formatting tools, coverage reports, and
  dependency scanning.
- Requiring pull requests for `main`. The local session pushes to `main`
  directly, and this plan keeps that working. See section 5.
- Deploying from Actions. Render keeps doing its own deploys.

## 2. Data model and migration

None.

## 3. Backend and build files

### New `.github/workflows/ci.yml` (repo root, not under `flexbuddy/`)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: ci-${{ github.ref }}
  # A newer push to a pull request replaces the older run; runs on main always finish.
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}

jobs:
  java:
    name: Java tests
    runs-on: ubuntu-24.04
    timeout-minutes: 20
    services:
      postgres:
        image: postgres:18
        env:
          POSTGRES_HOST_AUTH_METHOD: trust
        ports:
          - 5432:5432
        options: >-
          --health-cmd "pg_isready -U postgres"
          --health-interval 5s
          --health-timeout 5s
          --health-retries 10
    defaults:
      run:
        working-directory: flexbuddy
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
          cache-dependency-path: flexbuddy/pom.xml
      - name: Run tests
        env:
          FLEXBUDDY_TEST_POSTGRES_URL: jdbc:postgresql://localhost:5432/postgres
        run: sh ./mvnw -B -ntp verify
      - name: Keep test reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: flexbuddy/target/surefire-reports
          retention-days: 7

  js:
    name: JS tests
    runs-on: ubuntu-24.04
    timeout-minutes: 5
    defaults:
      run:
        working-directory: flexbuddy
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - name: Run tests
        run: node --test "src/test/js/*.test.js"
```

Notes for the implementer:
- `POSTGRES_HOST_AUTH_METHOD: trust` means no password exists anywhere.
  The test's built-in defaults for user and password (`postgres` /
  `postgres`) are accepted because trust ignores the password. Don't add
  `FLEXBUDDY_TEST_POSTGRES_PASSWORD`, and don't add any secrets.
- `verify` rather than `test`, so a packaging failure such as a broken
  `spring-boot-maven-plugin` repackage also turns the check red.
- The job names `Java tests` and `JS tests` are what GitHub and Render
  show. Keep them stable.
- No `paths-ignore`. Every commit on `main`, including docs-only ones,
  gets checks. Otherwise Render's "checks pass" gate would have nothing
  to wait for.

### `render.yaml`

Under the `flexbuddy` web service, after `healthCheckPath`, add:

```yaml
    # Deploy a main commit only after its GitHub checks (Java and JS tests) pass.
    autoDeployTrigger: checksPass
```

Nothing else in the file changes. After the Blueprint syncs, confirm it
in the dashboard (section 7). If the dashboard doesn't show it, set it
there by hand under **Settings → Build & Deploy → Auto-Deploy → "After
CI checks pass"**.

### `flexbuddy/src/test/java/com/angel/flexbuddy/migration/PostgresMigrationTest.java`

- Replace the hard-coded count (line 55):

  ```java
  MigrationInfo[] pending = latest.info().pending();
  MigrateResult result = latest.migrate();
  assertThat(result.migrationsExecuted).isEqualTo(pending.length).isGreaterThan(0);
  assertThat(latest.info().pending()).isEmpty();
  ```

  This pins that every migration after V3 applies on top of real V3 data
  and none is left pending, without editing the test each time a
  migration is added. Imports: `org.flywaydb.core.api.MigrationInfo` and
  `org.flywaydb.core.api.output.MigrateResult`.
- Extend `addAnnotatedClasses(...)` (around line 165) to all ten
  entities: add `PayoutDeposit`, `StandingEntry`, `TaxPayment`,
  `TaxReminderLog` and `PasswordResetToken`, with imports from
  `com.angel.flexbuddy.model`. Hibernate's `validate` then checks every
  migration from V15 to V21 against its entity. I ran exactly this
  locally and it passes on 89bde5d.
- Don't edit any migration file.

### `README.md`

- Under "Run the tests", add one paragraph:
  "GitHub Actions runs both suites, including the PostgreSQL migration
  test against Postgres 18, on every push to `main` and every pull
  request."
- Under "Deployment", replace "Pushes to `main` are automatically
  deployed through the connected Render Blueprint." with "Render deploys
  a push to `main` once its GitHub checks pass. A commit with a failing
  test is never deployed."

## 4. Frontend files

None.

## 5. Ripple list

- **Deploy timing:** a push now goes live about 2–3 minutes later than
  before, after the Java job finishes. The Docker build starts only after
  that.
- **Red checks on `main`:** the commit stays on `main` but isn't
  deployed. The fix is a new commit that turns the checks green. Never
  force-push `main`.
- **Branch protection:** don't add a rule that requires status checks or
  pull requests on `main`. GitHub would then reject the local session's
  direct pushes. Render's `checksPass` already keeps untested code away
  from drivers. What I do recommend is a ruleset on `main` that blocks
  force pushes and branch deletion: GitHub → Settings → Rules →
  Rulesets → New branch ruleset, target `main`, tick "Restrict
  deletions" and "Block force pushes", with no bypass list needed. This
  is a manual step and not part of the commit.
- **Actions minutes:** the repo is public, so standard runners are free
  with no monthly limit. Each run takes about 3–4 minutes.
- **Plan branches:** pushes to `plan/*` don't run CI, because only `main`
  and pull requests trigger it. That's intended.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes.
  `sw.js` is unchanged.

## 6. Tests to add

- `PostgresMigrationTest` changes as described in section 3. It pins:
  - every pending migration applies on data written at V3;
  - nothing is left pending;
  - all ten entities match the migrated schema.

  That last point catches the H2-versus-Postgres gap, where the test
  profile builds its schema from the entities and never runs Flyway.
- The workflow itself is the test of the CI setup. Before committing, run
  locally, from `flexbuddy/`:
  - `mvn -B -ntp verify` with `FLEXBUDDY_TEST_POSTGRES_URL` pointing at a
    local Postgres. Expect 0 failures and 0 skipped: 442 tests on 89bde5d,
    or 444 once `plan/mail-health-check` has shipped.
  - `node --test "src/test/js/*.test.js"`. Expect 45 passing.

## 7. Manual checks

There's no UI, so there is nothing to check at 375–430px. On desktop:

1. After the push, open GitHub → **Actions** → **CI**. The run for the
   commit should show both `Java tests` and `JS tests` green. The Java job
   log's last summary line should show `Failures: 0, Errors: 0,
   Skipped: 0`. 0 skipped proves the Postgres test ran.
2. On the commit page, the check mark next to the commit should list both
   jobs.
3. In Render, open the web service and check **Settings → Build & Deploy →
   Auto-Deploy**. It should read "After CI checks pass".
4. In Render **Events**, the deploy for this commit should start only
   after both checks finished. Compare the times with the Actions run.
5. To test the gate safely, create a branch `ci-check` with one assertion
   changed to fail. Push it and open a pull request in GitHub. The run
   should go red, and Render deploys nothing because it's not `main`.
   Close the pull request and delete the branch.
6. Open the live app on a phone or at 390px in desktop dev tools. Home
   should load as before.

## 8. Commit message

```
ci(build): run the tests on every push and deploy after they pass

Pushes to main went to drivers with no tests run. A GitHub Actions
workflow now runs the Java tests and the browser script tests on every
push to main and every pull request. The Java job starts a Postgres 18
service, the version Render runs, so the migration test runs too
instead of being skipped. Render now deploys a main commit only after
its checks pass, so a commit that breaks a test stays off the live
app.

The migration test had a hard-coded count of five migrations and would
have failed against today's eighteen. It now checks that every pending
migration applies on real V3 data and that nothing is left pending,
and it validates all ten entities against the migrated schema instead
of five.
```

## 9. Open questions

1. ~~Is the repo public or private?~~ **Decided:** public, so Actions
   minutes are free and unlimited for these jobs.
2. **Do you want the force-push and deletion ruleset on `main`?** I
   recommend it. It's a manual setting and not part of this commit.
3. **Should CI also build the Docker image?** That would be a weekly or
   manual job, or only on changes to `Dockerfile` or `pom.xml`. I left it
   out because it's slow and Render already builds it. Say if a failed
   Render image build has ever cost you a deploy.
