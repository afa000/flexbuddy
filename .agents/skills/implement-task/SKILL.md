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
3. Read the files under "Files to read" in the task and their existing tests. Search (grep)
   before opening any other file, and never read a message catalog whole. If the task names an
   implementer and it isn't you, and the owner hasn't handed it to you, stop and say so.
4. Implement only what the task asks for, following the existing patterns.
5. Add or update tests for the change.
6. Run the focused checks in the task spec and any checks required by `AGENTS.md` for the files you touched.
   Run the full suites here if the spec calls for them or focused checks cannot cover the change. Otherwise,
   `prepare-review` runs both suites on the committed result. Fix or report failures and record checks still due.
7. Read your own diff and remove anything unrelated to the task.
8. Write `.ai/implementations/<task-id>.md` with:
   - a summary and the changed files
   - each check you ran and its actual result, and each check you did not run
   - acceptance criteria still open, known limitations
   - security, database and earnings decisions worth a reviewer's attention

Do not commit unless asked. Never push, merge or deploy.
