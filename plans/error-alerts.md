# Plan: email the operator when something breaks

Planned against `origin/main` at 89bde5d. Ship after
`plan/mail-health-check`. It also reads best after `plan/ci`, but doesn't
depend on it.

## Where things stand

- No one is told when the app fails. A server error shows the driver
  `error.html` ("Something went wrong") and leaves a stack trace in the
  Render logs. A script crash on a phone, like the null-element crashes
  found in the layout review, leaves no trace anywhere.
- Mail already works: `MailConfig` picks `SmtpPasswordResetMailer` when
  `spring.mail.host` is set, which sends through Gmail on the
  `mailExecutor` pool. `LoggingPasswordResetMailer` is used otherwise.
- `GlobalExceptionHandler` turns `ScreenshotOcrException` and unknown
  `DataIntegrityViolationException`s into 500s without logging them. So
  they never reach the logs either.
- Unhandled request exceptions are logged at ERROR by Tomcat
  (`org.apache.catalina.core.ContainerBase.[Tomcat]...[dispatcherServlet]`).
  Failed `@Scheduled` jobs are logged at ERROR by Spring's
  `TaskUtils`, and failed `@Async` void methods by
  `SimpleAsyncUncaughtExceptionHandler`. One logging hook therefore
  catches all three.

## Approach and why

The app sends its own alert emails through the Gmail setup that already
exists, rather than using Sentry or another service:
- There's no new account, SDK or third-party data processor.
- The privacy policy change stays small.
- The setup already in Render is reused.

There are two sources:

1. **Server:** a Logback appender sends every ERROR-level log event to an
   alert service.
2. **Browser:** a small `errors.js` reports uncaught script errors on the
   app pages to `POST /client-errors`, which feeds the same service.

The service groups errors by fingerprint and limits emails, so a failure
that repeats every minute (for example `ReminderJob`) sends one email,
not 60.

Reports never include exception messages, log arguments, request bodies,
query strings, emails, shift data or money. They carry:
- class names;
- code locations;
- the app's own log message templates;
- for browser errors, the script message, scrubbed and cut to 300
  characters, and the account's numeric id.

Uptime is covered without code. An external monitor polls
`/actuator/health`, and Render's own notifications cover failed deploys
(section 7, manual setup).

## 1. Goal and out-of-scope

**Goal:**
- Within a minute of a server ERROR, or an uncaught script error on Home,
  Reports, Schedule, Import, Expenses or Account, the operator gets an
  email that says what failed and where.
- The same error repeating sends at most one email per 6 hours per
  fingerprint, plus a count of how many times it repeated.
- All alerts are capped at 20 emails per UTC day.

**Out of scope:**
- Sentry or any third-party error service.
- Metrics, dashboards and log shipping.
- Alerts on 4xx responses: a 404, validation failures or failed sign-ins.
- Errors during startup. The app isn't running then, so Render's
  failed-deploy notification and the uptime monitor cover them.
- Script errors on signed-out pages (login, register, password reset).
- The feedback button, which is in `plan/first-run-setup`.
- JVM memory settings.

## 2. Data model and migration

None. All alert state is in memory and resets on restart, which is
acceptable for a single instance.

## 3. Backend files

New package `com.angel.flexbuddy.alert`, in `src/main/java/com/angel/flexbuddy/alert/`.

### `ErrorAlertProperties.java` (new)

```java
@ConfigurationProperties("flexbuddy.alerts")
public record ErrorAlertProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("flexbuddysupport@gmail.com") String to,
        @DefaultValue("6h") Duration quietPeriod,
        @DefaultValue("20") int maxPerDay,
        @DefaultValue("10") int browserReportsPerAccountPerHour) {
}
```

Enable it with `@EnableConfigurationProperties(ErrorAlertProperties.class)`
on a new `AlertConfig` in the same package. Don't add it to
`SecurityConfig`.

### `ErrorReport.java` (new record)

```java
public record ErrorReport(Source source, String kind, String where, List<String> details,
        Long accountId, String screen, String buildId) {
    public enum Source { SERVER, BROWSER }
    /** Same source, kind and place count as one error for throttling. */
    public String fingerprint() { return source + "|" + kind + "|" + where; }
}
```

### `ErrorReports.java` (new; static and pure, so it can be unit tested)

`fromLogEvent(ILoggingEvent event, String buildId)`:
- **With a throwable:**
  - `kind` is the top throwable's simple class name.
  - `details` lists the cause chain as class names only, for example
    `"caused by PSQLException"`. Up to 8 stack frames follow, taken from
    the deepest cause that has any `com.angel.flexbuddy` frame. Only the
    `com.angel.flexbuddy` frames are kept, formatted
    `ShiftService.update(ShiftService.java:212)`.
  - If no cause has an app frame, use the deepest cause's first 3 frames.
  - `where` is the first app frame, or the logger's simple name.
- **Without a throwable:**
  - `kind` is `"Logged error"` and `where` is the logger's simple name.
  - `details` holds the message template from `event.getMessage()`, the
    unformatted pattern with `{}` and no arguments, but only when the
    logger name starts with `com.angel.flexbuddy`. Otherwise `details` is
    empty.
- **Never** read `getFormattedMessage()`, a throwable's `getMessage()`, MDC
  values or the event's arguments. Exception messages can hold emails,
  SQL values or tokens.

`fromBrowser(ClientErrorRequest request, long accountId)`:
- `source`: strip the origin and the query string. Keep it only if it
  matches `^/js/[a-z0-9-]+\.js$`; otherwise use `"page"`.
- `where` is `source:line:column`.
- `kind` is the text before the first `:` in the message when it matches
  `^[A-Za-z]*Error$`; otherwise `"Error"`.
- `message`:
  - collapse whitespace;
  - replace anything matching an email pattern with `[email]`;
  - replace runs of 16 or more letters, digits, `-` or `_` with `[id]`
    (this catches tokens);
  - cut it to 300 characters.

  The result is the only entry in `details`.
- `screen`: one of `home`, `reports`, `schedule`, `import`, `expenses` or
  `account`. Map `dashboard` to `home`, and anything else to `other`.
- `buildId`: matches `^[0-9A-Za-z.:-]{1,40}$`, or `"unknown"`.

### `ErrorAlertText.java` (new; static and pure)

`subject(ErrorReport r)` produces
`"[FlexBuddy] Server error: DataIntegrityViolationException at ShiftService.update"`
or
`"[FlexBuddy] Browser error: TypeError in /js/home.js"`.
Cut it to 150 characters.

`body(ErrorReport r, Instant at, int repeatsSinceLastEmail)` is plain
text with these lines:
- `When: 2026-10-02 14:03 UTC`
- `Version: <buildId>`
- `Where: <where>`
- the `details` lines;
- for browser reports, `Screen: <screen>` and `Account: #<id>`;
- `Repeated N more times since the last email` when N > 0;
- a footer: `Search the Render logs around this time for the full trace.`

The email carries no driver email address and no request path.

### `ErrorAlertService.java` (new `@Service`)

Constructor: `ErrorAlertProperties`, `ErrorAlertMailer`, `Clock`.

- `synchronized void report(ErrorReport r)` does nothing when
  `!enabled`. Otherwise it keeps, per fingerprint, `lastSentAt` and
  `suppressed`, in a `LinkedHashMap` capped at 500 entries that drops the
  oldest.
  - If the fingerprint was sent within `quietPeriod`, add 1 to
    `suppressed` and return.
  - If `maxPerDay` emails have gone out since the current UTC day began,
    add 1 to `suppressed` and return. The day count resets at UTC
    midnight according to the `Clock`.
  - Otherwise, call `mailer.send(subject, body)` with the fingerprint's
    `suppressed` count as the repeat count, then set `suppressed = 0` and
    `lastSentAt = now`.
- `synchronized boolean allowBrowserReport(long accountId)` allows at most
  `browserReportsPerAccountPerHour` per account in a rolling hour. The map
  is capped at 1,000 accounts and swept of stale entries on each call.
- `report` must never throw. Wrap the body in try/catch and log failures
  at **WARN**, never ERROR, with the class name only.

### `ErrorAlertMailer.java`, `SmtpErrorAlertMailer.java` and `LoggingErrorAlertMailer.java` (new, in `com.angel.flexbuddy.mail`)

- The interface has `void send(String subject, String body)`.
- The SMTP mailer:
  - takes `JavaMailSender`, `from` and `to`, and is `@Async("mailExecutor")`;
  - sends a `SimpleMailMessage`;
  - catches `RuntimeException` and logs
    `log.warn("error alert email could not be sent ({})", simpleName)`.

  It mirrors `SmtpPasswordResetMailer`.
- The logging mailer writes
  `log.info("error alert (mail not configured): {}", subject)`.

### `mail/MailConfig.java`

Add a second `@Bean ErrorAlertMailer errorAlertMailer(...)` with the same
host rule as `passwordResetMailer`. `to` comes from
`ErrorAlertProperties.to()`.

### `ErrorAlertAppender.java` (new; `extends AppenderBase<ILoggingEvent>`)

- Ignore events below `Level.ERROR`.
- Ignore loggers starting with any of:
  - `com.angel.flexbuddy.alert`
  - `com.angel.flexbuddy.mail`
  - `org.springframework.mail`
  - `jakarta.mail`
  - `org.eclipse.angus.mail`

  Mail failures must never mail.
- A `ThreadLocal<Boolean>` guard means an ERROR logged while reporting
  isn't reported again.
- Call `service.report(ErrorReports.fromLogEvent(event, buildId))` inside
  `try { } catch (Throwable ignored) { }`. A full `mailExecutor` queue
  throws `TaskRejectedException` from the async proxy, and that must not
  reach the code that logged.

### `ErrorAlertAppenderRegistrar.java` (new `@Component`)

- On `ApplicationReadyEvent`, when `enabled`, create the appender, give
  it the `LoggerContext` from `LoggerFactory.getILoggerFactory()`, start
  it, and attach it to the ROOT logger.
- On `ContextClosedEvent`, detach and stop it. Test contexts share one
  Logback root, so a context that isn't closed would keep reporting other
  tests' errors.
- The build id is `@Value("${flexbuddy.build-id:dev}")`, normalised the
  way `BuildInfoAdvice` does it.

### `controller/ClientErrorController.java` (new)

```java
@PostMapping("/client-errors")
ResponseEntity<Void> report(@Valid @RequestBody ClientErrorRequest request, Principal principal)
```

- Look up the user with
  `AppUserRepository.findByEmailIgnoreCase(principal.getName())`.
- If `alerts.allowBrowserReport(user.getId())`, then call
  `alerts.report(ErrorReports.fromBrowser(request, user.getId()))`.
- Always return **204**, including when the report was throttled.
- The endpoint requires sign-in and CSRF, which is the default. Don't add
  it to `permitAll`.

### `dto/ClientErrorRequest.java` (new record)

```java
public record ClientErrorRequest(
        @NotBlank @Size(max = 300) String message,
        @Size(max = 300) String source,
        @Min(0) Integer line,
        @Min(0) Integer column,
        @Size(max = 20) String screen,
        @Size(max = 40) String buildId) {
}
```

Field values are cut further by `ErrorReports.fromBrowser`. A message
over 300 characters gets a 400 response; the browser trims it first.

### `exception/GlobalExceptionHandler.java`

- Add `private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);`.
- In `handleScreenshotOcrException`, add
  `log.error("screenshot text extraction failed", exception);` before the
  return.
- In `handleDataIntegrity`'s final 500 branch, not the DUPLICATE branch,
  add `log.error("a change could not be saved", exception);`.
- Response codes and bodies stay exactly as they are.

### `src/main/resources/application.properties`

Add, after the mail block:

```
# Error alerts: the operator gets one email per distinct server or browser error, at most once every 6 hours and 20 a
# day. Reports carry class names and code locations, never exception messages or driver data.
flexbuddy.alerts.enabled=true
flexbuddy.alerts.to=${FLEXBUDDY_ALERT_TO:flexbuddysupport@gmail.com}
flexbuddy.alerts.quiet-period=6h
flexbuddy.alerts.max-per-day=20
flexbuddy.alerts.browser-reports-per-account-per-hour=10
```

### `src/test/resources/application-test.properties`

Add `flexbuddy.alerts.enabled=false`, so the shared test contexts never
attach the appender. Only the tests below turn it on.

## 4. Frontend files

### `static/js/errors.js` (new)

Load it **first** among the deferred scripts so it's listening before
the others run.

- Start with `if (typeof window.addEventListener !== 'function') return;`
  in the listener setup only. Export the pure helpers before that guard,
  so `load-script.js` (whose stub window has no `addEventListener`) can
  load the file.
- `buildId` comes from `new URL(document.currentScript?.src || location.href).searchParams.get('v') || 'unknown'`.
  Read it once at load.
- `screen()`: on `/account` it's `'account'`; otherwise it's
  `new URLSearchParams(location.search).get('screen') || 'home'`.
- Pure helper `shouldReport({message, filename, reason, online, sent, seen})`
  returns false when:
  - `online === false`;
  - `sent >= 3` (3 per page load);
  - the key `message|filename|line` is in `seen`;
  - `message` is `"Script error."` or empty;
  - `filename` isn't same-origin under `/js/`;
  - the message starts with `ResizeObserver loop`;
  - for rejections, `reason.name === 'AbortError'`, or `reason` is a
    `TypeError` whose message matches `/fetch|NetworkError|Load failed/i`.
    Offline fetches already show the offline banner.
- Pure helper `buildReport({message, filename, lineno, colno, screen, buildId})`
  returns the request body. It trims `message` to 300 characters and
  reduces `filename` to its path.
- Listeners:
  - `error`: `event.message`, `event.filename`, `event.lineno` and
    `event.colno`.
  - `unhandledrejection`: `reason?.message || String(reason)`, with
    `filename` taken from the first `/js/...js:line:col` match in
    `reason?.stack`. With no match, skip it, because it isn't our script.
- Send with `fetch('/client-errors', {method: 'POST', keepalive: true, credentials: 'same-origin', headers: {'Content-Type': 'application/json', [csrfHeader]: csrfToken}, body})`
  and `.catch(() => {})`. Read the CSRF values from the existing
  `meta[name="_csrf"]` and `meta[name="_csrf_header"]` tags. Don't use
  `apiFetch`, which may be the thing that broke, and which changes the
  offline banner.
- Export `window.flexbuddyErrors = {shouldReport, buildReport}`.

### `templates/shifts.html` and `templates/account.html`

Add
`<script th:src="@{/js/errors.js(v=${buildId})}" defer></script>` as the
**first** script tag: before `toast.js` at shifts.html line 867 and
account.html line 342. No other markup changes.

### `static/sw.js`

Add `'/js/errors.js'` as the first entry of `VERSIONED_ASSETS`.
`StaticAssetsTest.everyPageScriptIsPrecachedByTheServiceWorker` fails if
this is forgotten. The POST isn't intercepted, because the worker only
handles GET and `/share-import`. Nothing goes in `DATA_PATHS` or
`NETWORK_ONLY`.

### `templates/privacy.html`

- After the "Password reset" paragraph in "How the information is used"
  (line 57), add:

  > **Error reports:** when something goes wrong in FlexBuddy, the app
  > emails its developer a short technical report: the kind of error,
  > where in FlexBuddy's code it happened, the app version, and for
  > errors on your device the screen you were on and your account
  > number. Reports never include your shifts, earnings, expenses,
  > password, or email address.

- In "Hosting and sharing", after the Gmail sentence (line 61), add:
  "Error reports are sent the same way, to FlexBuddy's own support
  address."
- Update `Effective October 1, 2026` to the ship date.

## 5. Ripple list

- **Google Play Data safety** (manual, Play Console): browser error
  reports leave the device linked to an account id. Under "App info and
  performance", declare **Crash logs** and **Diagnostics** as collected,
  not shared, required, for "App functionality". Do this when the commit
  ships. Testers see the declaration on the store listing.
- **Gmail sending:** the cap of 20 alerts a day plus password resets
  stays far below Gmail's daily limit of about 500. Alerts and resets
  share the `mailExecutor` pool (2 threads, queue of 50). A reset is never
  blocked for long, because alert sends are capped.
- **Logs:** the two new `log.error` calls in `GlobalExceptionHandler` add
  stack traces to the Render logs for OCR and save failures, which were
  silent before.
- **Existing tests** must not change behaviour: alerts are off in the
  test profile.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format (`BackupShift`, `BackupSettings`, `AccountBackupFile` v4),
  `BlockEvaluationResponse` or `ShiftResponse` changes. There's no new
  account data, so backup, restore and account deletion don't change.
  Alert state holds account ids only in memory, for up to an hour.
- **Service worker:** `errors.js` goes in `VERSIONED_ASSETS`, and the
  build id bump refreshes caches.
- **Dates:** alert emails show UTC with the zone printed. They're for the
  operator and are never shown to drivers.
- **Phone layout:** no visible UI changes.

## 6. Tests to add

**`alert/ErrorAlertServiceTest`** (uses the existing public
`security.MutableClock` and a recording mailer). Each test pins one
behaviour:
- The first report of a fingerprint sends one email.
- The same fingerprint again within 6 hours sends nothing.
- After 6 hours it sends again, and the body says
  `Repeated 2 more times`.
- A different `where` with the same `kind` sends its own email.
- The 21st email in one UTC day isn't sent. After the clock passes UTC
  midnight, a new fingerprint sends.
- `enabled=false` sends nothing.
- `allowBrowserReport` is true 10 times for one account in an hour, false
  the 11th time, and true again 61 minutes later. Another account is
  unaffected.
- After 501 distinct fingerprints, the map holds 500.
- A mailer that throws doesn't make `report` throw.

**`alert/ErrorReportsTest`:**
- An exception `new IllegalStateException("driver@example.com 555-0100")`
  wrapped in a `DataIntegrityViolationException`: neither the email nor
  the number appears anywhere in the report. `details` includes
  `caused by IllegalStateException`.
- Frames are kept only for `com.angel.flexbuddy`, at most 8.
- A non-app logger without a throwable has empty `details`. An app logger
  has its unformatted message template.
- `fromBrowser`:
  - `https://flexbuddy.onrender.com/js/home.js?v=123` becomes
    `/js/home.js`;
  - `https://evil.example/x.js` becomes `page`;
  - an email in the message becomes `[email]`;
  - a 40-character token becomes `[id]`;
  - a 500-character message is cut to 300;
  - `screen=dashboard` becomes `home`, and `screen=weird` becomes
    `other`;
  - `TypeError: x is null` gives kind `TypeError`.

**`alert/ErrorAlertTextTest`:**
- The subjects for a server report and a browser report, each at most 150
  characters.
- The body has `When:` in UTC, `Version:` and `Where:`.
- The `Account: #42` line appears only for browser reports.
- No `Repeated` line appears when the count is 0.

**`alert/ErrorAlertAppenderTest`** (with a mocked service):
- A WARN event isn't reported. An ERROR event is reported once.
- ERROR events from `org.springframework.mail.X` and from
  `com.angel.flexbuddy.mail.X` aren't reported.
- A service whose `report` logs an ERROR through the same root logger is
  called once, not recursively.
- A service that throws `TaskRejectedException` doesn't propagate it.

**`alert/ErrorAlertRegistrationTest`** (`ApplicationContextRunner` with
the registrar, the service and a recording mailer):
- After ready, an ERROR logged with an exception sends one email. Its
  body doesn't contain the exception's message.
- After the context closes, the root logger no longer has the appender.
- With `flexbuddy.alerts.enabled=false`, nothing is attached.

**`mail/MailConfigTest`** (extend):
- With a host set, `errorAlertMailer` is a `SmtpErrorAlertMailer`;
  without one, it's a `LoggingErrorAlertMailer`.
- An SMTP failure logs one WARN containing the class name and not the
  `to` address.

**`controller/ClientErrorControllerTest`** (follow
`PasswordResetControllerTest`'s style):
- A signed-in request with CSRF and a valid body returns 204, and the
  service receives a report whose `accountId` is the user's id.
- A request without CSRF gets 403.
- A signed-out request is redirected to `/login`.
- A 301-character message gets 400.
- When `allowBrowserReport` returns false, the response is still 204 and
  `report` isn't called.

**`controller/ShiftControllerTest`** (extend, or the existing test that
covers OCR failures): an import preview whose extractor throws
`ScreenshotOcrException` still returns 500 with the same body as today.

**`src/test/js/errors.test.js`** (Node). Load the script with
`load-script.js` and an extra `{location: {href: 'https://flexbuddy.onrender.com/', origin: 'https://flexbuddy.onrender.com', search: '', pathname: '/'}}`:
- `shouldReport` is false for:
  - `"Script error."`;
  - an off-origin file;
  - offline;
  - the 4th report;
  - a repeated key;
  - a rejection whose reason is a `TypeError("Failed to fetch")`;
  - an `AbortError`.
- `shouldReport` is true for a
  `TypeError: Cannot read properties of null` in `/js/app.js`.
- `buildReport` cuts the message to 300 characters and reduces the
  filename to `/js/app.js`.

Run all of it:
- `mvn test`: all pass, with nothing skipped except the Postgres test
  when no database is set.
- `node --test "src/test/js/*.test.js"`.

## 7. Manual checks and setup

### Desktop, local

1. Start the app locally with mail configured to your Gmail. Because
   `application-test.properties` isn't used for a normal run, alerts are
   on.
2. Sign in, then temporarily break `home.js`. Locally only, add
   `document.querySelector('#nope').addEventListener('click', () => {})`
   inside `init()` and reload Home. Within a minute, an email arrives:
   - Subject: `[FlexBuddy] Browser error: TypeError in /js/home.js`.
   - Body: `Screen: home`, `Account: #<your id>`, and the line number.
3. Reload 5 times. No more emails arrive, and the Network tab shows the
   POST returning 204 each time, up to 3 per load. Undo the change.
4. Stop Postgres while the app is running and open Home. The server
   errors produce one email for each distinct error, not one per request.
   Restart Postgres.

### Phone width (390px in dev tools, then a real Android phone in the Play app)

5. Home, Reports, Schedule, Import and Expenses work exactly as before,
   with no layout change.
6. Turn on airplane mode and switch between screens. No report is sent, and no
   email arrives about "Failed to fetch".

### One-time setup, outside the commit

7. **Uptime:** create a free UptimeRobot account (or similar). Add an
   HTTPS monitor for `https://flexbuddy.onrender.com/actuator/health`
   with a 5-minute interval and email alerts to
   `flexbuddysupport@gmail.com`. It should show "Up" within 5 minutes.
   This needs `plan/mail-health-check` shipped, or a Gmail problem shows
   as downtime.
8. **Render:** under Account Settings → Notifications, make sure email
   notifications are on for failed deploys and for service events.
9. **Play Console:** update Data safety (section 5).

## 8. Commit message

```
feat(alerts): email the operator when the server or a page errors

Nothing reported failures: a server error left a stack trace in the
Render logs and a script crash on a phone left nothing at all. Every
ERROR the server logs now goes to an alert service through a Logback
appender, which catches failed requests, scheduled jobs and async
work in one place. The app pages also report uncaught script errors
to a new endpoint. The service groups errors by kind and place and
emails the support address through the existing Gmail setup, at most
once every six hours per error and twenty times a day, with a count
of the repeats in between.

Reports carry class names, code locations, the app version and, for
page errors, the screen and the account number. They never include
exception messages, request data or anything a driver entered, and
page messages are scrubbed of emails and long tokens before they are
sent. The screenshot and save failures that answered 500 without a
log line now log one, so they are reported too. The privacy policy
describes the reports.
```

## 9. Open questions

1. **Recipient:** alerts go to `flexbuddysupport@gmail.com`, the same
   address that sends them, by default. Set `FLEXBUDDY_ALERT_TO` in
   Render if you'd rather get them elsewhere.
2. ~~Account number in browser reports?~~ **Decided:** keep it. The
   `Account: #<id>` line and the privacy wording stay as planned.
3. **Quiet period and daily cap:** 6 hours and 20 a day are my defaults.
   Both are properties and can be changed without a code change.
4. **Sentry instead:** if you later want grouped stack traces with full
   history, Sentry's free tier is the next step. It needs a new account,
   a privacy policy change naming Sentry, and a Data safety entry for
   sharing.
