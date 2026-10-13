# Plan: accept "delete" however it's typed when deleting an account

Planned against `origin/main` at 2b7a47d.

## The bug

- **The label:** Account → Account & privacy → Delete account asks the
  driver to "Type **delete** to confirm"
  (`accountSection.typeDeleteToConfirm.html`, `account.html` line 370).
- **The check:** `AccountDeletionRequest.confirmation` is checked with
  `@Pattern(regexp = "delete")`, which is case-sensitive and doesn't
  trim.
- **But every field label is uppercased by CSS:** `.field > span:first-child { text-transform: uppercase; }`
  (`styles.css` line 1092). On screen, the label reads **"TYPE DELETE TO
  CONFIRM"**, and in Spanish **"ESCRIBA DELETE PARA CONFIRMAR"** (I
  captured this in a 375px screenshot of the Spanish Account page).
- **What happens:**
  - A driver who types `DELETE`, as shown, gets "Type delete exactly as
    shown."
  - The iPhone keyboard capitalises the first letter by default, so
    typing `delete` there produces `Delete`, which is also rejected. The
    input has no `autocapitalize="none"`.
  - Autocorrect can also turn it into `Deleted` or add a trailing space.
- `AccountControllerTest` line 546 currently *pins* the rejection of
  `DELETE`.

## 1. Goal and out-of-scope

**Goal:**
- **Accepted:** `delete`, `Delete`, `DELETE`, and any of these with
  spaces around them.
- **Still rejected:** any other word, such as `delet` or `remove`.
- **The keyboard:** stops capitalising and autocorrecting this box on
  phones.
- **The error message:** no longer says "exactly as shown".

**Out of scope:**
- Changing the confirmation word, or translating it. Spanish still asks
  for "delete", as the label already says.
- The password or emailed-code requirement.
- Any other form.

## 2. Data model and migration

None.

## 3. Backend files

### `dto/AccountDeletionRequest.java`

Replace the Lombok setter for `confirmation` with one that normalises
the value before validation runs:

```java
/** "Delete", "DELETE" and " delete " all count: the label is shown in capitals and phone keyboards capitalise. */
public void setConfirmation(String confirmation) {
    this.confirmation = confirmation == null ? null : confirmation.strip().toLowerCase(Locale.ROOT);
}
```

- Keep the `@Getter`. An explicit setter takes precedence over Lombok's
  for that field.
- Keep `@NotBlank` and `@Pattern(regexp = "delete")` as they are. They
  now see the normalised value, so the controller and service are
  unchanged.
- Data binding goes through the setter, so the check applies to form
  posts.

### `src/main/resources/i18n/messages.properties` and `messages_es.properties`

- **`validation.delete.exact`:**
  - English: `Type the word delete to confirm.`
  - Spanish: `Escriba la palabra delete para confirmar.`

  Keep the key name, so nothing else changes. Neither value has
  arguments, so the apostrophe rule doesn't apply.

## 4. Frontend files

### `templates/account.html` (line 371)

```html
<input th:field="*{confirmation}" type="text" autocomplete="off" autocapitalize="none" autocorrect="off"
       spellcheck="false" maxlength="12" required>
```

- `autocapitalize="none"` and `autocorrect="off"` stop iOS from
  capitalising the word or changing it. `autocorrect` is ignored by
  other browsers.
- `maxlength` goes from 6 to 12, so a stray space from a keyboard
  suggestion can't cut the word off. The server trims it.

### `static/css/styles.css` and `sw.js`

No change. The build id refreshes the cached Account page.

## 5. Ripple list

- **The safety of deletion is unchanged.** It still needs the password,
  or the emailed code for accounts without a password, plus the word.
  Only the capitalisation of the word is relaxed.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data.
- **Phone layout:** none. It's the same field.

## 6. Tests to add

**`controller/AccountControllerTest`:**
- **Replace the test at line 546,** which currently expects `DELETE` to
  be rejected, with `deleteAccount_acceptsTheWordInAnyCase`. With the
  right password, `confirmation` values of `DELETE`, `Delete` and
  `" delete "` each delete the account. Verify
  `accountService.deleteAccount(...)` is called, and the response
  redirects as the existing success test expects.
- **Add `deleteAccount_rejectsADifferentWord`:** `delet` and `remove`
  each give a field error on `confirmation` with the message `Type the
  word delete to confirm.`, and `deleteAccount` is never called.
- **The other deletion tests** (lines 407–581) pass unchanged; they send
  `delete`.

**`dto/AccountDeletionRequestTest`** (new, small): the setter turns
`" DELETE "` into `"delete"` and keeps `null` as `null`.

**`i18n/SpanishMessagesTest`:** passes. The key exists in both files with
no placeholders.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

Use a throwaway account. Create one with a Gmail plus-address, such as
`flexbuddysupport+deltest@gmail.com`.

### Desktop (1280×800)

1. Account → Account & privacy → Delete account. Enter the password and
   type `DELETE`, the way the label shows it, then press **Delete account
   permanently**. The account is deleted, and you land on the sign-in page
   with the deletion notice.
2. With another throwaway account, type `remove`. The field shows "Type
   the word delete to confirm." and the account still exists.

### Phone (375×667, then a real iPhone and Android phone)

3. On the iPhone, tap the confirmation box and type `delete`. The first
   letter stays lowercase, and autocorrect doesn't change the word.
   Deletion succeeds with a throwaway account.
4. In Spanish (Account → Language → Español), the label reads "ESCRIBA
   DELETE PARA CONFIRMAR", and typing `DELETE` deletes a throwaway
   account.
5. The field and error message fit at 375px, with no sideways scroll.

## 8. Commit message

```
fix(account): accept the delete confirmation in any capitalisation

The confirmation box asked drivers to type delete, but field labels
are shown in capitals, so the page read TYPE DELETE TO CONFIRM, and
typing DELETE as shown was rejected. On an iPhone the keyboard also
capitalised the first letter, so typing delete sent Delete and failed
as well.

The word is now trimmed and lowercased before it is checked, so any
capitalisation works and any other word is still refused, while the
password or emailed code is still required. The box no longer
capitalises or autocorrects on phones, and the error message asks for
the word instead of an exact match.
```

## 9. Open questions

None.
