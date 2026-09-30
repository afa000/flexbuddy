# Plan 17: On-device OCR (read screenshots on the phone)

Planned against `origin/main` at `eef0595` (latest migration V20). **No migration.** This
replaces plan 11, receipt photos, which is on hold; its branch is left untouched.

## 0. Go / no-go first (before any code is committed)

The draft's biggest risk is that tesseract.js reads Flex screenshots worse than the server
does. That's cheap to measure before building anything, so the implementing session does
this first, **locally and without committing anything**.

1. In a scratch folder **outside the repo**, run `npm install tesseract.js@7`. Write a
   20-line Node script that reads each screenshot with the same settings this plan uses:
   `eng` from `flexbuddy/src/main/resources/tessdata/`, the LSTM engine, text-line level.
   It prints the lines with their confidence.
2. Use **10 of your own real earnings screenshots**. They are personal data, so never copy
   them into the repo.
   - Import each one on the live server as normal, and note the station, date, start, end,
     base pay and tips it fills in.
   - For the phone engine, compare the script's lines with the server's "View extracted
     text" for the same screenshot. The fields that matter are the six the parser reads.
     For a stricter check, build section 3's text endpoint on a local branch that is never
     pushed, and post the lines to it.
3. **Go** if the phone engine gives the **same six fields on at least 9 of 10**
   screenshots. **Otherwise stop**, write the results into this plan, and don't ship.

Only after a "go" does the rest of this plan get built.

## 1. Goal and scope

An optional setting, **"Read screenshots on this phone"**:

- With it on, the import screen reads the screenshot with tesseract.js on the device. It
  then sends only the **recognised lines** (a few KB of JSON) to a new endpoint, which runs
  the **same** parser, warning rules and scheduled-block matcher as today. The screenshot
  itself never leaves the phone.
- It's **off by default**. Server OCR stays exactly as it is, and it's the automatic
  fallback.

### Corrections to the draft

These change what the feature is worth; see the pros and cons in section 10.

1. **It doesn't make importing work offline.** Parsing stays on the server, as the draft
   intends, and the preview also matches the reading against the driver's scheduled
   blocks, which needs the database. With no connection, the import still can't produce a
   review form. The draft's line "Offline with the option on, import works end to end
   except saving" isn't achievable without copying the parser into JavaScript, which would
   mean two parsers drifting apart. Out of scope.
2. **"Doesn't wait for a sleeping Render instance" doesn't apply.** The web service is on
   Render's paid `starter` plan, which doesn't spin down.
3. **The download is about 7 MB, not 12 MB:**
   - the engine: `tesseract-core-simd-lstm.wasm`, 2.9 MB (from `tesseract.js-core` 7);
   - your existing `eng.traineddata`: 4.1 MB, the fast LSTM model;
   - `tesseract.min.js` plus the worker script: about 0.2 MB.

   It's downloaded once per phone, only when the setting is turned on.
4. **Assets are served under `/ocr/`, not `/js/vendor/`.** The service worker caches
   `/js/**` in a cache named after each build, and deletes it on every deploy. That would
   re-download 7 MB after every push. The OCR files get their own cache, named after the
   tesseract.js version instead of the build.
5. **The iPhone check is dropped.** The app ships on Android. The website works on iPhone,
   but it isn't a target for this plan.

### Out of scope

- Offline import.
- Running the parser in the browser.
- Making it the default.
- Other languages.
- Receipts.
- Removing server OCR.

## 2. Data model and migration

None. The setting is per device, stored in `localStorage` as `flexbuddy-ocr-local` with
value `on`. It is never sent to the server.

## 3. Backend

### New endpoint: `POST /shifts/import-preview/text`

**New `dto/OcrTextPreviewRequest.java`** (record):

```java
public record OcrTextPreviewRequest(
        @NotNull @Size(max = 500) List<@Valid Line> lines,
        @Size(max = 255) String originalFilename) {
    public record Line(@NotNull @Size(max = 500) String text,
                       @Min(0) @Max(100) int confidence,
                       @Min(0) int x, @Min(0) int y, @Min(0) int width, @Min(0) int height) {}
}
```

- There's no client `meanConfidence`. The server computes it with `OcrResult.fromLines`,
  exactly as for server OCR, so both engines' confidence scores are calculated the same way.
- **The total text must be at most 20,000 characters.** Check it in the service, and throw
  `InvalidScreenshotException("The recognised text is too long.")`.

**`service/ShiftImportService.java`:**

- **Extract** everything after `ocrResult = textExtractor.extract(image)` in
  `createPreview` (lines ~106–147 at `eef0595`) into
  `private ShiftImportPreviewResponse preview(String email, OcrResult ocrResult, String filename, String contentType, long size)`.
  `createPreview` calls it. Its behaviour and its tests don't change.
- **Add `createPreviewFromText(String email, OcrTextPreviewRequest request)`:**
  - Map the lines to `OcrLine(text, confidence, x, y, width, height, index)`, with `index`
    being the position in the list.
  - `OcrResult.fromLines(...)`.
  - Then `preview(email, result, filename, "text/plain", 0L)`, since no image was received.
  - **Before the parser, apply the same line cleanup the server applies to its own output.**
    `TesseractScreenshotTextExtractor` (lines ~48–58 at `eef0595`) skips lines whose text is
    null or blank, rounds the confidence, and numbers the kept lines from 0 in order. Move
    that into one static helper, `OcrLines.fromRaw(...)`, and call it from both paths, so
    the parser always gets the same input.
- `ShiftImportPreviewResponse` **doesn't change**. It still carries `text`, `lines`,
  `meanConfidence` and `candidates`.

**`controller/ShiftController.java`:** add

```java
@PostMapping(value = "/import-preview/text", consumes = MediaType.APPLICATION_JSON_VALUE)
public ShiftImportPreviewResponse importPreviewFromText(Principal p, @Valid @RequestBody OcrTextPreviewRequest r)
```

It needs sign-in and CSRF, as every POST does.

### Serving the OCR assets

- **Vendored files** go under `src/main/resources/static/ocr/tesseract/`, copied from the npm
  tarballs of `tesseract.js@7.0.0` and its matching `tesseract.js-core@7`:
  - `tesseract.min.js`
  - `worker.min.js`
  - `tesseract-core-simd-lstm.wasm.js` and `tesseract-core-simd-lstm.wasm`
  - `tesseract-core-lstm.wasm.js` and `tesseract-core-lstm.wasm`, the fallback for phones
    without SIMD, which is rare on Android Chrome but real
  - `LICENSE` (Apache-2.0) and a `VERSION` file naming the two versions.

  Nothing is added to `pom.xml`. Maven's resource filtering only touches `sw.js` and the
  properties files (checked at `eef0595`), so the `.wasm` files are copied byte for byte.
- **The language file isn't duplicated.** Map `/ocr/tessdata/**` to `classpath:/tessdata/` in
  a new `config/OcrAssetsConfig.java` (`WebMvcConfigurer.addResourceHandlers`), with a
  one-year cache period. That serves the existing `eng.traineddata`, and a Tesseract update
  on the server updates the phone too.
- **`SecurityConfig`:** add `"/ocr/**"` to the `permitAll` list. These are public static
  files with no user data, and `/js/**` is already public.
- **`application.properties`** (optional): `server.compression.enabled=true` with
  `server.compression.mime-types=application/octet-stream,application/wasm,...`. It saves
  about 2 MB on the language file's first download. **Include it only if a quick check shows
  no effect on the existing JSON responses.** Otherwise skip it; it's a one-time download.

## 4. Frontend

### New file `static/js/ocr-local.js`

Add it to the page's script tags and to `VERSIONED_ASSETS`. It's small, so it's precached
like the others. It exposes
`window.flexbuddyOcrLocal = {isOn, setOn, prepare, isReady, recognise, remove, sizeOnDevice, preprocessPlan}`.

**`prepare(onProgress)`:**

- Fetch every asset under `/ocr/` into Cache Storage `flexbuddy-ocr-7.0.0`, the version from
  `VERSION`, reporting bytes as they arrive (this drives the progress bar).
- On success, `setOn(true)`. On failure, clean up the partial cache and leave the setting
  off.

**`recognise(blob)`:**

1. Load `tesseract.min.js` lazily, with a `<script>` from `/ocr/tesseract/`, the first time.
2. `Tesseract.createWorker('eng', 1, {workerPath, corePath: '/ocr/tesseract/', langPath: '/ocr/tessdata', gzip: false, cacheMethod: 'none'})`:
   - `1` is LSTM only.
   - `cacheMethod: 'none'` stops tesseract.js keeping its own IndexedDB copy, because the
     service worker cache is the one copy, so **Remove** clears everything.
   - Keep **one worker** for the page's lifetime.
3. **Preprocess on a canvas**, mirroring `ScreenshotPreprocessor`:
   - upscale so the short side is 1200 px, at most ×3;
   - grayscale;
   - invert if the mean is below 110;
   - the same threshold rule (read `ScreenshotPreprocessor.threshold`, and port it
     line for line);
   - 20 px white padding.

   Put the sizing and the histogram-threshold choice in **pure functions**,
   `preprocessPlan(width, height)` and `chooseThreshold(histogram)`, for `node --test`.
4. Set the page segmentation mode as the server does: `4` when height is more than
   1.6 × width, else `6`. Also set `user_defined_dpi = 300` and `preserve_interword_spaces = 1`.
5. `worker.recognize(canvas, {}, {blocks: true})`. Flatten to **text lines**:
   `{text, confidence: Math.round(line.confidence), x: bbox.x0, y: bbox.y0, width: x1 - x0, height: y1 - y0}`.
6. **Time limit: 25 s.** After that, terminate the worker and throw `LocalOcrTimeout`.

**`remove()`:** delete the `flexbuddy-ocr-*` caches, terminate the worker, and `setOn(false)`.

**`sizeOnDevice()`:** the sum of the `Content-Length` of the cached responses.

### `app.js`, `processScreenshot` (line ~440)

- **If** `flexbuddyOcrLocal.isOn() && await isReady()`:
  1. Show "Reading on your phone…".
  2. `lines = await recognise(file)`.
  3. `POST /shifts/import-preview/text` with `{lines, originalFilename: file.name}`.
  4. `populatePreview(preview)`, exactly as now.
- **On `LocalOcrTimeout`, or if the worker fails to load:** show the toast "Reading on the
  phone didn't work · using the server", and fall back to the existing upload. Skip the
  fallback when offline; the existing offline message applies.
- **Otherwise:** the existing upload path, unchanged.
- The shared-screenshot path (`importSharedScreenshot`) goes through `processScreenshot`, so
  it gets the same choice for free.
- The review form, the "View extracted text" panel and saving don't change.

### Account page (`account.html` and `account.js`)

A new card, **"Reading screenshots"**:

- A toggle, **"Read screenshots on this phone"**, with the hint: "Your screenshot stays on
  this phone; only the text it finds is sent. Needs a one-time download of about 7 MB."
- **Turning it on**:
  - If `navigator.connection?.type === 'cellular'`, or `saveData` is set, ask first: "This
    downloads about 7 MB. Continue on mobile data?"
  - Then `prepare()` with a `<progress>` bar, then "Ready · 7.2 MB on this phone".
- **Turning it off** keeps the files, so turning it on again is instant.
- **Remove downloaded files** calls `remove()` and shows "0 MB".
- The card says this setting is **for this phone only**.
- It's `data-online-only` while not yet downloaded. Once ready, it can be toggled offline.

### `sw.js`

- **Add `/ocr/` handling before `STATIC_PATHS`:** cache-first from `flexbuddy-ocr-*`, **no
  network write-back** (only `prepare()` fills that cache).
- **Add `key.startsWith('flexbuddy-ocr-')` to the keep set in `activate`**, so deploys don't
  wipe it. `clearUserData` also leaves it: it's not personal data.
- `VERSIONED_ASSETS` gets `/js/ocr-local.js`. `StaticAssetsTest` enforces this.
- Add `/^\/shifts\/import-preview\/text$/` to `NETWORK_ONLY`. The existing
  `/^\/shifts\/import-preview$/` ends with `$`, so it doesn't match the new path. The
  endpoint is a POST, which the worker never intercepts, but the list should stay complete.

### CSS

The new card uses the existing `.data-card` and `.settings-form` classes. `<progress>` gets
`width: 100%; height: 12px;`. Controls are at least 44px.

### Privacy policy and Play

- **`privacy.html`, "Screenshot imports":** add "If you turn on *Read screenshots on this
  phone*, the screenshot is read on your device and only the recognised text is sent to
  FlexBuddy."
- **`play-app-content.md`:** no change. Photos are still processed ephemerally by the server
  when the setting is off.

## 5. Ripple list

**Recurring rework items:**

| Item | Status |
|---|---|
| `AccountSettingsResponse`, backup format (v4), `BlockEvaluationResponse`, `ShiftResponse` | **Not touched.** |
| `ShiftImportPreviewResponse` | Not touched. Both paths build it the same way. |
| New account data | None. The setting and files live on the device. |
| `sw.js` | New `/ocr/` cache rule. **The activate keep-list includes `flexbuddy-ocr-*`.** `VERSIONED_ASSETS` gets `ocr-local.js`. `NETWORK_ONLY` gets the text endpoint. |
| Boxed request fields | Not applicable. New request only. |
| Phone layout | One card with a toggle, a progress bar and a button, all at least 44px. |
| Dates | None. |
| Money and tax wording | Not applicable. |
| Repo size | About 14 MB of vendored files: both engine builds, as `.wasm` and `.wasm.js`. **Vendor only the `.wasm` builds**, not the `.wasm.js` builds, if `corePath` loading works with them in the go/no-go spike. That brings it to about 6 MB. |

**Existing code that changes:**

| File | Change |
|---|---|
| `ShiftImportService` | The shared `preview(...)` extracted, plus `createPreviewFromText`. `createPreview` behaves the same. |
| `ShiftController` | + endpoint |
| `SecurityConfig` | + `/ocr/**` |
| `app.js` | `processScreenshot` branch |
| `sw.js` | As above |
| `account.html`, `account.js` | Card |
| `privacy.html` | One sentence |

**Existing tests:** `ShiftImportServiceTest` must keep passing unchanged; it guards the
refactor.

## 6. Tests to add

Expect about 8 Java tests and 5 JavaScript tests.

**`service/ShiftImportServiceTest.java`:**

1. `textPreviewGivesTheSameCandidateAsTheImagePathForTheSameLines`:
   - Stub the extractor to return fixture lines L for an image.
   - Call `createPreview`, and call `createPreviewFromText` with the same L.
   - The candidates are equal: station, date, start, end, base pay, tips, warnings,
     suggested status. `meanConfidence` is equal too.
2. `textPreviewRejectsOver500LinesAndOver20000Characters`
3. `textPreviewRunsTheScheduledBlockMatcher`: the matcher is called with the parsed date,
   times and station.
4. `textPreviewAppliesTheSameLineCleanupAsServerOcr`: blank and whitespace lines are dropped,
   and indexes are reassigned in order.

**`controller/ShiftControllerTest.java`:**

5. `textPreviewRequiresSignInAndCsrf`
6. `textPreviewValidatesLineFields`: confidence 101 gives 400. A missing `lines` gives 400.
7. `textPreviewReturnsThePreviewJson`

**`controller/PageControllerTest.java` or a new `OcrAssetsConfigTest`:**

8. `theLanguageFileIsServedPubliclyFromTheClasspath`: `GET /ocr/tessdata/eng.traineddata`
   without sign-in gives 200, with length 4,113,088 and a long-lived `Cache-Control`.

**JavaScript (`src/test/js/ocr-local.test.js`):**

1. `preprocessPlan(400, 800)` gives a 3× upscale, capped, to 1200×2400.
2. `preprocessPlan(1500, 3000)` gives no scaling.
3. `chooseThreshold` agrees with Java on 3 small hand-made histograms. The test holds the
   Java results, which you get once by calling `ScreenshotPreprocessor.threshold` in a
   throwaway JUnit run or a `jshell` session.
4. The segmentation-mode rule: height more than 1.6 × width gives 4, otherwise 6.
5. The flattened line shape from a fake tesseract.js result gives the right
   `x, y, width, height` and rounded confidence.

## 7. Manual checks

1. **Go / no-go (section 0)** is recorded in this plan, with results for all 10 screenshots.
2. **Mid-range Android phone, Play app, Wi-Fi.** Account page, then Reading screenshots,
   then on:
   - the progress bar runs to "Ready · about 7 MB";
   - Import a screenshot: "Reading on your phone…", and the review form is filled **within
     10 s**.
   - In `chrome://inspect`'s Network tab, **no image request** is made. Only `POST
     /shifts/import-preview/text`, a few KB, is sent.
3. **The same screenshot** with the setting off gives the same six fields.
4. **Mobile data.** Turn it on over cellular: the 7 MB question appears first. Cancel, and
   nothing downloads.
5. **Deploy survival.** After a new deploy, which changes the build id, the setting is
   still "Ready" and reading starts with no download (Network tab).
6. **Fallback.** Block `/ocr/tesseract/worker.min.js` in DevTools. The import shows "Reading
   on the phone didn't work · using the server" and finishes through the upload.
7. **Remove.** "Remove downloaded files" shows 0 MB, Cache Storage has no `flexbuddy-ocr-*`,
   and the next import uses the server.
8. **Shared screenshot.** Share an image from Google Photos to FlexBuddy with the setting
   on. It's read on the phone.
9. **Old or slow phone.** It either reads within 25 s or falls back to the server. It never
   hangs.

## 8. Suggested commit message

```
feat(import): read screenshots on the phone when the driver chooses

A new setting on the account page reads earnings screenshots on the
phone instead of the server. After a one-time download of about 7 MB,
the screenshot never leaves the phone: only the text found in it is
sent, and the server turns that text into the review form with the same
parser, checks and scheduled-block matching as before, so both ways of
reading give the same result.

The setting is off by default and belongs to the phone, not the
account. If reading on the phone fails or takes more than 25 seconds,
the import quietly uses the server instead. The downloaded files are
kept across app updates and can be removed from the same card.
```

## 9. Open questions

1. **Is the go/no-go bar right?** The same six fields on 9 of 10 screenshots.
2. **Vendoring.** Is it all right to commit about 6 MB of third-party engine files into the
   repo? Git keeps every version forever, so each tesseract.js upgrade adds another 6 MB to
   the history.
3. **Priority.** See the pros and cons below. Does this justify its cost now, or should it
   wait until testers ask about data use or privacy?

## 10. Pros and cons

**Pros:**

- **The screenshot never leaves the phone.** Earnings screenshots show pay and sometimes
  location. That's a real privacy story for the store listing and the privacy policy.
- **Much less mobile data per import.** A Flex screenshot is 1–3 MB to upload. The text is
  a few KB. On a weak signal at a station, this is the difference between waiting and not.
- **Less load on the small server.** Tesseract is the heaviest thing the app does, on a
  512 MB instance. Every import done on the phone is one the server skips.
- **No change for anyone who leaves it off.** Server OCR stays, and it's the fallback, so
  the risk is contained.
- **One parser.** Parsing stays on the server, so both engines produce identical previews,
  and parser fixes apply to both.

**Cons:**

- **It doesn't make import work offline.** That was the draft's headline benefit, but the
  preview needs the server for parsing and scheduled-block matching.
- **It doesn't make import faster** in any way you'd notice. The server doesn't sleep, and
  a phone takes a few seconds to read, about as long as the upload and server OCR.
- **Two OCR engines can disagree.** tesseract.js 7 and the server's Tesseract 5.5 won't
  always read the same text, and the canvas preprocessing is a second copy of Java code
  that must stay in step with it. The go/no-go test manages this risk but doesn't remove it.
- **A 7 MB download per phone,** repeated if Android's "Clear storage" is used.
- **About 6 MB of third-party files in the repo,** updated by hand, with no npm to track
  security fixes.
- **More to test on real phones.** Speed and accuracy vary by device.
- **Little impact on the closed test.** Server import already works. The gain is data use
  and privacy, not a missing capability.

**My overall view:** it's a solid, low-risk optional feature, **but a lower priority than
anything that helps your closed test succeed.** If you do it, do the section 0 go/no-go
first. It's an afternoon's work, and it tells you whether the rest is worth building.
