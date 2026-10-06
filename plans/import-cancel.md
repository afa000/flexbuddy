# Plan: let drivers go back to the photo gallery and pick another screenshot

Planned against `origin/main` at 6e56508. This is independent of
`plan/import-memory-limits`, but ship that one first: it stops the long
hangs that make this bug hurt most.

## What goes wrong today

**As reported:** on iPhone, after tapping a photo, there's no way back to
the gallery to pick a different one.

Two things combine to cause this.

**iOS commits on the first tap.** For a single-file
`<input type="file">` (`#screenshotInput`, `shifts.html` line 419), the
iOS photo picker has no "Done" step. Tapping a photo selects it, closes
the gallery and fires `change` immediately. A mis-tap is final as far as
iOS is concerned. The website can't change that behaviour; it can only
offer an easy way to reopen the gallery.

**FlexBuddy then offers no way to reopen it.** In the code:

- Choosing a file calls `processScreenshot(file)` (`app.js` line 513).
  It hides the drop zone, shows the file card, and calls
  `setProcessing(true)`.
- `setProcessing` (line 1638) sets `removeFileButton.disabled = true`.
  The **X is disabled for as long as the server takes to read the
  image**, and nothing else on the screen offers a way back: the
  "Start over" button sits inside `#previewForm`, which stays hidden
  until a preview arrives.
- The upload `fetch` has no `AbortController` and no timeout. If the
  server is slow, or runs out of memory on a large photo (see
  `plan/import-memory-limits`), the spinner can run for a minute or more.
- On Android the system back button gets drivers out. On iPhone there's
  no back button. The app uses `history.replaceState` for its screens, so
  swiping back doesn't help either. The driver has to wait it out or
  close the app.
- Even after the read finishes, getting back to the gallery takes two
  steps: tap the small X, which brings back the drop zone, then tap the
  drop zone. Nothing on the screen says "pick another photo".
- The X is only an icon. The app-wide tap-target rule (`styles.css`
  line 2106) makes it 44px, but the file card gives it a 36px column, so
  it overflows and is easy to miss next to the thumbnail.

## 1. Goal and out-of-scope

**Goal:**
- **The main fix:** as soon as a photo is chosen, a full-width **Choose a
  different screenshot** button appears under it. One tap stops any read
  in progress, clears the chosen photo, and **reopens the gallery**. It
  works while the screenshot is being read, after a preview appears, and
  after an error.
- While a screenshot is being read, the driver can also tap **Cancel**,
  which is always enabled, to stop and return to the drop zone without
  opening the gallery.
- An upload that takes longer than 60 s stops on its own with a clear
  message.
- A cancelled or timed-out response that arrives late never fills in the
  form.

**Out of scope:**
- Making iOS swipe-back move between FlexBuddy screens. That's a change
  to the app-wide navigation model (`pushState`), see open question 1.
- Stopping the server's OCR when the browser cancels. The server finishes
  the read and the result is thrown away.
- Shrinking images in the browser.
- Changes to the preview form, including the date and time box sizes,
  which are in `plan/ios-date-time-inputs`.

## 2. Data model and migration

None.

## 3. Backend files

None.

## 4. Frontend files

### `templates/shifts.html` (import screen, lines 428–445)

- **File card button:** keep `#removeFileButton`, but change its
  `aria-label` to `Remove screenshot and choose again`.
- **New button:** add it directly after `#fileCard` and before
  `#uploadError`:

  ```html
  <div class="file-actions is-hidden" id="fileActions">
      <button class="secondary-button" id="chooseAgainButton" type="button">Choose a different screenshot</button>
  </div>
  ```

- **Cancel button:** in `#processingRow`, after the "Reading shift
  details…" span, add:

  ```html
  <button class="text-button" id="cancelImportButton" type="button">Cancel</button>
  ```

### `static/js/app.js`

- **Elements:** register `fileActions`, `chooseAgainButton` and
  `cancelImportButton` in `elements`, next to `removeFileButton`
  (line 25).
- **New module state,** next to `let selectedFileUrl;` (line 177):

  ```js
  let importAbort;
  let importTimer;
  const IMPORT_TIMEOUT_MS = 60000;
  ```

- **`processScreenshot(file)`:**
  - Keep the type and size checks as they are.
  - Call `cancelImportRequest()` first, so a second pick replaces the
    first.
  - Then:

    ```js
    const abort = new AbortController();
    importAbort = abort;
    let timedOut = false;
    importTimer = setTimeout(() => { timedOut = true; abort.abort(); }, IMPORT_TIMEOUT_MS);
    ```

  - Pass `signal: abort.signal` to `apiFetch('/shifts/import-preview', …)`.
  - After `await response.json()`, call `populatePreview` only when
    `importAbort === abort`. If another pick or a cancel has replaced the
    request, return without changing anything.
  - In `catch (error)`:
    - If `error.name === 'AbortError' && !timedOut`, return silently,
      because the driver cancelled and `resetImport` has already reset
      the screen.
    - If `timedOut`, show `Reading this screenshot took too long. Try
      again, or choose a different screenshot.` in `#uploadError`, and
      keep the file card so the driver can choose again.
    - Otherwise, keep the existing message handling.
  - In `finally`, only when `importAbort === abort`:
    `clearTimeout(importTimer); importAbort = undefined; setProcessing(false);`.
- **New `cancelImportRequest()`:**

  ```js
  function cancelImportRequest() {
      clearTimeout(importTimer);
      const abort = importAbort;
      importAbort = undefined;
      abort?.abort();
  }
  ```

- **`resetImport()`** (line 1606): call `cancelImportRequest()` first,
  then `setProcessing(false)`, then the existing body. Also add
  `elements.fileActions.classList.add('is-hidden');`.
- **`showSelectedFile(file)`:** also show `#fileActions` by removing
  `is-hidden`.
- **`setProcessing(processing)`** (line 1638): **delete** the line
  `elements.removeFileButton.disabled = processing;`. The X stays usable.
  The processing row, which now includes Cancel, keeps toggling as
  before.
- **Listeners** (next to line 254):

  ```js
  elements.cancelImportButton.addEventListener('click', resetImport);
  elements.chooseAgainButton.addEventListener('click', () => {
      resetImport();
      openFilePicker();
  });
  ```

  `openFilePicker()` has to run inside the tap itself, or iOS won't open
  the picker. Call it synchronously in the handler, with no `await`
  before it. `resetImport` already clears `screenshotInput.value`, so
  picking the same photo again still fires `change`.
- **Shared screenshots:** the share-target path (line 2219) calls
  `processScreenshot` too, and gets the same cancel behaviour without
  further changes.

### `static/css/styles.css`

After the `.file-details` rules (around line 900), add:

```css
.file-actions { margin-top: 10px; }
.file-actions .secondary-button { width: 100%; min-height: 44px; }
.processing-row .text-button { margin-left: auto; color: var(--error); }
```

Also widen the file card's last grid column from `36px` to `44px`
(`.file-card { grid-template-columns: 78px 1fr 44px; }`, line 863). The X
is already 44px wide under the app-wide tap-target rule (line 2106) and
currently overflows its column.

### `static/sw.js`

No new script, so nothing changes. The build id refreshes caches.

## 5. Ripple list

- `resetImport` also runs from **Start over** and after a successful
  save. Calling `cancelImportRequest()` there does nothing when no request
  is in flight.
- `apiFetch` already rethrows `AbortError` without marking the app
  offline (line 214), so cancelling never shows the offline banner.
- `errors.js` already ignores `AbortError` rejections, so a cancel never
  sends an error report.
- The server still finishes an abandoned read. Once
  `plan/import-memory-limits` ships, that holds the single OCR slot for
  a few seconds, and a quick re-pick may wait briefly behind it. That's
  acceptable.
- **Phone layout:**
  - The new full-width button is 44px tall.
  - Cancel is a `.text-button`, which already has a minimum of 44×44.
  - The file card's grid column now matches its button, so nothing
    widens the page at 375px.
- **Shared records:** none of `AccountSettingsResponse`, the backup
  format, `BlockEvaluationResponse` or `ShiftResponse` changes. There's
  no new account data. `VERSIONED_ASSETS` and `DATA_PATHS` are
  unchanged.

## 6. Tests to add

`app.js` reads the page when it loads, so it can't be loaded by
`load-script.js`. The behaviour is pinned by markup tests plus the manual
checks below.

- **`PageControllerTest`, `importScreenOffersCancelAndChooseAgain`:** the
  rendered `/` page contains:
  - `id="cancelImportButton"` inside `id="processingRow"`;
  - `id="chooseAgainButton"` with the text `Choose a different screenshot`;
  - `id="removeFileButton"` **without** a `disabled` attribute.
- **`StaticAssetsTest`:** passes unchanged; no new script.

Run `mvn test` and `node --test "src/test/js/*.test.js"`.

## 7. Manual checks

To make the read slow enough to tap Cancel, use dev tools → Network →
throttling "Slow 3G" on desktop. On a phone, a large photo on mobile data
works.

### Desktop (1280×800)

1. Import → choose a screenshot. While "Reading shift details…" shows:
   - **Cancel** is visible;
   - the X is enabled;
   - "Choose a different screenshot" is visible.
2. Tap **Cancel**. The drop zone comes back immediately, with no error
   message. In the Network tab, the `import-preview` request shows as
   "(canceled)". Wait 30 s: the form never fills in on its own.
3. Choose a screenshot. While it reads, click **Choose a different
   screenshot**. The file picker opens straight away. Pick another
   screenshot: only the second one's details appear.
4. Pick the same file twice in a row, using the X in between. The second
   pick reads again rather than doing nothing.
5. **Timeout:** throttle to "Offline" right after choosing a file, and
   wait 60 s. To check faster, change `IMPORT_TIMEOUT_MS` to `5000` in a
   local build only. The message "Reading this screenshot took too long…" appears, and the
   file card stays with both buttons.

### Phone (375×667 and 430×932, then a real iPhone, both in Safari and from the Home Screen)

6. With no horizontal scroll, the file card's X sits fully inside the card.
   `document.documentElement.scrollWidth === innerWidth`.
7. Check the sizes:
   - "Choose a different screenshot" spans the panel and is at least 44px
     tall;
   - Cancel is at least 44×44.
8. **The reported bug, on the iPhone:** Import → tap the drop zone → tap
   the wrong photo. The gallery closes and the read starts. Under the
   thumbnail, tap **Choose a different screenshot**. The gallery opens
   again straight away: no extra tap, and no waiting for the read to
   finish. Pick the right photo: only its details appear in the form. Do
   this both in Safari and from the Home Screen app.
9. Repeat step 8, but wait for the wrong photo's preview to appear first,
   then tap **Choose a different screenshot**. The gallery opens the same
   way, and the old preview is cleared.
10. On Android in the Play app, steps 8 and 3 behave the same. The system
   back button still leaves the Import screen as before.

## 8. Commit message

```
fix(import): let drivers reopen the gallery after a wrong photo

On an iPhone the photo picker closes as soon as a photo is tapped,
so a mis-tap could not be undone there, and FlexBuddy gave no way
back to the gallery: the remove button was disabled for as long as
the server took to read the image, Start over only appears once a
preview exists, and a slow read could keep the spinner going for
over a minute.

A Choose a different screenshot button now appears as soon as a
photo is picked. It stops any read in progress and reopens the
gallery in the same tap. The remove button stays enabled and the
reading row has a Cancel button. Each upload can be
aborted, a response that arrives after a cancel or a newer pick is
ignored, and a read that takes over a minute stops with a message
saying so.
```

## 9. Open questions

1. **Pick in the gallery before leaving it.** iOS shows a selection
   screen with an **Add** button, instead of closing on the first tap,
   when the input allows several files (`multiple`). That would let a
   driver change their mind *inside* the gallery. The cost: every import
   needs one more tap, and drivers could select two photos, of which
   only the first would be read. I recommend the button in this plan
   first. Say if you'd also like `multiple` with first-photo-only
   handling.

2. **iOS swipe-back between screens.** FlexBuddy switches screens with
   `history.replaceState`, so swiping back from the right edge on an
   iPhone never returns to Home. Changing that to `pushState` affects
   every screen and the Android back button, so it should be its own
   plan. Do you want it?
3. **Timeout length.** 60 s is generous for Render's single CPU after
   `plan/import-memory-limits`, where a read usually takes 3–10 s. Say if
   you'd prefer 30 s.
