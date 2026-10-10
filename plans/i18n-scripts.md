# Plan: move browser-script text into the translation file (English only)

Planned against `origin/main` at 5de206b. This is **part 2 of 3** for
Spanish. **Ship `plan/i18n-server` first.** This plan uses its
`messages.properties`, `MessageSource` setup, `LocaleConfig` and
`MessageKeysTest`.

## Where things stand (after part 1)

- **Server text** comes from `i18n/messages.properties`, but the 20
  scripts in `static/js` still hold about 530 English strings: toasts,
  button labels, empty states, error messages, summaries, plurals
  ("1 block" or "3 blocks"), and HTML built with `innerHTML`. The
  largest are `app.js` (about 194), `account.js` (about 81) and
  `schedule.js` (about 34). `swipe.js` and `calendar.js` have almost
  none.
- **Dates and numbers** are formatted with the *device's* locale, through
  `toLocaleDateString(undefined, …)` and `Intl.*(undefined, …)` in
  `app.js` (lines 906, 921, 1122, 1124, 1749, 1754, 1981, 1985, 1989
  and 1994) and `account.js` line 52. A few calls force `'en-US'`
  (`account-sections.js` lines 10 and 11). The result is mixed: a phone
  set to Spanish already shows Spanish month names inside English
  sentences.
- **JS tests** (`node --test`, `load-script.js`) load scripts into a stub
  `window` and assert English text.

## 1. Goal and out-of-scope

**Goal:**
- **Every user-visible string** in the browser scripts comes from
  `messages.properties` (keys prefixed `js.`), through a small `t()`
  helper.
- **The page carries its language's messages** as inline JSON, so the
  scripts work offline from the cached page.
- **Dates and numbers** are formatted for the *page's* language (`en-US`
  for English) instead of the device's, so Spanish later gets Spanish
  dates and English gets English ones.
- **Tests:** the JS tests keep passing with English text, and a test
  fails if a script uses a key the file lacks.

**Out of scope:**
- Spanish itself (part 3).
- `errors.js` reports, which are operator text and stay English.
- The data lines in the feedback email (`App version:`, `Screen:`,
  `Device:` and `Date:`), which go to support and stay English. Only
  the prompt line the driver sees is translated.
- CSV and backup files.

## 2. Data model and migration

None.

## 3. Backend files

### `i18n/ScriptMessages.java` (new `@Component`)

- **On startup,** loads the key names from `i18n/messages.properties`
  with `PropertiesLoaderUtils.loadAllProperties`, keeping only keys
  starting with `js.`.
- **`String json(Locale locale)`:**
  - resolves every `js.*` key with
    `messageSource.getMessage(key, null, locale)`, the raw value with
    `{0}` and `''` left in place;
  - serialises the result to a JSON object with Jackson's `ObjectMapper`;
  - replaces `</` with `<\/`, so the JSON can't close the `<script>`
    element;
  - caches the result per locale in a `ConcurrentHashMap`, so it's built
    once per language per run.

### `config/ScriptMessagesAdvice.java` (new `@ControllerAdvice`)

- **Covers** `PageController`, `AccountController` and
  `PasswordResetController`, plus `EmailVerificationController` and
  `TwoFactorController` if they've shipped.
- **Adds** `@ModelAttribute("scriptMessages")`, which returns
  `scriptMessages.json(locale)`, with the `Locale` resolved by
  `LocaleConfig`.

## 4. Frontend files

### Templates that load scripts

In `shifts.html` and `account.html` (and `login.html`, `register.html`
and `reset-password.html` once `plan/password-visibility` has shipped),
add this as the **first** element inside `<body>`:

```html
<script id="flexbuddyMessages" type="application/json" th:utext="${scriptMessages}">{}</script>
```

- **The `<script>` tag that loads `i18n.js`** goes first among the
  deferred scripts, after `errors.js`:

  ```html
  <script th:src="@{/js/i18n.js(v=${buildId})}" defer></script>
  ```

- **`tax-summary.html`'s inline print-button script** has no visible text
  and needs no change.

### `static/js/i18n.js` (new)

```js
// Translations: the page's messages arrive as JSON in #flexbuddyMessages, rendered for the page's language. t() looks
// a key up and fills {0}, {1}, …; tn() picks the .one or .other form for a count. The helpers are pure apart from
// reading that JSON once, so they run under `node --test`.
(() => {
    let messages = window.flexbuddyMessages || {};
    try {
        const node = typeof document.getElementById === 'function' && document.getElementById('flexbuddyMessages');
        if (node) messages = JSON.parse(node.textContent || '{}');
    } catch {
        // A broken bundle leaves keys visible rather than breaking the page.
    }

    /** Fills {0}, {1}, … like Java's MessageFormat, where '' stands for one apostrophe when arguments are given. */
    function format(text, args) {
        if (!args.length) return text;
        return text.replace(/''/g, "\u0000").replace(/\{(\d+)\}/g, (m, i) => (i < args.length ? String(args[i]) : m))
            .replace(/\u0000/g, "'");
    }

    function t(key, ...args) {
        const text = messages[key];
        return text === undefined ? key : format(text, args);
    }

    /** "1 block" / "3 blocks": key.one and key.other, with the count as {0}. */
    function tn(count, key, ...args) {
        return t(`${key}.${count === 1 ? 'one' : 'other'}`, count, ...args);
    }

    /** The BCP 47 tag for dates and numbers: the page's language, US region, e.g. "en-US" or "es-US". */
    function appLocale() {
        const lang = (typeof document.documentElement?.lang === 'string' && document.documentElement.lang) || 'en';
        return `${lang.split('-')[0]}-US`;
    }

    window.flexbuddyI18n = {t, tn, appLocale, format};
    window.t = t;
    window.tn = tn;
})();
```

`t` and `tn` are globals on purpose. The other scripts already share
globals from `app.js` (`apiFetch`, `formatMoney`, …), and short names
keep about 530 call sites readable.

### All 20 scripts in `static/js`

- **Replace each user-visible literal:**
  - `'Time zone updated'` → `t('js.app.timeZoneUpdated')`.
  - `` `Time zone set to ${zone}` `` → `t('js.app.timeZoneSet', zone)`,
    with `js.app.timeZoneSet=Time zone set to {0}`.
  - Plurals: `` `${count(n, 'block', 'blocks')}` `` → `tn(n, 'js.common.blocks')`,
    with `js.common.blocks.one={0} block` and
    `js.common.blocks.other={0} blocks`. Replace `home.js`'s local
    `count()` helper with `tn`.
- **HTML built with `innerHTML`:**
  - Translated text is **text**. Keep wrapping it in `escapeHtml(...)`
    exactly where the English literal was interpolated.
  - A literal that contains markup (`'<strong>…</strong> …'`) is split,
    so only the words are translated and the markup stays in the code.
- **Key names:** `js.<file>.<name>`, for example `js.home.recentEmpty`
  and `js.schedule.confirmTitle`. Shared words go under `js.common.*`:
  `Save`, `Cancel`, `Undo`, `Not saved`, `Try again`, and the weekday
  and month short names if any script spells them out.
- **Keys must be literals.** Never build a key from a variable
  (`t('js.status.' + s)`). The key test can't see those. Use a lookup
  object of literal keys instead:
  `const STATUS = {COMPLETED: 'js.status.completed', …}; t(STATUS[s])`.
- **Locale:** every `toLocaleDateString(undefined, …)`,
  `toLocaleString()`, `toLocaleTimeString(undefined, …)` and
  `new Intl.*(undefined, …)` becomes `appLocale()`. That includes the
  `app.js` lines listed above and `account.js` line 52.
  `account-sections.js`'s `'en-US'` becomes `appLocale()` too.
  - **Never** use `toISOString()` for a date. Keep the existing local
    date helpers.
- **What doesn't change:**
  - `errors.js` and console messages stay English;
  - `feedback.js` translates only the prompt line;
  - `outbox.js` keeps its stored queue entries language-neutral (method,
    URL and body), so a language change doesn't break queued saves.
    Labels are built when shown.

### `src/main/resources/i18n/messages.properties`

Add every `js.*` key with today's exact English text, under a
`# ===== Browser scripts =====` header, grouped by file. Remove from
part 1's `MessageKeysTest` allow-list the keys that are now used.

### `static/sw.js`

Add `'/js/i18n.js'` to `VERSIONED_ASSETS`. The messages are inside the
cached page, so `DATA_PATHS` is unchanged.

### `src/test/js/load-script.js`

The loader reads the real English messages, so existing tests keep
asserting English:
- Add `function englishMessages()`. It reads
  `src/main/resources/i18n/messages.properties`, parses `key=value`
  lines (skipping `#` comments; handling `\n`, `\\`, `\uXXXX` and
  trailing-backslash continuations), and returns an object of the `js.*`
  entries.
- In `loadScript`, before running the requested file, run `i18n.js` in
  the same context with `context.flexbuddyMessages = englishMessages()`.
  That way `t` and `tn` exist in every test.
- **The stub `document`** gains `getElementById: () => null`, so
  `i18n.js` falls back to `window.flexbuddyMessages`.

## 5. Ripple list

- **Visible change:**
  - Text: none, since every key holds today's English.
  - **Dates and numbers:** now follow the page language (`en-US`), not
    the device. A driver whose phone is set to, say, English (UK) or
    Spanish will now see US-style dates and numbers in the English app,
    consistent with the US dollar amounts. Before, those were mixed.
- **The offline outbox** stores no translated text, so queued items replay
  correctly after a language change.
- **Shared records:** `AccountSettingsResponse`, the backup format,
  `BlockEvaluationResponse` and `ShiftResponse` are unchanged. There's no
  new account data.
- **Page weight:** the inline JSON is about 25 KB uncompressed, cached
  with the page for offline use.
- **Security:**
  - the JSON escapes `</` before it goes into the page;
  - translations are inserted as text, or escaped where they go into
    `innerHTML`;
  - message values never contain markup that scripts would insert raw.
- **Phone layout:** none.

## 6. Tests to add

**`i18n/MessageKeysTest`** (extend from part 1):
- Every `t('…')`, `t("…")`, `tn(…, '…')` and the literal values of lookup
  objects whose name ends in `KEYS` or that sit next to `t(` in
  `static/js/*.js` exist in `messages.properties`. For `tn`, both
  `.one` and `.other` must exist.
- **No computed keys:** any `t(` whose first argument isn't a string
  literal or an identifier fails, listing file and line. The identifier
  case is for a lookup like `t(STATUS[s])`, which the lookup-object rule
  covers.
- **No leftover English:** a heuristic that fails on user-visible English
  string literals left in `static/js`. Use an allow-list for the
  non-visible ones: keys, CSS classes, URLs, event names, `errors.js`,
  console messages and `feedback.js` data labels. The heuristic matches
  quoted strings that start with a capital letter, contain a space and
  aren't in the allow-list. It doesn't need to be perfect; it exists to
  catch obvious misses.

**`src/test/js/i18n.test.js`** (new):
- `t('missing.key')` returns `'missing.key'`.
- `format("It''s {0}", ['ready'])` gives `"It's ready"`, and
  `format("It's", [])` gives `"It's"` (no arguments, no change).
- `tn(1, 'js.common.blocks')` gives `'1 block'` and
  `tn(3, 'js.common.blocks')` gives `'3 blocks'`, using the real English
  file through the loader.
- `appLocale()` with `lang="es"` gives `'es-US'`, and with no lang gives
  `'en-US'`.

**Existing JS tests** (`home`, `reports`, `account-sections`, `setup`,
`feedback`, `password-toggle`, …) pass unchanged, now asserting text that
comes from the file.

**`i18n/ScriptMessagesTest`:**
- `json(Locale.ENGLISH)` is valid JSON, contains `js.home.recentEmpty`,
  and contains no `</script`.
- The JSON is built once: two calls return the same instance.

**`PageControllerTest`:** `/` renders `id="flexbuddyMessages"` with
non-empty JSON before the first script tag.

**`StaticAssetsTest`:** passes, with `i18n.js` precached.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### Desktop (1280×800), English

1. Click through every screen, sheet and dialog: Home, Reports (each tab),
   Schedule, the calendar, confirming a block, the finish sheet, Import
   (including an error), Expenses, the edit dialog, payout history,
   standing, the quick actions menu, and all six Account sections, saving
   each one. Every label, toast and empty state reads exactly as before.
   In particular, no raw key like `js.home.recentEmpty` appears anywhere.
2. Go offline (dev tools → Network → Offline) and reload Home from the
   service worker. The text is still English, not keys.
3. Queue a shift offline, then go online. The outbox strip's labels and
   the "synced" toast read correctly.

### Phone (375×667 and 430×932, then real devices)

4. **iPhone with Region set to United Kingdom:**
   - Before: dates such as "28 Sep".
   - After: "Sep 28", matching the rest of the app.
5. Text wraps the same as before at both widths.

## 8. Commit message

```
refactor(i18n): serve browser script text from the translation file

The scripts held about five hundred English strings inline, so a
second language would have meant editing every one of them by hand.
Each page now carries its language's script messages as inline JSON
from the same messages file the server uses, and the scripts read
them through small t and tn helpers that fill arguments and pick
singular or plural forms. The JSON is cached with the page, so text
works offline, and queued offline saves store no text at all.

Dates and numbers now follow the page's language with the US region
instead of the device's language, so an English page no longer mixes
in another language's month names. The JS tests load the real English
messages, and a test fails when a script uses a key the file lacks,
builds a key from a variable or leaves an obvious English string
behind.
```

## 9. Open questions

None.
