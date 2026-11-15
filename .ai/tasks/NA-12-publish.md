# Task: NA-12-publish - Release the shared import library for the native app

Status: Ready for implementation; owner approved the publishing setup and specification commit on November 15, 2026
Risk: medium (release workflow permissions, artifact identity and package credentials)
Implementer: Codex (Sol), assigned by the owner on November 15, 2026
Review: fresh top-model session using review-task

## Objective

Make the shared screenshot parser available as a versioned Maven artifact so the separate native app repository
can benchmark a phone OCR engine against the same parser. The server continues building its library from source;
normal server builds and deployments require no package credentials.

Depends on PR #47, merged to main as 6d7acdaca0b8726d68e59a0c1dac8bb2f32a8605 on November 15, 2026.
Plan: docs/native-app-plan.md, NA-12 step 2. This task prepares publishing; the owner controls first publication.

## Files to read

- AGENTS.md; docs/native-app-plan.md, NA-12; .ai/tasks/NA-12.md.
- flexbuddy/import-core/pom.xml and README.md; flexbuddy/reactor/pom.xml; flexbuddy/pom.xml.
- .github/workflows/ci.yml; scripts/verify.ps1; flexbuddy/Dockerfile; docs/agent-workflow.md (build/review loop).
- Existing import-core tests and relevant CI/build checks. Search before opening additional files.
- Official documentation, verified November 15, 2026:
  - https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry
  - https://docs.github.com/en/packages/learn-github-packages/about-permissions-for-github-packages
  - Official GitHub Actions and Apache Maven documentation for the exact workflow/deploy options used.

## Current behavior

com.angel:flexbuddy-import-core:0.1.0 is a thin Java 21 library with no runtime dependencies. The application's
reactor and Docker build it from source. It is locally installable but no publishing configuration exists.

Verified connected repository metadata: afa000/flexbuddy is public; afa000/flexbuddy-android is private.
GitHub's Maven registry uses repository-scoped visibility/access, and authentication is required even for public
packages. A publishing workflow can use its repository's GITHUB_TOKEN. Cross-repository consumption must not
assume the Android repository's GITHUB_TOKEN has access or that granular package access controls apply to Maven.

## Proposed setup for owner approval

- Registry: https://maven.pkg.github.com/afa000/flexbuddy.
- Initial coordinate: com.angel:flexbuddy-import-core:0.1.0, inheriting this public server repository's visibility.
- Release identity: immutable import-core-v0.1.0 Git tag. Later versions use corresponding new version tags.
  The owner explicitly approves and performs any tag push. Never overwrite a tag or published version.
- Trigger: manually dispatched workflow on the default main branch only. No publication on push, PR or release
  events. The owner starts the workflow after merging this setup and approving the actual first publication.
- Publisher credential: GitHub-provided GITHUB_TOKEN, with contents:read and packages:write only in the publishing
  job. No stored publishing PAT, repository-wide permissions increase, automatic package deletion or workflow writes.
- Reader credential: a separate classic personal access token with read:packages and the account's necessary read
  access. The owner configures it locally and in Android Actions secrets; never in chat, source or logs. Do not
  request repo/write:packages/delete:packages as routine reader scopes. Verify exact required access before setup.
- Android integration is a later app-repository task: authenticated Maven repository URL, pinned version 0.1.0,
  and credentials supplied through local/CI environment configuration. No dynamic version or mavenLocal dependency.
- Existing server verification, Docker and Render paths remain source-only and unchanged.

## Required behavior

### Artifact publishing configuration

1. Add distributionManagement for this library only, using a server id matching the workflow-generated Maven
   settings. Do not add a GitHub Packages download repository to the server or a credential to any POM.
2. Publish only the library's main JAR and POM at the approved coordinates. Do not deploy the Spring Boot app,
   aggregator, tests, screenshots, extracted text, traineddata or local files. Keep runtime dependencies empty.
3. Pin the deploy plugin version and other workflow dependencies consistently with existing project conventions.
   Do not add third-party actions, dependencies or frameworks without approval.

### Manual workflow

1. Add .github/workflows/publish-import-core.yml with workflow_dispatch only and an explicit release-version input.
   Reject invalid versions, SNAPSHOTs, mismatched POM versions and arbitrary unvalidated ref inputs.
2. Reject invocation from non-main workflow refs before a publishing job receives write permissions. Scope default
   permissions to contents:read. Keep publication serialized by artifact/version; do not cancel an active upload.
3. Resolve the exact tag derived from the validated version; require it to exist and its commit to be reachable
   from current origin/main. Build/deploy the same resolved commit, with checkout credentials not persisted.
   The tag, resolved SHA and coordinates must appear in the job summary. Do not automatically create or move tags.
4. Run the library tests and check its runtime dependency tree before deploying. The deployment command must target
   flexbuddy/import-core/pom.xml only. Keep tests/build outputs separate from any release credential files.
5. Preflight whether the version already exists. Refuse an existing version rather than replacing it. Fail closed
   on authentication, network or registry errors; do not treat those as a missing version. Do not delete/recreate
   versions to make a retry succeed; document partial-publication recovery as owner-operated.
6. Supply the publishing credential through environment-backed Maven settings. Never echo tokens, dump settings,
   enable Maven debug logging with secrets, or write credentials into uploaded artifacts or caches.
7. Confirm the deployed JAR/POM can be fetched and that their checksums match the built artifacts. An upload with a
   failed verification is an incomplete release; do not claim success. Record only safe metadata and hashes.

### Documentation and consumer handoff

- Document how the owner creates/pushes a release tag and dispatches the workflow, with publication kept a separate
  explicitly approved action. Include checks for existing version, failed release and reader authentication.
- Give credential-variable names and example placeholder configuration for local Maven and Android Gradle/CI.
  Do not read or modify existing settings.xml, local.properties, credential stores or repository secrets.
- Explain that Maven package access is repository-scoped and authenticated, even for this public server package.
  Do not promise a granular Manage Actions access setting for Maven without verified applicable documentation.
- Explain that Android must still verify Java 21/D8/API-26 compatibility and pass the six-field OCR benchmark;
  successful publication does not establish phone compatibility or OCR accuracy.

## Out of scope

- Actual package publication, tag/release creation or push, workflow dispatch, secret/account/permission setup,
  GitHub repository visibility changes or settings, and any package deletion/overwriting.
- Editing flexbuddy-android or Android signing/Play setup; OCR engine selection or new mobile dependencies.
- Parser, warnings, matching, financial rules, API contracts, migrations, catalogs, Render, production data/settings.
- Making server builds/deployment consume a remote published artifact, and broader NA-13 work.

## Design references

None; no user-facing UI changes.

## Acceptance criteria

- [ ] Owner-approved setup is recorded before implementation; specification committed before code changes.
- [ ] Library-only distribution configuration has no credentials and does not affect source-based server builds.
- [ ] Manual workflow validates branch/version/tag ancestry and deploys the exact validated source revision.
- [ ] Write permissions are limited to the publishing job, with no automatic PR/push publishing.
- [ ] Existing versions and ambiguous registry errors refuse publication; no tag/version overwrite or deletion.
- [ ] Tests/dependency inspection run before upload, and fetched artifacts are verified against local checksums.
- [ ] Docs cover authenticated readers, first release, failure/partial release and Android follow-ups accurately.
- [ ] Local verification passes; actual publication and remote consumer resolution are explicitly pending owner steps.

## Validation

- Run .\scripts\verify.ps1 -TaskId NA-12-publish; read its summary and report every skip, including private OCR
  samples and PostgreSQL status. No migration/UI/phone-width check is expected for a release workflow change.
- Validate workflow YAML and all scripts/guards using available tools without dispatching or uploading. Exercise
  version/tag/ref and registry-response handling with synthetic inputs/stubs, including existing version, 401,
  403, rate limiting, 404, server errors and network failure. Add no new dependency without permission.
- Check effective library POM, normal reactor packaging, runtime dependency tree and intended deployment file set.
- Use a temporary local file-backed Maven deployment target to validate JAR/POM packaging and checksum handling,
  without contacting GitHub Packages or replacing the real distribution destination in committed files.
- Review permissions and secret handling; git diff --check. Confirm no source/CI/default build/publishing side
  effects outside the named task. No real credential contents may be printed to prove configuration.
- First remote publication and authenticated local/Android resolution remain pending after setup PR review/merge
  and explicit owner approval. Report these as unrun, never as successful dry-run evidence.

## Security and regression risks

The critical boundary is which source revision can execute with packages:write. Validate ref/tag/version before
privilege, build only the approved source, and keep the upload command narrow. A classic reader token is owner-
managed and must never enter this repository or chat. Maven visibility follows the repository; the owner must
approve publishing the existing public code as a public artifact. Versions and tags are immutable release inputs.
No driver data, screenshot, secret, migration or deployed-server configuration is needed.
