# Plan 06c: JavaScript unit tests with `node --test`

Planned against `origin/main` at `0292b67`. **No migration and no backend change.**

This plan should ship **before or together with 06b** (`plans/offline-queue-client.md`), so
the outbox's pure functions have tests from their first commit. It doesn't depend on 06a or
06b.

## 1. Goal and scope

Add a zero-dependency JavaScript test setup using Node's built-in test runner (`node:test`
and `node:assert`). It needs **Node 22 or later**, the current LTS, which expands the test
file pattern itself.

- It loads the browser scripts from `static/js` unchanged, in a Node `vm` context with a
  small stub `document`.
- It ships with two real test files:
  - **`calendar.test.js`** covers the date helpers. Dates are one of the app's recurring
    rework areas.
  - **`dates-guard.test.js`** is a source scan that fails if any script derives a date from
    `toISOString()`, the rule that has caused rework before.
- The Windows commands and the Linux/macOS commands are documented in the README.

### Out of scope

- A browser or DOM test framework (jsdom, Playwright tests).
- `package.json`, npm dependencies or a bundler.
- Changing any script to make it testable, apart from one marker comment in `charts.js`.
- CI: the repository has none, and the Docker build runs `mvn -DskipTests`.
- Testing scripts that touch the page at load time (`app.js`, `account.js`, `tax.js`,
  `quick-actions.js`, `pwa.js`). New scripts, starting with `outbox.js` in 06b, expose their
  pure functions so they can be tested.

## 2. Data model and migration

None.

## 3. Backend

None. The Java suite and `./mvnw test` are unchanged. Maven ignores `src/test/js`, since
only `src/test/java` and `src/test/resources` are on its paths, so no JavaScript file ends
up in the jar.

## 4. Test harness and files

All new files are under `flexbuddy/src/test/js/`.

### `load-script.js`: the helper

```js
'use strict';
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const STATIC_JS = path.resolve(__dirname, '../../main/resources/static/js');

/**
 * Runs one browser script from static/js in a fresh context and returns that context, whose
 * `window` holds whatever the script exported (for example `context.flexbuddyCalendar`).
 * The stub document answers every query with nothing, so scripts that only touch the page
 * inside their functions load cleanly; scripts that query the page at load time are not
 * loadable here.
 */
function loadScript(name, extra = {}) {
    const document = {
        addEventListener() {}, removeEventListener() {},
        querySelector: () => null, querySelectorAll: () => [],
        documentElement: {dataset: {}}
    };
    const context = {document, navigator: {onLine: true}, console, setTimeout, clearTimeout, ...extra};
    context.window = context;
    vm.createContext(context);
    const file = path.join(STATIC_JS, name);
    vm.runInContext(fs.readFileSync(file, 'utf8'), context, {filename: file});
    return context;
}

module.exports = {loadScript, STATIC_JS};
```

**The cross-realm catch.** Objects made inside the loaded script (a `Date` from `parseIso`,
an array, a plain object) come from the context's realm. They are **not** `instanceof Date`
in the test file, and `assert.deepStrictEqual` treats them as different from literals made
in the test. So:

- compare **primitives or ISO strings**: `calendar.addDays(...)` already returns a string;
- or compare `JSON.parse(JSON.stringify(result))` against the literal.

Put this rule as a comment at the top of `load-script.js`.

### `calendar.test.js`

The first line of the file is `process.env.TZ = 'America/Los_Angeles';`, before anything else
runs. Node applies a `TZ` set this way, and it works the same on Windows, where a `TZ=…`
command prefix doesn't. `node --test` runs each file in its own process, so this doesn't leak
into other files.

It loads `calendar.js`, and `const cal = loadScript('calendar.js').flexbuddyCalendar;`.

1. **`toIso` uses the local calendar date.** `cal.toIso(new Date(2026, 8, 29, 23, 30))` is
   `'2026-09-29'`. At that moment it's already Sep 30 in UTC.
2. **`parseIso` gives local midnight.** For `cal.parseIso('2026-09-29')`, `getFullYear()`,
   `getMonth()`, `getDate()` and `getHours()` are `2026`, `8`, `29` and `0`.
3. **`addDays` across the daylight-saving changes.**
   - `('2026-03-08', 1)` gives `'2026-03-09'` (spring forward).
   - `('2026-11-01', 1)` gives `'2026-11-02'` (fall back).
   - `('2026-11-02', -1)` gives `'2026-11-01'`.
4. **`addDays` across months and years.**
   - `('2026-12-31', 1)` gives `'2027-01-01'`.
   - `('2026-03-01', -1)` gives `'2026-02-28'`.
   - `('2028-03-01', -1)` gives `'2028-02-29'`.
5. **`addMonths` clamps to the last day.**
   - `('2026-01-31', 1)` gives `'2026-02-28'`.
   - `('2028-01-31', 1)` gives `'2028-02-29'`.
   - `('2026-03-31', -1)` gives `'2026-02-28'`.
   - `('2026-10-15', 3)` gives `'2027-01-15'`.
6. **`weekStart` is Monday.**
   - `'2026-09-30'` (a Wednesday) gives `'2026-09-28'`.
   - `'2026-10-04'` (a Sunday) gives `'2026-09-28'`.
   - `'2026-09-28'` (a Monday) gives itself.
7. **The same answers far from UTC.** A second file, `calendar-kiritimati.test.js`, sets
   `process.env.TZ = 'Pacific/Kiritimati'` (UTC+14), re-runs cases 1, 3 and 6, and gets the
   same results. Keep it as a separate file, because `TZ` is per process.

Every expected value above, in both time zones, was checked in a scratch run against the
current `calendar.js` with Node 22.22.

### `dates-guard.test.js`: the rule, as a test

- Read every `*.js` in `STATIC_JS`.
- Fail if any line matches `/toISOString\(\)\s*\.(slice\(0,\s*10\)|split\(['"]T['"]\))/`,
  **unless** the same line contains the marker `// utc-day`.
- The failure message names the file, the line number, and the rule: "Use the device's
  local date (toIsoDate / flexbuddyCalendar.toIso) instead of toISOString for a date."
- A second assertion checks that the scan found at least 10 files. That catches a broken
  path, which would otherwise pass silently.

**The one existing match is correct and gets the marker.** `charts.js:391` builds its
month ticks from UTC day numbers (`new Date(day * DAY_MS)`, `getUTCDate`,
`timeZone: 'UTC'`), so it's consistent and not a local-date bug. Change only that line's
trailing comment:

```js
const tick = x(date.toISOString().slice(0, 10)); // utc-day: day numbers here are UTC throughout
```

That comment is the only change to application files in this commit.

### How to run

From `flexbuddy/`, the same command on Windows (PowerShell or cmd), Linux and macOS:

```
node --test "src/test/js/*.test.js"
```

- **The quotes matter.** Node 22 expands the pattern itself, so it works in PowerShell,
  which doesn't expand globs for other programs.
- **A bare directory (`node --test src/test/js/`) doesn't work.** Node 22 tries to load the
  directory as a module and fails with `MODULE_NOT_FOUND`. This was checked on Node 22.22.
- **Node 20 doesn't expand the pattern,** which is why 22 is the minimum.

### README (`README.md`, section "Run the tests")

After the Maven commands, add:

````
The browser scripts have their own tests, run with Node 22 or later and no install step:

```powershell
cd flexbuddy
node --test "src/test/js/*.test.js"
```

They load the scripts from `src/main/resources/static/js` as they are, so a script that
needs the page at load time can't be tested this way; new scripts keep their logic in
functions they export.
````

## 5. Ripple list

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format, `BlockEvaluationResponse`, `ShiftResponse` | **Not touched.** |
| New account data | None. |
| `sw.js` | No change. There's no new static file; test files aren't served. |
| Boxed request fields | Not applicable. |
| Phone layout | Not applicable. |
| Dates | This commit **adds a guard** for the browser-date rule and tests the calendar helpers in two time zones. |
| Money and tax wording | Not applicable. |
| Application files | `charts.js`: one trailing comment on line 391. |
| Java tests | None change. `StaticAssetsTest` only reads `static/` and `templates/`. |

## 6. Tests to add

The whole commit is tests: 7 cases in `calendar.test.js`, 3 in
`calendar-kiritimati.test.js`, and 2 in `dates-guard.test.js`. The Java suite stays as it is.

**How to check the guard catches something:** temporarily add
`const d = new Date().toISOString().slice(0, 10);` to `swipe.js` and run the tests. The guard
fails, naming `swipe.js` and the line. Remove it again.

## 7. Manual checks

1. **On this Windows machine:** `node --version` is 22 or later. From `flexbuddy/`,
   `node --test "src/test/js/*.test.js"` reports `# pass 12` and `# fail 0`, and runs in
   under a second or two.
2. **The guard works:** see section 6.
3. **The time-zone isolation works:** run only `node --test src/test/js/calendar-kiritimati.test.js`.
   It passes regardless of the machine's own zone.
4. **`./mvnw.cmd test`** still passes with the same Java count. **`docker build`** is
   unaffected, since it runs `-DskipTests`.
5. **The standing timeline is unchanged:** open the Schedule's Standing card. The month
   ticks and labels are as before, because only a comment changed.

## 8. Suggested commit message

```
test(js): run the browser scripts' tests with node --test

The scripts in static/js can now be tested with Node's built-in test
runner, with no install step and no dependencies. A small helper runs a
script as it is in a stand-in page and returns what it exported, so a
test sees the same code the browser does.

The first tests cover the calendar helpers the schedule relies on,
across month and year ends, leap years and both daylight-saving changes,
in two time zones a day apart. Another test reads every script and
fails if one takes a date from toISOString, which gives tomorrow's date
on a US evening; the standing chart's month ticks, which count UTC days
on purpose, are marked as the one exception.
```

## 9. Decisions

All questions are settled. Nothing is left open.

- **The JavaScript tests are not hooked into Maven.** `./mvnw test` stays Java-only, so it
  can never fail just because Node is missing. The implementing session runs
  `node --test "src/test/js/*.test.js"` as a separate step whenever a change touches
  `static/js`, and before every push that does. Revisit this if CI is ever added.
- **Check Node before starting.** The first step of implementing this plan is
  `node --version`. If it's missing or older than 22, install Node 22 LTS with
  `winget install OpenJS.NodeJS.LTS` on Windows, then open a new terminal so `node` is on the
  PATH. No other setup is needed. The README section in 4 says Node 22 or later is required.
