# Plan: a show/hide button on every password box

Planned against `origin/main` at 5de206b.

## Where things stand

There are five password boxes, and none has a way to see what was typed:

| Page | Template | Box |
| --- | --- | --- |
| Sign in | `login.html` line 79 | `name="password"`, `autocomplete="current-password"` |
| Create account | `register.html` line 54 | `th:field="*{password}"`, `new-password` |
| Choose a new password | `reset-password.html` lines 43 and 48 | `password`, `confirmPassword` |
| Delete account | `account.html` line 321 | `th:field="*{password}"`, `current-password` |

On a phone keyboard, a typo in a hidden password is the most common reason
a sign-in fails. With sign-in limits shipped (5 failures, then a 15-minute
lock), a few typos can now lock a driver out.

**A trap to avoid:** `BuildInfoAdvice` (`config/BuildInfoAdvice.java`
line 14) only covers `PageController` and `AccountController`. The reset
pages come from `PasswordResetController`, so `${buildId}` is empty
there. A script tag on those pages would load `/js/…js?v=` with an empty
version. The service worker caches `/js/` cache-first, so that empty
version would be cached forever. This plan adds the controller to the
advice.

## 1. Goal and out-of-scope

**Goal:**
- Every password box has an eye button inside its right edge.
- Tapping it shows the password as plain text. Tapping again hides it.
- The button's label and icon say what it will do: "Show password" or
  "Hide password".
- Before the form submits, every box goes back to hidden. Browsers then
  save the password as a password, and it's never left visible if the
  page is restored from the back/forward cache.
- Without JavaScript, the boxes work exactly as now, with no button.

**Out of scope:**
- Password strength meters.
- A "confirm password" box on Create account.
- Changing the minimum length.
- Any other sign-in change. Email verification, two-factor and Google
  sign-in each have their own plan.

## 2. Data model and migration

None.

## 3. Backend files

- **`config/BuildInfoAdvice.java`:** add `PasswordResetController.class` to
  `assignableTypes`, giving
  `{PageController.class, AccountController.class, PasswordResetController.class}`.
  Update the class comment's list if it names the controllers.

Nothing else changes on the server.

## 4. Frontend files

### `static/js/password-toggle.js` (new)

It adds the buttons itself, so the templates only need a marker
attribute.

```js
// Password boxes: adds a show/hide button to every input[data-reveal]. Before a form submits, every box is hidden
// again, so the browser saves a password rather than plain text, and nothing stays visible in the back/forward
// cache. The pure helper runs under `node --test`.
(() => {
    const EYE = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>';
    const EYE_OFF = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 3l18 18M10.6 5.1A10.8 10.8 0 0 1 12 5c6.4 0 10 7 10 7a17.6 17.6 0 0 1-3.2 4.1M6.6 6.6C3.8 8.4 2 12 2 12s3.6 7 10 7a9.7 9.7 0 0 0 5.4-1.6M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>';

    /** What the button shows and says for a box that is currently visible or hidden. */
    function buttonState(visible) {
        return visible
            ? {type: 'text', label: 'Hide password', pressed: 'true', icon: EYE_OFF}
            : {type: 'password', label: 'Show password', pressed: 'false', icon: EYE};
    }

    function apply(input, button, visible) {
        const state = buttonState(visible);
        input.type = state.type;
        button.setAttribute('aria-label', state.label);
        button.setAttribute('aria-pressed', state.pressed);
        button.title = state.label;
        button.innerHTML = state.icon;
    }

    function enhance(input) {
        const wrap = document.createElement('span');
        wrap.className = 'password-field';
        input.replaceWith(wrap);
        wrap.append(input);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'password-toggle';
        wrap.append(button);
        apply(input, button, false);
        button.addEventListener('click', () => {
            const end = input.selectionEnd;
            apply(input, button, input.type === 'password');
            input.focus();
            try { input.setSelectionRange(end, end); } catch { /* some types refuse a selection */ }
        });
        input.form?.addEventListener('submit', () => apply(input, button, false));
        window.addEventListener('pagehide', () => apply(input, button, false));
    }

    window.flexbuddyPasswordToggle = {buttonState};
    if (typeof document.querySelectorAll === 'function') {
        document.querySelectorAll('input[data-reveal]').forEach(enhance);
    }
})();
```

Notes for the implementer:
- **Moving the input:** `replaceWith` plus `append` keeps it inside the
  same `<label>`, so tapping the label still focuses it.
- **The button inside a label:** it's interactive content, so tapping it
  doesn't also trigger the label.
- **The stub document:** `load-script.js`'s stub `querySelectorAll`
  returns `[]`, so the file loads under Node.

### Templates

- **Mark each box:** add `data-reveal` to the five password inputs listed
  above. Change nothing else on them: `autocomplete`, `name` and
  `th:field` all stay.
- **Load the script:** add
  `<script th:src="@{/js/password-toggle.js(v=${buildId})}" defer></script>`
  before `</body>` in `login.html`, `register.html` and
  `reset-password.html`. On `login.html`, put it before the existing
  inline script at line 93.
- **`account.html`:** add the same tag after `tax.js` (line 347).

### `static/css/styles.css`

Add after the `.field input` rules (around line 1107):

```css
.password-field { position: relative; display: block; min-width: 0; }
.password-field input { padding-right: 52px; }
.password-toggle { position: absolute; top: 50%; right: 2px; width: 44px; height: 44px; display: grid; place-items: center; padding: 0; color: var(--muted); background: none; border: 0; border-radius: 6px; transform: translateY(-50%); cursor: pointer; }
.password-toggle:hover, .password-toggle:focus-visible { color: var(--text); }
.password-toggle svg { width: 22px; fill: none; stroke: currentColor; stroke-width: 1.9; stroke-linecap: round; stroke-linejoin: round; }
input[data-reveal]::-ms-reveal { display: none; }
```

The last line stops Edge on Windows from adding its own second eye icon
next to ours.

### `static/sw.js`

Add `'/js/password-toggle.js'` to `VERSIONED_ASSETS`.
`StaticAssetsTest.everyPageScriptIsPrecachedByTheServiceWorker` scans
every template and fails if it's missing.

## 5. Ripple list

- **Password managers:** Google Password Manager, iCloud Keychain and
  1Password key off `autocomplete` and `name`, which don't change.
  Switching the box back to `type="password"` on submit means they offer
  to save a password, as before.
- **Sign-in limits:** unchanged. This only makes typos less likely.
- **`BuildInfoAdvice`:** the reset pages now get a real build id, so
  their existing `styles.css?v=` link also gains a version. That's a
  correct cache key, where before it was an empty one.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data.
- **Phone layout:**
  - the button is 44×44 inside the 47px box;
  - the right padding of 52px keeps typed text from running under it;
  - `min-width: 0` on the wrapper keeps grid children from widening the
    page.
- **Accessibility:** the button announces "Show password" or "Hide
  password" with `aria-pressed`, and focus returns to the box after a
  tap.

## 6. Tests to add

- **`src/test/js/password-toggle.test.js`** (new): load it with
  `load-script.js`.
  - `buttonState(false)` gives
    `{type: 'password', label: 'Show password', pressed: 'false'}`.
  - `buttonState(true)` gives
    `{type: 'text', label: 'Hide password', pressed: 'true'}`.

  Compare primitives, per the loader's cross-realm note.
- **`PageControllerTest` or `AccountControllerTest`:**
  - `/login` and `/register` each render one `data-reveal` input and the
    `password-toggle.js?v=` script with a non-empty version.
  - `account.html` renders `data-reveal` on the deletion password.
- **`PasswordResetControllerTest`:** with a valid token,
  `/reset-password` renders two `data-reveal` inputs, and the script
  URL's `v=` is not empty. This pins the `BuildInfoAdvice` fix.
- **`StaticAssetsTest`:** passes once `sw.js` lists the script.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

### Desktop (Chrome and Safari, 1280×800)

1. On Sign in, type `secret-pass1`. The box shows dots and an eye button
   at the right edge.
2. Click the eye. The text reads `secret-pass1`, the button's tooltip
   says "Hide password", and the cursor stays at the end of the text.
3. Click again: dots again, and the tooltip says "Show password".
4. Show the password, then sign in. When Chrome offers to save the
   password, the saved entry is the password, not a username-only entry.
   Press Back after signing in: the box is hidden and empty.
5. On Create account, Choose a new password (both boxes, toggled
   separately) and Account → Delete account, the eye works the same way.
6. Edge on Windows shows only one eye per box.
7. Tab through the sign-in form: Email, then Password, then the eye
   button (with a focus ring), then Forgot password. Space on the eye
   toggles it.

### Phone (375×667 and 430×932, then a real iPhone and Android phone)

8. The eye sits inside the box, 44×44, and typed text never runs under
   it. Type 30 characters to check.
9. There's no sideways scroll on any of the four pages
   (`document.documentElement.scrollWidth === innerWidth`).
10. On the iPhone, tap the eye while the keyboard is open. The keyboard
    stays up, and iOS's password AutoFill still offers the saved
    password.
11. On Android in the Play app, sign in with the password shown. Google
    Password Manager still offers to save it.

## 8. Commit message

```
feat(auth): add a show and hide button to password boxes

Sign-in limits made a few mistyped passwords on a phone keyboard
enough to lock a driver out for fifteen minutes, and no password box
let the driver see what they had typed. Every password box now has
an eye button that shows or hides the password, announces which it
will do, and keeps the cursor where it was. Each box is hidden again
before its form submits and when the page is left, so browsers still
save a password and nothing stays visible in the page cache.

The password reset pages now receive the build id like the other
pages, so the new script and the stylesheet there carry a real
version instead of an empty one that the service worker would have
cached for good.
```

## 9. Open questions

None.
