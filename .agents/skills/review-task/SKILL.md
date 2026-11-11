---
name: review-task
description: Reviews a finished FlexBuddy task's pull request against its specification, in a fresh session.
---

# Review a FlexBuddy task

Input: a task id such as `FB-001`, with an open pull request from `ai/<task-id>`.

Run this in a new session, not the one that wrote the code, so the review sees only what is in the
repository.

1. Read the pull request description, which is the review packet: `gh pr view --json number,title,body,headRefOid`
   for the branch. It already contains the task specification, the handoff report and the check results, so don't
   read `.ai/tasks/<task-id>.md` separately unless the packet is missing or cut short. `AGENTS.md` is already loaded.
2. Read the diff: `git fetch origin`, then `git diff origin/main...origin/ai/<task-id>`. Open a
   changed file in full only when the diff alone doesn't show enough, and other files only to check
   a specific concern.
3. Check the CI results: `gh pr checks <number>`. A review doesn't replace them.
4. For each acceptance criterion, say whether the diff meets it and which test proves it.
5. Look for, in this order:
   - data from one account reachable by another, and weakened sign-in, rate-limit or CSRF checks;
   - migrations that edit an old file, break existing data or aren't covered by the migration test;
   - backup/restore, earnings, tax and scheduled-shift rules changed without the task asking;
   - user-facing text missing from either catalog, or `toISOString()` used for a date;
   - tests that would still pass without the change;
   - changes the task didn't ask for, and needless complexity.
6. Report findings ranked by severity, each with the file and line, what goes wrong, and the smallest
   fix. Say plainly when you found nothing, and list what you didn't check.

Do not edit code, push, approve, merge or comment on the pull request unless the owner asks. Fixes go
back to the implementer the task names, or to Claude Code for high-risk tasks.
