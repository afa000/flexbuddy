# FlexBuddy agent workflow

How Claude Code, Codex, Antigravity and OpenCode with a local model work on FlexBuddy without losing context between
them. The rules every agent follows are in [AGENTS.md](../AGENTS.md); this page covers setup and the day-to-day loop.

Claude Code and Codex are interchangeable for the whole loop. Where this page says **Opus**, Codex uses **Sol**
(`gpt-6.1-sol`); where it says **Sonnet**, Codex uses **Terra** (`gpt-6.1-terra`). Both read `AGENTS.md` and the
skills in `.agents/skills/`, and both run the same guard. Claude Code starts a skill with `/implement-task FB-xxx`,
Codex with `$implement-task FB-xxx`.

The repository, not chat history, is the record: each task, its handoff notes and its checks live in files, so any tool
can pick up where another stopped.

## The loop

1. Claude Code with Opus writes the task in `.ai/tasks/FB-xxx.md`, including its risk and who implements it, and
   pushes it on a new `ai/FB-xxx` branch.
2. The implementer the task names works on that branch, one agent at a time. Usually that is Claude Code with Opus
   ([which one](#which-tool-for-which-task)).
3. [`scripts/verify.ps1`](../scripts/verify.ps1) runs the Java and JavaScript suites; CI adds the PostgreSQL migration
   test on the pull request, and you can run it locally too ([setup](#local-postgresql-18-and-mailpit)).
4. [`scripts/review-packet.ps1`](../scripts/review-packet.ps1) turns the task, the handoff report and the results into
   the pull request description.
5. A fresh Claude Code session with Opus (or Codex with Sol) reviews the pull request with `/review-task FB-xxx`. You
   merge it yourself; merging to `main` deploys to Render.

### The native app's repository

The native Kotlin app is built in its own repository, `flexbuddy-android` (not created yet), following
[native-app-plan.md](native-app-plan.md). The loop is the same there. FB numbers are one sequence across both
repositories, so check both for the next free one. A feature that needs the server becomes two tasks: the `/api/v1`
endpoint here first, written into [api-v1.md](api-v1.md), then the app screen there. Why it's split this way:
[decision 1](decisions/0001-native-app-in-its-own-repository.md). Record later decisions of that size the same way,
as the next numbered file in `docs/decisions/`.

### The guard: hooks in Claude Code and Codex

[`scripts/hooks/guard.mjs`](../scripts/hooks/guard.mjs) runs before an agent's shell and file tools:
[`.claude/settings.json`](../.claude/settings.json) wires it into Claude Code and
[`.codex/hooks.json`](../.codex/hooks.json) into Codex. It refuses:

- `git push` to `main` (by name, `HEAD:main`, `--all`, `--mirror`, or a plain `git push` while on `main`) and
  `gh pr merge`. Pushing a feature or `ai/` branch still works, after you approve it in chat.
- an edit to a migration that git already tracks, from Claude Code's file tools or a Codex patch. A new
  `V<n>__*.sql` is fine.
- in Claude Code, reading a whole message catalog (about 25K tokens each): it says to grep for the keys instead.

Codex also has [`.codex/rules/flexbuddy.rules`](../.codex/rules/flexbuddy.rules), which forbids the same pushes and
merges by command prefix, as a second layer. OpenCode and Antigravity have their own deny rules in `opencode.json` and
`scripts/antigravity-cli-settings.json`.

So pushing `main` and merging pull requests are always your steps. The hooks back up the rules in AGENTS.md; they
don't replace them: a shell command can still change a file the file-tool check doesn't see. The guard's tests:
`node --test "scripts/hooks/*.test.mjs"` (not part of CI). A new Claude Code session picks up changes to the settings
file; Codex asks you to approve new or changed hooks in `/hooks` before they run, and needs a restart for new rules.

## One-time setup

Windows PowerShell 5.1 is enough; the scripts don't need PowerShell 7.

### GitHub CLI

Opening pull requests uses `gh`:

```powershell
winget install GitHub.cli
gh auth login --hostname github.com --git-protocol https --web
```

Open a new terminal afterwards so `gh` is on the PATH.

### Local PostgreSQL 18 and Mailpit

Both run in Docker Desktop (free for personal and education use). Start Docker Desktop first.

**PostgreSQL 18, for the migration test.** `PostgresMigrationTest` is skipped unless it has a database. It works in a
schema with a random name and drops it afterwards, but give it its own container anyway, on port 5433 so it never
meets a database the app uses:

```powershell
docker run -d --name flexbuddy-pg18 -e POSTGRES_PASSWORD=postgres -p 5433:5432 postgres:18
$env:FLEXBUDDY_TEST_POSTGRES_URL = "jdbc:postgresql://localhost:5433/postgres"
.\scripts\verify.ps1 -TaskId FB-xxx
```

The test's user and password default to `postgres`, the container's throwaway defaults, so only the URL is needed.
`summary.md` says whether the test ran. Later, `docker start flexbuddy-pg18` brings the container back.

**Mailpit, to see the app's emails.** A local mail server with a web inbox at http://localhost:8025:

```powershell
docker run -d --name mailpit -p 8025:8025 -p 1025:1025 axllent/mailpit
$env:FLEXBUDDY_MAIL_HOST = "localhost"; $env:FLEXBUDDY_MAIL_PORT = "1025"
cd flexbuddy; .\mvnw.cmd spring-boot:run
```

Sign-up, two-step and reset emails then arrive in the inbox as real messages, in either language. Not yet tried with
the app: its mail settings are written for Gmail (sign-in and STARTTLS on). If it can't connect, use the
`flexbuddy-links` launch configuration instead, which logs codes and links to the console.

### Ollama and the local models

Ollama 0.40 came with the AMD AI Bundle on this PC; elsewhere, install it from https://ollama.com/download/windows.

```powershell
ollama pull gemma4:12b      # the local model to use
ollama pull qwen3.5:9b      # optional fallback
```

**Use a 32K copy of the model with OpenCode.** Ollama loads models with a 4K context, which cuts off OpenCode's tool
list: at 4K the model cannot read a single file. The `OLLAMA_CONTEXT_LENGTH` setting doesn't help on this PC; the AMD
AI Bundle build kept loading at 4K after a restart. So the size is stored in a copy of the model instead, from
[`scripts/gemma-32k.Modelfile`](../scripts/gemma-32k.Modelfile) (and
[`scripts/qwen-32k.Modelfile`](../scripts/qwen-32k.Modelfile) for the fallback):

```powershell
ollama create gemma4:12b-32k -f scripts/gemma-32k.Modelfile
ollama create qwen3.5:9b-32k -f scripts/qwen-32k.Modelfile
```

The copy shares the original's files, so it takes no extra disk. While it runs, `ollama ps` should show `100% GPU` and
`32768` in the CONTEXT column; check this before trusting a run, because a 4K run fails quietly.

### OpenCode

```powershell
npm install -g opencode-ai
ollama launch opencode --model gemma4:12b-32k
```

OpenCode 2 reads `AGENTS.md`, the skills in `.agents/skills/` (and `.claude/skills/`, whose wrappers point to the same
steps), [`.opencode/small-context.md`](../.opencode/small-context.md) through `instructions`, and the rules in
[`opencode.json`](../opencode.json), written in the version 2 format: it asks before every edit and shell command,
lets read-only git commands through, and blocks `git push`, `git reset --hard`, `git clean` and `gh pr merge`. The
`headless` agent used by `run-agent.ps1` may also not open the message catalogs or touch a migration. Long sessions
are summarised automatically (`compaction`), and build output isn't watched. Don't add paid model providers to it.
`opencode debug agents` shows the rules each agent really gets; check it after changing the file.

Optional, your call: OpenCode's free hosted models (such as GLM-5.3-Flash Free) are far stronger than Gemma, but need an
OpenCode account, send the code to that provider, and are free only "for a limited time". They're not set up here.

### Codex

The Codex app (or the `codex` CLI, 0.159.0 or later for Terra) needs no project setup beyond what's committed:

- It reads `AGENTS.md` and the skills in `.agents/skills/`; start one with `$implement-task FB-xxx`, `$prepare-review`
  or `$review-task`.
- Your own `~/.codex/config.toml` picks the model: `model = "gpt-6.1-sol"` (the Opus role). For a Terra session, run
  `codex -m gpt-6.1-terra` or pick it with `/model`. There's deliberately no project `.codex/config.toml`, so the
  repository never overrides your choice.
- The project must be trusted in `~/.codex/config.toml` (it is), or Codex skips `.codex/hooks.json` and the rules.
  The first time, open `/hooks` and approve the two guard hooks. Start Codex from the repository root: the hook commands
  use paths relative to it.
- `git push` of an `ai/` branch and `gh pr create` need network access outside Codex's sandbox; approve them when it
  asks, as you would in Claude Code.

### Antigravity Pro

Sign in with the Google account that has the Pro plan and set AI-credit overages to Never. Antigravity reads `AGENTS.md`
from the workspace root ([rules docs](https://antigravity.google/docs/rules/)) and loads skills from
`.agents/skills/<name>/SKILL.md` ([skills docs](https://antigravity.google/docs/skills/)), so it needs no extra setup.
Run the smoke test under [Proving a tool works](#proving-a-tool-works) before giving it a real task.

### Claude Code

`CLAUDE.md` imports `AGENTS.md`. Claude Code reviews from the local checkout with `gh`, and it is the tool for Android
and emulator work because it already follows the emulator rules.

## Proving a tool works

Before a tool gets a real task, and again after updating it, give it this read-only prompt. It costs one short run.

```text
Read AGENTS.md and .ai/tasks/TEMPLATE.md. Without editing any file, answer: (1) which command runs both test suites,
(2) where a new migration goes and what its next number is, (3) which three skills exist in .agents/skills and what
each does, (4) whether you may push to main. Then run git status and report its output. Do not edit files.
```

It passes when every answer matches AGENTS.md, it ran `git status` itself, and `git status` shows nothing new
afterwards. From Claude Code: `.\scripts\run-agent.ps1 -Agent local -TaskId <any existing id> -Prompt "<the prompt>"`
(or `-Agent antigravity`); the log's "Refused tools" and "Handoff" sections show what it couldn't do. For the local
model, also check `ollama ps` shows `32768` context during the run.

## Which tool for which task

**What actually happens.** Since FB-011, every task has been handed to Claude Code with Opus, whatever its risk:
specs, code, Android builds and reviews. That is the default now. The other tools stay set up as optional helpers for
when Claude's usage limits are tight; nothing depends on them.

**Every task, whoever implements it:**

- **Plan:** Claude Code with **Opus**. The spec names the files to read, so the implementer doesn't explore.
- **Review:** a fresh Claude Code session with **Opus** and `/review-task`, before merging.

**Who implements, by the risk the spec gives it:**

| Risk | What it covers | Default | When usage is tight |
|---|---|---|---|
| High | Sign-in and security, payments and subscriptions, migrations, backup/restore, earnings and tax figures, deleting data, the app's tokens and offline outbox | Claude Code with **Opus** | Claude Code with **Sonnet**; never another tool |
| Medium | A feature with a clear spec across a few files, an API endpoint on an existing service, UI and responsive work, Kotlin/Compose screens | Claude Code with **Opus** | Claude Code with **Sonnet**, or **Antigravity** (its Pro model) |
| Low | One-function fixes, tests for a pure JavaScript function, docs, CSS, release notes drafts | Claude Code with **Opus** | **Sonnet**, or **OpenCode + Gemma** (`run-agent.ps1 -Agent local`) |

- **Name the implementer in the spec**, and when a task goes to someone else, say so in its `Implementer:` line with the
  date, as FB-011 to FB-024 do.
- **Escalate after two failed attempts:** local model → Antigravity → Claude. Record the attempts in the handoff
  report so the next tool doesn't repeat them.
- **Android wrapper, emulator and Play Console work** stay with Claude Code; it follows the emulator and signing rules.
- **The native Android app:** the same table, in its own repository. Claude handles anything touching sign-in, tokens,
  the outbox, earnings or background location, and reviews all of it.
- **Inside Claude Code**, pick the model in the model picker (in Codex, `/model`):
  - **Opus** (Codex: **Sol**) for specs, design decisions, reviews, high-risk code and debugging;
  - **Sonnet** (Codex: **Terra**) to implement a well-specified task more cheaply;
  - **Haiku**, or the Explore subagent, for broad searches and reading logs.
- **Reasoning effort:** high for specs, reviews and high-risk code; medium for routine implementation; low for docs and
  one-line fixes. Claude Code: `/effort`. Codex: `model_reasoning_effort`, or the effort choice in `/model`.
- **Claude Code or Codex:** either can run any task. Use whichever has usage left; keep one tool per task, and review
  with a fresh session of either.

Never let two agents edit the same working tree at once. Take turns, or give the second agent its own Git worktree.

## Saving tokens

Each tool has a limit: Claude's usage limits, about 30K tokens of Antigravity quota per run, and 32K of context for the
local model. These habits keep tasks inside them:

1. **The spec does the exploring.** It lists the files to read under "Files to read" and states the facts the
   implementer would otherwise go and find (class names, current behaviour, the next migration number).
2. **One task per session.** Start a new Claude Code session, or `/clear`, for each task, and another for its review. A
   long session re-reads its whole history on every message.
3. **Sonnet to build when usage is tight.** Opus plans and reviews; a well-specified task builds fine on Sonnet.
   Switch with the model picker.
4. **Search, don't read.** Grep the message catalogs (`messages.properties` is 94 KB, the Spanish one 103 KB) and
   other large files for the keys you need; Claude Code's guard refuses a whole-catalog read. Use an Explore subagent
   for "where is X" questions.
5. **Read summaries.** `verify.ps1` writes `.ai/artifacts/<task-id>/summary.md`; open the full Maven log only when a
   check failed, and then only around the failure.
6. **Review the diff, not the chat.** The reviewer reads the review packet (which holds the task, the handoff and the
   results) and `git diff`, nothing else.
7. **Keep AGENTS.md short.** Every tool loads it on every turn. Rules go there; explanations go on this page.
8. **Match the effort to the work.** See "Reasoning effort" above; routine work doesn't need high effort.

## A task from start to merge

**Plan.** In a new Claude Code session with Opus, give this prompt:

```text
You are the software architect for FlexBuddy.
Read AGENTS.md and the code the feature touches.
Feature request: [DESCRIBE FEATURE]

Pick the next free FB-number: check .ai/tasks/ on main and the ai/FB-* branches on origin, in this
repository and in flexbuddy-android once it exists.
Create branch ai/<task-id> from main, write .ai/tasks/<task-id>.md from .ai/tasks/TEMPLATE.md,
commit it and push the branch. Do not implement the feature.
Set Risk and Implementer from the table in docs/agent-workflow.md, and list every file the
implementer needs under "Files to read". Keep the task small enough for one agent session.
```

Pushing an `ai/` branch deploys nothing; Render only deploys `main`. The old `plan/<slug>` branches stay as history.

**Implement.** Use the implementer the task names. For a Claude task, start a new session (Opus by default, Sonnet to
save usage), switch to the branch and run the skill:

```powershell
git fetch origin
git switch ai/FB-001
```

> /implement-task FB-001

From Claude Code, `run-agent.ps1` starts Antigravity or the local model instead
([below](#running-everything-from-one-claude-code-session)). In another tool, ask it to use the `implement-task`
skill, or, if it doesn't load skills, say "Follow .agents/skills/implement-task/SKILL.md for FB-001."

**Verify and open the pull request.** Commit first: the checks record the commit they tested, and the packet warns when
that is no longer the latest one. Any untracked file counts as an uncommitted change: `verify.ps1` then reports "with
uncommitted changes" and `review-packet.ps1` refuses to run. Commit it, delete it, or add it to `.gitignore` (or, for a
file only on your machine, `.git/info/exclude`).

```powershell
git add <changed files>
git commit -m "feat(scope): ..."
git fetch origin main
.\scripts\verify.ps1 -TaskId FB-001
.\scripts\review-packet.ps1 -TaskId FB-001
git push -u origin ai/FB-001
gh pr create --base main --title "feat(scope): ..." --body-file .ai/reviews/FB-001.md
```

Read the packet for secrets before it becomes the pull request description. `/prepare-review FB-001` does these
steps and stops before pushing.

**Review and merge.** Wait for the Java and JS checks. Then, in a new Claude Code session with Opus (not the one that
wrote the code):

> /review-task FB-001

The review reads only the task, the packet and the diff, and reports findings without changing code. Fixes go back to
the implementer, or to Claude Code for a high-risk task, and the review runs again on the new commit. You merge the
pull request yourself, in GitHub; Claude Code's hook refuses `gh pr merge`. Merging to `main` deploys to Render, and
GitHub refuses the merge until both checks pass.

## Running everything from one Claude Code session

Claude Code can hand a task to Antigravity or the local model without either app being opened, using
[`scripts/run-agent.ps1`](../scripts/run-agent.ps1). Run it on the task's branch, in the working tree the agent should
edit (a separate Git worktree if you are working in the main one at the same time):

```powershell
.\scripts\run-agent.ps1 -Agent local -TaskId FB-003
.\scripts\run-agent.ps1 -Agent antigravity -TaskId FB-003 -Prompt "Only do step 1 of the task's handoff plan."
```

Without `-Prompt`, the agent implements the task with the `implement-task` skill and writes its handoff report. The
run is timed and saved to `.ai/artifacts/<task-id>/agent-<agent>-<time>.log`, with the agent's answer, its tool log,
the commands it ran and the files it changed. Then Claude Code checks the work and carries on with `verify.ps1`, the
packet and the pull request.

Nobody is there to approve anything during these runs, so each agent gets fixed rules instead:

- **Local model:** OpenCode runs as the `headless` agent defined in [`opencode.json`](../opencode.json). It may edit
  files and run only read-only git commands and `node --test`; everything else is refused. Your own interactive
  OpenCode sessions keep asking as before.
- **Antigravity:** the CLI (`agy`, installed with the app in `~/.gemini/bin`) runs with
  [`scripts/antigravity-cli-settings.json`](../scripts/antigravity-cli-settings.json) for that run only (`--settings`;
  your own `~/.gemini/antigravity-cli/settings.json` and interactive `agy` sessions are left alone). Its terminal
  commands run in the CLI's sandbox, which can read the repository but can't write to it, reach the network or write
  `.git`, so any read-only command runs without asking: searches with `<` in them, pipes, `powershell -Command ...`.
  The agent makes file changes with its own editing tools. Tests write build output, so they leave the sandbox, and
  only the allow-listed ones may: `node --test`, the Maven `verify`/`test` runs and `verify.ps1`, none of them chained
  with `;`, `&`, `|` or redirects. Commits, pushes and branch changes are refused everywhere.

Things to know:

- **A refused command used to end an Antigravity run.** All four October runs (FB-008, FB-009) stopped that way after
  about 40 searches and up to 7 minutes, with no answer. Antigravity 1.4.12's docs say a refused tool is now skipped
  and the run carries on; the smoke test under [Proving a tool works](#proving-a-tool-works) confirms it on this PC.
  The log now lists every refused tool under "Refused tools" and says whether a handoff report was written. Only a
  command that must leave the sandbox needs a rule; add it to `scripts/antigravity-cli-settings.json` if it is safe.
  Quoted `git grep` patterns with `<` or `|` in them are allowed.
- **Tell it what the spec says.** `run-agent.ps1` now tells every agent that the task file lists the files to read
  and the facts it needs, so it searches only for what's missing.
- **Antigravity rules must be regexes.** On Windows the CLI checks each command wrapped in quotes, so plain rules such
  as `command(git status)` never match. The sandbox won't start with a relative path in a `write_file`/`read_file`
  rule, so the script writes a copy with absolute paths next to the run's log.
- **The sandbox can't write to the working tree on this PC** (it is given only traverse rights on the folder, which
  is in OneDrive inside the user profile), so `run-agent.ps1` tells the agent to edit with its file tools and run `verify.ps1` or `node --test` outside the sandbox from the repository root.
  It needs Windows 11 24H2 or later.
- **Two runs can go at once**, each in its own worktree: the script finds each run's transcript by its conversation id.
- **Every Antigravity run uses Pro quota**, about 30K tokens even for a one-word answer.
- **Antigravity's browser checks don't run this way.** Claude Code checks UI changes itself, with its own browser on
  `localhost`.
- **Still yours to do:** sign-ins, Ollama updates and restarts (this session can't close Ollama), and GitHub settings.

## Handing a task to another agent

Ask the current agent to stop and record where it is:

```text
Stop implementation. Update .ai/implementations/FB-001.md with completed work, remaining work, changed files,
test results and unresolved issues. Do not begin new implementation work.
```

Then start the next one with:

```text
Read AGENTS.md, .ai/tasks/FB-001.md and .ai/implementations/FB-001.md. Inspect the existing git diff.
Continue only unfinished requirements and preserve completed work.
```

The handoff report is ignored by Git, which suits agents taking turns in one working tree. If the next agent works
elsewhere, commit the work in progress on the branch and paste the report into its first prompt.

## Design references

Put screenshots and notes for a task in `.ai/design/FB-001/` (for example `desktop-reference.png`,
`mobile-reference.png`, `design-notes.md`). Use a test account and made-up figures: no real earnings or calendar links.

## Android tasks

The app in `android/` is a Trusted Web Activity around the live site, so most changes are web changes and need no new
Android build. A wrapper change (icons, share target, manifest settings) goes like this:

1. Edit `android/twa-manifest.json` and raise `appVersionCode` and `appVersionName`.
2. From `android/`, apply and build unsigned:
   `npx --yes @bubblewrap/cli@1.25.0 update`, then `npx --yes @bubblewrap/cli@1.25.0 build --skipSigning`.
   `update` regenerates the icons from the site; restore the hand-drawn ones if it overwrote them.
3. You sign the APK and AAB yourself in an interactive terminal, following [android/README.md](../android/README.md).
   No agent handles the keystore or its password.
4. Check the build on the emulator. The Play-installed app there is signed in to production as the Play Reviewer
   account: launch, offline start and cached screens only, never add, edit or delete anything. Test anything that
   writes data against `localhost:8080` with a local test account.
5. Upload to Play yourself, then record the upload in
   [android/CHANGELOG-android.md](../android/CHANGELOG-android.md): what changed, how it was built and tested, and the
   track and date.

`/.well-known/assetlinks.json` must keep both certificate fingerprints, and the package name and upload key never
change. Store text and screenshots live in `android/store-listing.md` and `android/store-screenshots/`.

## What to expect from the local model

Trials on the RX 7800 XT, October 21, 2026. First Qwen 3.5 9B, all fully on the GPU:

| Run | Time | Result |
|---|---|---|
| Read-only prompt, 4K context (Ollama default) | 81 s | Could not see its tools; guessed paths and gave a wrong build command |
| Read-only prompt, 32K | about 3 min | Named every controller and service correctly, but invented six Thymeleaf templates and a `shifts.js` |
| FB-001 (tests for `toast.js`), attempt 1 | 165 s | Read the whole 84 KB message catalog, ran out of room and stopped; no files written |
| FB-001, attempt 2 | 225 s | Wrote a test file that never reached the script (9 of 13 tests failed), then the run broke off; no handoff report |
| FB-002, tests after an Antigravity handoff, at 4K by mistake | 70 s | Printed a command as text instead of using its tools; nothing written |
| FB-002, same handoff at 32K | 291 s | Read the handoff report and diff, added 3 tests and updated the report; 2 tests were useful, 1 could never fail but was reported as covering its criterion |

Claude Code finished FB-001 after the two failed attempts and fixed FB-002's third test.

Then the same two jobs with Gemma 4 12B at 32K, also fully on the GPU:

| Run | Time | Result |
|---|---|---|
| Read-only prompt | 101 s | Every path and command right, nothing invented; less detail than Qwen (no file names) |
| FB-002 tests, from the same handoff | 111 s | 2 new tests plus an assertion on the existing click test; all 3 fail without the fix. Its handoff report kept stale "no tests were written" lines |

Gemma was faster and more accurate on both, so it is the local model to use, with Qwen as a fallback. One caveat:
Gemma's FB-002 run started with the fix already committed and a shorter handoff report, a slightly easier start than
Qwen had.

Treat the local model as a helper for narrow tasks with one example to copy: docs, CSS, small fixes, a test or two.
Stop it after two failed attempts, check every test it writes fails without the change it guards, and don't take its
handoff report on trust. If it isn't saving time, drop it; nothing else depends on it.

The first handoff test (FB-002) worked: Antigravity made the change, checked it in its own browser on localhost and
wrote a handoff report clear enough for a local model to pick up from without any chat history.

**Writing tasks for the local model.** Its 32K context is small next to this codebase:

- Name the exact lines it needs from big files instead of the file. `messages.properties` alone is 84 KB, about 25K
  tokens; give it the keys and their English text.
- Point to one example to copy (an existing test or class), not several.
- Ask it to work in steps: write a little, run the tests, then continue.
- Don't count on a handoff report: check `git status` and the diff yourself after every run.

## Safety and cost

- No new subscriptions: local inference is free, and Claude, ChatGPT and Antigravity Pro are existing plans. Don't add
  paid API keys to OpenCode or scripts without checking billing.
- Spend Claude on planning, high-risk changes and review, not routine build failures; see
  [Saving tokens](#saving-tokens).
- Keep Ollama bound to localhost.
- Treat migrations, backup/restore and earnings changes as high-risk even when tests pass.
- Logs, handoff reports and review packets are ignored by Git; read them before sharing.
