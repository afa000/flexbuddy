---
name: implement-task
description: Implements a FlexBuddy task from .ai/tasks/, runs the checks, writes the handoff.
---

# Implement a FlexBuddy task

Input: a task id such as `FB-001`, with its specification in `.ai/tasks/<task-id>.md`.

1. Read `AGENTS.md` and the task specification.
2. Check the branch and the working tree (`git status`, `git diff`). Work on `ai/<task-id>`;
   if you are on `main`, stop and ask. If `.ai/implementations/<task-id>.md` exists, another
   agent started this task: read it and continue only the unfinished work.
3. Read the code the task touches and its existing tests.
4. Implement only what the task asks for, following the existing patterns.
5. Add or update tests for the change.
6. Run the checks: `.\scripts\verify.ps1 -TaskId <task-id>` from the repository root.
   If something fails, fix it or report it. Never hide a failure.
7. Read your own diff and remove anything unrelated to the task.
8. Write `.ai/implementations/<task-id>.md` with:
   - a summary and the changed files
   - each check you ran and its actual result, and each check you did not run
   - acceptance criteria still open, known limitations
   - security, database and earnings decisions worth a reviewer's attention

Do not commit unless asked. Never push, merge or deploy.
