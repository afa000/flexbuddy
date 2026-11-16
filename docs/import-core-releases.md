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
- **0.1.2**, prepared in NA-12-parser-fixes, not yet tagged or published. Reads the Spanish app's "Consejos" tip
  line and its "jue, oct 22" date header, and takes an Earnings list's date from the block whose times are used
  instead of a "Payment sent" row above it. Other screens parse as before. The server's source-module dependency is
  bumped with it. Release it with the steps below, using `0.1.2` and tag `import-core-v0.1.2`; consumers keep
  pinning 0.1.1 (below) until 0.1.2 is published.

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

The consumer's Maven repository id must be `flexbuddy-packages`, its URL must be the registry URL above, and its
dependency must pin `com.angel:flexbuddy-import-core:0.1.1`. This task does not modify anyone's existing settings.

For the later Android task, inside the existing `dependencyResolutionManagement.repositories` block:

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

Pin `implementation("com.angel:flexbuddy-import-core:0.1.1")` in the app module. In its CI, the owner supplies the
two environment variables from repository secrets only to jobs that need authenticated dependency resolution.
Do not supply them to untrusted fork jobs. Never use `mavenLocal()` or dynamic versions as the release dependency.
Actual resolution remains untested until the owner approves/releases the first version and configures a reader.

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

First remote publication, authenticated clean-consumer resolution, Android Java 21/D8/API-26 desugaring and the
six-field labelled OCR benchmark remain separate. The private sample test currently checks only five fields;
successful packaging and the synthetic OCR checks are not the phone's go/no-go evidence.
