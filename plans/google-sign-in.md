# Plan: sign in and sign up with a Google account

Planned against `origin/main` at 5de206b.

**Ship after `plan/email-verification` and `plan/two-factor`.** This plan
builds on:
- the `emailVerified` flag;
- `EmailCodeService` and the `email_code` purposes;
- `VerifiedLoginSuccessHandler`, with its two-step branch and the
  pending-session pattern;
- `SignInCompleter`;
- `deleteAccountData`.

Decided: a Google sign-in whose address matches an existing FlexBuddy
account **links to it automatically**.

## Where things stand (after the two earlier plans)

- **Only email and password exist.** Sign-up needs an emailed code, and
  two-step can be on.
- **Every controller uses the email as the user's name.** It reads
  `principal.getName()` and calls `findByEmailIgnoreCase(...)`, so a
  Google sign-in must produce a principal whose name is the FlexBuddy
  email, not Google's numeric ID.
- **`AppUser.passwordHash` is `nullable = false`.**
  - **Account deletion** requires the current password
    (`AccountDeletionRequest`, `@NotBlank`), and so does turning
    two-step off. An account created through Google has no password the
    driver knows.
- **Remember-me:** the `remember-me` checkbox is read from the sign-in
  request. With Google, the request that finishes sign-in is Google's
  callback, which doesn't carry it.
- **No OAuth library** is on the classpath yet.

## 1. Goal and out-of-scope

**Goal:**
- **Buttons:** the Sign in and Create account pages show
  **Continue with Google** when Google sign-in is configured. Without
  the two environment variables, the button is hidden and nothing else
  changes.
- **First time:** a new Google user gets an account created from the
  Google name and email, already verified, with no password, and lands
  on Home with the setup card.
- **Existing account with the same address:** Google is linked to it,
  and the driver lands in their existing data.
- **Squatted address:** if that existing account was an *unverified*
  sign-up, its password is wiped first, so someone who registered the
  address without owning it can't get in afterwards.
- **Keep me signed in** works with Google (ticked automatically in the
  Play app).
- **Two-step:** a driver who has it on is still asked for the code after
  Google (see open question 1).
- **Accounts without a password:**
  - they can add one through "Forgot password?";
  - they can delete the account with an emailed code instead of a
    password;
  - they can turn two-step off with a code alone.
- **Account → Account & privacy** shows a **Sign-in methods** card:
  Password (set or not set) and Google (linked or not). Google can be
  unlinked only when a password exists.

**Out of scope:**
- Apple and Microsoft sign-in.
- Google One Tap.
- Using any Google API beyond sign-in (no Drive or Calendar).
- Changing the FlexBuddy email when the Google address changes.
- Sign in with Google inside a native Android build. It works through
  the existing Trusted Web Activity, which runs in Chrome.

## 2. Data model and migration

New `src/main/resources/db/migration/V26__google_sign_in.sql`:

```sql
-- The Google account linked to a FlexBuddy account (its stable "sub" ID), and whether the driver has a password they
-- know. Accounts created with Google get an unusable random password hash and password_set = false.
alter table app_users add column google_subject varchar(255);
alter table app_users add constraint uk_app_users_google_subject unique (google_subject);
alter table app_users add column password_set boolean not null default true;

-- Emailed codes can confirm an account deletion for drivers without a password.
alter table email_code drop constraint chk_email_code_purpose;
alter table email_code add constraint chk_email_code_purpose
    check (purpose in ('VERIFY_EMAIL', 'SIGN_IN', 'CONFIRM_DELETE'));
```

### `model/AppUser.java`

Add:

```java
/** Google's stable account ID once Google sign-in is linked; null otherwise. */
@Column(length = 255, unique = true)
private String googleSubject;

/** False for an account created with Google until the driver chooses a password. */
@Column(nullable = false)
private boolean passwordSet = true;
```

`passwordSet = true` in the entity keeps every test fixture and existing
path unchanged.

`model/EmailCodePurpose`: add `CONFIRM_DELETE`.

### Backup, restore and deletion

- `google_subject` and `password_set` are sign-in details and are **not**
  in backups. The format stays version 4, and `BackupSettings` is
  unchanged.
- A restore never changes them.
- Deletion removes the row. There's nothing extra to clean up, and
  `deleteAccountData` is unchanged.

## 3. Backend files

### `pom.xml`

Add `spring-boot-starter-oauth2-client`, with no version, managed by the
Spring Boot 4.1.1 parent.

### `config/GoogleSignInConfig.java` (new)

Don't put the registration in `application.properties`. Spring Boot
refuses to start with an empty `client-id`. Build it only when
configured:

```java
@Configuration
public class GoogleSignInConfig {
    @Bean
    @ConditionalOnExpression("!'${flexbuddy.google.client-id:}'.isBlank()")
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${flexbuddy.google.client-id}") String clientId,
            @Value("${flexbuddy.google.client-secret}") String clientSecret) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId).clientSecret(clientSecret)
                .scope("openid", "email", "profile")
                .build());
    }
}
```

The redirect URI is Spring's default:
`{baseUrl}/login/oauth2/code/google`. `server.forward-headers-strategy=framework`
makes it `https://flexbuddy.onrender.com/login/oauth2/code/google`
behind Render.

### `security/GoogleAccountService.java` (new; extends `OidcUserService`)

`loadUser(OidcUserRequest)`:
1. Call `super.loadUser(request)`.
2. If `email_verified` isn't true, throw
   `OAuth2AuthenticationException(new OAuth2Error("unverified_email"))`.
3. Let `sub = oidcUser.getSubject()` and
   `email = oidcUser.getEmail().trim().toLowerCase(Locale.ROOT)`.
4. **Find the account,** in this order:
   1. `userRepository.findByGoogleSubject(sub)`. This is a new
      repository method.
   2. Otherwise `findByEmailIgnoreCase(email)`. If found, and its
      `googleSubject` is set and isn't `sub`, throw
      `OAuth2Error("linked_elsewhere")`. Otherwise link it: set
      `googleSubject = sub`.
      - **If the account was unverified:** set
        `passwordHash = encoder.encode(randomUnusable())`,
        `passwordSet = false` and `emailVerified = true`. The squatter's
        password stops working.
   3. Otherwise, **create** it:
      - `displayName`: Google `given_name`, falling back to `name`, then
        the part of the email before `@`, cut to 80 characters;
      - the email;
      - `passwordHash = encoder.encode(randomUnusable())`,
        `passwordSet = false`, `emailVerified = true`,
        `googleSubject = sub`.
      - Record `AttemptLimiter.REGISTER_IP` for the request's address
        (`RequestContextHolder`). If that bucket is locked, throw
        `OAuth2Error("too_many_signups")`.
5. Save, then return
   `new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), oidcUser.getIdToken(), oidcUser.getUserInfo(), "email")`.

   The name attribute `"email"` makes `getName()` return the email that
   every controller expects.

`randomUnusable()` is 32 bytes from `SecureRandom`, Base64-encoded. It's
never stored or shown in plain form.

### `security/GoogleRememberChoice.java` (new)

This is an `OAuth2AuthorizationRequestResolver` wrapping
`DefaultOAuth2AuthorizationRequestResolver`. When resolving, it reads the
`remember` query parameter from `/oauth2/authorization/google?remember=1`
and stores `GOOGLE_REMEMBER = true` in the session before redirecting to
Google.

### `security/VerifiedLoginSuccessHandler.java` (extend)

- Read the remember choice from either the `remember-me` parameter (form
  sign-in) or the `GOOGLE_REMEMBER` session attribute, which it then
  removes.
- Every path is unchanged, including the unverified one, which never
  applies to Google, and the two-step pending path, which stores
  `PENDING_REMEMBER` from that choice.
- **The normal path:** form sign-in already issued remember-me through
  the filter. For a Google sign-in with remember chosen, call
  `rememberMeServices.loginSuccess(new RememberMeRequest(request), response, authentication)`.
  `RememberMeRequest` is from the verification plan.

  `RotationTolerantRememberMeServices` stores the token under
  `authentication.getName()`, the email, so remember-me auto-login later
  loads the user through the normal `userDetailsService`.

### `security/GoogleFailureHandler.java` (new)

Maps the `OAuth2Error` code to a login-page notice through a query
parameter:

| Error | Redirect |
| --- | --- |
| `unverified_email` | `/login?google=unverified` |
| `linked_elsewhere` | `/login?google=linked` |
| `too_many_signups` | `/login?google=limit` |
| anything else, including the driver cancelling on Google | `/login?google=failed` |

### `config/SecurityConfig.java`

- Inject `ObjectProvider<ClientRegistrationRepository>`.
- When it's available:

  ```java
  http.oauth2Login(oauth -> oauth
          .loginPage("/login")
          .authorizationEndpoint(a -> a.authorizationRequestResolver(googleRememberChoice))
          .userInfoEndpoint(u -> u.oidcUserService(googleAccountService))
          .successHandler(verifiedLoginSuccessHandler)
          .failureHandler(googleFailureHandler));
  ```

- `/oauth2/authorization/**` and `/login/oauth2/code/**` are permitted by
  the OAuth2 filters themselves. No `permitAll` entries are needed.

### `controller/AccountController.java`

- **Sign-in and sign-up pages:** `loginPage` and `registrationPage` add
  `googleEnabled` (whether the `ClientRegistrationRepository` bean
  exists).
- **Account page:** `addAccountPageModel` adds `passwordSet` and
  `googleLinked`.
- **`POST /account/google/unlink`:** allowed only when `passwordSet`.
  Clears `googleSubject`, then redirects to `/account#account` with a
  notice.
- **Deleting a password-less account:**
  - `AccountDeletionRequest.password` loses `@NotBlank` and gains an
    optional `code` field (`@Size(max = 7)`).
  - In `deleteAccount`:
    - **Password set:** require the password, as now. Keep the message
      "Enter your password." when it's blank.
    - **No password:** require `code`, checked with
      `emailCodes.check(user, CONFIRM_DELETE, code)`.
  - `AccountService.deleteAccount(email, password)` gains an overload
    `deleteAccountConfirmed(email)`, which skips the password check.
    Only the controller calls it, after the code check passes.
  - Add `POST /account/delete-code`, which sends the `CONFIRM_DELETE`
    code and redirects to `/account?deleteCode#account`.

### `service/TwoFactorService.java` (from `plan/two-factor`)

`disable(user, password, code)`: when `!user.isPasswordSet()`, skip the
password check and require only the code.

### `service/PasswordResetService.java`

In `resetPassword`, also set `user.setPasswordSet(true)`. "Forgot
password?" is how a Google-only driver adds a password.

### `src/main/resources/application.properties`

Add:

```
# Google sign-in turns on only when both are set (Google Cloud console → APIs & Services → Credentials).
flexbuddy.google.client-id=${FLEXBUDDY_GOOGLE_CLIENT_ID:}
flexbuddy.google.client-secret=${FLEXBUDDY_GOOGLE_CLIENT_SECRET:}
```

### `render.yaml`

Add under `envVars`:

```yaml
      - key: FLEXBUDDY_GOOGLE_CLIENT_ID
        sync: false
      - key: FLEXBUDDY_GOOGLE_CLIENT_SECRET
        sync: false
```

## 4. Frontend files

### `templates/login.html` and `templates/register.html`

After the form, inside the card, add:

```html
<div class="auth-divider" th:if="${googleEnabled}"><span>or</span></div>
<a class="secondary-button auth-google" th:if="${googleEnabled}" id="googleSignIn" href="/oauth2/authorization/google">
    <svg viewBox="0 0 24 24" aria-hidden="true"><!-- Google "G" mark, four-colour, per Google's branding guidelines --></svg>
    <span>Continue with Google</span>
</a>
```

- Use the official Google "G" logo SVG, unmodified, from Google's
  sign-in branding guidelines
  (developers.google.com/identity/branding-guidelines). Download the
  asset pack and paste the four-colour `G` paths in place of the
  comment. The label text is **Continue with Google**. Don't restyle the
  logo.
- **`login.html`:** extend the inline script at line 93 so that a click
  on `#googleSignIn` appends `?remember=1` when `#rememberMe` is
  checked. `#rememberMe` is already ticked automatically in the Play
  app.
- **`register.html`:** the same, with remember when
  `app-display-mode` is standalone (from the verification plan).
- **`login.html` notices** for `param.google`:
  - `unverified`: "Your Google account's email isn't verified. Verify it
    with Google or sign up with email."
  - `linked`: "That Google account is linked to a different FlexBuddy
    account."
  - `limit`: "Too many sign-ups from this connection. Try again in an
    hour."
  - `failed`: "Google sign-in didn't finish. Try again."

### `templates/account.html` (Account & privacy, before "Two-step sign-in")

```html
<article class="panel data-card">
    <h3>Sign-in methods</h3>
    <ul class="signin-methods">
        <li><span>Password</span><b th:text="${passwordSet} ? 'Set' : 'Not set'">Set</b></li>
        <li><span>Google</span><b th:text="${googleLinked} ? 'Linked' : 'Not linked'">Not linked</b></li>
    </ul>
    <p th:unless="${passwordSet}">To add a password, use <a href="/forgot-password">Forgot password?</a> on the sign-in page with your email.</p>
    <form th:if="${googleLinked and passwordSet}" th:action="@{/account/google/unlink}" method="post">
        <button class="text-button" type="submit">Unlink Google</button>
    </form>
</article>
```

**Deletion form** (line 318 onwards): wrap the password label in
`th:if="${passwordSet}"`. Add a `th:unless` block with:
- a `Send me a code` form posting to `/account/delete-code`;
- a `code` input (`autocomplete="one-time-code"`, `class="code-input"`);
- the notice "We emailed a code to confirm." when
  `param.deleteCode` is present.

### `static/css/styles.css`

```css
.auth-divider { margin: 16px 0; display: flex; align-items: center; gap: 12px; color: var(--muted); font-size: 13px; }
.auth-divider::before, .auth-divider::after { content: ""; flex: 1; border-top: 1px solid var(--line-soft); }
.auth-google { width: 100%; min-height: 47px; display: flex; align-items: center; justify-content: center; gap: 10px; }
.auth-google svg { width: 20px; height: 20px; }
.signin-methods { margin: 0 0 12px; padding: 0; list-style: none; }
.signin-methods li { min-height: 44px; display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--line-soft); }
```

### `static/sw.js`

Add `/^\/oauth2\//` and `/^\/login\/oauth2\//` to `NETWORK_ONLY`.
They're navigations anyway, but this makes sure they're never cached.

### `templates/privacy.html`

- In "Information you provide", add: "**Google sign-in (optional):** if
  you continue with Google, Google shares your name, email address and a
  Google account ID with FlexBuddy. FlexBuddy stores the ID to recognise
  you next time and never receives your Google password or access to
  other Google data."
- In "Hosting and sharing", note that Google processes the sign-in.
- Update the effective date.

## 5. Ripple list

- **Shared records:** `AccountSettingsResponse`, `BackupShift`,
  `BackupSettings`, `AccountBackupFile` (v4), `BlockEvaluationResponse`
  and `ShiftResponse` are unchanged.
- **New account data** (`google_subject`, `password_set`): not in backups
  (see section 2), and deleted with the account. A
  `CONFIRM_DELETE` code is temporary.
- **Principal name:** stays the email for Google sign-ins, through the
  `"email"` name attribute. Every controller is untouched.
- **Remember-me:** issued by email, so auto-login is unchanged. "Sign out
  everywhere" covers Google sessions too.
- **Sign-in limits:** a Google sign-in never touches `login-email`. New
  accounts through Google count toward `register-ip`.
- **Email verification:** Google accounts are created verified. A pending
  unverified sign-up for the same address is taken over, with its
  password wiped.
- **Two-step:** still asked after Google, if on. The pending-session path
  is reused.
- **Play app:** Google's sign-in page opens in the Trusted Web Activity,
  which is Chrome. Google allows that; it blocks only embedded WebViews.
- **iPhone home-screen app:** a known risk. iOS may finish the Google
  sign-in in a Safari sheet whose cookies the home-screen app doesn't
  share. Step 11 checks this (see open question 2).
- **Google Play Data safety:** no change. Name and email are already
  declared, and the Google ID is an account identifier under the same
  "Account info" entry.
- **Phone layout:**
  - the Google button is full width and 47px tall;
  - the divider is text only;
  - the sign-in-methods rows are 44px;
  - there's no page widening.

## 6. Tests to add

**`security/GoogleAccountServiceTest`** (unit; stub the parent's
`super.loadUser` by passing a fake `OidcUser` through a package-private
`link(OidcUser, String ip)` method that `loadUser` delegates to):
- **New address:** creates a user with `emailVerified = true`,
  `passwordSet = false`, the `googleSubject` set, and the given name
  as `displayName`. The returned principal's `getName()` is the
  lowercase email.
- **Existing verified account:** linked. Its password hash is
  unchanged, and `passwordSet` stays true.
- **Existing unverified account:** linked and verified, and its
  password hash *changed*. The old password no longer matches
  (`passwordEncoder.matches` is false).
- **`email_verified = false`:** throws with code `unverified_email`.
- **Subject mismatch:** an account whose `googleSubject` is a different
  `sub` throws `linked_elsewhere`.
- **Found by subject, different email:** an account found by
  `googleSubject` with a different email at Google signs into the
  stored account, and doesn't change its email.
- **Sign-up limit:** with `register-ip` locked, a *new* account throws
  `too_many_signups`, and an existing one still links.

**`GoogleSignInIntegrationTest`** (new; `@SpringBootTest` with
`flexbuddy.google.client-id=test` and `client-secret=test`):
- `/login` shows `Continue with Google`, linking to
  `/oauth2/authorization/google`.
- `GET /oauth2/authorization/google?remember=1` redirects to
  `accounts.google.com`, and the session holds `GOOGLE_REMEMBER`.
- Calling `VerifiedLoginSuccessHandler` with an `OAuth2AuthenticationToken`
  named with the email and `GOOGLE_REMEMBER` in the session sets the
  remember-me cookie and redirects to `/`. With two-step on, it
  redirects to `/sign-in/code` instead and sets no cookie.

**`AccountControllerTest`:**
- Without the client-id property, `/login` has no `googleSignIn`.
- **Deleting a password-less account:** with a valid `CONFIRM_DELETE`
  code it succeeds. With no code it shows "Enter the code we emailed
  you." With a wrong code it shows "That code isn't right."
- **Deleting an account with a password** works exactly as before. The
  existing tests pass unchanged.
- **Unlinking:** `POST /account/google/unlink` is rejected when
  `passwordSet` is false, and clears `googleSubject` when it's true.

**`PasswordResetServiceTest`:** a reset sets `passwordSet = true`.

**`TwoFactorServiceTest`:** `disable` for a password-less account accepts
a code alone.

**`PostgresMigrationTest`:** the legacy user has `password_set = true`
and a null `google_subject`.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### One-time setup (outside the commit)

1. **Google Cloud console:** create a project "FlexBuddy".
2. **OAuth consent screen:**
   - External, app name FlexBuddy;
   - support email `flexbuddysupport@gmail.com`;
   - app domain `flexbuddy.onrender.com`, with the privacy policy URL
     `/privacy` and the terms URL `/terms`;
   - scopes: `openid`, `email` and `profile` only. These are
     non-sensitive and need no Google review.
   - **Publish to "In production".** In "Testing", only listed test
     users can sign in.
3. **Credentials:** create an OAuth client of type **Web application**.
   Its authorised redirect URIs are
   `https://flexbuddy.onrender.com/login/oauth2/code/google` and
   `http://localhost:8080/login/oauth2/code/google`.
4. **Render:** set `FLEXBUDDY_GOOGLE_CLIENT_ID` and
   `FLEXBUDDY_GOOGLE_CLIENT_SECRET`. Never paste them into chat or the
   repo.

### Desktop (1280×800)

5. Sign in shows **or** and **Continue with Google**, with the official G
   logo, full width. Create account shows the same button.
6. **New Google account:** sign up with a Google account that has never
   used FlexBuddy. You land on Home with the setup card, and the greeting
   uses your Google first name. Account → Sign-in methods shows "Password:
   Not set" and "Google: Linked".
7. **Linking:** sign out. Sign in with Google using the address of an
   existing password account. You land in that account's existing data.
   Sign-in methods shows both set and linked, and the password still works
   on its own.
8. **Keep me signed in:** tick it and sign in with Google. Close and
   reopen the browser: you're still signed in.
9. **Two-step:** with two-step on, Google sign-in leads to **Enter your
   code**.
10. **Password-less account:**
    - **Forgot password?** with its address sends a reset link. After
      setting a password, Sign-in methods shows "Password: Set", and
      **Unlink Google** appears.
    - **Deleting it before setting a password:** Account → Delete account
      shows **Send me a code** instead of the password field. The emailed
      code plus "delete" deletes the account.

### Phone (375×667 and 430×932, then real devices)

11. **iPhone, Safari:** Continue with Google, choose an account, and you
    return to FlexBuddy signed in. **iPhone, home-screen app:** do the
    same. If you end up signed in inside a Safari sheet but not in the
    app, record it under open question 2.
12. **Android, Play app:** Google's account chooser appears, and you
    return into the app signed in. Close and reopen the app: still
    signed in.
13. The button and divider fit at 375px with no sideways scroll, and the
    Sign-in methods rows are 44px tall.
14. **Cancelling** on Google's screen brings you back to Sign in with
    "Google sign-in didn't finish. Try again."

## 8. Commit message

```
feat(auth): let drivers sign in and sign up with google

Drivers can now continue with Google from the sign-in and sign-up
pages. A first sign-in creates an account from the Google name and
address, already confirmed and without a password, and a sign-in
whose address matches an existing account links Google to it. If that
account was an unconfirmed sign-up its password is wiped first, so
whoever registered an address they did not own cannot get in. Keep me
signed in carries through Google, and two-step sign-in still asks for
its code.

Accounts without a password can add one through the existing reset
email, confirm a deletion with an emailed code and turn two-step off
with a code alone. Account shows which sign-in methods are set up and
can unlink Google once a password exists. Google sign-in only turns on
when its client ID and secret are set in the environment.
```

## 9. Open questions

1. ~~Two-step after Google?~~ **Decided:** still ask for the FlexBuddy
   code after a Google sign-in when the driver has two-step on, as
   planned.
2. ~~The iPhone home-screen app?~~ **Decided:** run manual check 11. If
   the sign-in finishes in Safari instead of the app, plan the follow-up
   that hides the Google button when `navigator.standalone` is true and
   shows "Use your password here, or open FlexBuddy in Safari to use
   Google."
