# Sign-in limits

Planned against `origin/main` at `32feb25` (latest migration V20). **No migration.** This
ships **before** `plans/password-reset.md`, which reuses the limiter built here.

## 1. Goal and scope

Today nothing limits failed sign-ins: anyone can keep guessing a driver's password
indefinitely. This plan adds three limits.

| Limit | Rule | What happens |
|---|---|---|
| **Per account** (the main protection) | 5 failed sign-ins for one email within 15 minutes | That email is locked for 15 minutes |
| **Per connection** | 20 failed sign-ins from one IP address within 15 minutes, across any emails | That address is locked for 15 minutes |
| **Account creation** | 10 sign-up attempts from one IP address within 1 hour | Further sign-ups from it are refused for the rest of the hour |

**While a lock is on:**
- **Sign-in is refused even with the right password.** The password isn't checked at all.
  Otherwise the lock would tell a guesser when they'd found it.
- The sign-in page says: "Too many sign-in attempts. Wait 15 minutes, then try again."
- **The message is the same whether or not the email has an account.** Unknown emails
  count and lock exactly like real ones.

**Unaffected:**
- **A successful sign-in clears that email's failures.**
- **Remember-me sign-ins** (the cookie that keeps the Play app signed in) aren't affected.
- **Signed-in use of the app** isn't affected. This is only about the sign-in and sign-up
  forms.

### Known trade-offs (accepted for now)

- **Someone can lock a driver out for 15 minutes** by failing 5 times with their email.
  That's the usual cost of per-account limits. It's short, and once
  `plans/password-reset.md` ships, a password reset clears the lock.
- **The counts are kept in memory,** not the database. The app runs as a single Render
  instance, so that's enough, but a restart or deploy clears them. If FlexBuddy ever runs
  on more than one instance, the counts move to the database. That would be its own plan.
- **The per-connection limit can be dodged.** The address comes from the
  `X-Forwarded-For` header through `server.forward-headers-strategy=framework`, which a
  determined attacker can fake. That's why the **per-account limit is the real
  protection**; the per-connection one only catches careless bulk guessing.

### Out of scope

- Password reset (the next plan).
- CAPTCHAs.
- Email alerts about failed sign-ins.
- Two-factor sign-in.
- Limits on signed-in API calls.

## 2. Data model and migration

None.

## 3. Backend

### New `security/AttemptLimiter.java`

A `@Component` that keeps counts in memory. It uses the existing `Clock` bean, so tests
can move time.

```java
public record Policy(int maxAttempts, Duration window, Duration lockFor) {}

public boolean isLocked(String bucket, String key);
public void record(String bucket, String key);                    // records one attempt; locks when the window holds maxAttempts
public void clear(String bucket, String key);
public Optional<Duration> lockedFor(String bucket, String key);   // time left, for logs and tests
```

**Buckets and their policies** come from configuration (below):

| Bucket | Key | Policy |
|---|---|---|
| `login-email` | email, trimmed and lower-cased | 5 / 15 min, locked 15 min |
| `login-ip` | IP address | 20 / 15 min, locked 15 min |
| `register-ip` | IP address | 10 / 1 hour, locked for what's left of that hour |

`plans/password-reset.md` adds two more buckets.

**How it works:**

- Each `bucket|key` pair holds the times of recent attempts and an optional
  "locked until" time, in a `ConcurrentHashMap`.
- `record` drops attempts older than the window, adds the new one, and sets the lock when
  the count reaches `maxAttempts`.
- **Memory cap:** at most 50,000 keys. When full, drop the entries with the oldest last
  attempt first, so a flood of made-up emails can't use up memory.
- `@Scheduled(fixedDelay = 10 minutes)` `sweep()` removes entries whose window and lock
  have both passed. Scheduling is already enabled in `SchedulingConfig`.
- Changes for one key are atomic (`compute`), so two requests at once can't both slip
  under the limit.

### Configuration (`application.properties`)

```properties
flexbuddy.security.login.max-failures-per-email=5
flexbuddy.security.login.max-failures-per-ip=20
flexbuddy.security.login.window=15m
flexbuddy.security.login.lock=15m
flexbuddy.security.registration.max-per-ip=10
flexbuddy.security.registration.window=1h
```

Bind them with a `@ConfigurationProperties("flexbuddy.security")` record,
`SecurityLimitsProperties`.

### Sign-in

**New `security/LoginAttemptFilter.java`** (a `OncePerRequestFilter`). It runs **only for
`POST /login`**:

- Read `username` with `trim().toLowerCase(Locale.ROOT)`, and the address with
  `request.getRemoteAddr()`, which the forward-headers setting fills from Render's proxy
  header.
- If `login-email` or `login-ip` is locked for them, **redirect to `/login?locked`**
  straight away, without passing the request on. The password is never checked.

**Recording failures and successes:**

- **Failures:** a small `@Component LoginAttemptEvents` with
  `@EventListener AuthenticationFailureBadCredentialsEvent`. It records one attempt in both
  `login-email` (from `event.getAuthentication().getName()`) and `login-ip` (from
  `WebAuthenticationDetails.getRemoteAddress()`).
  - Spring Security turns "no such account" into the same bad-credentials failure, so
    unknown emails are counted too.
- **Success:** `@EventListener InteractiveAuthenticationSuccessEvent` clears that email's
  `login-email` bucket. The IP bucket isn't cleared, so one good account can't wipe a
  guessing run's history.
- **Choosing the redirect:** a `LockAwareFailureHandler` (extending
  `SimpleUrlAuthenticationFailureHandler`) redirects to `/login?locked` if the attempt that
  just failed caused a lock, and to `/login?error` otherwise. The failure event is
  published before the handler runs, so the count is already up to date.

**`SecurityConfig`:**

- `http.addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)`.
- In `formLogin`, add `.failureHandler(lockAwareFailureHandler)`.
- **Keep** `.loginPage("/login")`, `.defaultSuccessUrl("/", true)` and `.permitAll()` as
  they are.
- The remember-me configuration doesn't change.

### Account creation (`AccountController.register`, `POST /register`)

**First,** check `limiter.isLocked("register-ip", ip)`. If it's locked:
- add a global error to the form: "Too many sign-ups from this connection. Try again in an
  hour.";
- return `"register"` with **status 429**. Set it on the response; the form still shows.

**Otherwise,** `limiter.record("register-ip", ip)`, then continue as today. Every attempt
counts, including ones that fail validation, so the form can't be used to test emails
quickly.

**Constructor:** `AccountController` gets `AttemptLimiter`. **`AccountControllerTest`** is a
`@WebMvcTest`, so it needs `@MockitoBean AttemptLimiter`, with `isLocked` returning `false`
by default.

## 4. Frontend

### `templates/login.html`

Next to the existing `param.error`, `param.expired` and similar notices, add:

```html
<div class="notice error-notice auth-notice" th:if="${param.locked}" role="alert">
  Too many sign-in attempts. Wait 15 minutes, then try again.
</div>
```

Read the 15 from configuration (`${@securityLimitsProperties.login().lock().toMinutes()}`)
so the text and the rule can't disagree. `plans/password-reset.md` adds "or reset your
password" to this message.

### `templates/register.html`

**Add** a global-error notice above the form. Today the page only shows field errors
(lines 42, 47 and 53, at `32feb25`), so without this the limit message wouldn't appear:

```html
<div class="notice error-notice auth-notice" role="alert" th:if="${#fields.hasGlobalErrors()}" th:errors="*{global}"></div>
```

There's no JavaScript change, no CSS change (the existing `.auth-notice` style is reused),
and no service-worker change: `/login` and `/register` aren't cached.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format (v4), `BlockEvaluationResponse`, `ShiftResponse` | **Not touched.** |
| New account data | None. Nothing is stored. Counts are in memory and hold no passwords. |
| `sw.js` | No change. |
| Boxed request fields | Not applicable. |
| Phone layout | One notice, reusing the existing `.auth-notice` style. |
| Dates | Durations only, from the `Clock` bean. |
| Money and tax wording | Not applicable. |

**Existing code that changes:**

| File | Change |
|---|---|
| `SecurityConfig` | Filter before `UsernamePasswordAuthenticationFilter`; `failureHandler` |
| `AccountController` | Constructor + the `register` check |
| `login.html` | `param.locked` notice |
| `register.html` | Global-error notice (new) |
| `application.properties` | 6 properties |

**New:** `AttemptLimiter`, `SecurityLimitsProperties`, `LoginAttemptFilter`,
`LoginAttemptEvents`, `LockAwareFailureHandler`.

**Existing tests that change:**

- **`AccountControllerTest`:** `@MockitoBean AttemptLimiter`. Every existing test keeps
  passing, because `isLocked` returns `false`.
- **`PersistentSignInIntegrationTest`:** the counts are in memory and shared across the
  Spring test context, so **clear the limiter in its `@BeforeEach`**. Otherwise failures
  recorded by one test can lock another.

## 6. Tests to add

### New `security/AttemptLimiterTest.java` (plain JUnit with a mutable test `Clock`)

1. `fourFailuresStayUnlockedAndTheFifthLocks`: policy 5 / 15 min.
2. `failuresOutsideTheWindowDontCount`: 4 failures, then 16 minutes pass, then 1 more
   failure: still unlocked.
3. `theLockLastsItsDurationThenLifts`: locked at 0:00, still locked at 0:14:59, unlocked at
   0:15:00. `lockedFor` reports the time left.
4. `clearRemovesFailuresAndLock`
5. `bucketsAndKeysAreIndependent`: `login-email|a` locked doesn't affect
   `login-email|b` or `login-ip|a`.
6. `sweepRemovesExpiredEntries`: the map size drops to 0 after the window and lock pass.
7. `theKeyCountIsCapped`: with a cap of 3 for the test, a 4th key drops the oldest one.
8. `concurrentFailuresNeverExceedTheLimitUnnoticed`: 10 threads each record once for the
   same key with a max of 5. It ends locked, with no exception.

### New `LoginLimitIntegrationTest.java`

Set up like `PersistentSignInIntegrationTest`: `@SpringBootTest`, `@AutoConfigureMockMvc`,
`@ActiveProfiles("test")`, with a `@TestConfiguration` providing a `@Primary` mutable
`Clock`. One real user, with the correct password `right-password-1`.

9. `fiveWrongPasswordsLockTheAccountEvenAgainstTheRightPassword`:
   - 5 wrong `POST /login`s redirect to `/login?error` four times, and to `/login?locked`
     on the 5th;
   - a 6th with the **right** password redirects to `/login?locked`, and no session is
     authenticated.
10. `theLockLiftsAfter15Minutes`: move the clock 15 minutes, and the right password then
    signs in.
11. `aSuccessfulSignInClearsEarlierFailures`: 4 wrong, 1 right, then 4 wrong again: still
    `?error`, not locked.
12. `unknownEmailsLockTheSameWay`: 5 failures for `nobody@example.com` give `?locked` on
    the 5th. Same message, same timing.
13. `oneConnectionGuessingManyEmailsIsLocked`: 20 failures across 20 different emails from
    `remoteAddr 203.0.113.9` lock that address. A 21st attempt for a **real** email with
    the right password, from that address, gives `?locked`. From `203.0.113.10` it signs
    in.
14. `rememberMeStillWorksForALockedEmail`: lock the email, then a request carrying a valid
    remember-me cookie (built as in `PersistentSignInIntegrationTest`) is still
    authenticated.
15. `registrationIsLimitedPerConnection`: 10 `POST /register`s from one address are
    accepted or validated as usual. The 11th returns 429 with "Too many sign-ups from this
    connection".

### `controller/AccountControllerTest.java`

16. `registerShowsTheLimitMessageWhenLocked`: with `isLocked("register-ip", any)` stubbed to
    `true`, the response is 429, the body contains the message, and
    `accountService.register` is never called.

That's about 16 new tests. Every existing test should still pass.

## 7. Manual checks

Do these on a local run, then once on Render after deploying.

1. **Wrong password 5 times.**
   - Attempts 1–4 show the usual "wrong email or password" notice. The 5th shows "Too many
     sign-in attempts. Wait 15 minutes, then try again."
   - The right password is then refused with the same message.
   - After 15 minutes it works.
2. **A made-up email** behaves identically: same messages, same number of attempts.
3. **The Play app.** A phone that's already signed in through remember-me stays signed in
   and keeps working while its email is locked from another device.
4. **Sign-up.** 11 quick sign-up attempts from one browser: the 11th shows "Too many
   sign-ups from this connection…".
5. **On Render:**
   - Two different networks, for example phone data and home Wi-Fi, have **separate**
     per-connection counts. That shows `getRemoteAddr()` is the visitor's address, not
     Render's proxy.
   - If both share one count, stop and report it. The proxy header setting needs a look
     before this plan's per-connection limit is trustworthy.
6. **Deploy resets the counts.** Lock an email, deploy (or restart the service), and the
   lock is gone. That's expected and documented.
7. **Phone layout.** At 375px the lock notice fits and wraps, with no horizontal scroll.

## 8. Suggested commit message

```
feat(auth): limit failed sign-ins and sign-ups

Five wrong passwords for one email within fifteen minutes now lock
that email for fifteen minutes, and twenty failures from one
connection lock that connection, so a password can no longer be
guessed by trying again and again. While locked, sign-in is refused
even with the right password, and the message is the same whether or
not the email has an account. A successful sign-in clears the count,
and phones that stay signed in are not affected.

Sign-ups are limited to ten an hour from one connection. The counts
are kept in memory, which suits the single server the app runs on;
they reset when it restarts.
```

## 9. Decisions

All questions are settled. Nothing is left open.

- **The limits as written:** 5 failed sign-ins per email and 20 per connection within 15
  minutes, each giving a 15-minute lock.
- **Sign-ups:** 10 an hour per connection is enough, even for drivers signing up together
  on shared station Wi-Fi.
