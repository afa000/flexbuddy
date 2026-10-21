---
name: prepare-review
description: Checks a finished FlexBuddy task and builds the review packet used as the PR description.
---

# Prepare a FlexBuddy task for review

Input: a task id such as `FB-001`.

1. Read `AGENTS.md`, `.ai/tasks/<task-id>.md` and `.ai/implementations/<task-id>.md`.
   If the implementation report is missing or lists open acceptance criteria, say so and stop.
2. Check that you are on `ai/<task-id>` and that `git status` is clean. If there are
   uncommitted changes, ask before committing them.
3. Run `git fetch origin main`, then `.\scripts\verify.ps1 -TaskId <task-id>` from the
   repository root.
4. If a check fails, report the failure. Do not continue to the packet.
5. Run `.\scripts\review-packet.ps1 -TaskId <task-id>`.
6. Read `.ai/reviews/<task-id>.md` and check it for secrets, private calendar links and real
   driver data.
7. Report where the packet is and the command to open the pull request with it:
   `gh pr create --base main --title "<commit title>" --body-file .ai/reviews/<task-id>.md`

Never push, open a pull request, merge or deploy without the owner's approval.
