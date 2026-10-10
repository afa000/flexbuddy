# Plan: Spanish, with a language setting

Planned against `origin/main` at 5de206b. This is **part 3 of 3**. **Ship
`plan/i18n-server` and `plan/i18n-scripts` first.** This plan adds a
second messages file and a way to choose it. It also assumes the three
sign-in plans (verification, two-factor and Google) have shipped, so V27
is the next migration. If the order changes, use the next free migration
number and don't leave a gap.

Decided: **English and Spanish.** Other languages can be added later as
more message files once testers ask.

## Why Spanish and how

- **Why:** FlexBuddy's tax, mileage and payout features are US-specific,
  so the languages that matter are the ones US delivery drivers speak.
  Spanish is by far the most common after English.
- **Dialect:** **US Spanish**, with neutral Latin-American vocabulary.
  Money and dates are formatted with `es-US`. I checked Node's `Intl` on
  this machine:

  | Locale | Money | Date | Time |
  | --- | --- | --- | --- |
  | `es-US` | `$1,234.50` | `lun, 28 de sept` | `4:05 p.m.` |
  | `es-ES` | `1234,50 US$` | | `16:05` |

  `es-US` keeps dollar amounts in the format drivers see on their Flex
  pay, while the words are Spanish. `es-ES` would show amounts in a
  format US drivers don't expect.

## 1. Goal and out-of-scope

**Goal:**
- `messages_es.properties` translates every key in `messages.properties`.
  A test fails if a key is missing or has the wrong placeholders.
- **Language choice:**
  - **New visitors** get Spanish when their browser or phone prefers it,
    and English otherwise.
  - **Signed-out pages** have an **English · Español** switch at the
    bottom.
  - **Account** gets a **Language** setting: Automatic (device),
    English or Español. It's stored with the account and kept in
    backups.
- **Emails, push reminders and calendar events** use the driver's
  setting. Automatic uses the language they signed up in.
- **Dates and numbers** follow the language (`es-US` for Spanish).
- **The Privacy Policy and Terms** stay English, with a Spanish note at
  the top saying so. See open question 2.
- **Every money and tax figure** keeps its "estimate" wording in Spanish
  ("estimación"), and nothing reads as advice.

**Out of scope:**
- **Reading screenshots from the Flex app set to Spanish.** That's
  `plan/spanish-screenshots`, which doesn't depend on the translation
  plans and can ship earlier.
- **The Play Store listing in Spanish:** a manual step in Play Console.
  A draft can go in `android/store-listing.md` later.
- **Right-to-left languages.**
- **A professional legal translation.**

## 2. Data model and migration

New `src/main/resources/db/migration/V27__language.sql`:

```sql
-- The driver's chosen language for pages, email and reminders: 'en' or 'es'; null follows the device.
alter table app_users add column language varchar(10);
alter table app_users add constraint chk_app_users_language check (language is null or language in ('en', 'es'));
```

### `model/AppUser.java`

```java
/** "en" or "es"; null means follow the device's language. */
@Column(length = 10)
private String language;
```

### `dto/AccountSettingsResponse.java` (shared record: keep the field order and the compatibility constructors)

- Append `String language` **after** `askMissingMiles`, as the last
  component.
- Add a compatibility constructor with the current 18 components that
  passes `null`.
- Update the existing compatibility constructors, which pass
  `askMissingMiles = true` and so on, to delegate with `null` as the new
  last argument.

### `dto/BackupSettings.java` (shared record: keep the field order and the compatibility constructors)

- Append `String language` after `askMissingMiles`.
- Add a compatibility constructor with the current 15 components that
  passes `null`.
- Update the older ones.
- The format stays **version 4**.

### Backup, restore and deletion

- **`AccountBackupService`:** pass `user.getLanguage()`.
- **`AccountRestoreService`:** in the `version >= 4` block, add
  `if (file.settings().language() != null) owner.setLanguage(file.settings().language());`.
  Ignore values other than `en` and `es`.
- **Deletion:** the column goes with the row.

## 3. Backend files

### `config/LocaleConfig.java` (replace part 1's English-only resolver)

```java
/** Spanish or English: a saved choice (cookie, set at sign-in and from Account) wins, then the browser's preference. */
public class FlexBuddyLocaleResolver extends CookieLocaleResolver {
    static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.forLanguageTag("es"));
    FlexBuddyLocaleResolver() {
        super("fb_lang");
        setCookieMaxAge(Duration.ofDays(365));
        setCookieSameSite("Lax");
        setDefaultLocaleFunction(request -> bestMatch(request.getLocales()));
    }
    static Locale bestMatch(Enumeration<Locale> wanted) { /* first whose language is en or es; else English */ }
    @Override public Locale resolveLocale(HttpServletRequest request) { return clamp(super.resolveLocale(request)); }
    static Locale clamp(Locale l) { return "es".equals(l.getLanguage()) ? SUPPORTED.get(1) : Locale.ENGLISH; }
}
```

Also register a `LocaleChangeInterceptor` with `paramName = "lang"`
(through `WebMvcConfigurer.addInterceptors`). `?lang=es` on any page
switches the language and sets the cookie. Only `en` and `es` are
accepted; anything else is clamped to English.

### Sign-in success (`security/VerifiedLoginSuccessHandler.java`, from the earlier plans)

Before redirecting, if `user.getLanguage() != null`, call
`localeResolver.setLocale(request, response, Locale.forLanguageTag(user.getLanguage()))`.
A driver's saved choice then follows them to a new device.

### Registration (`AccountService.register` and `GoogleAccountService`)

Store `user.setLanguage(...)` with the request's resolved language only
when it's `es`. Leave it `null` for English, which means automatic.
Emails and reminders for a Spanish sign-up are then in Spanish from the
start.

### `controller/AccountController.java`

Add:

```java
@PutMapping("/account/language")
@ResponseBody
public AccountSettingsResponse updateLanguage(Principal principal, @Valid @RequestBody LanguageRequest request,
        HttpServletRequest http, HttpServletResponse response)
```

- `LanguageRequest(@Pattern(regexp = "en|es") String language)` is a new
  DTO. `null` means automatic.
- It saves the choice through
  `settingsService.updateLanguage(email, language)` (new). For a choice,
  it sets the cookie with `localeResolver.setLocale`. For automatic, it
  clears the cookie, so the device's preference applies.
- It returns the settings.

### `service/AccountSettingsService.java`

- `updateLanguage`.
- `response(...)` passes `user.getLanguage()` as the new last argument.

### Emails, push and calendar (`MessageSource` calls from part 1)

Replace `Locale.ENGLISH` with `UserLocales.of(user)`, a new static
helper: `es` gives Spanish, and anything else gives English. That covers
these places:
- `SmtpPasswordResetMailer`, `SmtpEmailCodeMailer`, `ReminderJob`,
  `TaxReminderJob` and `CalendarFeedService`.
- **The reset mailer** has only the address, not the user. Pass the
  locale in from `PasswordResetService`, which has the user. Add a
  parameter to `sendResetLink`, and update its two implementations and
  tests.

### `src/main/resources/i18n/messages_es.properties` (new)

UTF-8, with the **same keys** in the same order and groups as
`messages.properties`.

**Writing it:**
- **Draft:** the implementer drafts every value.
- **Review:** no separate human review before release. The implementer
  does a second full read-through of the Spanish file in context, by
  clicking every screen in Spanish (manual check 3). They check against
  the glossary and fix anything that reads as a literal translation.
  After release, Spanish-speaking testers report wording problems
  through Send feedback.
- **Register:** use **usted** throughout ("Inicie sesión", "Su código",
  "Vuelva a intentarlo"). Drivers' screenshots of the Amazon Flex app in
  Spanish show usted ("Su tablero", "Vuelva más tarde para revisar…"),
  so FlexBuddy matches the app they already use.
- **Placeholders:** keep `{0}` and `{1}` exactly. Apply the `''` rule
  from part 1 to values with arguments.
- **Plurals:** `.one` and `.other`, the same as English.

**Glossary.** Use these consistently. Where the Amazon Flex app in
Spanish has a natural term, FlexBuddy uses the same one; these were
taken from screenshots of its menu, offers and schedule screens. Where
Flex's Spanish is a machine-translation slip, don't copy it: it uses
"Pagar" (to pay) for *Pay*, "Longitud" (length) for *Duration*, and
"Tú eres fuera de línea".

| English | Spanish |
| --- | --- |
| block | bloque |
| offers | ofertas (as in Flex) |
| start / end of a block | hora de inicio / hora de fin (Flex shows "Inicio" / "Terminar en") |
| pay (of a block) | pago (not Flex's "Pagar") |
| duration | duración (not Flex's "Longitud") |
| stations | estaciones (as in Flex) |
| earnings | ganancias (as in Flex) |
| settings | configuración (as in Flex) |
| updates | actualizaciones (as in Flex) |
| help | ayuda (as in Flex) |
| calendar | calendario (as in Flex) |
| station | estación |
| base pay | pago base |
| tips | propinas |
| miles / mileage | millas / millaje |
| payout | depósito |
| forfeit | perder el bloque (late forfeit = pérdida tardía) |
| standing | nivel (Fantastic, Great, Fair, At risk stay in English, as Amazon shows them) |
| estimated taxes | impuestos estimados |
| set aside | apartar |
| estimate, not tax advice | estimación, no es asesoría fiscal |
| backup / restore | copia de seguridad / restaurar |
| Home / Reports / Schedule / Import / Expenses | Inicio / Informes / Programación (as in Flex) / Importar / Gastos |
| sign in / sign out / create account | iniciar sesión / cerrar sesión / crear cuenta |
| two-step sign-in / recovery code | verificación en dos pasos / código de recuperación |

**Keep in English:** brand names and fixed program terms (FlexBuddy,
Amazon Flex, Google, Fantastic and the other standing tiers),
`Schedule C`, and IRS names of forms.

## 4. Frontend files

### `templates/account.html` (Account & privacy, first card)

```html
<article class="panel data-card">
    <h3 th:text="#{account.language.title}">Language</h3>
    <label class="field"><span th:text="#{account.language.label}">App language</span>
        <select id="languageSelect">
            <option value="" th:text="#{account.language.auto}">Automatic (device)</option>
            <option value="en">English</option>
            <option value="es">Español</option>
        </select>
    </label>
</article>
```

The language names are deliberately **not** translated. Each is shown in
its own language, so a driver can always find theirs.

### `static/js/account.js`

- **Render:** `languageSelect.value = settings.language ?? ''`.
- **On `change`:**
  1. `PUT /account/language` with
     `{language: value || null}`, using `csrfHeaders`.
  2. On success, call `flexbuddyPwa.clearUserData()` if available,
     because cached pages are in the old language. Then
     `location.reload()`.
  3. On failure, show a toast through `t('js.account.languageFailed')`.
- **Offline:** disable the select (`data-online-only`).

### Signed-out pages (`login`, `register`, `forgot-password`, `reset-password`, `reset-password-invalid`, `error`)

At the bottom of the auth card:

```html
<p class="language-switch">
    <a th:href="@{''(lang='en')}" th:classappend="${#locale.language == 'en'} ? 'is-current'" lang="en" hreflang="en">English</a>
    <span aria-hidden="true">·</span>
    <a th:href="@{''(lang='es')}" th:classappend="${#locale.language == 'es'} ? 'is-current'" lang="es" hreflang="es">Español</a>
</p>
```

`@{''(lang='es')}` keeps the current path. On `reset-password`, keep the
`token` parameter: `@{/reset-password(token=${token},lang='es')}`.

### `templates/privacy.html` and `templates/terms.html`

When `#locale.language == 'es'`, show a notice above the English text:
"Esta política está disponible solo en inglés. Si tienes preguntas,
escríbenos a flexbuddysupport@gmail.com." The terms page says "Estos
términos…". The legal `<article>` already has `lang="en"` from part 1.

### `templates/shifts.html` and the others

No new markup. Part 1's `th:lang` makes `<html lang="es">`, and part 2's
`appLocale()` then formats dates and numbers as `es-US`.

### `static/css/styles.css`

```css
.language-switch { margin: 18px 0 0; display: flex; justify-content: center; gap: 10px; font-size: 14px; }
.language-switch a { min-height: 44px; display: inline-flex; align-items: center; }
.language-switch a.is-current { font-weight: 750; text-decoration: none; color: var(--text); }
```

**Spanish runs about 20–30% longer than English.** Check the tightest
places in the manual checks:
- the bottom navigation labels;
- Home's three figures ("Last 7 days", "Est. net / hr", "Set aside");
- the quick-action rows;
- section summaries;
- buttons inside `.form-actions`.

Fix any overflow with `overflow-wrap: anywhere` / `min-width: 0` on that
element, or a shorter Spanish word. Never fix it with smaller text below
13px.

### `static/sw.js`

- **Cached pages** are cleared on a language change by
  `clearUserData()`. `SHELL_PAGES` caching is otherwise unchanged.
- **`PAGES_CACHE`:** cached `/` and `/account` pages are per device, and
  a device has one language at a time, so no cache key change is needed.
- **No new assets.**

## 5. Ripple list

- **Shared records:**
  - `AccountSettingsResponse` gains `language` as its last field, with
    compatibility constructors kept.
  - `BackupSettings` gains `language` as its last field, with
    compatibility constructors kept; the format stays version 4.
  - `BlockEvaluationResponse`, `BackupShift`, `AccountBackupFile` and
    `ShiftResponse` are unchanged.
- **New account data** (`language`): in backups, restored when present,
  and deleted with the account.
- **Optional request field:** `LanguageRequest.language`, where `null`
  means automatic. That's a deliberate value here, not "keep stored".
  This endpoint sets only the language, so there's nothing to keep.
- **Signed-out pages** gain a language switch, and the `fb_lang` cookie
  is set for a year (functional, not tracking). Mention it in the
  privacy policy's storage section, in English.
- **Native date and time pickers** follow the *phone's* language, not
  FlexBuddy's. That's an OS limitation. The value they produce is the
  same either way.
- **Screenshot import** of Spanish Flex screenshots is handled by
  `plan/spanish-screenshots`. If that plan hasn't shipped yet when this
  one does, add one line under Import in Spanish only: "La lectura de
  capturas funciona mejor con la app de Flex en inglés." Remove that
  line when it ships.
- **Money and tax:** every figure keeps "estimación" wording, and the
  tax pages' notes say "no es asesoría fiscal".
- **Phone layout:** longer Spanish strings are the main risk. They're
  covered by the checks at 375px.
- **Dates:** the device's local date for "today" is unchanged. Only
  formatting follows the language.

## 6. Tests to add

**`i18n/SpanishMessagesTest`** (new; plain JUnit):
- Every key in `messages.properties` exists in `messages_es.properties`,
  and the reverse. Any missing or extra keys are listed.
- For every key, the set of `{n}` placeholders is identical in both
  files.
- The `''` rule holds in Spanish values with arguments.
- No Spanish value is identical to its English one, except an allow-list
  of brand names, numbers-only values and the language names. This
  catches untranslated copies.

**`config/FlexBuddyLocaleResolverTest`:**
- `Accept-Language: es-MX,es;q=0.9` gives `es`.
- `fr-FR,es;q=0.5` gives `es`.
- `de-DE` gives `en`.
- No header gives `en`.
- A cookie `fb_lang=es` wins over `Accept-Language: en`.
- `?lang=es` sets the cookie.
- `?lang=xx` gives `en`.

**`AccountControllerTest`:**
- `PUT /account/language` with `{"language":"es"}` saves `es`, sets the
  `fb_lang` cookie and returns `"language":"es"`.
- With `{"language":null}`, it saves null and expires the cookie.
- With `{"language":"fr"}`, it returns 400.

**`AccountBackupServiceTest` and `AccountRestoreServiceTest`:** the
language round-trips. A v4 file without the field keeps the current
value, and `"language":"xx"` is ignored.

**Rendering (`@SpringBootTest` + MockMvc):**
- `/login` with `Accept-Language: es` renders `<html lang="es"`,
  "Iniciar sesión", and no `??`.
- `/` for a signed-in user with `language = "es"` renders Spanish
  navigation labels.

**Mail and push:**
- `ReminderJobTest`: a user with `language = "es"` gets a Spanish push
  title.
- `PasswordResetServiceTest` / `MailConfigTest`: the reset email for a
  Spanish user has a Spanish subject.

**`src/test/js/i18n.test.js`** (extend): with `lang="es"`, `appLocale()`
is `es-US`, and `formatMoney`-style formatting gives `$1,234.50`.

**`PostgresMigrationTest`:** the legacy user's `language` is null.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### Desktop (1280×800)

1. With the browser's language set to Spanish, open `/login` signed
   out. It's in Spanish, with **English · Español** at the bottom and
   *Español* in bold. Click English: the page switches, and it stays
   English after a reload (cookie).
2. Create an account in Spanish. The verification email arrives in
   Spanish.
3. Click through every screen and dialog in Spanish (the same list as
   `plan/i18n-scripts` step 1). Look for:
   - any English left over. The only expected exceptions are brand
     names, standing tiers and the legal pages;
   - any raw key;
   - dates like `lun, 28 de sept`, and money like `$1,234.50`.
4. Account → Language → English: the app reloads in English. Set it to
   Automatic: it follows the browser language again.
5. Turn on a push reminder for a Spanish account. Its title arrives in
   Spanish. The calendar feed's event titles are in Spanish.
6. Download a backup: `settings.language` is `"es"`. Restore it into an
   English account: that account becomes Spanish.

### Phone (375×667 and 430×932, then a real iPhone and Android phone set to Spanish)

7. At **375px in Spanish**, check these for overflow, clipping or
   sideways scroll:
   - the bottom navigation;
   - Home's figures row and week card;
   - quick actions;
   - every Account section summary and form;
   - the edit dialog's buttons;
   - the finish sheet;
   - the import review;
   - the payout history.

   `document.documentElement.scrollWidth === innerWidth` must hold on
   every screen.
8. On Android in the Play app, with the phone set to Spanish, the app is
   in Spanish on first launch, without visiting Account.
9. On the iPhone, set the phone to Spanish but FlexBuddy to English in
   Account. The app is English, while the native date picker wheel shows
   Spanish month names. That's expected (an OS limitation).
10. The tax pages in Spanish say "estimación" next to every figure and
    "no es asesoría fiscal" in their notes.

## 8. Commit message

```
feat(i18n): add spanish with a language setting

Spanish is the language most US delivery drivers speak after English,
so FlexBuddy now ships a full Spanish translation of its pages,
scripts, email, push reminders and calendar events. A new visitor
gets Spanish when their phone or browser prefers it, signed-out pages
have an English and Español switch, and Account has a Language
setting that is kept with the account and in backups and follows the
driver to a new device when they sign in.

Spanish uses the US region, so dollar amounts and times keep the
format drivers see on their pay while words and dates are Spanish.
Every figure keeps its estimate wording and nothing reads as tax
advice. The privacy policy and terms stay in English with a Spanish
note saying so, and reading screenshots from the Flex app set to
Spanish will follow once sample screenshots are available. A test
fails if the Spanish file misses a key, changes a placeholder or
leaves a value untranslated.
```

## 9. Open questions

1. ~~Who reviews the Spanish, and *tú* or *usted*?~~ **Decided:**
   *usted*, matching the Flex app, and "bloque". There's no separate
   reviewer: the implementer does a read-through, and testers send
   feedback after release.
2. ~~The legal pages?~~ **Decided:** they stay English, with the Spanish
   note.
3. ~~Spanish screenshots?~~ **Received:** planned in
   `plan/spanish-screenshots`.
