# Plan: move server-side text into a translation file (English only, no visible change)

Planned against `origin/main` at 5de206b. This is **part 1 of 3** for
Spanish:

1. `plan/i18n-server` (this plan): pages, form errors, error messages,
   emails, push notifications and calendar text come from one English
   message file.
2. `plan/i18n-scripts`: the browser scripts' text comes from the same
   file.
3. `plan/spanish`: adds `messages_es.properties`, a language setting and
   Spanish screenshot reading.

Parts 1 and 2 change **nothing a driver can see**. They make part 3
mostly a matter of translating one file. Splitting them keeps each
commit reviewable: part 1 touches every template, and part 2 touches
every script.

Decided: English and Spanish now. Other languages can be added later as
more message files.

## Where things stand

All text is hard-coded:

| Where | Size |
| --- | --- |
| 12 Thymeleaf templates | about 1,950 lines. `shifts.html` is 909 and `account.html` 352 |
| Validation messages (`message = "…"`) | 32 |
| Exception messages that reach the driver | about 75 throw sites across 13 exception types |
| Controller `reject(...)` / `rejectValue(...)` messages | 4 |
| Emails | `SmtpPasswordResetMailer` (and the code mailer, once `plan/email-verification` ships) |
| Push titles and bodies | `ReminderJob` lines 69, 90 and 113, and `TaxReminderJob` |
| Calendar event titles and descriptions | built for `IcsWriter` |

Details that matter:
- There's no `MessageSource` setup, no `messages*.properties`, no
  `LocaleResolver`, and every `<html>` says `lang="en"`.
- **Exceptions reach the screen as text.** `GlobalExceptionHandler`
  returns `exception.getMessage()` as the response body, and the scripts
  show that text.
- **25 tests use `hasMessage(...)`** on these exceptions. If messages
  turned into keys, those tests would break.

## 1. Goal and out-of-scope

**Goal:**
- Every piece of server-rendered text a driver can see comes from
  `src/main/resources/i18n/messages.properties`, looked up for the
  request's language:
  - template text and attributes;
  - validation and form errors;
  - error bodies from `GlobalExceptionHandler`;
  - email subjects and bodies;
  - push titles and bodies;
  - calendar event text;
  - the tax summary page.
- There's one supported language for now, **English**, so the output is
  byte-for-byte the same text as today.
- `<html lang>` reflects the request's language.
- A test fails if any template or Java code uses a key that isn't in the
  file.

**Out of scope:**
- **Browser script text,** which is part 2.
- **Spanish,** a language picker and Spanish screenshot reading, which
  are part 3.
- **Privacy Policy and Terms of Use.** They stay English-only. Legal
  text needs a careful translation, and part 3 decides how they're
  handled.
- **Machine formats:** CSV headers, backup JSON keys and API field names.
- **Operator-only text:** error-alert emails and logs.

## 2. Data model and migration

None.

## 3. Backend files

### `src/main/resources/application.properties`

Add:

```
# Every piece of text a driver sees comes from i18n/messages*.properties; English is the base file.
spring.messages.basename=i18n/messages
spring.messages.encoding=UTF-8
spring.messages.fallback-to-system-locale=false
spring.messages.use-code-as-default-message=false
```

`fallback-to-system-locale=false` means a server with a non-English
system locale still uses `messages.properties`.

### `src/main/resources/i18n/messages.properties` (new)

UTF-8. Keys are grouped by page or area, with a comment header per
group. The naming scheme:

```
# ===== Shared =====
app.name=FlexBuddy
common.save=Save
common.cancel=Cancel
...
# ===== Sign in (login.html) =====
login.title=Sign in | FlexBuddy
login.heading=Sign in
login.email=Email
login.password=Password
login.forgot=Forgot password?
login.remember=Keep me signed in
login.remember.hint=Stay signed in on this device for 30 days.
...
# ===== Errors returned to the app (GlobalExceptionHandler) =====
error.shift.notFound=Shift not found.
error.shift.endBeforeStart=The end time must be after the start time.
...
# ===== Validation =====
validation.name.required=Enter your name.
...
# ===== Email =====
email.reset.subject=Reset your FlexBuddy password
email.reset.body=Hi {0},\n\nSomeone asked to reset ...
# ===== Push =====
push.upcoming.title=Upcoming block · {0}
...
```

Rules for the implementer:
- **Use each key's current English text exactly,** including
  punctuation and the `·` character. Nothing visible may change.
- **Placeholders are `{0}`, `{1}`** (`MessageFormat`). Because of that,
  a literal apostrophe in a value must be written `''`, as in
  `isn''t`. This is the easiest thing to get wrong; the test in
  section 6 checks it.
- Text with markup ("Type <strong>delete</strong> to confirm") goes in
  `.html`-suffixed keys, used only with `th:utext`. Every other key is
  plain text with `th:text`.
- Plurals: two keys, `….one` and `….other`.

### `i18n/Messages.java` (new; small helper)

```java
/** English text for a message key, for exception messages and logs; requests use the MessageSource and locale. */
public final class Messages {
    private static final ResourceBundleMessageSource ENGLISH = englishSource();
    public static String english(String key, Object... args) { return ENGLISH.getMessage(key, args, Locale.ENGLISH); }
    ...
}
```

This keeps every exception's `getMessage()` in English. The 25
`hasMessage(...)` tests pass unchanged, and logs stay English.

### `exception/LocalizedMessage.java` (new interface) and the 13 user-facing exception types

- **The interface:** `String messageKey(); Object[] messageArgs();`.
- **Each type** that `GlobalExceptionHandler` returns as text gets a
  constructor `(String key, Object... args)` that calls
  `super(Messages.english(key, args))` and stores the key and args:
  `InvalidBackupException`, `InvalidShiftException`,
  `InvalidFilterException`, `ShiftNotFoundException`,
  `InvalidScreenshotException`, `ExpenseNotFoundException`,
  `ScreenshotBusyException`, `InvalidStandingException`,
  `InvalidPushSubscriptionException`, `InvalidPayoutException`,
  `ScreenshotOcrException`, `InvalidRequestIdException`,
  `InvalidAccountPasswordException` and `TaxPaymentNotFoundException`.
  - **Keep the existing `(String message)` constructors** until every
    throw site is converted. Then delete them in the same commit, so
    nothing hard-coded is left.
- **Convert all about 75 throw sites** to keys. Where the message has a
  value in it, pass it as an argument, for example
  `new InvalidShiftException("error.shift.tooLong", MAX_HOURS)`.

### `exception/GlobalExceptionHandler.java`

- Inject `MessageSource`.
- Every handler that returns `exception.getMessage()` returns
  `text(exception, locale)` instead, where `locale` is a `Locale` handler
  parameter:

  ```java
  private String text(RuntimeException exception, Locale locale) {
      return exception instanceof LocalizedMessage m
              ? messages.getMessage(m.messageKey(), m.messageArgs(), locale)
              : exception.getMessage();
  }
  ```

- The fixed strings in the handler (`"The change could not be saved."`)
  become keys.

### Validation messages (32)

- Change `message = "Enter your name."` to
  `message = "{validation.name.required}"`. Spring Boot's validator
  resolves `{…}` from the application `MessageSource`.
- **Files:** `RegistrationRequest`, `AccountDeletionRequest`,
  `ReminderSettingsRequest` and every DTO with `message =` (find them
  with `grep -rn 'message = "' src/main/java`).
- Class-level `@AssertTrue` messages work the same way.

### Controllers

- `AccountController` and `PasswordResetController`: the
  `reject`/`rejectValue` default messages become the key form,
  `bindingResult.rejectValue("email", "validation.email.registered")`.
  The codes now *are* message keys. The 4 call sites are found by
  `grep -rn 'reject(\|rejectValue(' src/main/java/com/angel/flexbuddy/controller`.
- `TaxPageController` and `PageController`: any text put into the model
  becomes a key, rendered with `#{…}`.

### Emails, push and calendar

- **`SmtpPasswordResetMailer`** (and `SmtpEmailCodeMailer` if
  `plan/email-verification` has shipped): inject `MessageSource`, and
  build the subject and body from `email.reset.subject` and
  `email.reset.body`. Use `Locale.ENGLISH` for now. Part 3 passes the
  driver's language.
- **`ReminderJob`** (lines 69, 90 and 113) and **`TaxReminderJob`:** the
  same, with the keys under `push.*` and `Locale.ENGLISH` for now.
- **`CalendarFeedService`** (which builds `IcsWriter.Event`s): event
  summary and description from `calendar.*` keys, `Locale.ENGLISH`.

### `config/LocaleConfig.java` (new)

```java
@Bean
LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setSupportedLocales(List.of(Locale.ENGLISH));
    resolver.setDefaultLocale(Locale.ENGLISH);
    return resolver;
}
```

Only English is supported, so every request resolves to English. Part 3
replaces this with a resolver that knows Spanish and the driver's
setting.

## 4. Frontend files (templates)

### All 12 templates except the legal text

- **Text:**
  - `<h1>Sign in</h1>` → `<h1 th:text="#{login.heading}">Sign in</h1>`.
    Keep the English text inside the tag, so templates still read
    naturally.
  - Text with markup → `th:utext="#{….html}"`.
  - **Mixed text and data:** existing expressions like
    `th:text="${'Hi, ' + currentUser.displayName}"` become
    `th:text="#{home.greeting(${currentUser.displayName})}"`, with
    `home.greeting=Hi, {0}`.
  - **`<title>`:** `th:text="#{login.title}"`.
- **Attributes:** use Thymeleaf's generic attribute form:
  - `aria-label="Account and settings"` →
    `th:aria-label="#{header.account}"`;
  - likewise `th:placeholder`, `th:title`, `th:alt`, and
    `th:content` for the meta description.
  - **`th:attr`:** keep it for `data-*` attributes that already use it.
- **`<html lang="en">`:** becomes
  `<html th:lang="${#locale.toLanguageTag()}" lang="en">`.
- **Option labels** in selects: `<option value="30" th:text="#{reminders.lead.30}">30 minutes before</option>`.
- **Files and key prefixes:**

  | Template | Prefix |
  | --- | --- |
  | `login.html` | `login.*` |
  | `register.html` | `register.*` |
  | `forgot-password.html` | `forgot.*` |
  | `reset-password.html` and `reset-password-invalid.html` | `reset.*` |
  | `error.html` | `errorPage.*` |
  | `delete-account.html` | `deletePage.*` |
  | `shifts.html` | `home.*`, `reports.*`, `schedule.*`, `import.*`, `expenses.*`, `dialog.*`, `quick.*`, `outbox.*`, `header.*` |
  | `account.html` | `account.*`, `costs.*`, `taxes.*`, `payouts.*`, `reminders.*`, `backup.*`, `privacyCard.*` |
  | `tax-summary.html` | `taxSummary.*` |

  Templates added by the other plans (`verify-email.html`,
  `sign-in-code.html`, `account-two-factor.html`) follow the same rules
  if they've shipped first. Otherwise those plans should be implemented
  with keys from the start; put a note in each when this ships.
- **`privacy.html` and `terms.html`:**
  - only the page `<title>`, the brand block and the back-link get keys;
  - the legal body stays English;
  - add `th:lang="${#locale.toLanguageTag()}"` to `<html>` and
    `lang="en"` to the `<article>` holding the legal text, so screen
    readers read it as English later.
- **No script changes** in this part. Script tags and inline scripts stay
  as they are.

### `static/sw.js`

No change. The build id refreshes cached pages.

## 5. Ripple list

- **Visible change:** none. Every key holds today's exact text. Step 1
  checks this by comparing the rendered pages before and after.
- **Shared records:** `AccountSettingsResponse`, the backup format,
  `BlockEvaluationResponse` and `ShiftResponse` are unchanged. There's no
  new account data.
- **Error bodies:** the same English text for the same requests. The
  scripts display them unchanged.
- **The `MessageFormat` apostrophe rule:** an English value with `'` and
  arguments must use `''`. Values without arguments are rendered by
  Spring without `MessageFormat` *only* if `alwaysUseMessageFormat` is
  false (the default), so a single `'` in a value with no `{0}` is
  shown as is.

  To keep one rule, write `''` **only** in values that have arguments.
  The test in section 6 enforces it.
- **Tests:** `@WebMvcTest` slices include `MessageSourceAutoConfiguration`,
  so templates render English in tests. Tests asserting page text keep
  passing. The 25 `hasMessage(...)` tests pass because `getMessage()` is
  still English.
- **Performance:** messages are cached by `ResourceBundleMessageSource`,
  so there's no measurable cost.
- **Phone layout:** none.

## 6. Tests to add

**`i18n/MessageKeysTest`** (new; plain JUnit with no Spring, like
`StaticAssetsTest`):
- **Every key used exists in `messages.properties`:**
  - every `#{key}` and `#{key(…)}` in `classpath:/templates/*.html`;
  - every `"{key}"` in a `message = ` attribute under `src/main/java`;
  - every string literal passed to a `LocalizedMessage` exception
    constructor, `Messages.english(…)` or `getMessage("…"`.

  Scan the source text with regexes, and report the missing keys and the
  files they're in.
- **Every key in the file is used somewhere,** with an allow-list for
  keys only the scripts will use after part 2. This keeps the file free
  of dead text.
- **The apostrophe rule:** any value that has `{0}` or another argument
  and contains a single `'` not doubled fails the test, with the key
  named.
- **No duplicate keys** in the file.

**`i18n/RenderedTextUnchangedTest`** (new; `@SpringBootTest` +
`MockMvc`, test profile):
- Render `/login`, `/register`, `/forgot-password`, `/` (signed in,
  with a seeded user and shift), `/account` and `/tax/year-summary`.
- Assert a list of exact English strings for each page, picked to cover
  every key group:
  - `/login`: "Sign in", "Forgot password?", "Keep me signed in" and
    "Stay signed in on this device for 30 days.";
  - `/`: "Needs attention", "Recent blocks" and "Evaluate a block";
  - `/account`: "Earnings & costs" as `Earnings &amp; costs`, "Save
    reminders" and "Delete account permanently";
  - and so on.
- Assert that no page contains `??`. That's how Thymeleaf renders a
  missing key, as `??key_en??`.

**`GlobalExceptionHandler`:** in an existing controller test (for
example `ShiftControllerTest`), a missing shift still returns the same
English body as today. Also add one test where the request has
`Accept-Language: fr`: English is still returned, because only English
is supported.

**Mail, push and calendar:**
- `MailConfigTest`: the reset email subject and body are unchanged.
- `ReminderJobTest`: push titles are unchanged.
- `IcsWriterTest` / `CalendarFeedServiceTest`: event summaries are
  unchanged.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### Desktop (1280×800)

1. **Before and after:** before merging, save the HTML of `/login`,
   `/register`, `/forgot-password`, `/`, `/account` and
   `/tax/year-summary` from the old build. Do the same with the new build, then diff them,
   ignoring the `?v=` build id and the CSRF token.
   - The only differences allowed are `lang="en"`, which now comes from
     `th:lang` and still reads `en`.
   - No text differs.
2. **No missing keys:** click through every screen (Home, Reports,
   Schedule, Import, Expenses), every Account section, the edit dialog,
   the finish sheet and the payout history. Then search each page for
   `??`; nothing should be found.
3. **Error text:** trigger these and check the text is exactly as
   before:
   - save a shift with the end before the start;
   - import a `.gif` renamed to `.png`;
   - delete the account with a wrong password.
4. **Emails and push:** request a password reset; the email text is
   identical. With push on, a reminder's title is identical.
5. **Calendar:** subscribe to the calendar feed; event titles are
   unchanged.

### Phone (375×667 and 430×932)

6. Home and Account look identical to before at both widths. No text
   changed, so nothing wraps differently.

## 8. Commit message

```
refactor(i18n): serve page and message text from a translation file

Every piece of text the server renders or returns to a driver was
written inline, which made a second language a rewrite. Page text and
attributes, form and validation errors, the error text the app shows
from failed requests, password reset email, push reminders and
calendar events now come from one English messages file, looked up
for the request's language. Only English is supported, so every page
reads exactly as before.

Exceptions shown to drivers carry a message key and keep an English
message for logs and tests, and the error handler looks the text up
for the request. A test fails when a template or class uses a key the
file lacks, when the file keeps an unused key, or when a message with
arguments has an apostrophe MessageFormat would swallow. The privacy
policy and terms stay English. Browser scripts move to the same file
in a later change.
```

## 9. Open questions

None. English-only output is the point of this step.
