# Task: <task-id> - <title>

For a small, clearly scoped low-risk task, keep the header, objective, files to read, observable acceptance criteria
and validation. Add the other sections when they affect implementation or review.

Status: Ready for implementation
Risk: high | medium | low (see "Which tool for which task" in docs/agent-workflow.md)
Implementer: Claude Code (Opus or Sonnet) | Codex (Astra, Sol or Terra) | Antigravity (Gemini 4 Neon, Argon or Hydrogen) | Antigravity with Orchestrator (Neon) | OpenCode + local model
Review: fresh Claude Code (Opus), Codex (Astra or Sol at High or above) or Antigravity (standalone Gemini 4 Neon High or Gemini 4 Argon High) session, review-task skill

## Objective

What problem does this solve for drivers?

## Files to read

Every file the implementer needs, with the class, method or lines that matter. The implementer reads these and their
tests, and searches before opening anything else. For the message catalogs, list the keys, not the files.

## Current behavior

How does the app behave today? Name the screens, endpoints and files involved.

## Required behavior

What must change?

## Out of scope

What must not change?

## Design references

Paths under `.ai/design/<task-id>/`, if any. Use test accounts and made-up data in screenshots.

## Acceptance criteria

- [ ] Requirement 1
- [ ] Requirement 2

## Validation

Which Java, JavaScript, PostgreSQL migration and browser checks prove it works. Note anything
that can only be checked on the emulator or in production, and how to check it without writing
data there.

## Security and regression risks

Sign-in, per-user data, earnings figures, migrations, backup/restore, both language catalogs,
Android.
