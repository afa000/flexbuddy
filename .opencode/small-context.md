# Working in FlexBuddy with a small model

You may have only 32K tokens of context. Spend them on the task, not on exploring.

- The task file (`.ai/tasks/<task-id>.md`) already lists the files to read and the facts you need. Read those and
  nothing else unless a step fails.
- Never open `flexbuddy/src/main/resources/i18n/messages*.properties`; each is about 25K tokens. Use the grep tool for
  the exact keys the task names.
- Read a big file in parts: find the line with grep, then read about 60 lines around it.
- Copy one existing example (a test or a class the task names) instead of comparing several.
- Work in small steps: change one thing, run `node --test flexbuddy/src/test/js/<name>.test.js`, then continue.
- A test you add must fail without your change. Say so in the handoff report, with the test's name.
- If a command is refused, don't retry it; note it in the handoff report and carry on.
- Before you stop, write `.ai/implementations/<task-id>.md`: what changed, each check and its real result, and what
  is unfinished. Never claim a check passed unless you ran it.
