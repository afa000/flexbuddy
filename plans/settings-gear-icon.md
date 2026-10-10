# Plan: a gear icon for the Account button

Planned against `origin/main` at 5de206b.

## Where things stand

The top-right button on the main page (`shifts.html` lines 41–42) is a
round header button showing the **first letter of the driver's name**
("A"). It opens `/account`, where all the settings live. The letter
looks like a profile picture, so drivers don't recognise it as the
place for settings.

- **Style:** `.account-button` (`styles.css` line 174) sets an 18px,
  800-weight, uppercase letter.
- **Size:** `.round-header-button` makes it 52px, or 44px in the phone
  header (lines 1553–1569).
- **The other header buttons:** the theme toggle next to it uses inline
  SVG icons styled by `.round-header-button svg` (line 349). Those use a
  26px stroke icon in `currentColor`.
- **Tests:** `PageControllerTest.headerHasHomeLogoAndAccountButtonButNoSignOut`
  (line 257) pins the letter, and `AccountControllerTest` (line 367)
  checks that the Account page itself has no `account-button`.

## 1. Goal and out-of-scope

**Goal:**
- The Account button shows a classic gear icon instead of the initial.
- It has the same size, colour and position as now, and matches the
  theme toggle's line icons in both themes.
- Its accessible name becomes "Account and settings".

**Out of scope:**
- The Account page itself, its header and back button.
- Renaming "Account" anywhere else.
- Profile photos.
- The bottom navigation.

## 2. Data model and migration

None.

## 3. Backend files

None. `currentUser` is still used for the Home greeting.

## 4. Frontend files

### `templates/shifts.html` (lines 41–42)

Replace the letter link with:

```html
<a class="round-header-button account-button" href="/account" aria-label="Account and settings" title="Account and settings">
    <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="3.2"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"/></svg>
</a>
```

That's the widely used open-source "settings" gear outline (Feather
style). It's drawn with strokes, so the existing
`.round-header-button svg` rule styles it, with no new SVG styling
needed.

### `static/css/styles.css` (line 174)

- Replace the `.account-button` rule with
  `.account-button { color: var(--header-control-text); text-decoration: none; }`.
  The font size, weight and uppercase settings only served the letter.
- Add `.account-button svg { width: 24px; }`, just under the theme
  toggle's 26px. A gear's teeth reach the edge of its box, so at 24px it
  looks the same size as the moon and sun.

### `static/sw.js`

No change. No new asset; the build id refreshes the cached `/` page and
stylesheet.

## 5. Ripple list

- **The initial letter** isn't shown anywhere else, so nothing else loses
  it. The greeting "Hi, Angel" on Home still shows the name.
- **Offline:** the cached `/` shell updates on the next online load,
  through `networkFirstPage`.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data.
- **Phone layout:** the tap target stays 44×44 on phones and 52×52 on
  desktop, and the header width is unchanged.
- **Screen readers:** the button now reads "Account and settings, link"
  instead of "Account, link".

## 6. Tests to add

- **`PageControllerTest.headerHasHomeLogoAndAccountButtonButNoSignOut`**
  (line 257): replace the letter assertion with:
  - `class="round-header-button account-button"` together with
    `aria-label="Account and settings"`;
  - an `<svg` inside that link;
  - a negative check that the link no longer ends `>A</a>`.

  Keep the other assertions in that test as they are.
- **`AccountControllerTest.theAccountHeaderHasNoSubtitleOrAccountButton`**
  (line 367): unchanged, and must still pass.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### Desktop (1280×800)

1. Home's top-right shows the theme toggle and a gear, both 52px circles
   with white line icons of matching weight. The gear isn't a letter.
2. Hovering the gear shows the tooltip "Account and settings". Clicking
   it opens Account.
3. Switch to light mode. The gear stays the same colour as the sun icon
   next to it.

### Phone (375×667 and 430×932, then a real iPhone and Android phone)

4. The header shows the logo, "FlexBuddy", the theme toggle and the gear.
   Both buttons are 44×44, and nothing wraps or scrolls sideways.
5. In the Play app on Android, after the update loads, the gear appears
   and opens Account.
6. With VoiceOver or TalkBack, the gear is announced as "Account and
   settings".

## 8. Commit message

```
style(header): show a gear icon on the account button

The top-right button showed the first letter of the driver's name,
which reads as a profile picture, so drivers did not recognise it as
the way into their settings. It now shows a gear drawn in the same
line style as the theme toggle beside it, keeps its size and place,
and is announced as Account and settings.
```

## 9. Open questions

None.
