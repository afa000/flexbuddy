# Publishing the shared import library

The release setup publishes `com.angel:flexbuddy-import-core` to `https://maven.pkg.github.com/afa000/flexbuddy`.
Every release needs the owner's approval of that actual release; merging a version bump does not publish.

## Releases

- **0.1.0**, published November 15, 2026 from tag `import-core-v0.1.0` (commit 2057ab8). Not usable on Android 8
  to 13: it calls `Stream.toList()` and `Matcher.results()`, which Android only has from API 34 and D8 does not
  backport, so every import there throws `NoSuchMethodError`.
- **0.1.1**, published November 15, 2026 from tag `import-core-v0.1.1` (commit 61d15cf). Replaces those calls with
  API-26-safe code that returns the same results (same elements, order and unmodifiable lists); no parsing, warning
  or matching rule changes. `AndroidApiCompatibilityTest` now refuses both calls.
- **0.1.2**, published November 15, 2026 from tag `import-core-v0.1.2` (commit f6392ce, Publish import core run
  43787558971). Reads the Spanish app's "Consejos" tip line and its "jue, oct 22" date header, and takes an Earnings
  list's date from the block whose times are used instead of a "Payment sent" row above it. Other screens parse as
  before. The Android app pins it from FB-039.
- **0.2.0**, published November 17, 2026 from tag `import-core-v0.2.0` (commit b8f50bb, Publish import core run
  44023503043). Adds the earnings figures in `com.angel.flexbuddy.importcore.figures` (NA-13): the block earnings
  rules, net earnings, goal progress, pay periods, IRS and custom mileage rates and the offer evaluator, which the
  server now delegates to. Screenshot parsing, warnings and matching are unchanged from 0.1.2. SHA-256: JAR
  `21bd80c64e1611263f259973dce7cd4d5a2273b4a1b4f25a19a561a97fe70835`, POM
  `79053a2caef9a222ee3a793d350ec746439d3a16536b69f5e07af6fe35fd18d8`. Android FB-041 pins 0.2.0 and verified
  the figure contracts on API26/37. This remains the merged Android baseline until FB-046 verifies 0.3.0.
- **0.3.0**, published November 17, 2026 from annotated tag `import-core-v0.3.0`, source commit
  `f4eb79d0d07c9902d71eead64f6195ab73ac1ec9` (FB-044, PR #57),
  [Publish import core run 44234045242](https://github.com/afa000/flexbuddy/actions/runs/44234045242).
  Adds `GoalPreparation` (90-day history boundary, goal averages and scheduled net estimate) and `PlannedWork`
  (upcoming count/minutes/gross pay and overdue confirmations). Existing import and 0.2.0 figure APIs are unchanged.
  The guarded workflow uploaded the tested JAR/POM and verified authenticated downloads. A separate read-only
  registry download independently matched both release-manifest SHA-256 values:
  JAR `7eff707f4d647364b1601a3695c3195680684943c5a32bcbd309f6d601d86216`,
  POM `79e804387b07b36728976da25321905c42a3ffca83573a1b7e11227ad136b022`.
- **0.4.0**, published November 19, 2026 from annotated tag `import-core-v0.4.0`, source commit
  `a246fe6ed2747faecefafb8cbf848c4fc3da4f2e` (FB-053, PR #61),
  [Publish import core run 44624897743](https://github.com/afa000/flexbuddy/actions/runs/44624897743). Adds the
  blocks and expenses CSV writers in `com.angel.flexbuddy.importcore.export` (`ShiftCsv`, `ExpenseCsv` and their
  plain `ShiftCsvRow` / `ExpenseCsvRow` inputs), which the server's CSV downloads now delegate to with unchanged
  bytes. Import, figures and 0.3.0 APIs are unchanged. The guarded workflow uploaded the tested JAR/POM and
  verified authenticated downloads against the release manifest: JAR
  `01d10d13b95a10a98c1732002a0d9103a0a6dd7f669dcda0e2697b7f5f60397b`, POM
  `b674e0ae844d4509a668abcf344021e101efb9ec86de8f215c839543930e0cae`. The run's release artifact matched those
  hashes when downloaded locally. A separate read-only registry download was not done: the local credential has no
  `read:packages` scope. Android keeps its current pin until its own task pins and verifies 0.4.0.
- **0.5.0**, published November 20, 2026 from annotated tag `import-core-v0.5.0`, source commit
  `1df2cbf142d474b63bf9a701e8372b74f8dde51f` (FB-055, PR #63),
  [Publish import core run 44812258745](https://github.com/afa000/flexbuddy/actions/runs/44812258745). Adds the
  earnings report in `com.angel.flexbuddy.importcore.figures` (`EarningsReport` with its plain `ReportBlock` /
  `ReportCost` inputs, `RouteRules` and the `ReportSelection` helpers), which the server's earnings report now
  delegates to with unchanged responses. Import, figures, export and 0.4.0 APIs are unchanged. The guarded workflow
  uploaded the tested JAR/POM and verified authenticated downloads against the release manifest: JAR
  `91b698a18e690b0b32b93519b69f33a93336b840d5a4d9753ded6c6021c8106c`, POM
  `af8761039f39d0f793d7ec10e6507a5cfb4d5abbae4766152607397bf7fa769e`. The run's release artifact matched those
  hashes when downloaded locally. A separate read-only registry download was not done. Android's Reports screen
  (FB-056) pins and verifies it in its own task.

The server adopts these helpers without changing its goal/planning results. See the library README for selection
and rounding contracts. Android's merged FB-041/FB-043 baseline still pins verified 0.2.0; FB-046 separately pins
0.3.0, audits the entire resolved JAR and tests API26/current-API runtime behavior before adoption. Registry byte
verification does not prove a clean Maven/Gradle consumer or Android runtime. Offline Home goals/planning UI and
Reports integration remain later work. The reader examples below intentionally retain the current Android 0.2.0
baseline until that verification gate passes.

The library remains part of the server's source reactor. Server verification, Docker and Render do not download
this package or need a package credential. Publishing does not prove Android compatibility or OCR accuracy.

## Release controls

- Only **Publish import core**, manually dispatched from `main` in `afa000/flexbuddy`, can publish. Pushes, PRs and
  release events do not trigger it. A dispatch from another branch skips both jobs.
- The stable version input derives exactly one tag, `import-core-vVERSION`. The tag must already exist, match the
  standalone library POM and point to a commit reachable from `origin/main`. Versions such as `latest`, snapshots,
  prereleases, paths or shell fragments are refused. The tag is resolved to an immutable commit SHA.
- The first job has read permission only. It checks out that SHA, runs the library tests, inspects runtime
  dependencies and packages only the main JAR and POM with a SHA-256 manifest. Staging runs from a copy of the
  dispatch commit's release script taken before the tagged build, and refuses the copy if its checksum changed.
- The publishing job uses trusted tooling from the workflow's dispatch commit. It rechecks the tag/SHA, manifest,
  coordinates and file set. Only this job has `packages:write`; its ephemeral `GITHUB_TOKEN` is exposed only to the
  upload/verification step. No long-lived publishing PAT is needed.
- Upload runs the pinned Maven Deploy Plugin's `deploy-file` goal through the dispatch commit's Maven wrapper (the
  same Maven version as every other project build) from a temporary neutral project. The tagged POM
  is supplied only as the uploaded `pomFile`; its Maven lifecycle, extensions and scripts do not execute with write
  permission. This uploads the same tested library POM/JAR, never the server or reactor.
- All releases of this artifact are serialized, and an active upload is not cancelled by a newer dispatch.
- Preflight checks metadata and both versioned artifacts. Existing versions or partial files stop publication.
  Authentication failures, rate limits, server errors, malformed metadata and network failures stop it too.
- Successful upload is followed by authenticated downloads of both files and exact SHA-256 comparison. Only a
  verified release gets the success summary. Brief 404 visibility delays are retried; the workflow does not retry
  a deployment.
- HTTPS redirects may be followed, but authentication is never forwarded to a different origin.

## First release: owner-operated steps

1. Merge the reviewed publishing setup. Confirm the intended `main` commit contains this workflow, the library
   release configuration and the approved parser. Review its passing CI and the package version before tagging.
2. Confirm `flexbuddy/import-core/pom.xml` says `0.1.0`. The initial tag must point to a commit containing the new
   publishing configuration, rather than the earlier extraction-only commit. For subsequent releases, update the
   library version and the server's pinned source-module dependency together in a separately reviewed change.
3. Approve the actual publication, then create and push the annotated tag yourself. Replace `REVIEWED_MAIN_SHA`
   with the reviewed full commit hash. Do not overwrite an existing tag:

   ```powershell
   git tag -a import-core-v0.1.0 REVIEWED_MAIN_SHA -m "release import core 0.1.0"
   git push origin refs/tags/import-core-v0.1.0
   ```

4. In this repository's GitHub Actions, select **Publish import core**, choose branch **main**, enter `0.1.0` and
   dispatch it. Do not dispatch from the release tag or a feature branch. The workflow never creates or pushes tags.
5. Check the run summary for the intended coordinates, tag, full source SHA and both SHA-256 hashes. Confirm upload
   and downloaded-byte verification passed. A missing success summary is not proof of a successful release.
6. Configure a read-only reader credential outside this repository, then verify authenticated resolution from a
   clean consumer. The Android integration task pins this exact version and verifies its own build/runtime support.

## Reader authentication

GitHub's Maven registry requires authentication even for public packages. Maven packages inherit the repository's
visibility/access; this package belongs to the public server repository. The Android repository is private, but
that does not make this server package private. These rules are documented in
[GitHub's Maven registry guide](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
and [package permissions](https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages).

Use a separate **classic** personal access token with `read:packages` and the necessary read access. The publisher
uses its own ephemeral workflow token; readers do not need write/delete scopes. Do not assume the Android
repository's `GITHUB_TOKEN` can read this cross-repository Maven package or that Maven exposes the granular
**Manage Actions access** controls available to other registries.

The owner creates/configures reader credentials locally and in the Android repository's Actions secrets. Do not
paste credentials in chat, commit them, place them in a POM or Gradle file, or print environment/settings contents.
The following names are examples for the future consumer setup:

- `FLEXBUDDY_PACKAGES_USER`: the GitHub account login.
- `FLEXBUDDY_PACKAGES_TOKEN`: that account's read-only classic package token.

A local Maven consumer can use a private, owner-created settings file outside the repository, referencing the
environment rather than a literal token:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers><server>
    <id>flexbuddy-packages</id>
    <username>${env.FLEXBUDDY_PACKAGES_USER}</username>
    <password>${env.FLEXBUDDY_PACKAGES_TOKEN}</password>
  </server></servers>
</settings>
```

The consumer's Maven repository id must be `flexbuddy-packages`, and its URL must be the registry URL above.
A standalone Maven consumer pins the exact published version it intends to verify: 0.3.0 is available for its
release-specific consumer check. Published `com.angel:flexbuddy-import-core:0.2.0` is the merged Android baseline
example, not a universal Maven version requirement. This task does not modify anyone's existing settings.

The Android app (repository setup in FB-038/FB-039, pin upgraded in FB-041) uses this, inside its
`dependencyResolutionManagement.repositories` block:

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/afa000/flexbuddy")
    content { includeModule("com.angel", "flexbuddy-import-core") }
    credentials {
        username = providers.environmentVariable("FLEXBUDDY_PACKAGES_USER").orNull
        password = providers.environmentVariable("FLEXBUDDY_PACKAGES_TOKEN").orNull
    }
}
```

Pin `implementation("com.angel:flexbuddy-import-core:0.2.0")` in the app module, with the release run's manifest
hashes in Gradle dependency verification. In its CI, the owner supplies the two environment variables from
repository secrets only to jobs that need authenticated dependency resolution. Do not supply them to untrusted fork
jobs. Never use `mavenLocal()` or dynamic versions as the release dependency.
Historical reader checks resolved 0.1.0 from a clean Maven consumer and 0.1.2 in Android CI with repository
secrets. FB-041 subsequently pinned published 0.2.0 and verified its figures on API26/37. Published 0.3.0 remains
subject to the separate Android pinning and runtime verification steps above.

## Failure and partial publication

Treat tags and published versions as immutable. Do not move tags, replace artifacts, delete/recreate versions or
automatically retry a failed deployment. Some files may have reached the registry before an error occurred.

If a run fails, inspect its safe metadata and the package's existing version/files. Authentication or registry
errors must be resolved before deciding whether anything was published. If the correct JAR/POM are present, verify
them against the original run's hashes before describing the release as usable. If the state is partial or differs,
the owner decides the recovery; prefer a separately reviewed new version/tag. There is no automatic cleanup job.
Cancellation after an upload starts has the same uncertainty. A later preflight refuses existing metadata/files.

On the first release, a refusal before any upload may come from the preflight's assumptions about the registry,
not from an existing version. It treats only HTTP 404 as "not published yet", and expects the package's
`maven-metadata.xml` to have a plain `<metadata>` root with the library's group and artifact ids. Unit tests use
synthetic responses, so GitHub's real answers are first seen on that run. If the run reports
"Registry metadata check returned HTTP ..." or "Unexpected registry metadata", note the status it printed and check
the package page before changing anything. Loosening the preflight is a separately reviewed change, never a retry
with the checks turned off.

## Local validation and remaining work

The release helpers use Python 3's standard library, already available on the Ubuntu runner; no Python packages or
third-party actions are installed. Run their tests locally with:

```powershell
python -B -m unittest discover -s scripts/release -p 'test_*.py'
.\scripts\verify.ps1 -TaskId NA-12-publish
```

CI runs these release tests in a separate read-only job. Unit tests use synthetic registry responses and disposable
local Git fixtures; they never contact GitHub Packages, publish or access reader credentials. A temporary file-backed
Maven deployment also verifies packaging/checksums during implementation validation, without a remote upload.

Remote publication through 0.3.0 is complete, as recorded above. Earlier authenticated Maven/Android resolution
and runtime checks are historical release evidence, not pending first-publication work. The separate FB-046 gate
covers 0.3.0's resolved consumer bytes, entire-JAR audit and Android Java 21/D8/API26/current-API runtime checks;
this documentation task does not perform or claim those checks.

OCR changes still require the six-field labelled phone benchmark gate. The server's private sample test checks
only five fields; successful packaging and synthetic OCR checks alone do not establish that phone benchmark.
