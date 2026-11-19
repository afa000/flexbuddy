---
name: review-task
description: Reviews a finished FlexBuddy task's pull request against its specification, in a fresh session.
---

# Review a FlexBuddy task

Input: a task id such as `FB-001`, with an open pull request from `ai/<task-id>`.

Run this in a fresh top-model session so the review sees only what is in the repository. In Antigravity,
choose standalone Gemini 4 Neon (High) by default, or Gemini 4 Argon (High) for a second opinion or fallback.

1. Record the PR number, base SHA and head SHA from
   `gh pr view --json number,title,body,baseRefName,baseRefOid,headRefOid` for the branch. The description is the review
   packet. If it is missing, cut short or tied to a different head, read the task spec from the recorded head with
   `git show <head-sha>:.ai/tasks/<task-id>.md` after fetching. Treat older verification as historical.
2. Fetch the objects with `git fetch origin refs/heads/<base-ref-name> refs/pull/<number>/head` and compare the
   recorded revisions with `git diff <base-sha>...<head-sha>`. If a recorded commit is missing, fetch it by SHA.
   If it still cannot be read, report that revision as unavailable. Read files at the recorded head with
   `git show <head-sha>:<path>` when the diff needs more context.
3. Check the latest run of this PR's CI workflow on the recorded head with
   `gh api --method GET 'repos/{owner}/{repo}/actions/workflows/ci.yml/runs' -f event=pull_request -f head_sha=<head-sha>`.
   Match both `head_sha` and the PR number in `pull_requests`. Record the run URL and conclusion, or report CI as
   pending or unverified if a matching completed run is unavailable. Only a successful matching run counts as passed.
4. For each acceptance criterion, say whether the diff meets it and which test proves it.
5. Look for, in this order:
   - data from one account reachable by another, and weakened sign-in, rate-limit or CSRF checks;
   - migrations that edit an old file, break existing data or aren't covered by the migration test;
   - backup/restore, earnings, tax and scheduled-shift rules changed without the task asking;
   - user-facing text missing from either catalog, or `toISOString()` used for a date;
   - tests that would still pass without the change;
   - changes the task didn't ask for, and needless complexity.
6. Recheck `gh pr view <number> --json headRefOid`. Include the reviewed short head SHA and its CI status in
   the report. If the head changed, say which revision the findings apply to and that the new head needs review.
   Rank findings by severity and include the file, line, impact and smallest fix. List what you didn't check.

Do not edit code, push, approve, merge or comment on the pull request unless the owner asks. Fixes go
back to the implementer the task names. For high-risk fixes, follow the standalone top-model rule in
`AGENTS.md`.
