# FlexBuddy agent workflow

How Claude Code, Antigravity and OpenCode with a local model work on FlexBuddy without losing context between them.
The rules every agent follows are in [AGENTS.md](../AGENTS.md); this page covers setup and the day-to-day loop.

The repository, not chat history, is the record: each task, its handoff notes and its checks live in files, so any tool
can pick up where another stopped.

## The loop

1. A planner (Claude or ChatGPT) writes the task in `.ai/tasks/FB-xxx.md` and pushes it on a new `ai/FB-xxx` branch.
2. One agent at a time implements it on that branch: Claude Code, Antigravity, or OpenCode with Qwen.
3. [`scripts/verify.ps1`](../scripts/verify.ps1) runs the Java and JavaScript suites; CI adds the PostgreSQL migration
   test on the pull request.
4. [`scripts/review-packet.ps1`](../scripts/review-packet.ps1) turns the task, the handoff report and the results into
   the pull request description.
5. Claude Code reviews the branch (or ChatGPT reviews the PR on GitHub). Merging to `main` deploys to Render.

## One-time setup

Windows PowerShell 5.1 is enough; the scripts don't need PowerShell 7.

### GitHub CLI

Opening pull requests uses `gh`:

```powershell
winget install GitHub.cli
gh auth login --hostname github.com --git-protocol https --web
```

Open a new terminal afterwards so `gh` is on the PATH.

### Ollama and the local models

Ollama 0.40 came with the AMD AI Bundle on this PC; elsewhere, install it from https://ollama.com/download/windows.

```powershell
ollama pull qwen3.5:9b
ollama pull gemma4:12b      # optional comparison model
```

**Raise the context to 32K before using OpenCode.** Ollama otherwise loads models with 4K, which cuts off OpenCode's
tool list: in the first trial the model could not read a single file. Set it once, then quit and reopen Ollama from
the tray:

```powershell
setx OLLAMA_CONTEXT_LENGTH 32768
```

While a model runs, `ollama ps` should show `100% GPU` and `32768` in the CONTEXT column. Until Ollama restarts, the
copy `qwen3.5:9b-32k` (made with `PARAMETER num_ctx 32768`) does the same job; remove it afterwards with
`ollama rm qwen3.5:9b-32k`.

### OpenCode

```powershell
npm install -g opencode-ai
ollama launch opencode --model qwen3.5:9b
```

OpenCode reads `AGENTS.md`, the skills in `.agents/skills/` and the permissions in [`opencode.json`](../opencode.json):
it asks before every edit and shell command, lets read-only git commands through, and blocks `git push`,
`git reset --hard` and `git clean`. Don't add paid model providers to it.

First test, before any real task:

```text
Inspect the FlexBuddy repository. Explain its architecture and identify where the Java controllers, services,
repositories, Thymeleaf templates, JavaScript and tests live. Explain how to run validation. Do not edit files.
```

### Antigravity Pro

Sign in with the Google account that has the Pro plan and set AI-credit overages to Never. Antigravity reads `AGENTS.md`
from the workspace root ([rules docs](https://antigravity.google/docs/rules/)) and loads skills from
`.agents/skills/<name>/SKILL.md` ([skills docs](https://antigravity.google/docs/skills/)), so it needs no extra setup.

### Claude Code

`CLAUDE.md` imports `AGENTS.md`. Claude Code reviews from the local checkout with `gh`, and it is the tool for Android
and emulator work because it already follows the emulator rules.

## Which tool for which task

| Work | Tool |
|---|---|
| Docs, small CSS, single-file tests | OpenCode + Qwen (trial) |
| Small Java or JavaScript bug | OpenCode + Qwen first; Antigravity after two failed attempts |
| New UI, responsive work, browser checks | Antigravity Pro |
| Spring Security, migrations, backup/restore, earnings | Antigravity Pro or Claude Code, plus CI and a separate review |
| Android wrapper, emulator checks, Play listing | Claude Code |
| Planning a feature, large design choices | Claude or ChatGPT |
| Final review | Claude Code (local diff) or ChatGPT (GitHub PR) |

Never let two agents edit the same working tree at once. Take turns, or give the second agent its own Git worktree.

## A task from start to merge

**Plan.** Give the planner this prompt:

```text
You are the software architect for FlexBuddy.
Read AGENTS.md and the code the feature touches.
Feature request: [DESCRIBE FEATURE]

Pick the next free FB-number: check .ai/tasks/ on main and the ai/FB-* branches on origin.
Create branch ai/<task-id> from main, write .ai/tasks/<task-id>.md from .ai/tasks/TEMPLATE.md,
commit it and push the branch. Do not implement the feature.
Keep the task small enough for one agent session.
```

Pushing an `ai/` branch deploys nothing; Render only deploys `main`. The old `plan/<slug>` branches stay as history.

**Implement.** Fetch the branch and ask the agent to use the `implement-task` skill:

```powershell
git fetch origin
git switch ai/FB-001
```

> Use the implement-task skill for FB-001.

If the tool doesn't load skills, say "Follow .agents/skills/implement-task/SKILL.md for FB-001."

**Verify and open the pull request.** Commit first: the checks record the commit they tested, and the packet warns when
that is no longer the latest one.

```powershell
git add <changed files>
git commit -m "feat(scope): ..."
git fetch origin main
.\scripts\verify.ps1 -TaskId FB-001
.\scripts\review-packet.ps1 -TaskId FB-001
git push -u origin ai/FB-001
gh pr create --base main --title "feat(scope): ..." --body-file .ai/reviews/FB-001.md
```

Read the packet for secrets before it becomes the pull request description. The `prepare-review` skill does these steps
and stops before pushing.

**Review and merge.** Wait for the Java and JS checks, then ask Claude Code to review the branch (for example with
`/code-review`), or give ChatGPT the PR link. Merging to `main` deploys to Render.

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

Trials with Qwen 3.5 9B on the RX 7800 XT, October 21, 2026, all fully on the GPU:

| Run | Time | Result |
|---|---|---|
| Read-only prompt, 4K context (Ollama default) | 81 s | Could not see its tools; guessed paths and gave a wrong build command |
| Read-only prompt, 32K | about 3 min | Named every controller and service correctly, but invented six Thymeleaf templates and a `shifts.js` |
| FB-001 (tests for `toast.js`), attempt 1 | 165 s | Read the whole 84 KB message catalog, ran out of room and stopped; no files written |
| FB-001, attempt 2 | 225 s | Wrote a test file that never reached the script (9 of 13 tests failed), then the run broke off; no handoff report |

Claude Code finished FB-001 after the two failed attempts. Treat Qwen as a helper for narrow tasks: docs, CSS, small
fixes. Stop it after two failed attempts, check what it reports, and keep timing real tasks. If it isn't saving time,
drop it; nothing else depends on it.

**Writing tasks for the local model.** Its 32K context is small next to this codebase:

- Name the exact lines it needs from big files instead of the file. `messages.properties` alone is 84 KB, about 25K
  tokens; give it the keys and their English text.
- Point to one example to copy (an existing test or class), not several.
- Ask it to work in steps: write a little, run the tests, then continue.
- Don't count on a handoff report: check `git status` and the diff yourself after every run.

## Safety and cost

- No new subscriptions: local inference is free, and Claude, ChatGPT and Antigravity Pro are existing plans. Don't add
  paid API keys to OpenCode or scripts without checking billing.
- Spend Claude and ChatGPT on planning, hard changes and review, not routine build failures.
- Keep Ollama bound to localhost.
- Treat migrations, backup/restore and earnings changes as high-risk even when tests pass.
- Logs, handoff reports and review packets are ignored by Git; read them before sharing.
